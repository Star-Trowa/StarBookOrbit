package io.github.star_trowa.starbookorbit.presentation.reader

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.content.res.Resources
import android.net.Uri
import android.view.MotionEvent
import android.widget.PopupMenu
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
import io.github.star_trowa.starbookorbit.presentation.setup.SetupActivity
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.abs

class ReaderActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_URL = "extra_url"
        private const val PREFS_HINTS = "orbit_hints"
        private const val KEY_DRAG_HINT_SHOWN = "drag_hint_shown"
    }

    private lateinit var currentUrl: String

    private lateinit var binding: ActivityReaderBinding

    private var fileUploadCallback: ValueCallback<Array<Uri>>? = null

    private val filePickerLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val uris = if (result.resultCode == RESULT_OK) {
                WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Full screen — no white bar
        WindowCompat.setDecorFitsSystemWindows(window, false)

        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.statusBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        binding = ActivityReaderBinding.inflate(layoutInflater)
        setContentView(binding.root)

        currentUrl = intent.getStringExtra(EXTRA_URL) ?: run { finish(); return }

        // Setup UI components
        setupWebView()
        setupBackHandler()
        setupFab()

        // Start listening to the ViewModel's state machine
        observeState()
        observeEvents()

        // Kick off the network ping immediately
        viewModel.verifyServer(currentUrl)
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
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_refresh -> { // FIXED: Replaced '1 ->' with actual ID
                        if (binding.errorState.isVisible) {
                            viewModel.verifyServer(currentUrl)
                        } else {
                            binding.webView.reload()
                        }
                        true
                    }
                    R.id.action_disconnect -> { // FIXED: Replaced '2 ->' with actual ID
                        viewModel.disconnect()
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

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        binding.webView.apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                loadWithOverviewMode = true
                useWideViewPort = true
                builtInZoomControls = false
                displayZoomControls = false
                setSupportZoom(true)
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

                    val intent = fileChooserParams?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                        type = "*/*"
                    }

                    try {
                        filePickerLauncher.launch(intent)
                    } catch (_: ActivityNotFoundException) {
                        fileUploadCallback?.onReceiveValue(null) // Safe-reset the WebView engine; tell WebView: canceled
                        fileUploadCallback = null // Clear the reference
                        return false // Tell WebChromeClient: failed
                    }
                    return true
                }
            }
        }

        binding.btnRetry.setOnClickListener {
            viewModel.verifyServer(currentUrl)
        }
    }

    private fun setupBackHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.errorState.isVisible) {
                    // 1. If trapped on the error screen, treat 'Back' as 'Change Server'
                    viewModel.disconnect()
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

    override fun onDestroy() {
        binding.webView.apply {
            clearHistory()
            clearCache(true)
            loadUrl("about:blank")
            destroy()
        }
        super.onDestroy()
    }
}