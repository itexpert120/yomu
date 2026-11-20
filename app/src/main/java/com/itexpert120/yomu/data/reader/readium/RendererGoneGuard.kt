package com.itexpert120.yomu.data.reader.readium

import android.graphics.Bitmap
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager

/**
 * Keeps a dead WebView renderer from killing the whole app.
 *
 * Android reclaims the WebView renderer process freely while the app is in the background. If no
 * WebViewClient handles [WebViewClient.onRenderProcessGone], Chromium aborts the app process
 * ("Render process's crash wasn't handled by all associated webviews"). Readium's page clients
 * don't handle it, so each page WebView's client is wrapped with one that does and reports it.
 *
 * Install on the navigator fragment's child FragmentManager; page fragments are created lazily by
 * the navigator's pager, so each new page view is wrapped as it appears.
 */
internal fun FragmentManager.guardWebViewRenderers(onRendererGone: () -> Unit) {
    registerFragmentLifecycleCallbacks(
        object : FragmentManager.FragmentLifecycleCallbacks() {
            override fun onFragmentViewCreated(fm: FragmentManager, f: Fragment, v: View, savedInstanceState: Bundle?) {
                v.guardWebViews(onRendererGone)
            }
        },
        true,
    )
    // Pages that already exist when the guard is installed.
    fragments.forEach { it.view?.guardWebViews(onRendererGone) }
}

private fun View.guardWebViews(onRendererGone: () -> Unit) {
    when (this) {
        is WebView -> {
            val current = webViewClient
            if (current !is RendererGuardWebViewClient) {
                webViewClient = RendererGuardWebViewClient(current) { dead ->
                    // A WebView whose renderer is gone must not draw again; hide it until the
                    // navigator is rebuilt.
                    dead.visibility = View.GONE
                    onRendererGone()
                }
            }
        }
        is ViewGroup -> for (i in 0 until childCount) getChildAt(i).guardWebViews(onRendererGone)
    }
}

/** Forwards everything Readium's page clients override; only renderer death is handled here. */
private class RendererGuardWebViewClient(
    private val delegate: WebViewClient,
    private val onGone: (WebView) -> Unit,
) : WebViewClient() {
    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = delegate.shouldOverrideUrlLoading(view, request)

    override fun shouldOverrideKeyEvent(view: WebView, event: KeyEvent): Boolean = delegate.shouldOverrideKeyEvent(view, event)

    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? = delegate.shouldInterceptRequest(view, request)

    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) = delegate.onPageStarted(view, url, favicon)

    override fun onPageFinished(view: WebView, url: String?) = delegate.onPageFinished(view, url)

    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) = delegate.onReceivedError(view, request, error)

    override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) = delegate.onReceivedHttpError(view, request, errorResponse)

    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
        onGone(view)
        return true
    }
}
