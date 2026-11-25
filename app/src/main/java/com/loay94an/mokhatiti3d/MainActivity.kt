package com.loay94an.mokhatiti3d

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.util.Log
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
import androidx.webkit.WebViewAssetLoader

class MainActivity : ComponentActivity() {
    private lateinit var engineView: WebView
    private lateinit var assetLoader: WebViewAssetLoader
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
            Log.e(TAG, "Save project failed", error)
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
                    val safeName = fileName.substringAfterLast('/').ifBlank { "مشروع.sh3x" }
                    savePicker.launch(safeName)
                }
            } catch (error: Exception) {
                Log.e(TAG, "Prepare project save failed", error)
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

        assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        engineView = findViewById(R.id.engineView)
        engineView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = false
            allowFileAccessFromFileURLs = false
            allowUniversalAccessFromFileURLs = false
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
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ) = assetLoader.shouldInterceptRequest(request.url)

            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                val uri = request.url
                return uri.scheme != "https" || uri.host != ASSET_HOST
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                if (url != START_URL) return
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
                          var name = anchor.getAttribute('download') || 'مشروع.sh3x';
                          fetch(anchor.href).then(function(response) {
                            if (!response.ok) throw new Error('تعذر قراءة ملف المشروع');
                            return response.blob();
                          }).then(function(blob) {
                            return new Promise(function(resolve, reject) {
                              var reader = new FileReader();
                              reader.onload = function() {
                                resolve(String(reader.result).split(',')[1]);
                              };
                              reader.onerror = reject;
                              reader.readAsDataURL(blob);
                            });
                          }).then(function(base64) {
                            AndroidFileBridge.saveFile(name, base64);
                          }).catch(function(error) {
                            console.error('Native save failed', error);
                            alert('تعذر حفظ المشروع: ' + error.message);
                          });
                        }, true);
                      }
                      window.setTimeout(function() {
                        var canvas3D = document.getElementById('home-3D-view');
                        var gl = canvas3D && (canvas3D.getContext('webgl') ||
                            canvas3D.getContext('experimental-webgl'));
                        var homes = window.application && application.getHomes
                            ? application.getHomes() : [];
                        var plan = document.getElementById('home-plan');
                        var planCanvas = plan && plan.querySelector('canvas');
                        var planContext = planCanvas && planCanvas.getContext('2d');
                        var result = {
                          application: typeof application === 'object',
                          homeCount: homes.length,
                          plan: !!plan,
                          plan2D: !!planCanvas && !!planContext,
                          view3D: !!canvas3D,
                          webgl: !!gl,
                          webglVersion: gl ? gl.getParameter(gl.VERSION) : '',
                          arabic: document.documentElement.lang === 'ar' &&
                                  document.documentElement.dir === 'rtl'
                        };
                        console.log('MOKHATITI_SMOKE ' + JSON.stringify(result));
                      }, 5000);
                    })();
                    """.trimIndent()
                )
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: android.webkit.WebResourceError
            ) {
                super.onReceivedError(view, request, error)
                if (request.isForMainFrame) {
                    Log.e(TAG, "Editor page load failed: ${error.description}")
                    Toast.makeText(
                        this@MainActivity,
                        "تعذر تحميل محرر التصميم",
                        Toast.LENGTH_LONG
                    ).show()
                }
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
                        putExtra(
                            Intent.EXTRA_MIME_TYPES,
                            arrayOf(
                                "application/x-sweethome3d",
                                "application/octet-stream",
                                "application/zip"
                            )
                        )
                    }
                    filePicker.launch(intent)
                    true
                } catch (error: Exception) {
                    Log.e(TAG, "Open document picker failed", error)
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
                val line = message.message()
                if (line.startsWith("MOKHATITI_SMOKE ")) {
                    Log.i("MokhatitiSmoke", line.removePrefix("MOKHATITI_SMOKE "))
                } else {
                    Log.d(
                        "MokhatitiEngine",
                        "$line — ${message.sourceId()}:${message.lineNumber()}"
                    )
                }
                return true
            }
        }
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
        engineView.loadUrl(START_URL)
    }

    override fun onDestroy() {
        filePathCallback?.onReceiveValue(null)
        filePathCallback = null
        pendingSaveBytes = null
        if (::engineView.isInitialized) {
            engineView.stopLoading()
            engineView.removeJavascriptInterface("AndroidFileBridge")
            engineView.destroy()
        }
        super.onDestroy()
    }

    companion object {
        private const val TAG = "Mokhatiti"
        private const val ASSET_HOST = "appassets.androidplatform.net"
        private const val START_URL =
            "https://appassets.androidplatform.net/assets/engine/index.html"
    }
}
