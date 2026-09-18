package com.example.websitetopk

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.*
import android.widget.ProgressBar
import androidx.activity.OnBackPressedCallback
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

    private val startUrl = BuildConfig.WEB_URL
    private val permissionRequestCode = 9001
    private val fileRequestCode = 9002

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
        webView.loadUrl(startUrl)

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
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = BuildConfig.ENABLE_FILE_UPLOAD
            allowContentAccess = BuildConfig.ENABLE_FILE_UPLOAD
            javaScriptCanOpenWindowsAutomatically = true
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                runOnUiThread {
                    val resources = request.resources.toSet()
                    val permissions = mutableListOf<String>()

                    if (resources.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE) &&
                        BuildConfig.PERMISSION_CAMERA) {
                        permissions += Manifest.permission.CAMERA
                    }
                    if (resources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE) &&
                        BuildConfig.PERMISSION_MICROPHONE) {
                        permissions += Manifest.permission.RECORD_AUDIO
                    }

                    if (permissions.all {
                            ContextCompat.checkSelfPermission(this@MainActivity, it) ==
                                PackageManager.PERMISSION_GRANTED
                        }) {
                        request.grant(resources.toTypedArray())
                    } else {
                        permissionCallback = request
                        ActivityCompat.requestPermissions(
                            this@MainActivity,
                            permissions.toTypedArray(),
                            permissionRequestCode
                        )
                    }
                }
            }

            override fun onShowFileChooser(
                webView: WebView?,
                callback: ValueCallback<Array<Uri>>?,
                params: FileChooserParams?
            ): Boolean {
                if (!BuildConfig.ENABLE_FILE_UPLOAD) return false
                fileCallback?.onReceiveValue(null)
                fileCallback = callback
                return try {
                    startActivityForResult(
                        params?.createIntent()?.apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                        },
                        fileRequestCode
                    )
                    true
                } catch (_: Exception) {
                    fileCallback = null
                    false
                }
            }
        }

        webView.setDownloadListener { url, _, _, _, _ ->
            if (BuildConfig.ENABLE_DOWNLOADS) {
                try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                catch (_: Exception) {}
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

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val uri = request?.url ?: return false
                if (uri.scheme == "http" || uri.scheme == "https") return false
                if (BuildConfig.ALLOW_EXTERNAL_LINKS) {
                    try { startActivity(Intent(Intent.ACTION_VIEW, uri)) } catch (_: Exception) {}
                }
                return true
            }
        }
    }

    private fun requestConfiguredPermissions() {
        val list = mutableListOf<String>()

        if (BuildConfig.PERMISSION_CAMERA) list += Manifest.permission.CAMERA
        if (BuildConfig.PERMISSION_MICROPHONE) list += Manifest.permission.RECORD_AUDIO
        if (BuildConfig.PERMISSION_LOCATION) {
            list += Manifest.permission.ACCESS_FINE_LOCATION
            list += Manifest.permission.ACCESS_COARSE_LOCATION
        }
        if (BuildConfig.PERMISSION_NOTIFICATIONS && android.os.Build.VERSION.SDK_INT >= 33) {
            list += Manifest.permission.POST_NOTIFICATIONS
        }
        if (BuildConfig.PERMISSION_CONTACTS) {
            list += Manifest.permission.READ_CONTACTS
            list += Manifest.permission.WRITE_CONTACTS
        }
        if (BuildConfig.PERMISSION_CALENDAR) {
            list += Manifest.permission.READ_CALENDAR
            list += Manifest.permission.WRITE_CALENDAR
        }
        if (BuildConfig.PERMISSION_PHONE) {
            list += Manifest.permission.READ_PHONE_STATE
            list += Manifest.permission.CALL_PHONE
        }
        if (BuildConfig.PERMISSION_BLUETOOTH && android.os.Build.VERSION.SDK_INT >= 31) {
            list += Manifest.permission.BLUETOOTH_CONNECT
            list += Manifest.permission.BLUETOOTH_SCAN
        }

        val missing = list.distinct().filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), permissionRequestCode)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == permissionRequestCode) {
            permissionCallback?.let { request ->
                if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    request.grant(request.resources)
                } else {
                    request.deny()
                }
                permissionCallback = null
            }
        }
    }

    @Deprecated("Deprecated in Android API, retained for compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == fileRequestCode) {
            val results = if (resultCode == Activity.RESULT_OK) {
                WebChromeClient.FileChooserParams.parseResult(resultCode, data)
            } else null
            fileCallback?.onReceiveValue(results)
            fileCallback = null
        }
    }

    override fun onDestroy() {
        webView.stopLoading()
        webView.destroy()
        super.onDestroy()
    }
}
