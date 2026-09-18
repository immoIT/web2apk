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
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.KeyEvent
import android.content.res.Configuration
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

    /** True on Android TV / Leanback devices. */
    private val isTvDevice: Boolean
        get() = (resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_TELEVISION

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

        // Make the WebView the initial remote-focus target on Android TV.
        webView.isFocusable = true
        webView.isFocusableInTouchMode = true
        webView.descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
        webView.requestFocus(View.FOCUS_DOWN)

        // Pull-to-refresh is a touch gesture and can consume D-pad events on TV.
        swipeRefresh.isEnabled = BuildConfig.ENABLE_PULL_TO_REFRESH && !isTvDevice
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

        // Android TV remotes commonly send BACK as a key event. Treat the first
        // press as a request to close the active WebView state (modal/dialog,
        // fullscreen/popup, etc.) or navigate one WebView history step. A second
        // press within one second exits the Activity. This avoids accidentally
        // exiting while a popup is still open.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            private var lastBackPressAt = 0L

            override fun handleOnBackPressed() {
                val now = SystemClock.elapsedRealtime()
                if (lastBackPressAt != 0L && now - lastBackPressAt <= 1000L) {
                    lastBackPressAt = 0L
                    log("Back: double press -> finish activity")
                    finish()
                    return
                }

                lastBackPressAt = now
                closeCurrentWebState { closed ->
                    if (closed) {
                        log("Back: closed active WebView popup/state")
                        webView.requestFocus(View.FOCUS_DOWN)
                        return@closeCurrentWebState
                    }

                    if (webView.canGoBack()) {
                        log("Back: navigating WebView history")
                        webView.goBack()
                    } else {
                        log("Back: waiting for second press to exit")
                    }
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
                injectTvNavigation(view)
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

    /**
     * Adds spatial D-pad navigation for pages that were designed primarily for touch.
     * Navigation is modal-aware so background controls cannot steal focus while a
     * popup/dialog is visible. Normal text-entry arrow behavior is preserved.
     */
    private fun injectTvNavigation(view: WebView?) {
        if (!isTvDevice || view == null || !isTrustedPage()) return

        val script = """
            (function() {
                if (window.__androidTvNavigationInstalled) return;
                window.__androidTvNavigationInstalled = true;

                function editable(el) {
                    if (!el) return false;
                    var tag = (el.tagName || '').toLowerCase();
                    return tag === 'input' || tag === 'textarea' || tag === 'select' || el.isContentEditable;
                }

                function visible(el) {
                    if (!el || el.nodeType !== 1) return false;
                    var r = el.getBoundingClientRect();
                    if (r.width <= 0 || r.height <= 0) return false;
                    var st = window.getComputedStyle(el);
                    if (st.display === 'none' || st.visibility === 'hidden' || st.opacity === '0') return false;
                    if (el.disabled || el.getAttribute('aria-disabled') === 'true') return false;
                    return true;
                }

                function focusableSelector() {
                    return 'a[href],button,input,textarea,select,summary,[tabindex]:not([tabindex="-1"]),[role="button"],[role="link"],[onclick]';
                }

                function modalCandidates() {
                    var selectors = [
                        'dialog[open]',
                        '[role="dialog"]',
                        '[aria-modal="true"]',
                        '[data-modal="true"]',
                        '[data-popup="true"]',
                        '[data-dialog="true"]',
                        '.modal', '.popup', '.dialog',
                        '[class*="modal"]', '[class*="popup"]', '[class*="dialog"]'
                    ];
                    var seen = [];
                    selectors.forEach(function(selector) {
                        try {
                            Array.prototype.slice.call(document.querySelectorAll(selector)).forEach(function(el) {
                                if (seen.indexOf(el) < 0 && visible(el)) seen.push(el);
                            });
                        } catch (_) {}
                    });
                    return seen;
                }

                function likelyModal(el) {
                    if (!visible(el)) return false;
                    if (el.matches && (el.matches('dialog[open],[role="dialog"],[aria-modal="true"],[data-modal="true"],[data-popup="true"],[data-dialog="true"]'))) return true;

                    var st = window.getComputedStyle(el);
                    var r = el.getBoundingClientRect();
                    var position = st.position;
                    var z = parseInt(st.zIndex, 10);
                    var className = String(el.className || '').toLowerCase();
                    var id = String(el.id || '').toLowerCase();
                    var named = /(^|[-_ ])(modal|popup|dialog|overlay)([-_ ]|$)/.test(className + ' ' + id) ||
                                className.indexOf('modal') >= 0 || className.indexOf('popup') >= 0 || className.indexOf('dialog') >= 0;

                    // Generic fixed overlays are accepted only when they look like
                    // a real dialog layer (large, above the page, and containing a
                    // button/control). This avoids treating ordinary fixed headers
                    // or menus as a modal.
                    if (named && (position === 'fixed' || position === 'absolute')) return true;
                    if ((position === 'fixed' || position === 'absolute') && z >= 10 &&
                        r.width >= window.innerWidth * 0.20 && r.height >= window.innerHeight * 0.15) {
                        try {
                            if (el.querySelector(focusableSelector())) return true;
                        } catch (_) {}
                    }
                    return false;
                }

                function activeModal() {
                    var current = document.activeElement;
                    if (current) {
                        var ancestor = current.closest && current.closest(
                            'dialog[open],[role="dialog"],[aria-modal="true"],[data-modal="true"],[data-popup="true"],[data-dialog="true"],.modal,.popup,.dialog,[class*="modal"],[class*="popup"],[class*="dialog"]'
                        );
                        if (ancestor && likelyModal(ancestor)) return ancestor;
                    }

                    var candidates = modalCandidates().filter(likelyModal);
                    if (!candidates.length) return null;

                    // Prefer the highest visible layer. If z-index ties, prefer the
                    // last DOM element because dialogs are normally appended last.
                    candidates.sort(function(a, b) {
                        var za = parseInt(window.getComputedStyle(a).zIndex, 10);
                        var zb = parseInt(window.getComputedStyle(b).zIndex, 10);
                        za = isNaN(za) ? 0 : za;
                        zb = isNaN(zb) ? 0 : zb;
                        return za - zb;
                    });
                    return candidates[candidates.length - 1];
                }

                function focusables(root) {
                    root = root || document;
                    var nodes = [];
                    try {
                        nodes = Array.prototype.slice.call(root.querySelectorAll(focusableSelector()));
                    } catch (_) {}
                    return nodes.filter(visible).filter(function(el, index, arr) {
                        return arr.indexOf(el) === index;
                    });
                }

                function modalFocusables(modal) {
                    var list = focusables(modal);
                    // If the modal itself is focusable and has no child controls,
                    // keep it navigable rather than falling back to the page.
                    if (!list.length && visible(modal) && modal.tabIndex >= 0) list = [modal];
                    return list;
                }

                function rememberFocus() {
                    var el = document.activeElement;
                    if (el && el !== document.body && !activeModal()) {
                        window.__androidTvLastPageFocus = el;
                    }
                }

                function restorePageFocus() {
                    var previous = window.__androidTvLastPageFocus;
                    if (previous && document.contains(previous) && visible(previous)) {
                        try { previous.focus({preventScroll:false}); return true; } catch (_) {}
                    }
                    var list = focusables(document);
                    if (list.length) {
                        try { list[0].focus({preventScroll:false}); return true; } catch (_) {}
                    }
                    return false;
                }

                function focusInitialModal(modal) {
                    if (!modal) return false;
                    var list = modalFocusables(modal);
                    if (!list.length) {
                        try { modal.setAttribute('tabindex', '-1'); modal.focus({preventScroll:false}); return true; } catch (_) {}
                    }

                    // Do not force the remote onto the Close button when the popup
                    // contains real actions. Start on the first actionable control;
                    // Close remains part of the same modal focus graph. If Close is
                    // the only control, it is naturally selected.
                    var nonDismiss = list.find(function(el) {
                        var text = [
                            el.innerText || '',
                            el.getAttribute('aria-label') || '',
                            el.getAttribute('title') || '',
                            el.getAttribute('data-action') || ''
                        ].join(' ').toLowerCase();
                        return !/(^|\b)(close|cancel|dismiss)\b/.test(text);
                    });
                    var target = nonDismiss || list[0];
                    try {
                        target.focus({preventScroll:false});
                        target.scrollIntoView({block:'nearest', inline:'nearest'});
                        window.__androidTvLastModal = modal;
                        return true;
                    } catch (_) {}
                    return false;
                }

                function move(direction) {
                    var modal = activeModal();
                    var list = modal ? modalFocusables(modal) : focusables(document);
                    if (!list.length) return false;

                    var current = document.activeElement;
                    if (!current || current === document.body || list.indexOf(current) < 0) {
                        if (modal) return focusInitialModal(modal);
                        try { list[0].focus({preventScroll:false}); return true; } catch (_) { return false; }
                    }

                    var a = current.getBoundingClientRect();
                    var ax = a.left + a.width / 2;
                    var ay = a.top + a.height / 2;
                    var best = null;
                    var bestScore = Infinity;

                    list.forEach(function(el) {
                        if (el === current) return;
                        var r = el.getBoundingClientRect();
                        var x = r.left + r.width / 2;
                        var y = r.top + r.height / 2;
                        var dx = x - ax, dy = y - ay;
                        var primary, secondary;

                        if (direction === 'left') { if (dx >= -2) return; primary = -dx; secondary = Math.abs(dy); }
                        else if (direction === 'right') { if (dx <= 2) return; primary = dx; secondary = Math.abs(dy); }
                        else if (direction === 'up') { if (dy >= -2) return; primary = -dy; secondary = Math.abs(dx); }
                        else { if (dy <= 2) return; primary = dy; secondary = Math.abs(dx); }

                        var score = primary * primary + secondary * secondary * 1.8;
                        if (score < bestScore) { bestScore = score; best = el; }
                    });

                    if (!best) return false;
                    try {
                        best.focus({preventScroll:false});
                        best.scrollIntoView({block:'nearest', inline:'nearest'});
                        return true;
                    } catch (_) {}
                    return false;
                }

                function clickFocused(el) {
                    if (!el || el === document.body) return false;
                    var tag = (el.tagName || '').toLowerCase();
                    if (tag === 'a' || tag === 'button' || tag === 'summary' ||
                        el.getAttribute('role') === 'button' || el.getAttribute('role') === 'link' ||
                        el.hasAttribute('onclick')) {
                        try { el.click(); return true; } catch (_) {}
                    }
                    return false;
                }

                // Called by the Android Activity for BACK. Returns true when a
                // visible WebView state was consumed/closed, false otherwise.
                window.__androidCloseCurrentWebState = function() {
                    try {
                        var modal = activeModal();
                        if (!modal) {
                            // Exit browser/fullscreen media state before touching WebView
                            // history. This is common for TV video players.
                            if (document.fullscreenElement) {
                                try {
                                    if (document.exitFullscreen) document.exitFullscreen();
                                    return true;
                                } catch (_) {}
                            }
                            var fullscreenVideo = document.querySelector('video');
                            if (fullscreenVideo && (fullscreenVideo.webkitDisplayingFullscreen || fullscreenVideo.webkitPresentationMode === 'fullscreen')) {
                                try {
                                    if (typeof fullscreenVideo.webkitExitFullscreen === 'function') fullscreenVideo.webkitExitFullscreen();
                                    else if (typeof fullscreenVideo.webkitSetPresentationMode === 'function') fullscreenVideo.webkitSetPresentationMode('inline');
                                    return true;
                                } catch (_) {}
                            }
                            // Fullscreen media and browser-style overlays often expose
                            // a close/exit button without a dialog role. Use the focused
                            // element's close semantics before considering page history.
                            var focused = document.activeElement;
                            if (focused && visible(focused)) {
                                var text = [
                                    focused.innerText || '',
                                    focused.getAttribute('aria-label') || '',
                                    focused.getAttribute('title') || '',
                                    focused.getAttribute('data-action') || ''
                                ].join(' ').toLowerCase();
                                if (/\b(close|dismiss|cancel|exit fullscreen|exit)\b/.test(text) && clickFocused(focused)) return true;
                            }
                            return false;
                        }

                        var dialog = modal;
                        if (typeof dialog.close === 'function' && dialog.open) {
                            dialog.close();
                            return true;
                        }

                        var controls = modalFocusables(modal);
                        var closeControl = controls.find(function(el) {
                            var text = [
                                el.innerText || '',
                                el.getAttribute('aria-label') || '',
                                el.getAttribute('title') || '',
                                el.getAttribute('data-action') || '',
                                el.getAttribute('data-testid') || ''
                            ].join(' ').toLowerCase().trim();
                            return /(^|\b)(close|cancel|dismiss|done|ok|×|✕|✖)(\b|$)/.test(text);
                        });

                        if (!closeControl) {
                            closeControl = controls.find(function(el) {
                                var label = [el.innerText || '', el.getAttribute('aria-label') || '', el.getAttribute('title') || ''].join(' ').toLowerCase();
                                return /\b(close|cancel|dismiss)\b/.test(label);
                            });
                        }

                        if (closeControl && clickFocused(closeControl)) return true;

                        // Last resort for a conventional modal with a single actionable
                        // control: activate it. This handles icon-only close buttons whose
                        // accessible label is missing.
                        if (controls.length === 1 && clickFocused(controls[0])) return true;

                        return false;
                    } catch (e) {
                        console.error('Android TV close-state handling failed', e);
                        return false;
                    }
                };

                document.addEventListener('focusin', function() {
                    var modal = activeModal();
                    if (modal) {
                        if (window.__androidTvLastModal !== modal) focusInitialModal(modal);
                        window.__androidTvLastModal = modal;
                    } else {
                        rememberFocus();
                        window.__androidTvLastModal = null;
                    }
                }, true);

                document.addEventListener('keydown', function(e) {
                    var key = e.key;
                    var modal = activeModal();
                    var el = document.activeElement;

                    if (modal && (!el || !modal.contains(el))) {
                        if (focusInitialModal(modal)) {
                            e.preventDefault();
                            e.stopPropagation();
                        }
                        return;
                    }

                    if (editable(el) && (key === 'ArrowLeft' || key === 'ArrowRight')) return;
                    if (editable(el) && (key === 'ArrowUp' || key === 'ArrowDown') &&
                        ((el.tagName || '').toLowerCase() === 'textarea' || el.isContentEditable)) return;

                    if (key === 'ArrowLeft' || key === 'ArrowRight' || key === 'ArrowUp' || key === 'ArrowDown') {
                        if (move(key.substring(5).toLowerCase())) {
                            e.preventDefault();
                            e.stopPropagation();
                        }
                    } else if (key === 'Enter' || key === 'NumpadEnter') {
                        if (clickFocused(el)) {
                            e.preventDefault();
                            e.stopPropagation();
                        }
                    }
                }, true);

                // Detect dynamically created SPA popups/dialogs and put focus inside
                // them without stealing focus from ordinary page updates.
                var observer = new MutationObserver(function() {
                    var modal = activeModal();
                    if (modal && window.__androidTvLastModal !== modal) {
                        focusInitialModal(modal);
                        window.__androidTvLastModal = modal;
                    } else if (!modal && window.__androidTvLastModal) {
                        window.__androidTvLastModal = null;
                        restorePageFocus();
                    }
                });
                try { observer.observe(document.documentElement, {childList:true, subtree:true, attributes:true, attributeFilter:['class','style','hidden','open','aria-hidden','aria-modal']}); } catch (_) {}

                // Keep a visible focus ring even when the website has weak TV styling.
                var style = document.createElement('style');
                style.id = '__android_tv_focus_style';
                style.textContent = '*:focus{outline:3px solid #ffffff !important;outline-offset:2px;}';
                (document.head || document.documentElement).appendChild(style);

                // Give a modal a chance to settle after framework rendering.
                setTimeout(function() {
                    var modal = activeModal();
                    if (modal) focusInitialModal(modal);
                }, 150);
            })();
        """.trimIndent()

        view.evaluateJavascript(script, null)
        view.postDelayed({
            if (!isFinishing && !isDestroyed && isTrustedPage()) view.evaluateJavascript(script, null)
        }, 1200)
    }

    private fun closeCurrentWebState(onResult: (Boolean) -> Unit) {
        if (!isTrustedPage()) {
            onResult(false)
            return
        }

        val script = """
            (function() {
                try {
                    if (typeof window.__androidCloseCurrentWebState === 'function') {
                        return window.__androidCloseCurrentWebState() ? 'true' : 'false';
                    }
                } catch (e) {
                    console.error('Android close-state bridge failed', e);
                }
                return 'false';
            })();
        """.trimIndent()

        webView.evaluateJavascript(script) { result ->
            onResult(result == "true" || result == "\"true\"")
        }
    }

    private fun injectCustomScripts(view: WebView?) {
        if (!BuildConfig.ENABLE_CUSTOM_SCRIPTS || view == null || !isTrustedPage()) return

        try {
            val files = assets.open("web2apk-custom-files.txt").bufferedReader().useLines { lines ->
                lines.map { it.trim() }.filter { it.isNotEmpty() }.toList()
            }

            if (files.isEmpty()) return

            log("Injecting ${files.size} custom WebView JS file(s): ${files.joinToString()}")
            files.forEach { fileName ->
                val script = assets.open("custom-js/$fileName").bufferedReader().use { it.readText() }
                if (script.isBlank()) return@forEach

                // Each configured file is evaluated separately, in the same order
                // used during the build. This keeps generated JS files independent
                // inside the APK while still injecting all of them into the page.
                view.evaluateJavascript(
                    "(function(){try{\n$script\n}catch(e){console.error('web2apk custom script failed: $fileName',e);}})();",
                    null
                )
            }
        } catch (_: java.io.FileNotFoundException) {
            // No custom JS files were selected/generated. This is a valid/default configuration.
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
