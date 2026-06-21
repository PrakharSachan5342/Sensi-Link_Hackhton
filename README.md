# SENSE-LINK

**An offline, on-device edge-AI app that lets Deaf and hard-of-hearing people and hearing people
hold a real conversation — no internet required.**

A signer signs to the camera; the phone watches the hands, turns the signs into a fluent spoken
sentence, and says it out loud. A hearing person replies by voice; the reply appears as text with a
haptic cue. Everything — vision, language, speech — runs locally on the phone, so it keeps working
in metros, elevators, clinics and anywhere the network drops.

---

## The problem

Assistive translation tools fail in three ways that matter most exactly when they're needed:

1. **Broken, undignified output.** Sign languages use spatial, topic-comment grammar, not English
   word order. Naïve translators emit telegraphic fragments like *"water give need"*. People deserve
   to be quoted in full sentences.
2. **Cloud dependence.** Most tools stream camera or audio to a server. They die in the subway, the
   basement clinic, the crowded venue — the places people actually need them.
3. **Thermal and battery cost.** Continuous computer vision plus a live cellular radio overheats and
   drains the phone within minutes.

## The approach

SENSE-LINK keeps the entire pipeline on the device:

- **Hand tracking** with MediaPipe Hand Landmarker (21 3D landmarks) on the GPU.
- A **geometric gesture classifier** that turns landmarks into semantic tokens.
- An **on-device language model** (Gemma-2B, 4-bit, via LiteRT) that rewrites token fragments into a
  natural, polite sentence — with a deterministic rule-based composer as an always-available fallback.
- **Text-to-speech** to voice the sentence, and **speech-to-text** for the reply.
- **Haptics** and a **shake-to-clear** gesture via the phone's vibrator and accelerometer.

No request ever leaves the device. The app runs in airplane mode.

---

## What's in this repository

| Path | Description |
|------|-------------|
| `index.html` | The self-contained web application (UI + full pipeline). |
| `android/` | Native Android app (Kotlin + Jetpack Compose) that hosts the pipeline and adds the on-device LLM. |
| `android/app/src/main/assets/www/` | The web app plus every dependency bundled for offline use. |
| `android/tools/fetch-assets.mjs` | Reproducible fetcher that bundles MediaPipe, the model and fonts. |
| `docs/TECHNICAL_DOCUMENTATION.md` | Full technical write-up: architecture, data flow, design decisions. |
| `docs/THEME.md` | Visual design system. |
| `releases/` | Prebuilt debug APK. |

The Android app is a genuine native application (Compose UI, builds to an APK). It hosts the web
pipeline inside a hardware-accelerated `WebView` whose assets are served from a secure in-app origin,
and it adds native capabilities — the on-device LLM and robust runtime permissions — over a small
JavaScript bridge.

---

## Build

Requirements: Android SDK with platform 34 and build-tools 34, plus a JDK 17 or newer.

```bash
cd android

# (only needed on a fresh checkout, or after editing index.html)
node tools/fetch-assets.mjs

./gradlew :app:assembleDebug          # Windows: .\gradlew.bat :app:assembleDebug
```

Output: `android/app/build/outputs/apk/debug/app-debug.apk`

| Component | Version |
|-----------|---------|
| Gradle | 8.10.2 |
| Android Gradle Plugin | 8.5.2 |
| Kotlin | 1.9.24 |
| Jetpack Compose BOM | 2024.06.00 |
| compileSdk / targetSdk | 34 |
| minSdk | 26 |

## Install

```bash
adb install -r releases/app-debug.apk
```

Grant **Camera** and **Microphone** on first launch. To prove the offline claim, switch the phone to
airplane mode before signing.

---

## On-device language model

The composer is backed by Gemma-2B (4-bit) running through LiteRT / the MediaPipe LLM Inference
runtime, exposed to the UI through the `AndroidLLM` bridge. The model weights (~1.3 GB) are not
shipped inside the APK; provision them once:

```bash
# Side-load (recommended for a controlled demo):
adb push gemma.task /sdcard/Android/data/com.kraftshala.senselink/files/gemma.task

# Or set window.LLM_MODEL_URL in index.html to a direct .task URL and the app
# downloads it on first run into the app's private storage.
```

If no model is present, the app falls back to the deterministic composer automatically, and a
short watchdog guarantees a sentence is always produced. The source tag in the UI reads `GEMMA·2B`
when the model is live and `GEMMA·INT4` on the fallback path.

---

## Roadmap

- Native `SpeechRecognizer` bridge for the reverse (voice → text) loop, with an offline language pack.
- Universal smartwatch relay: identify a paired watch over BLE (Device Information Service) and use
  the on-device model to generate the right notification payload, so a finalized sentence and its
  haptic cues reach any standards-compliant wearable.
- Expanded gesture vocabulary and a personalisation pass for individual signing styles.

## License

MIT.
