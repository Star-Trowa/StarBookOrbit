package io.github.star_trowa.starbookorbit.presentation.about

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.snackbar.Snackbar
import io.github.star_trowa.starbookorbit.BuildConfig
import io.github.star_trowa.starbookorbit.R
import io.github.star_trowa.starbookorbit.databinding.ActivityAboutBinding

class AboutActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAboutBinding

    companion object {

        private const val DEVELOPER_URL =
            "https://github.com/Star-Trowa"

        private const val GITHUB_URL =
            "https://github.com/Star-Trowa/StarBookOrbit"

        private const val RELEASES_URL =
            "https://github.com/Star-Trowa/StarBookOrbit/releases"

        private const val HELIBOARD_URL =
            "https://github.com/Star-Trowa/heliboard"

        private const val HELIBOARD_THEMES_URL =
            "https://github.com/Star-Trowa/heliboard-themes"

        private const val LINKDING_THEMES_URL =
            "https://github.com/Star-Trowa/linkding-themes"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener {
            finish()
        }

        binding.tvVersion.text = BuildConfig.VERSION_NAME

        binding.btnDeveloper.setOnClickListener {
            openUrl(DEVELOPER_URL)
        }

        binding.btnGithub.setOnClickListener {
            openUrl(GITHUB_URL)
        }

        binding.btnStarGithub.setOnClickListener {
            openUrl(GITHUB_URL)
        }

        binding.btnReleases.setOnClickListener {
            openUrl(RELEASES_URL)
        }

        binding.btnHeliBoard.setOnClickListener {
            openUrl(HELIBOARD_URL)
        }

        binding.btnHeliBoardThemes.setOnClickListener {
            openUrl(HELIBOARD_THEMES_URL)
        }

        binding.btnLinkdingThemes.setOnClickListener {
            openUrl(LINKDING_THEMES_URL)
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.appBar) { view, insets ->
            val top = insets.getInsets(
                WindowInsetsCompat.Type.statusBars()
            ).top

            view.updatePadding(top = top)
            insets
        }
    }

    private fun openUrl(url: String) {
        try {
            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    url.toUri()
                )
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