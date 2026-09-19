package io.github.star_trowa.starbookorbit.presentation.reader

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.ValueCallback
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import io.github.star_trowa.starbookorbit.StarBookOrbitApp
import io.github.star_trowa.starbookorbit.databinding.ActivityReaderBinding
import io.github.star_trowa.starbookorbit.presentation.settings.SettingsActivity
import io.github.star_trowa.starbookorbit.presentation.setup.SetupActivity
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ReaderActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_URL = "extra_url"
    }

    private lateinit var currentUrl: String
    private lateinit var binding: ActivityReaderBinding

    private lateinit var navigationController: ReaderNavigationController
    private lateinit var webViewController: ReaderWebViewController
    private lateinit var tapZoneController: ReaderTapZoneController
    private lateinit var fabController: ReaderFabController

    private var fileUploadCallback: ValueCallback<Array<Uri>>? = null
    private var isVolumePagingEnabled: Boolean = false
    private var isTapPagingEnabled: Boolean = false

    private val filePickerLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val uris =
                if (result.resultCode == RESULT_OK && result.data != null) {
                    val data = result.data!!

                    when {
                        data.clipData != null -> {
                            Array(data.clipData!!.itemCount) { index ->
                                data.clipData!!.getItemAt(index).uri
                            }
                        }

                        data.data != null -> {
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemBars()

        binding = ActivityReaderBinding.inflate(layoutInflater)
        setContentView(binding.root)

        currentUrl =
            intent.getStringExtra(EXTRA_URL)
                ?: run {
                    finish()
                    return
                }

        // Initialize controllers
        navigationController =
            ReaderNavigationController(binding.webView)

        webViewController =
            ReaderWebViewController(
                activity = this,
                webView = binding.webView,
                launchFilePicker = { callback, intent ->
                    fileUploadCallback?.onReceiveValue(null)
                    fileUploadCallback = callback

                    try {
                        filePickerLauncher.launch(intent)
                        true
                    } catch (_: ActivityNotFoundException) {
                        fileUploadCallback?.onReceiveValue(null)
                        fileUploadCallback = null
                        false
                    }
                },
                onProgressChanged = {
                    binding.progress.progress = it
                },
                onPageFinished = {
                    binding.progress.isVisible = false
                },
                onMainFrameError = { errorMsg ->
                    binding.tvErrorTechDetails.text = errorMsg
                    binding.progress.isVisible = false
                    binding.errorState.isVisible = true
                    binding.webView.isVisible = false
                    binding.webView.loadUrl("about:blank")
                    binding.webView.setBackgroundColor(Color.TRANSPARENT)
                }
            )

        webViewController.setup()

        tapZoneController =
            ReaderTapZoneController(
                lifecycleOwner = this,
                webView = binding.webView,
                navigation = navigationController,
                isEnabled = { isTapPagingEnabled }
            )

        tapZoneController.start()

        fabController =
            ReaderFabController(
                activity = this,
                root = binding.root,
                fab = binding.fab,
                webView = binding.webView,
                onRefresh = {
                    if (binding.errorState.isVisible) {
                        viewModel.verifyServer(currentUrl)
                    } else {
                        binding.webView.reload()
                    }
                },
                onSettings = {
                    startActivity(
                        Intent(this, SettingsActivity::class.java)
                    )
                },
                onSwapServer = {
                    viewModel.swapServer()
                }
            )

        fabController.setup()

        setupBackHandler()
        observeState()
        observeEvents()

        binding.btnRetry.setOnClickListener {
            viewModel.verifyServer(currentUrl)
        }

        viewModel.verifyServer(currentUrl)
    }

    private fun hideSystemBars() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)

        if (hasFocus) {
            hideSystemBars()
        }
    }

    override fun onKeyDown(
        keyCode: Int,
        event: KeyEvent?
    ): Boolean {
        if (
            isVolumePagingEnabled &&
            navigationController.isCurrentlyReading()
        ) {
            when (keyCode) {
                KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    navigationController.nextPage()
                    return true
                }

                KeyEvent.KEYCODE_VOLUME_UP -> {
                    navigationController.previousPage()
                    return true
                }
            }
        }

        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(
        keyCode: Int,
        event: KeyEvent?
    ): Boolean {
        if (
            isVolumePagingEnabled &&
            navigationController.isCurrentlyReading() &&
            (
                keyCode == KeyEvent.KEYCODE_VOLUME_DOWN ||
                        keyCode == KeyEvent.KEYCODE_VOLUME_UP
                )
        ) {
            return true
        }

        return super.onKeyUp(keyCode, event)
    }

    private fun setupBackHandler() {
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (binding.errorState.isVisible) {
                        viewModel.swapServer()
                    } else if (binding.webView.canGoBack()) {
                        binding.webView.goBack()
                    } else {
                        finish()
                    }
                }
            }
        )
    }

    private fun observeEvents() {
        lifecycleScope.launch {
            viewModel.events.collectLatest { event ->
                when (event) {
                    is ReaderViewModel.Event.NavigateToSetup -> {
                        startActivity(
                            Intent(
                                this@ReaderActivity,
                                SetupActivity::class.java
                            ).apply {
                                putExtra(
                                    SetupActivity.EXTRA_PREFILL_URL,
                                    currentUrl
                                )
                                putExtra("force_show_setup", true)
                            }
                        )

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
                            // First time boot: Load the base server URL
                            binding.webView.loadUrl(currentUrl)
                        } else if (currentWebUrl == "about:blank") {
                            // Recovering from an error: Go back to the exact page user was on
                            if (binding.webView.canGoBack()) {
                                binding.webView.goBack()
                            } else {
                                binding.webView.loadUrl(currentUrl)
                            }
                        }

                        // If it's any other URL, do nothing. The WebView is already where it needs to be.
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
        CookieManager.getInstance().flush()
    }

    override fun onResume() {
        super.onResume()

        binding.webView.onResume()
        binding.webView.requestFocus()

        val prefs =
            getSharedPreferences(
                SettingsActivity.PREFS_NAME,
                MODE_PRIVATE
            )

        isVolumePagingEnabled =
            prefs.getBoolean(
                SettingsActivity.KEY_VOLUME_PAGING,
                false
            )

        isTapPagingEnabled =
            prefs.getBoolean(
                SettingsActivity.KEY_TAP_ZONES,
                false
            )
    }

    override fun onDestroy() {
        fileUploadCallback?.onReceiveValue(null)
        fileUploadCallback = null

        binding.webView.apply {
            clearHistory()
            (parent as? ViewGroup)?.removeView(this)
            destroy()
        }

        super.onDestroy()
    }
}
