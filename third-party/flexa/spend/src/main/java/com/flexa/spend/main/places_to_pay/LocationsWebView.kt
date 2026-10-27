package com.flexa.spend.main.places_to_pay

import android.annotation.SuppressLint
import android.content.Intent
import android.util.Log
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.flexa.spend.BuildConfig

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LocationsWebView(
    modifier: Modifier = Modifier,
    viewModel: PlacesToPayViewModel,
    onViewCreated: (WebView) -> Unit,
    onTitle: (title: String) -> Unit,
    onFirstPage: (Boolean) -> Unit,
) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    var loading by remember { mutableStateOf(true) }
    val blur by animateDpAsState(
        targetValue = if (loading) 10.dp else 0.dp, label = "blur",
    )

    AndroidView(
        modifier = modifier.blur(blur),
        factory = {
            webView = WebView(it).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                webChromeClient = object : WebChromeClient() {
                    override fun onReceivedTitle(view: WebView?, t: String?) {
                        super.onReceivedTitle(view, t)
                        onTitle.invoke(t ?: "")
                    }
                }
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean {
                        if (!viewModel.url.host.equals(request?.url?.host, true)) {
                            try {
                                request?.url?.let { uri ->
                                    webView?.context?.startActivity(
                                        Intent(Intent.ACTION_VIEW, uri)
                                    )
                                }
                            } catch (ex: Exception) {
                                return false
                            }
                            return true
                        } else {
                            loading = true
                            Log.d(
                                "TAG",
                                "Url: ${request?.url} NB: ${request?.url?.getQueryParameter("nb")}"
                            )
                            val nextUrl = request?.url?.toString() ?: ""
                            viewModel.urlsList.add(nextUrl)
                            onFirstPage.invoke(viewModel.urlsList.isEmpty())
                            return false
                        }
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        onTitle.invoke(view?.title ?: "")
                        super.onPageFinished(view, url)
                        loading = false
                    }
                }
                val sdkVersion = BuildConfig.SPEND_VERSION
                settings.userAgentString += " Spend/$sdkVersion"
                settings.javaScriptEnabled = true
                if (viewModel.bundle.isEmpty) {
                    loadUrl(viewModel.url.toString())
                }
            }
            try {
                webView!!
            } finally {
                onViewCreated.invoke(webView!!)
            }
        },
        update = {
            if (!viewModel.bundle.isEmpty) {
                webView?.restoreState(viewModel.bundle)
                viewModel.bundle.clear()
            }
        })
}
