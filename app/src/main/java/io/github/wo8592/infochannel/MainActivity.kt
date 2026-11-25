package io.github.wo8592.infochannel

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Message
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.core.view.isVisible
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var errorPanel: android.view.View
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var renderRecoveryStarted = false
    private var webViewUsable = true

    private val fileChooserLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val callback = fileCallback
            fileCallback = null
            callback?.onReceiveValue(
                WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
            )
        }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        progressBar = findViewById(R.id.progressBar)
        swipeRefresh = findViewById(R.id.swipeRefresh)
        errorPanel = findViewById(R.id.errorPanel)
        findViewById<Button>(R.id.retryButton).setOnClickListener {
            errorPanel.isVisible = false
            webView.reload()
        }

        swipeRefresh.setColorSchemeColors(ContextCompat.getColor(this, R.color.accent))
        swipeRefresh.setProgressBackgroundColorSchemeColor(
            ContextCompat.getColor(this, R.color.window_background)
        )
        swipeRefresh.setOnRefreshListener { webView.reload() }

        configureWebView()
        webView.webViewClient = ChannelWebViewClient()
        webView.webChromeClient = ChannelChromeClient()

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    when {
                        errorPanel.isVisible -> errorPanel.isVisible = false
                        webView.canGoBack() -> webView.goBack()
                        else -> finish()
                    }
                }
            }
        )

        if (savedInstanceState != null) {
            webView.restoreState(savedInstanceState)
        } else {
            webView.loadUrl(HOME_URL)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if (webViewUsable) webView.saveState(outState)
        super.onSaveInstanceState(outState)
    }

    override fun onPause() {
        if (webViewUsable) {
            webView.onPause()
            CookieManager.getInstance().flush()
        }
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (webViewUsable) webView.onResume()
    }

    override fun onDestroy() {
        fileCallback?.onReceiveValue(null)
        fileCallback = null
        releaseWebView()
        super.onDestroy()
    }

    private fun releaseWebView() {
        if (!::webView.isInitialized || !webViewUsable) return
        webViewUsable = false
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.destroy()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        val debuggable = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        WebView.setWebContentsDebuggingEnabled(debuggable)

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }

        webView.setBackgroundColor(ContextCompat.getColor(this, R.color.window_background))
        webView.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            swipeRefresh.isEnabled = scrollY == 0 && !errorPanel.isVisible
        }
        webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            enqueueDownload(url, userAgent, contentDisposition, mimeType)
        }

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadsImagesAutomatically = true
            useWideViewPort = true
            loadWithOverviewMode = true
            mediaPlaybackRequiresUserGesture = true
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            cacheMode = WebSettings.LOAD_DEFAULT
            javaScriptCanOpenWindowsAutomatically = true
            setSupportMultipleWindows(true)
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
            allowFileAccess = false
            allowContentAccess = true
            safeBrowsingEnabled = true
            userAgentString = "$userAgentString InfoChannelApp/1.0"
        }
    }

    private fun showError() {
        errorPanel.isVisible = true
        progressBar.isVisible = false
        swipeRefresh.isRefreshing = false
        swipeRefresh.isEnabled = false
    }

    private fun enqueueDownload(
        url: String,
        userAgent: String,
        contentDisposition: String,
        mimeType: String,
    ) {
        try {
            val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
            val request = DownloadManager.Request(Uri.parse(url)).apply {
                addRequestHeader("User-Agent", userAgent)
                CookieManager.getInstance().getCookie(url)?.let { addRequestHeader("Cookie", it) }
                setMimeType(mimeType)
                setTitle(fileName)
                setDescription(fileName)
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                } else {
                    setDestinationInExternalFilesDir(
                        this@MainActivity,
                        Environment.DIRECTORY_DOWNLOADS,
                        fileName,
                    )
                }
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
            }
            val manager = getSystemService<DownloadManager>()
                ?: throw IllegalStateException("DownloadManager unavailable")
            manager.enqueue(request)
            Toast.makeText(this, R.string.download_started, Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            Toast.makeText(this, R.string.download_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun openExternal(uri: Uri) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.cannot_open_link, Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadInApp(uri: Uri) {
        val https = if (uri.scheme.equals("http", ignoreCase = true)) {
            uri.buildUpon().scheme("https").build()
        } else {
            uri
        }
        webView.loadUrl(https.toString())
    }

    private inner class ChannelWebViewClient : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            if (!request.isForMainFrame) return false
            val uri = request.url
            val scheme = uri.scheme?.lowercase()
            if (scheme != "http" && scheme != "https") {
                if (scheme == "about" || scheme == "blob" || scheme == "data") return false
                openExternal(uri)
                return true
            }
            if (isChannelHost(uri)) {
                if (scheme == "http") {
                    loadInApp(uri)
                    return true
                }
                return false
            }
            openExternal(uri)
            return true
        }

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            errorPanel.isVisible = false
            swipeRefresh.isEnabled = view.scrollY == 0
            progressBar.isVisible = true
        }

        override fun onPageFinished(view: WebView, url: String) {
            swipeRefresh.isRefreshing = false
            progressBar.isVisible = false
        }

        override fun onReceivedError(
            view: WebView,
            request: WebResourceRequest,
            error: WebResourceError,
        ) {
            if (request.isForMainFrame) showError()
        }

        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            releaseWebView()
            if (!renderRecoveryStarted) {
                renderRecoveryStarted = true
                recreate()
            }
            return true
        }
    }

    private inner class ChannelChromeClient : WebChromeClient() {
        override fun onProgressChanged(view: WebView, newProgress: Int) {
            progressBar.progress = newProgress
            progressBar.isVisible = newProgress in 1..99
            if (newProgress == 100) swipeRefresh.isRefreshing = false
        }

        override fun onShowFileChooser(
            webView: WebView,
            filePathCallback: ValueCallback<Array<Uri>>,
            fileChooserParams: FileChooserParams,
        ): Boolean {
            fileCallback?.onReceiveValue(null)
            fileCallback = filePathCallback
            return try {
                val chooser = Intent.createChooser(
                    fileChooserParams.createIntent(),
                    getString(R.string.choose_file),
                )
                fileChooserLauncher.launch(chooser)
                true
            } catch (_: ActivityNotFoundException) {
                fileCallback = null
                filePathCallback.onReceiveValue(null)
                Toast.makeText(this@MainActivity, R.string.cannot_open_file, Toast.LENGTH_SHORT).show()
                false
            }
        }

        override fun onCreateWindow(
            view: WebView,
            isDialog: Boolean,
            isUserGesture: Boolean,
            resultMsg: Message,
        ): Boolean {
            val popup = WebView(this@MainActivity)
            popup.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    if (isChannelHost(request.url)) {
                        loadInApp(request.url)
                    } else {
                        openExternal(request.url)
                    }
                    popup.destroy()
                    return true
                }
            }
            val transport = resultMsg.obj as WebView.WebViewTransport
            transport.webView = popup
            resultMsg.sendToTarget()
            return true
        }
    }

    companion object {
        private const val HOME_HOST = "wo8592-a11y.github.io"
        private const val HOME_URL = "https://wo8592-a11y.github.io/wo8592/"

        private fun isChannelHost(uri: Uri): Boolean {
            return uri.host.equals(HOME_HOST, ignoreCase = true)
        }
    }
}
