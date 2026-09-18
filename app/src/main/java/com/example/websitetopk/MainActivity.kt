package com.example.websitetopk

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var swipeRefresh: SwipeRefreshLayout

    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var permissionCallback: PermissionRequest? = null
    private var pendingWebResources: Array<String> = emptyArray()

    private val startUrl = BuildConfig.WEB_URL
    private val configuredPermissionRequestCode = 9001
    private val webPermissionRequestCode = 9002

    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val callback = fileCallback ?: return@registerForActivityResult
        fileCallback = null
        val results = if (result.resultCode == Activity.RESULT_OK) {
            WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
        } else {
            null
        }
        callback.onReceiveValue(results)
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        progressBar = findViewById(R.id.progressBar)
        swipeRefresh = findViewById(R.id.swipeRefresh)

        swipeRefresh.isEnabled = BuildConfig.ENABLE_PULL_TO_REFRESH
        swipeRefresh.setOnRefreshListener { webView.reload() }

        configureWebView()
        requestConfiguredPermissions()

        if (savedInstanceState == null) {
            webView.loadUrl(startUrl)
        } else {
            webView.restoreState(savedInstanceState)
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) webView.goBack() else finish()
            }
        })
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        webView.settings.apply {
            javaScriptEnabled = BuildConfig.ENABLE_JAVASCRIPT
            domStorageEnabled = BuildConfig.ENABLE_DOM_STORAGE
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = BuildConfig.ENABLE_FILE_UPLOAD
            allowContentAccess = BuildConfig.ENABLE_FILE_UPLOAD
            javaScriptCanOpenWindowsAutomatically = true
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                runOnUiThread { handleWebPermissionRequest(request) }
            }

            override fun onShowFileChooser(
                webView: WebView?,
                callback: ValueCallback<Array<Uri>>?,
                params: FileChooserParams?
            ): Boolean {
                if (!BuildConfig.ENABLE_FILE_UPLOAD || callback == null || params == null) {
                    callback?.onReceiveValue(null)
                    return false
                }

                fileCallback?.onReceiveValue(null)
                fileCallback = callback

                return try {
                    val intent = params.createIntent().apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                    }
                    fileChooserLauncher.launch(intent)
                    true
                } catch (_: Exception) {
                    fileCallback = null
                    callback.onReceiveValue(null)
                    false
                }
            }
        }

        webView.setDownloadListener { url, _, _, _, _ ->
            if (BuildConfig.ENABLE_DOWNLOADS) {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                } catch (_: Exception) {
                    // No browser/app is available for this URL.
                }
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                progressBar.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                progressBar.visibility = View.GONE
                swipeRefresh.isRefreshing = false
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: android.webkit.WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame == true) {
                    progressBar.visibility = View.GONE
                    swipeRefresh.isRefreshing = false
                }
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val uri = request?.url ?: return false
                if (uri.scheme == "http" || uri.scheme == "https") return false

                if (BuildConfig.ALLOW_EXTERNAL_LINKS) {
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, uri))
                    } catch (_: Exception) {
                        // No handler for this URI scheme.
                    }
                }
                return true
            }
        }
    }

    private fun handleWebPermissionRequest(request: PermissionRequest) {
        if (isFinishing || isDestroyed) {
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
            request.deny()
            return
        }

        val requiredPermissions = buildList {
            if (allowedResources.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE)) {
                add(Manifest.permission.CAMERA)
            }
            if (allowedResources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE)) {
                add(Manifest.permission.RECORD_AUDIO)
            }
        }

        val missingPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isEmpty()) {
            request.grant(allowedResources)
        } else {
            permissionCallback?.deny()
            permissionCallback = request
            pendingWebResources = allowedResources
            ActivityCompat.requestPermissions(
                this,
                missingPermissions.toTypedArray(),
                webPermissionRequestCode
            )
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
        if (BuildConfig.PERMISSION_NOTIFICATIONS && Build.VERSION.SDK_INT >= 33) {
            requested += Manifest.permission.POST_NOTIFICATIONS
        }
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
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), configuredPermissionRequestCode)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode != configuredPermissionRequestCode && requestCode != webPermissionRequestCode) return

        if (requestCode == configuredPermissionRequestCode) return

        val request = permissionCallback ?: return
        permissionCallback = null

        val resources = pendingWebResources
        pendingWebResources = emptyArray()

        val allGranted = resources.all { resource ->
            when (resource) {
                PermissionRequest.RESOURCE_VIDEO_CAPTURE ->
                    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                PermissionRequest.RESOURCE_AUDIO_CAPTURE ->
                    ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                else -> false
            }
        }

        if (allGranted && resources.isNotEmpty()) {
            request.grant(resources)
        } else {
            request.deny()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        webView.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        fileCallback?.onReceiveValue(null)
        fileCallback = null
        permissionCallback?.deny()
        permissionCallback = null
        webView.stopLoading()
        webView.webChromeClient = null
        webView.webViewClient = null
        webView.destroy()
        super.onDestroy()
    }
}
