package com.loay94an.mokhatiti3d

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
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
    private var pendingSaveBytes: ByteArray? = null

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

    private val savePicker = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val bytes = pendingSaveBytes
        pendingSaveBytes = null
        if (uri == null || bytes == null) return@registerForActivityResult
        try {
            contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) }
                ?: throw IllegalStateException("تعذر فتح ملف الحفظ")
            Toast.makeText(this, "تم حفظ ملف المشروع", Toast.LENGTH_SHORT).show()
        } catch (error: Exception) {
            Toast.makeText(this, "فشل حفظ المشروع: ${error.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    private inner class AndroidFileBridge {
        @JavascriptInterface
        fun saveFile(fileName: String, base64Data: String) {
            try {
                val bytes = Base64.decode(base64Data, Base64.DEFAULT)
                runOnUiThread {
                    pendingSaveBytes = bytes
                    val safeName = fileName.substringAfterLast('/').ifBlank { "مشروع.sh3d" }
                    savePicker.launch(safeName)
                }
            } catch (error: Exception) {
                runOnUiThread {
                    Toast.makeText(
                        this@MainActivity,
                        "تعذر تجهيز الملف للحفظ: ${error.localizedMessage}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
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
            allowFileAccessFromFileURLs = true
            allowUniversalAccessFromFileURLs = true
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
        engineView.addJavascriptInterface(AndroidFileBridge(), "AndroidFileBridge")
        engineView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                view.evaluateJavascript(
                    """
                    (function() {
                      if (!window.__mokhatitiSaveBridgeInstalled) {
                        window.__mokhatitiSaveBridgeInstalled = true;
                        document.addEventListener('click', function(event) {
                          var anchor = event.target && event.target.closest
                              ? event.target.closest('a[download]') : null;
                          if (!anchor || !anchor.href || anchor.href.indexOf('blob:') !== 0) return;
                          event.preventDefault();
                          event.stopImmediatePropagation();
                          var name = anchor.getAttribute('download') || 'مشروع.sh3d';
                          fetch(anchor.href).then(function(response) { return response.blob(); })
                            .then(function(blob) {
                              return new Promise(function(resolve, reject) {
                                var reader = new FileReader();
                                reader.onload = function() { resolve(String(reader.result).split(',')[1]); };
                                reader.onerror = reject;
                                reader.readAsDataURL(blob);
                              });
                            })
                            .then(function(base64) { AndroidFileBridge.saveFile(name, base64); })
                            .catch(function(error) { console.error('Native save failed', error); });
                        }, true);
                      }
                      var canvas = document.createElement('canvas');
                      var gl = canvas.getContext('webgl') || canvas.getContext('experimental-webgl');
                      var result = {
                        application: typeof application === 'object',
                        plan: !!document.getElementById('home-plan'),
                        view3D: !!document.getElementById('home-3D-view'),
                        webgl: !!gl,
                        arabic: document.documentElement.lang === 'ar' && document.documentElement.dir === 'rtl'
                      };
                      console.log('MOKHATITI_SMOKE ' + JSON.stringify(result));
                      return result;
                    })()
                    """.trimIndent()
                ) { result ->
                    android.util.Log.i("MokhatitiSmoke", result ?: "null")
                }
            }

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
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "*/*"
                        putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                            "application/x-sweethome3d",
                            "application/octet-stream",
                            "application/zip"
                        ))
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
            engineView.removeJavascriptInterface("AndroidFileBridge")
            engineView.destroy()
        }
        super.onDestroy()
    }
}
