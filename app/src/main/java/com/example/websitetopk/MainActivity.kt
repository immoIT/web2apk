package com.example.websitetopk

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.content.ClipData
import android.content.ClipboardManager
import android.webkit.JavascriptInterface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.PermissionRequest
import android.webkit.SslErrorHandler
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.net.http.SslError
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "WebsiteToAPK"
        private const val CONFIGURED_PERMISSION_REQUEST_CODE = 9001
        private const val WEB_PERMISSION_REQUEST_CODE = 9002
    }

    private lateinit var webView: WebView
    private lateinit var swipeRefresh: SwipeRefreshLayout

    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var permissionCallback: PermissionRequest? = null
    private var pendingWebResources: Array<String> = emptyArray()
    private val clipboardManager by lazy {
        getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
    }

    private val startUrl = BuildConfig.WEB_URL

    private fun log(message: String) {
        if (BuildConfig.ENABLE_LOGGING) Log.d(TAG, message)
    }

    private fun logError(message: String, throwable: Throwable? = null) {
        if (BuildConfig.ENABLE_LOGGING) Log.e(TAG, message, throwable)
    }

    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val callback = fileCallback ?: return@registerForActivityResult
        fileCallback = null
        val results = if (result.resultCode == Activity.RESULT_OK) {
            runCatching {
                WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
            }.getOrElse {
                logError("File chooser result parsing failed", it)
                null
            }
        } else {
            log("File chooser cancelled: resultCode=${result.resultCode}")
            null
        }
        callback.onReceiveValue(results)
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        applyFullscreenMode()
        log("onCreate: package=${packageName}, sdk=${Build.VERSION.SDK_INT}, fullscreen=${BuildConfig.ENABLE_FULLSCREEN}, startUrl=$startUrl")

        setContentView(R.layout.activity_main)
        webView = findViewById(R.id.webView)
        swipeRefresh = findViewById(R.id.swipeRefresh)

        swipeRefresh.isEnabled = BuildConfig.ENABLE_PULL_TO_REFRESH
        swipeRefresh.setOnRefreshListener {
            log("Pull-to-refresh: reload requested")
            webView.reload()
        }

        webView.addJavascriptInterface(WebClipboardBridge(), "AndroidClipboard")
        configureWebView()
        requestConfiguredPermissions()

        if (savedInstanceState == null) {
            log("Loading initial URL")
            webView.loadUrl(startUrl)
        } else {
            log("Restoring WebView state")
            webView.restoreState(savedInstanceState)
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    log("Back: navigating WebView history")
                    webView.goBack()
                } else {
                    log("Back: finishing activity")
                    finish()
                }
            }
        })
    }

    private fun applyFullscreenMode() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)

        if (BuildConfig.ENABLE_FULLSCREEN) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            controller.hide(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            controller.show(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        log("Configuring WebView: JS=${BuildConfig.ENABLE_JAVASCRIPT}, DOM=${BuildConfig.ENABLE_DOM_STORAGE}, downloads=${BuildConfig.ENABLE_DOWNLOADS}, uploads=${BuildConfig.ENABLE_FILE_UPLOAD}")
        webView.settings.apply {
            javaScriptEnabled = BuildConfig.ENABLE_JAVASCRIPT
            domStorageEnabled = BuildConfig.ENABLE_DOM_STORAGE
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = BuildConfig.ENABLE_FILE_UPLOAD
            allowContentAccess = BuildConfig.ENABLE_FILE_UPLOAD
            javaScriptCanOpenWindowsAutomatically = true
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                log("JS console: ${consoleMessage.message()} @ ${consoleMessage.sourceId()}:${consoleMessage.lineNumber()} level=${consoleMessage.messageLevel()}")
                return true
            }

            override fun onPermissionRequest(request: PermissionRequest) {
                log("Web permission request: origin=${request.origin}, resources=${request.resources.contentToString()}")
                runOnUiThread { handleWebPermissionRequest(request) }
            }

            override fun onShowFileChooser(
                webView: WebView?,
                callback: ValueCallback<Array<Uri>>?,
                params: FileChooserParams?
            ): Boolean {
                if (!BuildConfig.ENABLE_FILE_UPLOAD || callback == null || params == null) {
                    log("File chooser denied: uploads disabled or invalid callback/params")
                    callback?.onReceiveValue(null)
                    return false
                }

                fileCallback?.onReceiveValue(null)
                fileCallback = callback

                return try {
                    val intent = params.createIntent().apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                    }
                    log("Launching file chooser: accept=${params.acceptTypes.contentToString()}, multiple=${params.mode == FileChooserParams.MODE_OPEN_MULTIPLE}")
                    fileChooserLauncher.launch(intent)
                    true
                } catch (t: Throwable) {
                    fileCallback = null
                    callback.onReceiveValue(null)
                    logError("File chooser launch failed", t)
                    false
                }
            }
        }

        webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, contentLength ->
            log("Download requested: url=$url mimeType=$mimeType contentLength=$contentLength disposition=$contentDisposition userAgent=${userAgent?.take(80)}")
            if (BuildConfig.ENABLE_DOWNLOADS) {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                } catch (t: Throwable) {
                    logError("No handler available for download URL: $url", t)
                }
            } else {
                log("Download ignored because downloads are disabled")
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                log("Page started: $url")
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                log("Page finished: $url")
                injectClipboardSupport(view)
                injectCustomScripts(view)
                swipeRefresh.isRefreshing = false
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                logError("Web resource error: mainFrame=${request?.isForMainFrame}, url=${request?.url}, code=${error?.errorCode}, description=${error?.description}")
                if (request?.isForMainFrame == true) {
                    swipeRefresh.isRefreshing = false
                }
            }

            @Suppress("DEPRECATION")
            override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                super.onReceivedError(view, errorCode, description, failingUrl)
                logError("Legacy Web resource error: code=$errorCode description=$description url=$failingUrl")
            }

            override fun onReceivedHttpError(
                view: WebView?,
                request: WebResourceRequest?,
                errorResponse: android.webkit.WebResourceResponse?
            ) {
                super.onReceivedHttpError(view, request, errorResponse)
                logError("HTTP error: mainFrame=${request?.isForMainFrame}, url=${request?.url}, status=${errorResponse?.statusCode}, reason=${errorResponse?.reasonPhrase}")
            }

            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                logError("SSL error: primary=${error?.primaryError}, url=${error?.url}")
                handler?.cancel()
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val uri = request?.url ?: return false
                log("URL navigation requested: $uri")
                if (uri.scheme == "http" || uri.scheme == "https") return false

                if (BuildConfig.ALLOW_EXTERNAL_LINKS) {
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, uri))
                        log("Opened external URI: $uri")
                    } catch (t: Throwable) {
                        logError("No handler for external URI: $uri", t)
                    }
                } else {
                    log("Blocked external URI because ALLOW_EXTERNAL_LINKS=false: $uri")
                }
                return true
            }
        }
    }

    private inner class WebClipboardBridge {
        @JavascriptInterface
        fun readText(): String {
            if (!isTrustedPage()) {
                log("Clipboard read blocked: current page is not the configured trusted host")
                return ""
            }

            return try {
                if (!clipboardManager.hasPrimaryClip()) {
                    log("Clipboard read: no primary clip")
                    ""
                } else {
                    val text = clipboardManager.primaryClip
                        ?.getItemAt(0)
                        ?.coerceToText(this@MainActivity)
                        ?.toString()
                        .orEmpty()
                    log("Clipboard read: ${text.length} characters")
                    text
                }
            } catch (t: Throwable) {
                logError("Clipboard read failed", t)
                ""
            }
        }

        @JavascriptInterface
        fun writeText(text: String?) {
            if (!isTrustedPage()) {
                log("Clipboard write blocked: current page is not the configured trusted host")
                return
            }

            try {
                val value = text.orEmpty()
                clipboardManager.setPrimaryClip(ClipData.newPlainText("WebView", value))
                log("Clipboard write: ${value.length} characters")
            } catch (t: Throwable) {
                logError("Clipboard write failed", t)
            }
        }
    }

    private fun isTrustedPage(): Boolean {
        val currentHost = runCatching { Uri.parse(webView.url ?: startUrl).host }.getOrNull()
        val trustedHost = runCatching { Uri.parse(startUrl).host }.getOrNull()
        return !trustedHost.isNullOrEmpty() && currentHost == trustedHost
    }

    private fun injectClipboardSupport(view: WebView?) {
        if (view == null || !isTrustedPage()) return

        val script = """
            (function() {
                try {
                    if (!window.AndroidClipboard) return;

                    // Always refresh the bridge. Some web apps replace navigator.clipboard
                    // during startup, so a one-time injection is not reliable.
                    var nativeClipboard = {
                        readText: function() {
                            try {
                                return Promise.resolve(String(window.AndroidClipboard.readText() || ''));
                            } catch (e) {
                                console.error('Native clipboard read failed', e);
                                return Promise.resolve('');
                            }
                        },
                        writeText: function(text) {
                            try {
                                window.AndroidClipboard.writeText(String(text == null ? '' : text));
                            } catch (e) {
                                console.error('Native clipboard write failed', e);
                            }
                            return Promise.resolve();
                        }
                    };

                    try {
                        Object.defineProperty(navigator, 'clipboard', {
                            configurable: true,
                            enumerable: true,
                            get: function() { return nativeClipboard; }
                        });
                    } catch (e) {
                        try { navigator.clipboard = nativeClipboard; } catch (_) {}
                    }

                    // Make the native clipboard available to paste buttons/icons too.
                    // This handles apps that do not call navigator.clipboard.readText()
                    // themselves when the user taps a Paste control.
                    if (!window.__androidPasteHandlerInstalled) {
                        window.__androidPasteHandlerInstalled = true;

                        function isPasteControl(el) {
                            if (!el || !el.closest) return false;
                            var control = el.closest('button, [role="button"], input[type="button"], input[type="submit"], a');
                            if (!control) return false;

                            var text = [
                                control.innerText || '',
                                control.getAttribute('aria-label') || '',
                                control.getAttribute('title') || '',
                                control.getAttribute('data-action') || ''
                            ].join(' ').toLowerCase();

                            return /\bpaste\b/.test(text);
                        }

                        function insertTextIntoTarget(text) {
                            var target = document.activeElement;

                            // If the Paste icon/button itself has focus, use the most
                            // recently focused editable element instead.
                            if (!target || !(
                                target.tagName === 'INPUT' ||
                                target.tagName === 'TEXTAREA' ||
                                target.isContentEditable
                            )) {
                                target = window.__androidLastEditableElement;
                            }

                            if (!target) return false;

                            try {
                                if (target.isContentEditable) {
                                    target.focus();
                                    document.execCommand('insertText', false, text);
                                    return true;
                                }

                                if (target.tagName === 'INPUT' || target.tagName === 'TEXTAREA') {
                                    var start = typeof target.selectionStart === 'number'
                                        ? target.selectionStart : target.value.length;
                                    var end = typeof target.selectionEnd === 'number'
                                        ? target.selectionEnd : target.value.length;

                                    var value = target.value || '';
                                    target.focus();
                                    var newValue = value.slice(0, start) + text + value.slice(end);

                                    // Use the native setter so controlled inputs (for
                                    // example React/Vue forms) also observe the change.
                                    var proto = target.tagName === 'TEXTAREA'
                                        ? HTMLTextAreaElement.prototype
                                        : HTMLInputElement.prototype;
                                    var setter = Object.getOwnPropertyDescriptor(proto, 'value');
                                    if (setter && setter.set) {
                                        setter.set.call(target, newValue);
                                    } else {
                                        target.value = newValue;
                                    }

                                    var cursor = start + text.length;
                                    try {
                                        target.setSelectionRange(cursor, cursor);
                                    } catch (_) {}

                                    target.dispatchEvent(new Event('input', { bubbles: true }));
                                    target.dispatchEvent(new Event('change', { bubbles: true }));
                                    return true;
                                }
                            } catch (e) {
                                console.error('Paste insertion failed', e);
                            }
                            return false;
                        }

                        document.addEventListener('focusin', function(event) {
                            var el = event.target;
                            if (el && (
                                el.tagName === 'INPUT' ||
                                el.tagName === 'TEXTAREA' ||
                                el.isContentEditable
                            )) {
                                window.__androidLastEditableElement = el;
                            }
                        }, true);

                        document.addEventListener('click', function(event) {
                            if (!isPasteControl(event.target)) return;

                            // Read the Android clipboard at click time, while the
                            // Activity/WebView is foregrounded, then populate the
                            // focused field. Do not block the site's own click handler.
                            try {
                                var text = String(window.AndroidClipboard.readText() || '');
                                if (text) insertTextIntoTarget(text);
                            } catch (e) {
                                console.error('Paste button handling failed', e);
                            }
                        }, true);
                    }
                } catch (e) {
                    console.error('Android clipboard bridge failed', e);
                }
            })();
        """.trimIndent()

        view.evaluateJavascript(script, null)

        // Re-apply after SPA/framework startup code has had a chance to replace
        // navigator.clipboard. This is intentionally short-lived.
        view.postDelayed({
            if (!isFinishing && !isDestroyed && isTrustedPage()) {
                view.evaluateJavascript(script, null)
            }
        }, 500)

        view.postDelayed({
            if (!isFinishing && !isDestroyed && isTrustedPage()) {
                view.evaluateJavascript(script, null)
            }
        }, 1500)
    }

    private fun injectCustomScripts(view: WebView?) {
        if (!BuildConfig.ENABLE_CUSTOM_SCRIPTS || view == null || !isTrustedPage()) return

        try {
            val script = assets.open("web2apk-custom.js").bufferedReader().use { it.readText() }
            if (script.isBlank()) return

            log("Injecting custom WebView script (${script.length} characters)")
            view.evaluateJavascript(
                "(function(){try{\n$script\n}catch(e){console.error('web2apk custom script failed',e);}})();",
                null
            )
        } catch (_: java.io.FileNotFoundException) {
            // No custom script was uploaded. This is a valid/default configuration.
        } catch (t: Throwable) {
            logError("Custom WebView script injection failed", t)
        }
    }

    private fun handleWebPermissionRequest(request: PermissionRequest) {
        if (isFinishing || isDestroyed) {
            log("Denying web permission because activity is finishing/destroyed")
            request.deny()
            return
        }

        val allowedResources = request.resources.filter { resource ->
            when (resource) {
                PermissionRequest.RESOURCE_VIDEO_CAPTURE -> BuildConfig.PERMISSION_CAMERA
                PermissionRequest.RESOURCE_AUDIO_CAPTURE -> BuildConfig.PERMISSION_MICROPHONE
                else -> false
            }
        }.toTypedArray()

        if (allowedResources.isEmpty()) {
            log("Denied unsupported/disabled web resources: ${request.resources.contentToString()}")
            request.deny()
            return
        }

        val requiredPermissions = buildList {
            if (allowedResources.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE)) add(Manifest.permission.CAMERA)
            if (allowedResources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE)) add(Manifest.permission.RECORD_AUDIO)
        }

        val missingPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isEmpty()) {
            log("Granting web resources immediately: ${allowedResources.contentToString()}")
            request.grant(allowedResources)
        } else {
            permissionCallback?.deny()
            permissionCallback = request
            pendingWebResources = allowedResources
            log("Requesting Android permissions for web resources: ${missingPermissions.joinToString()}")
            ActivityCompat.requestPermissions(this, missingPermissions.toTypedArray(), WEB_PERMISSION_REQUEST_CODE)
        }
    }

    private fun requestConfiguredPermissions() {
        val requested = mutableListOf<String>()

        if (BuildConfig.PERMISSION_CAMERA) requested += Manifest.permission.CAMERA
        if (BuildConfig.PERMISSION_MICROPHONE) requested += Manifest.permission.RECORD_AUDIO
        if (BuildConfig.PERMISSION_LOCATION) {
            requested += Manifest.permission.ACCESS_FINE_LOCATION
            requested += Manifest.permission.ACCESS_COARSE_LOCATION
        }
        if (BuildConfig.PERMISSION_NOTIFICATIONS && Build.VERSION.SDK_INT >= 33) requested += Manifest.permission.POST_NOTIFICATIONS
        if (BuildConfig.PERMISSION_CONTACTS) {
            requested += Manifest.permission.READ_CONTACTS
            requested += Manifest.permission.WRITE_CONTACTS
        }
        if (BuildConfig.PERMISSION_CALENDAR) {
            requested += Manifest.permission.READ_CALENDAR
            requested += Manifest.permission.WRITE_CALENDAR
        }
        if (BuildConfig.PERMISSION_PHONE) {
            requested += Manifest.permission.READ_PHONE_STATE
            requested += Manifest.permission.CALL_PHONE
        }
        if (BuildConfig.PERMISSION_BLUETOOTH && Build.VERSION.SDK_INT >= 31) {
            requested += Manifest.permission.BLUETOOTH_CONNECT
            requested += Manifest.permission.BLUETOOTH_SCAN
        }

        val missing = requested.distinct().filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        log("Configured permissions: requested=${requested.joinToString()}, missing=${missing.joinToString()}")
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), CONFIGURED_PERMISSION_REQUEST_CODE)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        log("Permission result: requestCode=$requestCode permissions=${permissions.contentToString()} grants=${grantResults.contentToString()}")

        if (requestCode != CONFIGURED_PERMISSION_REQUEST_CODE && requestCode != WEB_PERMISSION_REQUEST_CODE) return
        if (requestCode == CONFIGURED_PERMISSION_REQUEST_CODE) return

        val request = permissionCallback ?: return
        permissionCallback = null
        val resources = pendingWebResources
        pendingWebResources = emptyArray()

        val allGranted = resources.all { resource ->
            when (resource) {
                PermissionRequest.RESOURCE_VIDEO_CAPTURE -> ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                PermissionRequest.RESOURCE_AUDIO_CAPTURE -> ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                else -> false
            }
        }

        if (allGranted && resources.isNotEmpty()) {
            log("Web permission granted after Android permission result")
            request.grant(resources)
        } else {
            log("Web permission denied after Android permission result")
            request.deny()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        webView.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        log("onDestroy")
        fileCallback?.onReceiveValue(null)
        fileCallback = null
        permissionCallback?.deny()
        permissionCallback = null
        webView.stopLoading()
        webView.webChromeClient = null
        // WebViewClient is non-null by Android API contract; do not assign null here.
        webView.destroy()
        super.onDestroy()
    }
}
