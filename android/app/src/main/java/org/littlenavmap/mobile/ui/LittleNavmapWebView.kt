/*
 * Copyright 2015-2026 Alexander Barthel (alex@littlenavmap.org)
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Modified for the Little Navmap Android client in 2026.
 */

package org.littlenavmap.mobile.ui

import android.annotation.SuppressLint
import android.content.pm.ApplicationInfo
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.view.View
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import org.littlenavmap.mobile.R
import org.littlenavmap.mobile.model.ServerProfile
import java.util.Locale

@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun LittleNavmapWebView(
    profile: ServerProfile,
    destination: PageDestination,
    isVisible: Boolean,
    onWebViewReady: (WebView) -> Unit,
    onLoadingChanged: (Boolean) -> Unit,
    onMainFrameLoaded: () -> Unit,
    onMainFrameError: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val surfaceColor = androidx.compose.material3.MaterialTheme.colorScheme.surface.toArgb()
    val connectionFailed = stringResource(R.string.connection_failed)
    val sslLabel = stringResource(R.string.ssl)
    val expectedOrigin = remember(profile.baseUrl) { profile.baseUrl.toUri() }
    val currentDestination by rememberUpdatedState(destination)
    val currentIsVisible by rememberUpdatedState(isVisible)
    val currentWebViewReady by rememberUpdatedState(onWebViewReady)
    val currentLoadingChanged by rememberUpdatedState(onLoadingChanged)
    val currentMainFrameLoaded by rememberUpdatedState(onMainFrameLoaded)
    val currentMainFrameError by rememberUpdatedState(onMainFrameError)

    AndroidView(
        factory = { factoryContext ->
            WebView.setWebContentsDebuggingEnabled(
                factoryContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0,
            )
            WebView(factoryContext).apply {
                setBackgroundColor(surfaceColor)
                visibility = if (currentIsVisible) View.VISIBLE else View.INVISIBLE
                isEnabled = currentIsVisible
                importantForAccessibility = if (currentIsVisible) {
                    View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
                } else {
                    View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                }
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    allowFileAccess = false
                    allowContentAccess = false
                    @Suppress("DEPRECATION")
                    allowFileAccessFromFileURLs = false
                    @Suppress("DEPRECATION")
                    allowUniversalAccessFromFileURLs = false
                    mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    javaScriptCanOpenWindowsAutomatically = false
                    setSupportMultipleWindows(false)
                    safeBrowsingEnabled = true
                    mediaPlaybackRequiresUserGesture = true
                    userAgentString = "$userAgentString LittleNavmap-Android"
                }
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
                webViewClient = object : WebViewClient() {
                    private var mainFrameFailed = false
                    private var mainFrameUrl: String? = null

                    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                        mainFrameFailed = false
                        mainFrameUrl = url
                        currentLoadingChanged(true)
                    }

                    override fun onPageCommitVisible(view: WebView, url: String?) {
                        if (!mainFrameFailed) currentMainFrameLoaded()
                        applyMobileAdaptation(view)
                    }

                    override fun onPageFinished(view: WebView, url: String?) {
                        currentLoadingChanged(false)
                        if (!mainFrameFailed) currentMainFrameLoaded()
                        applyMobileAdaptation(view)
                        currentDestination.clickScript?.let { script ->
                            view.evaluateJavascript(script, null)
                        }
                    }

                    override fun shouldOverrideUrlLoading(
                        view: WebView,
                        request: WebResourceRequest,
                    ): Boolean {
                        val requestedUri = request.url
                        if (requestedUri.isHttpOrHttps() &&
                            requestedUri.hasSameOriginAs(expectedOrigin)
                        ) {
                            return false
                        }
                        if (!request.isForMainFrame &&
                            requestedUri.toString().equals("about:blank", ignoreCase = true)
                        ) {
                            return false
                        }

                        val shouldOpenExternally = request.hasGesture() || request.isForMainFrame
                        if (shouldOpenExternally && requestedUri.isHttpOrHttps()) {
                            openExternalUri(context, requestedUri.toString())
                        }
                        return true
                    }

                    override fun onReceivedError(
                        view: WebView,
                        request: WebResourceRequest,
                        error: WebResourceError,
                    ) {
                        if (request.isForMainFrame) {
                            mainFrameFailed = true
                            currentLoadingChanged(false)
                            currentMainFrameError(error.description.toString())
                        }
                    }

                    override fun onReceivedHttpError(
                        view: WebView,
                        request: WebResourceRequest,
                        errorResponse: WebResourceResponse,
                    ) {
                        if (request.isForMainFrame && errorResponse.statusCode >= 400) {
                            mainFrameFailed = true
                            currentLoadingChanged(false)
                            currentMainFrameError(
                                "$connectionFailed: HTTP ${errorResponse.statusCode}",
                            )
                        }
                    }

                    override fun onReceivedSslError(
                        view: WebView,
                        handler: SslErrorHandler,
                        error: SslError,
                    ) {
                        handler.cancel()
                        val isMainFrameError = error.url == mainFrameUrl || error.url == view.url
                        if (!isMainFrameError) return

                        mainFrameFailed = true
                        currentLoadingChanged(false)
                        currentMainFrameError("$connectionFailed ($sslLabel)")
                    }
                }
                setDownloadListener { url, _, _, _, _ ->
                    if (url != null) openExternalUri(context, url)
                }
                currentWebViewReady(this)
                loadUrl(profile.baseUrl)
            }
        },
        update = { view ->
            view.setBackgroundColor(surfaceColor)
            view.visibility = if (isVisible) View.VISIBLE else View.INVISIBLE
            view.isEnabled = isVisible
            view.importantForAccessibility = if (isVisible) {
                View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
            } else {
                View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            }
        },
        onRelease = { view ->
            view.stopLoading()
            view.webViewClient = WebViewClient()
            view.setDownloadListener(null)
            view.removeAllViews()
            view.destroy()
        },
        modifier = modifier,
    )
}

private fun applyMobileAdaptation(view: WebView) {
    view.evaluateJavascript(WebScripts.MOBILE_ADAPTATION) {
        view.requestLayout()
        view.postVisualStateCallback(
            System.nanoTime(),
            object : WebView.VisualStateCallback() {
                override fun onComplete(requestId: Long) {
                    view.postInvalidateOnAnimation()
                }
            },
        )
    }
}

internal fun openExternalUri(context: Context, url: String): Boolean {
    val uri = url.toUri()
    if (!uri.isHttpOrHttps()) return false
    val intent = Intent(Intent.ACTION_VIEW, uri).apply {
        addCategory(Intent.CATEGORY_BROWSABLE)
    }
    return try {
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}

private fun Uri.isHttpOrHttps(): Boolean =
    scheme.equals("http", ignoreCase = true) || scheme.equals("https", ignoreCase = true)

private fun Uri.hasSameOriginAs(other: Uri): Boolean =
    scheme.equals(other.scheme, ignoreCase = true) &&
        host?.lowercase(Locale.ROOT) == other.host?.lowercase(Locale.ROOT) &&
        effectivePort() == other.effectivePort()

private fun Uri.effectivePort(): Int = when {
    port != -1 -> port
    scheme.equals("http", ignoreCase = true) -> 80
    scheme.equals("https", ignoreCase = true) -> 443
    else -> -1
}

internal object WebScripts {
    private val ALLOWED_BUTTON_IDS = setOf(
        "buttonMap",
        "buttonFlightPlan",
        "buttonProgress",
    )

    val MOBILE_ADAPTATION: String = """
        (function () {
          'use strict';
          var styleId = 'little-navmap-android-adaptation';
          var css = [
            '#menubarContainer{display:none!important;}',
            '#appShell{display:block!important;height:100%!important;}',
            '#mainContainer[data-menubarsplacement]{grid-template-columns:minmax(0,1fr)!important;grid-template-rows:minmax(0,1fr)!important;}',
            '#mainContainer>iframe{grid-column:1!important;grid-row:1!important;}',
            'html,body{overscroll-behavior:contain;-webkit-text-size-adjust:100%;}',
            'button,input,select,[role="button"]{touch-action:manipulation;}',
            'body.lnm-android-data-page #header{border-radius:0!important;padding:8px!important;margin:0 0 8px!important;}',
            'body.lnm-android-data-page #header p{font-size:0!important;margin:0!important;display:flex!important;gap:8px!important;align-items:center!important;flex-wrap:wrap!important;}',
            'body.lnm-android-data-page #header a{display:none!important;}',
            'body.lnm-android-data-page #header button,body.lnm-android-data-page #header input,body.lnm-android-data-page #header select,body.lnm-android-data-page #header #icaoSelector{font-size:14px!important;min-height:40px!important;box-sizing:border-box!important;}',
            'a{touch-action:manipulation;}'
          ].join('');

          function isFullScreenFrame(frame) {
            var frameId = frame.id || '';
            var frameName = frame.name || '';
            return frameId === 'webFrontend' ||
              frameId === 'contentiframe' ||
              frameId === 'addoniframe' ||
              frameName === 'contentiframe' ||
              frameName === 'addoniframe';
          }

          function adapt(frameWindow, inheritedHeight) {
            var doc;
            try {
              doc = frameWindow.document;
            } catch (ignored) {
              return;
            }
            if (!doc || !doc.documentElement) return;
            var viewportHeight = Math.max(
              Number(frameWindow.innerHeight) || 0,
              Number(inheritedHeight) || 0
            );
            var pixelHeight = '';
            if (viewportHeight > 0) {
              pixelHeight = Math.round(viewportHeight) + 'px';
              doc.documentElement.style.minHeight = pixelHeight;
              if (doc.body) doc.body.style.minHeight = pixelHeight;
            }
            if (!doc.getElementById(styleId)) {
              var style = doc.createElement('style');
              style.id = styleId;
              style.textContent = css;
              (doc.head || doc.documentElement).appendChild(style);
            }
            if (doc.body && [
              'flightplanPage',
              'aircraftPage',
              'progressPage',
              'airportPage'
            ].indexOf(doc.body.id) !== -1) {
              doc.body.classList.add('lnm-android-data-page');
            }
            var frames = doc.getElementsByTagName('iframe');
            for (var index = 0; index < frames.length; index += 1) {
              var frame = frames[index];
              if (frame.dataset && frame.dataset.lnmAndroidBound !== 'true') {
                frame.dataset.lnmAndroidBound = 'true';
                frame.addEventListener('load', function () {
                  try {
                    adapt(window, 0);
                  } catch (ignored) { }
                });
              }
              var childHeight = 0;
              if (isFullScreenFrame(frame)) {
                childHeight = viewportHeight;
                if (pixelHeight) frame.style.minHeight = pixelHeight;
              }
              try {
                adapt(frame.contentWindow, childHeight);
              } catch (ignored) { }
            }
          }

          if (!window.__lnmAndroidViewportBound) {
            window.__lnmAndroidViewportBound = true;
            window.addEventListener('resize', function () { adapt(window, 0); });
            window.addEventListener('orientationchange', function () { adapt(window, 0); });
          }
          adapt(window, 0);
          [100, 300, 800, 1600].forEach(function (delay) {
            window.setTimeout(function () { adapt(window, 0); }, delay);
          });
        }());
    """.trimIndent()

    val CLICK_MAP: String = clickScript("buttonMap")
    val CLICK_FLIGHT_PLAN: String = clickScript("buttonFlightPlan")
    val CLICK_PROGRESS: String = clickScript("buttonProgress")

    private fun clickScript(buttonId: String): String {
        require(buttonId in ALLOWED_BUTTON_IDS)
        return """
            (function () {
              'use strict';
              var remainingAttempts = 20;
              var targetId = '$buttonId';

              function clickIn(frameWindow) {
                var doc;
                try {
                  doc = frameWindow.document;
                } catch (ignored) {
                  return false;
                }
                if (!doc) return false;
                var button = doc.getElementById(targetId);
                if (button) {
                  var anchor = button.querySelector('a');
                  (anchor || button).click();
                  return true;
                }
                var frames = doc.getElementsByTagName('iframe');
                for (var index = 0; index < frames.length; index += 1) {
                  try {
                    if (clickIn(frames[index].contentWindow)) return true;
                  } catch (ignored) { }
                }
                return false;
              }

              function attempt() {
                if (!clickIn(window) && remainingAttempts > 0) {
                  remainingAttempts -= 1;
                  window.setTimeout(attempt, 100);
                }
              }

              attempt();
            }());
        """.trimIndent()
    }

}
