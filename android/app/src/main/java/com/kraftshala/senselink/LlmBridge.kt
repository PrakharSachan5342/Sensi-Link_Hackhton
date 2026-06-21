package com.kraftshala.senselink

import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.activity.ComponentActivity
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class LlmBridge(
    private val activity: ComponentActivity,
    private val webView: WebView,
) {
    private val worker = Executors.newSingleThreadExecutor()

    @Volatile private var engine: LlmInference? = null
    @Volatile private var ready = false
    @Volatile private var statusText = "MODEL NOT INSTALLED"

    private fun modelFile(): File {
        val ext = activity.getExternalFilesDir(null)
        if (ext != null) {
            val extModel = File(ext, MODEL_NAME)
            if (extModel.exists()) return extModel
        }
        val dir = File(activity.filesDir, "llm").apply { mkdirs() }
        return File(dir, MODEL_NAME)
    }

    fun initIfPresent() {
        val f = modelFile()
        if (!f.exists() || f.length() < 1_000_000L) {
            statusText = "MODEL NOT INSTALLED"
            return
        }
        worker.execute {
            try {
                statusText = "LOADING GEMMA…"
                val options = LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(f.absolutePath)
                    .setMaxTokens(512)
                    .build()
                engine = LlmInference.createFromOptions(activity, options)
                ready = true
                statusText = "GEMMA 2B · ON-DEVICE"
            } catch (e: Throwable) {
                ready = false
                statusText = "LOAD FAILED (${e.message})"
            }
        }
    }

    @JavascriptInterface fun isReady(): Boolean = ready

    @JavascriptInterface fun status(): String = statusText

    @JavascriptInterface fun modelPath(): String = modelFile().absolutePath

    @JavascriptInterface
    fun compose(id: Int, tokensJson: String, locale: String) {
        val eng = engine
        if (eng == null || !ready) {
            deliver(id, "")
            return
        }
        worker.execute {
            try {
                val arr = JSONArray(tokensJson)
                val sb = StringBuilder()
                for (i in 0 until arr.length()) {
                    if (i > 0) sb.append(' ')
                    sb.append(arr.getString(i))
                }
                val prompt = "<start_of_turn>user\n" +
                    "Rewrite these sign-language gesture tokens into ONE short, natural, polite spoken sentence. " +
                    "Reply with only the sentence, no preamble or quotes. Locale: $locale. Tokens: $sb" +
                    "<end_of_turn>\n<start_of_turn>model\n"
                val raw = eng.generateResponse(prompt) ?: ""
                deliver(id, clean(raw))
            } catch (e: Throwable) {
                deliver(id, "")
            }
        }
    }

    @JavascriptInterface
    fun ensureModel(url: String) {
        if (modelFile().exists()) {
            initIfPresent()
            return
        }
        if (url.isBlank()) {
            progress(-1, "NO MODEL URL SET")
            return
        }
        worker.execute {
            val dir = File(activity.filesDir, "llm").apply { mkdirs() }
            val dest = File(dir, MODEL_NAME)
            val tmp = File(dir, "$MODEL_NAME.part")
            try {
                progress(0, "DOWNLOADING GEMMA…")
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 30000
                    readTimeout = 60000
                }
                val total = conn.contentLengthLong
                conn.inputStream.use { input ->
                    tmp.outputStream().use { out ->
                        val buf = ByteArray(1 shl 16)
                        var read: Int
                        var done = 0L
                        var lastPct = -1
                        while (input.read(buf).also { read = it } >= 0) {
                            out.write(buf, 0, read)
                            done += read
                            if (total > 0) {
                                val pct = (done * 100 / total).toInt()
                                if (pct != lastPct) {
                                    lastPct = pct
                                    progress(pct, "DOWNLOADING GEMMA…")
                                }
                            }
                        }
                    }
                }
                if (tmp.renameTo(dest)) {
                    progress(100, "MODEL READY")
                    initIfPresent()
                } else {
                    tmp.delete()
                    progress(-1, "SAVE FAILED")
                }
            } catch (e: Throwable) {
                tmp.delete()
                progress(-1, "DOWNLOAD FAILED (${e.message})")
            }
        }
    }

    private fun clean(s: String): String =
        s.trim().substringBefore('\n').trim().removeSurrounding("\"").trim()

    private fun deliver(id: Int, sentence: String) {
        val js = "window.__onLLM && window.__onLLM($id, ${JSONObject.quote(sentence)})"
        webView.post { webView.evaluateJavascript(js, null) }
    }

    private fun progress(pct: Int, msg: String) {
        val js = "window.__onLLMDownload && window.__onLLMDownload($pct, ${JSONObject.quote(msg)})"
        webView.post { webView.evaluateJavascript(js, null) }
    }

    fun close() {
        try {
            engine?.close()
        } catch (_: Throwable) {
        }
        worker.shutdownNow()
    }

    companion object {
        private const val MODEL_NAME = "gemma.task"
    }
}
