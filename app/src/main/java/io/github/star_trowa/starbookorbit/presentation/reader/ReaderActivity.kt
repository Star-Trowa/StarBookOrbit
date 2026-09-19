package io.github.star_trowa.starbookorbit.presentation.reader

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.content.res.Resources
import android.net.Uri
import android.view.KeyEvent
import android.view.MotionEvent
import android.widget.PopupMenu
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import android.view.ViewGroup
import android.webkit.ValueCallback
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebChromeClient
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import io.github.star_trowa.starbookorbit.R
import io.github.star_trowa.starbookorbit.StarBookOrbitApp
import io.github.star_trowa.starbookorbit.databinding.ActivityReaderBinding
import io.github.star_trowa.starbookorbit.presentation.settings.SettingsActivity
import io.github.star_trowa.starbookorbit.presentation.setup.SetupActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.abs

class ReaderActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_URL = "extra_url"
        private const val PREFS_HINTS = "orbit_hints"
        private const val KEY_DRAG_HINT_SHOWN = "drag_hint_shown"
        private val AUDIO_FORMATS = listOf("=m4b", "=mp3", "=ogg", "=m4a", "=opus", "=flac")
        private val COMIC_FORMATS = listOf("cbz", "cbr", "cb7")
        private val JS_CHECK_HEADER_VISIBLE = """
            (function() {
                var header = document.querySelector('header');
                if (!header) return false;
                var style = window.getComputedStyle(header);
                var opacity = parseFloat(style.opacity);
                var rect = header.getBoundingClientRect();
                return opacity > 0.05 && rect.bottom > 0;
            })();
        """.trimIndent()
    }
    private lateinit var currentUrl: String

    private lateinit var binding: ActivityReaderBinding

    private var fileUploadCallback: ValueCallback<Array<Uri>>? = null

    // Default to false so hardware powered page navigation remains off unless explicitly enabled
    private var isVolumePagingEnabled: Boolean = false
    private var isTapPagingEnabled: Boolean = false

    // Tracks whether the web app's fixed header/status bar is currently visible.
    // Defaults to true so tap zones stay OFF until the JS watcher confirms it's hidden.
    @Volatile
    private var isHeaderVisible: Boolean = true

    private val filePickerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val uris: Array<Uri>? =
                if (result.resultCode == RESULT_OK && result.data != null) {
                    val data = result.data!!
                    when {
                        data.clipData != null -> {
                            // Multiple files selected
                            Array(data.clipData!!.itemCount) { index ->
                                data.clipData!!.getItemAt(index).uri
                            }
                        }
                        data.data != null -> {
                            // Single file selected
                            arrayOf(data.data!!)
                        }
                        else -> null
                    }
                } else {
                    null
                }
            fileUploadCallback?.onReceiveValue(uris)
            fileUploadCallback = null
        }

    private val viewModel: ReaderViewModel by viewModels {
        val container = (application as StarBookOrbitApp).container
        ReaderViewModel.factory(
            container.settingsRepository,
            container.checkServerStatusUseCase
        )
    }

    private fun hideSystemBars() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Full screen — no white bar
        WindowCompat.setDecorFitsSystemWindows(window, false)

        hideSystemBars()

        binding = ActivityReaderBinding.inflate(layoutInflater)
        setContentView(binding.root)

        currentUrl = intent.getStringExtra(EXTRA_URL) ?: run { finish(); return }

        // Setup UI components
        setupWebView()
        startHeaderVisibilityPolling()
        setupTapZones()
        setupBackHandler()
        setupFab()

        // Start listening to the ViewModel's state machine
        observeState()
        observeEvents()

        // Kick off the network ping immediately
        viewModel.verifyServer(currentUrl)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemBars()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // ONLY intercept if the setting is on AND we are inside a book
        if (isVolumePagingEnabled && isCurrentlyReading()) {
            when (keyCode) {
                KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    scrollToNextPage()
                    return true
                }
                KeyEvent.KEYCODE_VOLUME_UP -> {
                    scrollToPreviousPage()
                    return true
                }
            }
        }
        // If not in a book (e.g., on the main page), let Android handle the volume normally
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (isVolumePagingEnabled && isCurrentlyReading() &&
            (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP)
        ) {
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    private fun navigateReader(direction: String) {
        if (!isCurrentlyReading()) return

        if (isPdfReader()) {
            binding.webView.requestFocus()
            val keyCode = if (direction == "next") {
                KeyEvent.KEYCODE_DPAD_RIGHT
            } else {
                KeyEvent.KEYCODE_DPAD_LEFT
            }
            binding.webView.dispatchKeyEvent(
                KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
            )
            binding.webView.dispatchKeyEvent(
                KeyEvent(KeyEvent.ACTION_UP, keyCode)
            )
            return
        }
        triggerEpubNavigation(direction)
    }

    private fun triggerEpubNavigation(direction: String) {
        val js = if (direction == "next") {
            """
        (function() {
            try {
                const reader = document.querySelector('foliate-view');

                if (reader && typeof reader.next === 'function') {
                    reader.next();
                    return "foliate-next";
                }

                const event = new KeyboardEvent('keydown', {
                    key: 'ArrowRight',
                    code: 'ArrowRight',
                    keyCode: 39,
                    which: 39,
                    bubbles: true,
                    cancelable: true,
                    composed: true
                });

                const frames = document.querySelectorAll('iframe');

                for (const frame of frames) {
                    try {
                        const doc = frame.contentDocument;
                        if (!doc) continue;

                        const target =
                            doc.activeElement ||
                            doc.body ||
                            doc.documentElement;

                        if (target) {
                            target.dispatchEvent(event);
                            return "iframe-next";
                        }
                    } catch (e) {
                        // Ignore inaccessible iframe.
                    }
                }

                document.dispatchEvent(event);
                return "document-next";

            } catch (e) {
                return "error-next";
            }
        })();
        """.trimIndent()
        } else {
            """
        (function() {
            try {
                const reader = document.querySelector('foliate-view');

                if (reader && typeof reader.prev === 'function') {
                    reader.prev();
                    return "foliate-prev";
                }

                const event = new KeyboardEvent('keydown', {
                    key: 'ArrowLeft',
                    code: 'ArrowLeft',
                    keyCode: 37,
                    which: 37,
                    bubbles: true,
                    cancelable: true,
                    composed: true
                });

                const frames = document.querySelectorAll('iframe');

                for (const frame of frames) {
                    try {
                        const doc = frame.contentDocument;
                        if (!doc) continue;

                        const target =
                            doc.activeElement ||
                            doc.body ||
                            doc.documentElement;

                        if (target) {
                            target.dispatchEvent(event);
                            return "iframe-prev";
                        }
                    } catch (e) {
                        // Ignore inaccessible iframe.
                    }
                }

                document.dispatchEvent(event);
                return "document-prev";

            } catch (e) {
                return "error-prev";
            }
        })();
        """.trimIndent()
        }

        binding.webView.evaluateJavascript(js, null)
    }

    private fun isPdfReader(): Boolean {
        val url = binding.webView.url?.lowercase() ?: return false

        return url.contains(".pdf") ||
                url.contains("/pdf") ||
                url.contains("format=pdf")
    }

    private fun isComicReader(): Boolean {
        val url = binding.webView.url?.lowercase() ?: return false
        return COMIC_FORMATS.any { ext -> url.contains(".$ext") || url.contains("format=$ext") }
    }

    // True only for the epub/Foliate reader. The one reader whose header
    // actually shows/hides. PDF's header is permanently visible, and comics
    // have their own overlay entirely, so neither needs header polling.
    private fun needsHeaderTracking(): Boolean {
        return isCurrentlyReading() && !isPdfReader() && !isComicReader()
    }

    // True whenever our overlay tap zones should be allowed to claim a tap.
    // Comics ship their own tap-to-turn-page zones, so they're excluded here
    // even though isCurrentlyReading() is still true for them.
    private fun customTapZonesEligible(): Boolean {
        return isCurrentlyReading() && !isComicReader()
    }

    private fun isCurrentlyReading(): Boolean {
        return isEbookUrl(binding.webView.url)
    }

    private fun isEbookUrl(url: String?): Boolean {
        if (url == null || !url.contains("/read", ignoreCase = true)) return false

        return AUDIO_FORMATS.none { url.contains(it, ignoreCase = true) }
    }

    private fun scrollToNextPage() {
        navigateReader("next")
    }

    private fun scrollToPreviousPage() {
        navigateReader("prev")
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupFab() {
        // nudge FAB above system nav bar
        ViewCompat.setOnApplyWindowInsetsListener(binding.fab) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = bars.bottom + 16.dpToPx()
                marginEnd = bars.right + 16.dpToPx()
            }
            insets
        }

        // Set low transparency so it doesn't block book text
        binding.fab.alpha = 0.45f

        // Drag logic initialization
        var dX = 0f
        var dY = 0f
        var isDragging = false // Track if the user actually moved their finger

        binding.fab.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    dX = view.x - event.rawX
                    dY = view.y - event.rawY
                    isDragging = false
                    true // Return true to claim the gesture stream
                }
                MotionEvent.ACTION_MOVE -> {
                    // Check if movement is intentional, not just a shaky finger tap
                    if (abs(event.rawX + dX - view.x) > 5 || abs(event.rawY + dY - view.y) > 5) {
                        isDragging = true
                        view.animate()
                            .x(event.rawX + dX)
                            .y(event.rawY + dY)
                            .setDuration(0).start()
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        view.performClick()
                    } else {
                        // snap to nearest left or right edge
                        val parent = view.parent as android.view.View
                        val midX = parent.width / 2f
                        val targetX = if (view.x + view.width / 2 < midX) {
                            16.dpToPx().toFloat()
                        } else {
                            (parent.width - view.width - 16.dpToPx()).toFloat()
                        }
                        view.animate()
                            .x(targetX)
                            .setDuration(200)
                            .setInterpolator(android.view.animation.DecelerateInterpolator())
                            .start()
                    }
                    true
                }
                else -> false
            }
        }

        // Click logic for the popup menu
        binding.fab.setOnClickListener { anchor ->
            val popup = PopupMenu(this, anchor)
            popup.menuInflater.inflate(R.menu.reader_menu, popup.menu)

            // Return focus to WebView if menu is closed without selecting an option
            popup.setOnDismissListener {
                binding.webView.requestFocus()
            }

            // Only show "Forward" if there is actually a page to go forward to
            val forwardItem = popup.menu.findItem(R.id.action_forward)
            forwardItem?.isVisible = binding.webView.canGoForward()

            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_refresh -> {
                        if (binding.errorState.isVisible) {
                            viewModel.verifyServer(currentUrl)
                        } else {
                            binding.webView.reload()
                        }
                        true
                    }
                    R.id.action_settings -> {
                        startActivity(Intent(this, SettingsActivity::class.java))
                        true
                    }
                    R.id.action_swap_server -> {
                        viewModel.swapServer()
                        true
                    }
                    R.id.action_forward -> {
                        if (binding.webView.canGoForward()) {
                            binding.webView.goForward()
                        }
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }

        showDragHintIfNeeded()
    }

    private fun showDragHintIfNeeded() {
        val prefs = getSharedPreferences(PREFS_HINTS, MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_DRAG_HINT_SHOWN, false)) {
            Snackbar.make(
                binding.root,
                getString(R.string.hint_drag_fab),
                Snackbar.LENGTH_INDEFINITE // Stays until clicked or swiped away
            ).setAction(getString(R.string.action_got_it)) {
            }.show()

            prefs.edit {
                putBoolean(KEY_DRAG_HINT_SHOWN, true)
            }
        }
    }

    private fun Int.dpToPx(): Int =
        (this * Resources.getSystem().displayMetrics.density).toInt()

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    private fun setupWebView() {
        val cookieManager = android.webkit.CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        // TODO: If SSO is implemented, uncomment below
        // cookieManager.setAcceptThirdPartyCookies(binding.webView, true)

        binding.webView.apply {
            isFocusable = true
            isFocusableInTouchMode = true
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                loadWithOverviewMode = true
                useWideViewPort = true
                builtInZoomControls = false
                displayZoomControls = false
                setSupportZoom(true)
            }

            setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
                val request = android.app.DownloadManager.Request(url.toUri()).apply {
                    val cookies = cookieManager.getCookie(url)
                    addRequestHeader("cookie", cookies)
                    addRequestHeader("User-Agent", userAgent)

                    var fileName = android.webkit.URLUtil.guessFileName(url, contentDisposition, mimeType)

                    // Intercept and manually parse the header if Android gets lazy
                    if (contentDisposition != null) {
                        try {
                            // Look for: filename="The Book Title.epub"
                            var match = Regex("filename=\"([^\"]+)\"").find(contentDisposition)
                            if (match == null) {
                                // Look for unquoted: filename=The_Book_Title.epub
                                match = Regex("filename=([^;]+)").find(contentDisposition)
                            }
                            if (match != null) {
                                fileName = match.groupValues[1]
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                    // Clean up URL encoding (e.g. turns "The%20Book.epub" into "The Book.epub")
                    fileName = java.net.URLDecoder.decode(fileName, "UTF-8")

                    setTitle(fileName)
                    setDescription("Downloading file...")
                    setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)

                    setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, fileName)
                }

                val downloadManager = getSystemService(DOWNLOAD_SERVICE) as android.app.DownloadManager
                downloadManager.enqueue(request)

                android.widget.Toast.makeText(this@ReaderActivity, "Download started...", android.widget.Toast.LENGTH_SHORT).show()
            }

            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                    // Let the ViewModel handle loading states
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    binding.progress.isVisible = false
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    if (request?.isForMainFrame == true) {
                        val chromeError = error?.description?.toString() ?: "Unknown Network Error"
                        binding.tvErrorTechDetails.text = chromeError

                        binding.progress.isVisible = false
                        binding.errorState.isVisible = true
                        binding.webView.isVisible = false

                        view?.loadUrl("about:blank")
                        view?.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    }
                }
            }

            webChromeClient = object : WebChromeClient() {

                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    binding.progress.progress = newProgress
                }

                override fun onShowFileChooser(
                    webView: WebView?,
                    filePathCallback: ValueCallback<Array<Uri>>?,
                    fileChooserParams: FileChooserParams?
                ): Boolean {

                    fileUploadCallback?.onReceiveValue(null)
                    fileUploadCallback = filePathCallback

                    val allowMultiple =
                        fileChooserParams?.mode == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE

                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)

                        // Do NOT inherit the website's restrictive accept filter.
                        type = "*/*"

                        putExtra(Intent.EXTRA_ALLOW_MULTIPLE, allowMultiple)
                    }

                    try {
                        filePickerLauncher.launch(intent)
                    } catch (_: ActivityNotFoundException) {
                        fileUploadCallback?.onReceiveValue(null)
                        fileUploadCallback = null
                        return false
                    }
                    return true
                }
            }
        }

        binding.btnRetry.setOnClickListener {
            viewModel.verifyServer(currentUrl)
        }
    }


    private fun startHeaderVisibilityPolling() {
        lifecycleScope.launch {
            while (true) {
                if (isTapPagingEnabled && needsHeaderTracking()) {
                    binding.webView.evaluateJavascript(JS_CHECK_HEADER_VISIBLE) { result ->
                        isHeaderVisible = result == "true"
                    }
                } else {
                    // PDF, comics, not reading, or tap paging off — nothing to track.
                    isHeaderVisible = false
                }
                delay(250)
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupTapZones() {
        var downX = 0f
        var downY = 0f
        var downTime = 0L
        var edgeGesture = false

        val touchSlop = android.view.ViewConfiguration
            .get(this)
            .scaledTouchSlop

        binding.webView.setOnTouchListener { _, event ->

            when (event.actionMasked) {

                MotionEvent.ACTION_DOWN -> {

                    // Let the normal BookOrbit WebView handle scrolling/swiping.
                    if (!isCurrentlyReading() || !isTapPagingEnabled) {
                        return@setOnTouchListener false
                    }

                    downX = event.x
                    downY = event.y
                    downTime = android.os.SystemClock.uptimeMillis()

                    edgeGesture = false

                    val headerBlocksTapZones =
                        isHeaderVisible && needsHeaderTracking()

                    if (!headerBlocksTapZones && customTapZonesEligible()) {
                        val width = binding.webView.width
                        val height = binding.webView.height

                        if (width > 0 && height > 0) {
                            val controlBarHeight = (height * 0.07f)
                                .coerceIn(40.dpToPx().toFloat(), 64.dpToPx().toFloat())

                            val inVerticalTapZone =
                                event.y >= controlBarHeight &&
                                        event.y <= height - controlBarHeight

                            val inHorizontalTapZone =
                                event.x < width / 3f ||
                                        event.x > width * 2f / 3f

                            edgeGesture =
                                inVerticalTapZone && inHorizontalTapZone
                        }
                    }

                    edgeGesture
                }

                MotionEvent.ACTION_MOVE -> {
                    if (!isCurrentlyReading() || !isTapPagingEnabled) {
                        return@setOnTouchListener false
                    }

                    edgeGesture
                }

                MotionEvent.ACTION_UP -> {
                    if (!edgeGesture) {
                        return@setOnTouchListener false
                    }

                    val dx = event.x - downX
                    val dy = event.y - downY

                    val distance = kotlin.math.sqrt(
                        dx * dx + dy * dy
                    )

                    val duration =
                        android.os.SystemClock.uptimeMillis() - downTime

                    edgeGesture = false

                    // Only treat it as a tap, not a swipe/drag.
                    if (
                        distance <= touchSlop * 2 &&
                        duration <= 500L
                    ) {
                        val width = binding.webView.width

                        if (event.x < width / 3f) {
                            scrollToPreviousPage()
                        } else if (event.x > width * 2f / 3f) {
                            scrollToNextPage()
                        }
                    }

                    true
                }

                MotionEvent.ACTION_CANCEL -> {
                    edgeGesture = false
                    true
                }

                else -> {
                    edgeGesture
                }
            }
        }
    }

    private fun setupBackHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.errorState.isVisible) {
                    // 1. If trapped on the error screen, treat 'Back' as 'Swap Server'
                    viewModel.swapServer()
                } else if (binding.webView.canGoBack()) {
                    // 2. If browsing normally, go back to the previous web page
                    binding.webView.goBack()
                } else {
                    // 3. If at the root of a successful web session, exit the app
                    finish()
                }
            }
        })
    }

    private fun observeEvents() {
        lifecycleScope.launch {
            viewModel.events.collectLatest { event ->
                when (event) {
                    is ReaderViewModel.Event.NavigateToSetup -> {
                        val intent = Intent(this@ReaderActivity, SetupActivity::class.java).apply {
                            putExtra(SetupActivity.EXTRA_PREFILL_URL, currentUrl)
                            putExtra("force_show_setup", true)
                        }
                        startActivity(intent)
                        finish()
                    }
                }
            }
        }
    }

    private fun observeState() {
        lifecycleScope.launch {
            viewModel.state.collectLatest { state ->
                when (state) {
                    is ReaderViewModel.State.Idle -> {
                        // Do nothing, waiting to start
                    }
                    is ReaderViewModel.State.Loading -> {
                        binding.progress.isVisible = true
                        binding.errorState.isVisible = false
                        binding.webView.isVisible = false
                    }
                    is ReaderViewModel.State.ServerUp -> {
                        binding.progress.isVisible = false
                        binding.webView.isVisible = true

                        val currentWebUrl = binding.webView.url

                        if (currentWebUrl.isNullOrEmpty()) {
                            // 1. First time boot: Load the base server URL
                            binding.webView.loadUrl(currentUrl)
                        } else if (currentWebUrl == "about:blank") {
                            // 2. Recovering from an error: Go back to the exact page user was on
                            if (binding.webView.canGoBack()) {
                                binding.webView.goBack()
                            } else {
                                binding.webView.loadUrl(currentUrl) // Fallback
                            }
                        }
                        // 3. If it's any other URL, do nothing. The WebView is already where it needs to be.
                    }
                    is ReaderViewModel.State.ServerDown -> {
                        binding.progress.isVisible = false
                        binding.errorState.isVisible = true
                        binding.webView.isVisible = false
                    }
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // Force the session to save to the phone's disk IMMEDIATELY
        android.webkit.CookieManager.getInstance().flush()
    }

    override fun onResume() {
        super.onResume()
        // Wake the WebView back up
        binding.webView.onResume()
        binding.webView.requestFocus()

        // Read the latest state from SharedPreferences every time the activity comes to the foreground
        val prefs = getSharedPreferences(SettingsActivity.PREFS_NAME, MODE_PRIVATE)
        isVolumePagingEnabled = prefs.getBoolean(SettingsActivity.KEY_VOLUME_PAGING, false)
        isTapPagingEnabled = prefs.getBoolean(SettingsActivity.KEY_TAP_ZONES, false)
    }

    override fun onDestroy() {
        // Save cookies one last time just to be safe
        android.webkit.CookieManager.getInstance().flush()

        binding.webView.apply {
            clearHistory()
            // Detach from layout to prevent memory leaks
            (parent as? ViewGroup)?.removeView(this)
            destroy()
        }
        super.onDestroy()
    }
}
