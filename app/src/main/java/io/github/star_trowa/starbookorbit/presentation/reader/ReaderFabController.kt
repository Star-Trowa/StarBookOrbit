package io.github.star_trowa.starbookorbit.presentation.reader

import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.webkit.WebView
import android.widget.PopupMenu
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import com.google.android.material.snackbar.Snackbar
import io.github.star_trowa.starbookorbit.R
import kotlin.math.abs

class ReaderFabController(
    private val activity: AppCompatActivity,
    private val root: View,
    private val fab: View,
    private val webView: WebView,
    private val onRefresh: () -> Unit,
    private val onSettings: () -> Unit,
    private val onSwapServer: () -> Unit
) {

    companion object {
        private const val PREFS_HINTS = "orbit_hints"
        private const val KEY_DRAG_HINT_SHOWN = "drag_hint_shown"
    }

    @SuppressLint("ClickableViewAccessibility")
    fun setup() {
        ViewCompat.setOnApplyWindowInsetsListener(fab) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin =
                    bars.bottom + (16 * view.resources.displayMetrics.density).toInt()

                marginEnd =
                    bars.right + (16 * view.resources.displayMetrics.density).toInt()
            }

            insets
        }

        // Set low transparency so it doesn't block book text
        fab.alpha = 0.45f

        // Drag logic initialization
        var dX = 0f
        var dY = 0f
        var isDragging = false

        fab.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    dX = view.x - event.rawX
                    dY = view.y - event.rawY
                    isDragging = false
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    // Check if movement is intentional, not just a shaky finger tap
                    if (
                        abs(event.rawX + dX - view.x) > 5 ||
                        abs(event.rawY + dY - view.y) > 5
                    ) {
                        isDragging = true

                        view.animate()
                            .x(event.rawX + dX)
                            .y(event.rawY + dY)
                            .setDuration(0)
                            .start()
                    }

                    true
                }

                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        view.performClick()
                    } else {
                        // snap to nearest left or right edge
                        val parent = view.parent as View
                        val midX = parent.width / 2f
                        val padding =
                            16 * view.resources.displayMetrics.density

                        val targetX =
                            if (view.x + view.width / 2 < midX) {
                                padding
                            } else {
                                parent.width - view.width - padding
                            }

                        view.animate()
                            .x(targetX)
                            .setDuration(200)
                            .setInterpolator(DecelerateInterpolator())
                            .start()
                    }

                    true
                }

                else -> false
            }
        }

        fab.setOnClickListener { anchor ->
            val popup = PopupMenu(activity, anchor)
            popup.menuInflater.inflate(R.menu.reader_menu, popup.menu)

            // Return focus to WebView if menu is closed without selecting an option
            popup.setOnDismissListener {
                webView.requestFocus()
            }

            // Only show "Forward" if there is actually a page to go forward to
            popup.menu.findItem(R.id.action_forward)?.isVisible =
                webView.canGoForward()

            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_refresh -> {
                        onRefresh()
                        true
                    }

                    R.id.action_settings -> {
                        onSettings()
                        true
                    }

                    R.id.action_swap_server -> {
                        onSwapServer()
                        true
                    }

                    R.id.action_forward -> {
                        if (webView.canGoForward()) {
                            webView.goForward()
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
        val prefs =
            activity.getSharedPreferences(PREFS_HINTS, Context.MODE_PRIVATE)

        if (!prefs.getBoolean(KEY_DRAG_HINT_SHOWN, false)) {
            Snackbar.make(
                root,
                activity.getString(R.string.hint_drag_fab),
                Snackbar.LENGTH_INDEFINITE
            ).setAction(activity.getString(R.string.action_got_it)) {
            }.show()

            prefs.edit {
                putBoolean(KEY_DRAG_HINT_SHOWN, true)
            }
        }
    }
}
