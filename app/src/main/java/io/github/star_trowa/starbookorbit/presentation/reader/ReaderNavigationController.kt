package io.github.star_trowa.starbookorbit.presentation.reader

import android.view.KeyEvent
import android.webkit.WebView

class ReaderNavigationController(
    private val webView: WebView
) {

    companion object {
        private val AUDIO_FORMATS = listOf("=m4b", "=mp3", "=ogg", "=m4a", "=opus", "=flac")
        private val COMIC_FORMATS = listOf("cbz", "cbr", "cb7")
    }

    fun nextPage() = navigateReader("next")

    fun previousPage() = navigateReader("prev")

    fun isCurrentlyReading(): Boolean {
        return isEbookUrl(webView.url)
    }

    fun isPdfReader(): Boolean {
        val url = webView.url?.lowercase() ?: return false
        return url.contains(".pdf") ||
                url.contains("/pdf") ||
                url.contains("format=pdf")
    }

    fun isComicReader(): Boolean {
        val url = webView.url?.lowercase() ?: return false
        return COMIC_FORMATS.any { ext ->
            url.contains(".$ext") || url.contains("format=$ext")
        }
    }

    fun needsHeaderTracking(): Boolean {
        return isCurrentlyReading() && !isPdfReader() && !isComicReader()
    }

    fun customTapZonesEligible(): Boolean {
        return isCurrentlyReading() && !isComicReader()
    }

    private fun isEbookUrl(url: String?): Boolean {
        if (url == null || !url.contains("/read", ignoreCase = true)) return false

        return AUDIO_FORMATS.none { url.contains(it, ignoreCase = true) }
    }

    private fun navigateReader(direction: String) {
        if (!isCurrentlyReading()) return

        if (isPdfReader()) {
            webView.requestFocus()

            val keyCode = if (direction == "next") {
                KeyEvent.KEYCODE_DPAD_RIGHT
            } else {
                KeyEvent.KEYCODE_DPAD_LEFT
            }

            webView.dispatchKeyEvent(
                KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
            )

            webView.dispatchKeyEvent(
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

        webView.evaluateJavascript(js, null)
    }
}
