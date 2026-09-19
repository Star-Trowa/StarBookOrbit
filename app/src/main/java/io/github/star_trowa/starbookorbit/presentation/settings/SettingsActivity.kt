package io.github.star_trowa.starbookorbit.presentation.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
import io.github.star_trowa.starbookorbit.R
import io.github.star_trowa.starbookorbit.databinding.ActivitySettingsBinding
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.core.content.edit
import io.github.star_trowa.starbookorbit.presentation.about.AboutActivity

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    companion object {
        const val PREFS_NAME = "orbit_settings"
        const val KEY_VOLUME_PAGING = "pref_volume_paging"
        const val KEY_TAP_ZONES = "pref_tap_zones"
        const val DONATE_URL =
            "https://github.com/Star-Trowa/StarBookOrbit#support"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize SharedPreferences
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        binding.switchVolumePaging.isChecked = prefs.getBoolean(KEY_VOLUME_PAGING, false)
        binding.switchVolumePaging.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit { putBoolean(KEY_VOLUME_PAGING, isChecked) }
        }

        binding.switchTapZones.isChecked = prefs.getBoolean(KEY_TAP_ZONES, false)
        binding.switchTapZones.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit { putBoolean(KEY_TAP_ZONES, isChecked) }
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.appBar) { view, insets ->
            val statusBarInset = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            view.updatePadding(top = statusBarInset.top)
            insets
        }
        binding.toolbar.setNavigationOnClickListener { finish() }

        setupBattery()
        setupSupport()
    }

    override fun onResume() {
        super.onResume()
    }

    private fun setupBattery() {
        binding.btnBatteryOptimization.setOnClickListener {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = "package:$packageName".toUri()
            }
            try {
                startActivity(intent)
            } catch (_: Exception) {
                Snackbar.make(binding.root, R.string.settings_battery_unavailable, Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupSupport() {

        binding.btnDonate.setOnClickListener {
            openUrl(DONATE_URL)
        }

        binding.btnShare.setOnClickListener {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(
                    Intent.EXTRA_TEXT,
                    getString(R.string.share_starbookorbit_text)
                )
            }

            startActivity(
                Intent.createChooser(
                    shareIntent,
                    getString(R.string.settings_share)
                )
            )
        }

        binding.btnAbout.setOnClickListener {
            startActivity(
                Intent(this, AboutActivity::class.java)
            )
        }
    }

    private fun openUrl(url: String) {
        try {
            startActivity(
                Intent(Intent.ACTION_VIEW, url.toUri())
            )
        } catch (_: Exception) {
            Snackbar.make(
                binding.root,
                R.string.settings_link_unavailable,
                Snackbar.LENGTH_SHORT
            ).show()
        }
    }
}