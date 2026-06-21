package com.kraftshala.senselink

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.webkit.WebViewAssetLoader
import java.io.IOException

class MainActivity : ComponentActivity() {

    private var webView: WebView? = null
    private var llmBridge: LlmBridge? = null
    private var pendingWebRequest: PermissionRequest? = null

    fun registerLlm(bridge: LlmBridge) {
        llmBridge = bridge
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        val req = pendingWebRequest
        pendingWebRequest = null
        if (req != null) {
            val grantable = req.resources.filter { res ->
                osPermissionsFor(res).all { isGranted(it) }
            }.toTypedArray()
            if (grantable.isNotEmpty()) req.grant(grantable) else req.deny()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val upfront = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
            .filter { !isGranted(it) }
            .toTypedArray()
        if (upfront.isNotEmpty()) permissionLauncher.launch(upfront)

        WebView.setWebContentsDebuggingEnabled(true)
        setContent { SenseLinkApp(onWebViewCreated = { webView = it }) }
    }

    fun handleWebPermissionRequest(request: PermissionRequest) {
        runOnUiThread {
            val requiredOs = request.resources.flatMap { osPermissionsFor(it) }.distinct()
            val missing = requiredOs.filter { !isGranted(it) }
            if (missing.isEmpty()) {
                request.grant(request.resources)
            } else {
                pendingWebRequest = request
                permissionLauncher.launch(missing.toTypedArray())
            }
        }
    }

    private fun osPermissionsFor(resource: String): List<String> = when (resource) {
        PermissionRequest.RESOURCE_VIDEO_CAPTURE -> listOf(Manifest.permission.CAMERA)
        PermissionRequest.RESOURCE_AUDIO_CAPTURE -> listOf(Manifest.permission.RECORD_AUDIO)
        else -> emptyList()
    }

    private fun isGranted(p: String): Boolean =
        ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

    override fun onResume() {
        super.onResume()
        webView?.onResume()
    }

    override fun onPause() {
        webView?.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        llmBridge?.close()
        llmBridge = null
        webView?.destroy()
        webView = null
        super.onDestroy()
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun SenseLinkApp(onWebViewCreated: (WebView) -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val assetLoader = WebViewAssetLoader.Builder()
                    .addPathHandler("/assets/", MimeAwareAssetsHandler(ctx))
                    .build()
                val host = ctx.findMainActivity()

                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setBackgroundColor(Color.parseColor("#0E0E10"))

                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        mediaPlaybackRequiresUserGesture = false
                        allowFileAccess = false
                        allowContentAccess = false
                    }

                    webViewClient = object : WebViewClient() {
                        override fun shouldInterceptRequest(
                            view: WebView,
                            request: WebResourceRequest
                        ): WebResourceResponse? =
                            assetLoader.shouldInterceptRequest(request.url)
                    }

                    webChromeClient = object : WebChromeClient() {
                        override fun onPermissionRequest(request: PermissionRequest) {
                            if (host != null) host.handleWebPermissionRequest(request)
                            else request.grant(request.resources)
                        }
                    }

                    if (host != null) {
                        val bridge = LlmBridge(host, this)
                        addJavascriptInterface(bridge, "AndroidLLM")
                        host.registerLlm(bridge)
                        bridge.initIfPresent()
                    }

                    loadUrl("https://appassets.androidplatform.net/assets/www/index.html")
                    onWebViewCreated(this)
                }
            }
        )
    }
}

private fun Context.findMainActivity(): MainActivity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is MainActivity) return c
        c = c.baseContext
    }
    return null
}

private class MimeAwareAssetsHandler(context: Context) : WebViewAssetLoader.PathHandler {

    private val assets = context.assets

    override fun handle(path: String): WebResourceResponse? {
        val assetPath = path.trimStart('/')
        return try {
            val stream = assets.open(assetPath)
            val mime = mimeFor(assetPath)
            val encoding = if (
                mime.startsWith("text/") ||
                mime.endsWith("javascript") ||
                mime.endsWith("json") ||
                mime == "image/svg+xml"
            ) "utf-8" else null
            WebResourceResponse(mime, encoding, stream).apply {
                responseHeaders = mapOf(
                    "Access-Control-Allow-Origin" to "*",
                    "Cache-Control" to "no-cache"
                )
            }
        } catch (e: IOException) {
            null
        }
    }

    private fun mimeFor(p: String): String = when {
        p.endsWith(".js") || p.endsWith(".mjs") -> "text/javascript"
        p.endsWith(".wasm") -> "application/wasm"
        p.endsWith(".html") || p.endsWith(".htm") -> "text/html"
        p.endsWith(".css") -> "text/css"
        p.endsWith(".json") -> "application/json"
        p.endsWith(".woff2") -> "font/woff2"
        p.endsWith(".woff") -> "font/woff"
        p.endsWith(".ttf") -> "font/ttf"
        p.endsWith(".svg") -> "image/svg+xml"
        p.endsWith(".png") -> "image/png"
        p.endsWith(".jpg") || p.endsWith(".jpeg") -> "image/jpeg"
        p.endsWith(".task") || p.endsWith(".bin") || p.endsWith(".data") -> "application/octet-stream"
        else -> "application/octet-stream"
    }
}
