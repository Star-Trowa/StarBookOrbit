package io.github.star_trowa.starbookorbit.presentation.setup

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import android.content.ClipboardManager
import android.view.inputmethod.EditorInfo
import io.github.star_trowa.starbookorbit.StarBookOrbitApp
import io.github.star_trowa.starbookorbit.databinding.ActivitySetupBinding
import io.github.star_trowa.starbookorbit.domain.model.ServerConfig
import io.github.star_trowa.starbookorbit.presentation.reader.ReaderActivity
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SetupActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PREFILL_URL = "extra_prefill_url"
    }

    private lateinit var binding: ActivitySetupBinding

    private val viewModel: SetupViewModel by viewModels {
        val container = (application as StarBookOrbitApp).container
        SetupViewModel.factory(
            container.settingsRepository,
            container.validateUrlUseCase
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivitySetupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 1. Handle pre-filled URL from disconnects
        val prefill = intent.getStringExtra(EXTRA_PREFILL_URL)
        prefill?.let { binding.inputUrl.editText?.setText(it) }

        // 2. Start listening to the ViewModel
        observeState()

        // 3. The "Connect" Button
        binding.btnConnect.setOnClickListener {
            val url = binding.inputUrl.editText?.text?.toString().orEmpty()
            viewModel.connect(url)
        }

        // 4. The New "Paste" Button
        binding.btnPaste.setOnClickListener {
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            val clipText = clipboard.primaryClip?.getItemAt(0)?.text?.toString()

            if (!clipText.isNullOrBlank()) {
                binding.inputUrl.editText?.setText(clipText)
                binding.inputUrl.editText?.setSelection(clipText.length)
                binding.inputUrl.error = null // Clear any existing red error messages
            }
        }

        // 5. The Keyboard "Done" Action
        binding.inputUrl.editText?.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                binding.btnConnect.performClick()
                true
            } else {
                false
            }
        }

        // 6. The "Back" Button Escape Hatch
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val currentState = viewModel.state.value

                if (currentState is SetupViewModel.State.ExistingConfig) {
                    // Send safely back to their library
                    navigateToReader(currentState.config)
                } else {
                    // No saved server, exit the app normally
                    finish()
                }
            }
        })
    }

    private fun observeState() {
        // Check if the flag was thrown
        val forceShow = intent.getBooleanExtra("force_show_setup", false)
        // Remove it so Android forgets it on recreation
        if (forceShow) {
            intent.removeExtra("force_show_setup")
        }

        lifecycleScope.launch {
            viewModel.state.collectLatest { state ->
                when (state) {
                    is SetupViewModel.State.CheckingExisting -> showLoading(true)
                    is SetupViewModel.State.ExistingConfig -> {
                        if (forceShow) {
                            // If swapping servers, ignore the saved config and show the UI
                            showLoading(false)
                        } else {
                            // Normal app launch, log right in
                            navigateToReader(state.config)
                        }
                    }
                    is SetupViewModel.State.Idle -> showLoading(false)
                    is SetupViewModel.State.Saving -> showLoading(true)
                    is SetupViewModel.State.Error -> {
                        showLoading(false)
                        binding.inputUrl.error = state.message
                    }
                    is SetupViewModel.State.Ready -> navigateToReader(state.config)
                }
            }
        }
    }

    private fun showLoading(loading: Boolean) {
        binding.progress.isVisible = loading
        binding.btnConnect.isEnabled = !loading
        binding.inputUrl.isEnabled = !loading
    }

    private fun navigateToReader(config: ServerConfig) {
        val intent = Intent(this, ReaderActivity::class.java).apply {
            putExtra(ReaderActivity.EXTRA_URL, config.normalizedUrl)
        }
        startActivity(intent)
        finish()
    }
}