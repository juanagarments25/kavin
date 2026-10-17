package com.kavin.linkcontrolx

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

/**
 * QR Login — a thin Android wrapper around the web app bundled in app/src/main/assets.
 * The WebView loads index.html locally, so the app works offline. It also:
 *   - grants the page camera access (for the QR scanner) after the OS permission, and
 *   - wires the photo-capture <input type=file> fallback to the Android file picker.
 */
class MainActivity : ComponentActivity() {

    private lateinit var web: WebView
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var pendingWebPermission: PermissionRequest? = null

    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        pendingWebPermission?.let { req ->
            if (granted) req.grant(req.resources) else req.deny()
            pendingWebPermission = null
        }
    }

    private val fileChooser = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val cb = filePathCallback ?: return@registerForActivityResult
        val data = result.data
        val uris = if (result.resultCode == RESULT_OK && data?.data != null) arrayOf(data.data!!) else emptyArray()
        cb.onReceiveValue(uris)
        filePathCallback = null
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        web = WebView(this)
        setContentView(web)

        with(web.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true          // localStorage for the saved number/codes
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = true
        }
        web.webViewClient = WebViewClient()
        web.webChromeClient = object : WebChromeClient() {
            // Page asked for camera (getUserMedia)
            override fun onPermissionRequest(request: PermissionRequest) {
                if (request.resources.any { it == PermissionRequest.RESOURCE_VIDEO_CAPTURE }) {
                    if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.CAMERA)
                        == PackageManager.PERMISSION_GRANTED) {
                        request.grant(request.resources)
                    } else {
                        pendingWebPermission = request
                        cameraPermission.launch(Manifest.permission.CAMERA)
                    }
                } else request.deny()
            }
            // Photo-capture fallback (<input type="file" accept="image/*" capture>)
            override fun onShowFileChooser(
                webView: WebView?, callback: ValueCallback<Array<Uri>>?,
                params: FileChooserParams?,
            ): Boolean {
                filePathCallback?.onReceiveValue(null)
                filePathCallback = callback
                val intent = params?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = "image/*"; addCategory(Intent.CATEGORY_OPENABLE)
                }
                return try { fileChooser.launch(intent); true }
                catch (e: Exception) { filePathCallback = null; false }
            }
        }

        if (savedInstanceState == null) web.loadUrl("file:///android_asset/index.html")
    }

    override fun onBackPressed() {
        if (web.canGoBack()) web.goBack() else super.onBackPressed()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState); web.saveState(outState)
    }
    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState); web.restoreState(savedInstanceState)
    }
}
