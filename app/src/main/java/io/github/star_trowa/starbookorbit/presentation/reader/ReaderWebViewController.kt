package io.github.star_trowa.starbookorbit.presentation.reader

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import java.net.URLDecoder

class ReaderWebViewController(
    private val activity: AppCompatActivity,
    private val webView: WebView,
    private val launchFilePicker: (ValueCallback<Array<Uri>>?, Intent) -> Boolean,
    private val onProgressChanged: (Int) -> Unit,
    private val onPageFinished: () -> Unit,
    private val onMainFrameError: (String) -> Unit
) {

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    fun setup() {
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)

        webView.apply {
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
                val request = DownloadManager.Request(url.toUri()).apply {
                    val cookies = cookieManager.getCookie(url)
                    addRequestHeader("cookie", cookies)
                    addRequestHeader("User-Agent", userAgent)

                    var fileName =
                        URLUtil.guessFileName(url, contentDisposition, mimeType)

                    if (contentDisposition != null) {
                        try {
                            var match =
                                Regex("filename=\"([^\"]+)\"").find(contentDisposition)

                            if (match == null) {
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
                    fileName = URLDecoder.decode(fileName, "UTF-8")

                    setTitle(fileName)
                    setDescription("Downloading file...")
                    setNotificationVisibility(
                        DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                    )
                    setDestinationInExternalPublicDir(
                        Environment.DIRECTORY_DOWNLOADS,
                        fileName
                    )
                }

                val downloadManager =
                    activity.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

                downloadManager.enqueue(request)

                Toast.makeText(
                    activity,
                    "Download started...",
                    Toast.LENGTH_SHORT
                ).show()
            }

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(
                    view: WebView?,
                    url: String?
                ) {
                    this@ReaderWebViewController.onPageFinished()
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    if (request?.isForMainFrame == true) {
                        val chromeError =
                            error?.description?.toString() ?: "Unknown Network Error"

                        onMainFrameError(chromeError)
                    }
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(
                    view: WebView?,
                    newProgress: Int
                ) {
                    this@ReaderWebViewController.onProgressChanged(newProgress)
                }

                override fun onShowFileChooser(
                    webView: WebView?,
                    filePathCallback: ValueCallback<Array<Uri>>?,
                    fileChooserParams: FileChooserParams?
                ): Boolean {
                    val allowMultiple =
                        fileChooserParams?.mode ==
                                FileChooserParams.MODE_OPEN_MULTIPLE

                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)

                        // Do NOT inherit the website's restrictive accept filter.
                        type = "*/*"

                        putExtra(Intent.EXTRA_ALLOW_MULTIPLE, allowMultiple)
                    }

                    return launchFilePicker(filePathCallback, intent)
                }
            }
        }
    }
}
