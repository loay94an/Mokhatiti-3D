package com.loay94an.mokhatiti3d

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

class MainActivity : ComponentActivity() {
    private lateinit var engineView: WebView
    private var filePathCallback: ValueCallback<Array<Uri>>? = null

    private val filePicker = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val callback = filePathCallback ?: return@registerForActivityResult
        val data = result.data
        val uris = if (result.resultCode == RESULT_OK && data != null) {
            val clip = data.clipData
            when {
                clip != null -> Array(clip.itemCount) { index -> clip.getItemAt(index).uri }
                data.data != null -> arrayOf(data.data!!)
                else -> null
            }
        } else null
        callback.onReceiveValue(uris)
        filePathCallback = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_RTL
        setContentView(R.layout.activity_main)

        engineView = findViewById(R.id.engineView)
        engineView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = false
            displayZoomControls = false
            mediaPlaybackRequiresUserGesture = true
            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }
        engineView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        engineView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                val uri = request.url
                return uri.scheme != "file" && uri.scheme != "about"
            }
        }
        engineView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView,
                callback: ValueCallback<Array<Uri>>,
                params: FileChooserParams
            ): Boolean {
                filePathCallback?.onReceiveValue(null)
                filePathCallback = callback
                return try {
                    val intent = params.createIntent().apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                    }
                    filePicker.launch(intent)
                    true
                } catch (_: Exception) {
                    filePathCallback = null
                    Toast.makeText(
                        this@MainActivity,
                        "تعذر فتح منتقي الملفات",
                        Toast.LENGTH_SHORT
                    ).show()
                    false
                }
            }

            override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                android.util.Log.d(
                    "MokhatitiEngine",
                    "${message.message()} — ${message.sourceId()}:${message.lineNumber()}"
                )
                return true
            }
        }

        engineView.loadUrl("file:///android_asset/engine/test/testHome.html?skipTests=true")
    }

    override fun onBackPressed() {
        if (::engineView.isInitialized && engineView.canGoBack()) {
            engineView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        if (::engineView.isInitialized) {
            engineView.stopLoading()
            engineView.destroy()
        }
        super.onDestroy()
    }
}
