package io.github.star_trowa.starbookorbit.presentation.reader

import android.annotation.SuppressLint
import android.os.SystemClock
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.webkit.WebView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sqrt

class ReaderTapZoneController(
    private val lifecycleOwner: LifecycleOwner,
    private val webView: WebView,
    private val navigation: ReaderNavigationController,
    private val isEnabled: () -> Boolean
) {

    companion object {
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

    @Volatile
    private var isHeaderVisible: Boolean = true

    fun start() {
        startHeaderVisibilityPolling()
        setupTapZones()
    }

    private fun startHeaderVisibilityPolling() {
        lifecycleOwner.lifecycleScope.launch {
            lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (isActive) {
                    if (isEnabled() && navigation.needsHeaderTracking()) {
                        webView.evaluateJavascript(JS_CHECK_HEADER_VISIBLE) { result ->
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
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupTapZones() {
        var downX = 0f
        var downY = 0f
        var downTime = 0L
        var edgeGesture = false

        val touchSlop =
            ViewConfiguration.get(webView.context).scaledTouchSlop

        webView.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    // Let the normal BookOrbit WebView handle scrolling/swiping.
                    if (!navigation.isCurrentlyReading() || !isEnabled()) {
                        return@setOnTouchListener false
                    }

                    downX = event.x
                    downY = event.y
                    downTime = SystemClock.uptimeMillis()
                    edgeGesture = false

                    val headerBlocksTapZones =
                        isHeaderVisible && navigation.needsHeaderTracking()

                    if (!headerBlocksTapZones && navigation.customTapZonesEligible()) {
                        val width = webView.width
                        val height = webView.height

                        if (width > 0 && height > 0) {
                            val controlBarHeight =
                                (height * 0.07f).coerceIn(
                                    40 * webView.resources.displayMetrics.density,
                                    64 * webView.resources.displayMetrics.density
                                )

                            val inVerticalTapZone =
                                event.y >= controlBarHeight &&
                                        event.y <= height - controlBarHeight

                            val inHorizontalTapZone =
                                event.x < width / 3f ||
                                        event.x > width * 2 / 3f

                            edgeGesture =
                                inVerticalTapZone && inHorizontalTapZone
                        }
                    }

                    edgeGesture
                }

                MotionEvent.ACTION_MOVE -> {
                    if (!navigation.isCurrentlyReading() || !isEnabled()) {
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

                    val distance = sqrt(dx * dx + dy * dy)

                    val duration =
                        SystemClock.uptimeMillis() - downTime

                    edgeGesture = false

                    // Only treat it as a tap, not a swipe/drag.
                    if (
                        distance <= touchSlop * 2 &&
                        duration <= 500L
                    ) {
                        if (event.x < webView.width / 3f) {
                            navigation.previousPage()
                        } else if (event.x > webView.width * 2 / 3f) {
                            navigation.nextPage()
                        }
                    }

                    true
                }

                MotionEvent.ACTION_CANCEL -> {
                    edgeGesture = false
                    true
                }

                else -> edgeGesture
            }
        }
    }
}
