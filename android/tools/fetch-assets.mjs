
import { mkdir, writeFile, readFile } from "node:fs/promises";
import { existsSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join, resolve } from "node:path";

const __dirname = dirname(fileURLToPath(import.meta.url));
const ANDROID = resolve(__dirname, "..");
const REPO = resolve(ANDROID, "..");
const WWW = join(ANDROID, "app", "src", "main", "assets", "www");
const MP = join(WWW, "mediapipe");
const FONTS = join(WWW, "fonts");

const MP_VER = "0.10.14";
const MP_BASE = `https://cdn.jsdelivr.net/npm/@mediapipe/tasks-vision@${MP_VER}`;
const MODEL_URL =
  "https://storage.googleapis.com/mediapipe-models/hand_landmarker/hand_landmarker/float16/1/hand_landmarker.task";
const FONTS_CSS_URL =
  "https://fonts.googleapis.com/css2?family=Anton&family=Archivo:wght@400;500;600;700;800;900&family=Space+Mono:wght@400;700&display=swap";
const CHROME_UA =
  "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36";

const KB = (n) => `${(n / 1024).toFixed(1)} KB`;

async function getBuffer(url, headers = {}) {
  const res = await fetch(url, { headers, redirect: "follow" });
  if (!res.ok) throw new Error(`${res.status} ${res.statusText} for ${url}`);
  return Buffer.from(await res.arrayBuffer());
}

async function download(url, dest, headers = {}) {
  const buf = await getBuffer(url, headers);
  await mkdir(dirname(dest), { recursive: true });
  await writeFile(dest, buf);
  console.log(`  ✓ ${dest.replace(WWW, "www")}  (${KB(buf.length)})`);
  return buf.length;
}

async function main() {
  await mkdir(join(MP, "wasm"), { recursive: true });
  await mkdir(FONTS, { recursive: true });

  const htmlOnly = process.argv.includes("--html-only");
  if (!htmlOnly) {
  console.log("MediaPipe tasks-vision runtime:");
  await download(`${MP_BASE}/vision_bundle.mjs`, join(MP, "vision_bundle.mjs"));
  for (const f of [
    "vision_wasm_internal.js",
    "vision_wasm_internal.wasm",
    "vision_wasm_nosimd_internal.js",
    "vision_wasm_nosimd_internal.wasm",
  ]) {
    await download(`${MP_BASE}/wasm/${f}`, join(MP, "wasm", f));
  }

  console.log("Hand-landmark model:");
  await download(MODEL_URL, join(MP, "hand_landmarker.task"));

  console.log("Google Fonts:");
  const css = (await getBuffer(FONTS_CSS_URL, { "User-Agent": CHROME_UA })).toString("utf8");
  const urls = [...css.matchAll(/url\((https:\/\/fonts\.gstatic\.com\/[^)]+\.woff2)\)/g)].map((m) => m[1]);
  const uniq = [...new Set(urls)];
  let localCss = css;
  let ok = 0;
  for (const u of uniq) {
    const name = u.split("/").pop();
    try {
      await download(u, join(FONTS, name), { "User-Agent": CHROME_UA });
      localCss = localCss.split(u).join(`./${name}`);
      ok++;
    } catch (e) {
      console.log(`  ! skipped ${name}: ${e.message}`);
    }
  }
  await writeFile(join(FONTS, "fonts.css"), localCss, "utf8");
  console.log(`  ✓ fonts.css written (${ok}/${uniq.length} woff2 localised)`);
  } else {
    console.log("--html-only: skipping asset downloads; rewriting index.html only");
  }

  console.log("Rewriting index.html -> assets/www/index.html:");
  const srcHtml = join(REPO, "index.html");
  if (!existsSync(srcHtml)) throw new Error(`missing ${srcHtml}`);
  let html = await readFile(srcHtml, "utf8");

  const before = html;

  html = html.replace(
    /<link rel="preconnect"[^>]*>\s*<link rel="preconnect"[^>]*>\s*<link href="https:\/\/fonts\.googleapis\.com\/css2[^"]*" rel="stylesheet">/,
    '<link href="./fonts/fonts.css" rel="stylesheet">'
  );

  html = html.split(`'${MP_BASE}'`).join(`'./mediapipe/vision_bundle.mjs'`);

  html = html.split(`'${MP_BASE}/wasm'`).join(`'./mediapipe/wasm'`);

  html = html.split(`'${MODEL_URL}'`).join(`'./mediapipe/hand_landmarker.task'`);

  const checks = [
    ["./fonts/fonts.css", html.includes("./fonts/fonts.css")],
    ["./mediapipe/vision_bundle.mjs", html.includes("./mediapipe/vision_bundle.mjs")],
    ["./mediapipe/wasm", html.includes("'./mediapipe/wasm'")],
    ["./mediapipe/hand_landmarker.task", html.includes("./mediapipe/hand_landmarker.task")],
  ];
  const remoteLeft = (html.match(/https:\/\/(cdn\.jsdelivr\.net|storage\.googleapis\.com|fonts\.googleapis\.com|fonts\.gstatic\.com)/g) || []);
  for (const [label, pass] of checks) console.log(`  ${pass ? "✓" : "✗"} rewrote -> ${label}`);
  console.log(`  remote URLs remaining in html: ${remoteLeft.length}`);
  if (html === before) throw new Error("index.html unchanged — rewrite patterns did not match!");

  await mkdir(WWW, { recursive: true });
  await writeFile(join(WWW, "index.html"), html, "utf8");
  console.log(`  ✓ wrote ${join(WWW, "index.html").replace(ANDROID, "android")}`);
  console.log("\nDone. assets/www is ready for an offline build.");
}

main().catch((e) => {
  console.error("\nFETCH FAILED:", e.message);
  process.exit(1);
});
