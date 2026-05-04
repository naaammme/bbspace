package com.naaammme.bbspace.feature.webview

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.naaammme.bbspace.core.common.UserAgentBuilder
import com.naaammme.bbspace.core.model.WebLinkParser
import com.naaammme.bbspace.core.model.WebLinkTarget
import com.naaammme.bbspace.core.model.SpaceRoute
import com.naaammme.bbspace.core.model.LiveRoute
import com.naaammme.bbspace.core.model.SpaceRouteTool
import com.naaammme.bbspace.core.model.LiveRouteTool

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebViewScreen(
    url: String,
    onBack: () -> Unit,
    onOpenVideo: (WebLinkTarget.ToVideo) -> Unit,
    onOpenSpace: (SpaceRoute) -> Unit,
    onOpenLive: (LiveRoute) -> Unit,
    onOpenExternal: (String) -> Unit
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf("") }
    var progress by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(true) }
    var canGoBack by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    val currentUrl by rememberUpdatedState(url)
    val currentOnBack by rememberUpdatedState(onBack)
    val currentOnOpenVideo by rememberUpdatedState(onOpenVideo)
    val currentOnOpenSpace by rememberUpdatedState(onOpenSpace)
    val currentOnOpenLive by rememberUpdatedState(onOpenLive)
    val currentOnOpenExternal by rememberUpdatedState(onOpenExternal)

    val webView = remember(context) {
        WebView(context).apply {
            with(settings) {
                javaScriptEnabled = true
                domStorageEnabled = true
                useWideViewPort = true
                loadWithOverviewMode = true
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
                mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                userAgentString = UserAgentBuilder.buildRestfulUserAgent(
                    model = Build.MODEL,
                    osVer = Build.VERSION.RELEASE
                )
            }
            val cookieM = CookieManager.getInstance()
            cookieM.setAcceptCookie(true)
            cookieM.setAcceptThirdPartyCookies(this, true)
            setDownloadListener { downloadUrl, _, _, _, _ ->
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl)))
                }
            }
        }
    }

    DisposableEffect(webView) {
        fun routeUrl(requestUrl: String): Boolean {
            val scheme = Uri.parse(requestUrl).scheme.orEmpty().lowercase()
            if (scheme.isNotEmpty() && scheme != "http" && scheme != "https") {
                currentOnOpenExternal(requestUrl)
                return true
            }

            val target = WebLinkParser.parse(requestUrl)
            return when (target) {
                is WebLinkTarget.Stay -> false
                is WebLinkTarget.ToVideo -> {
                    currentOnOpenVideo(target)
                    true
                }

                is WebLinkTarget.ToSpace -> {
                    currentOnOpenSpace(
                        SpaceRoute(mid = target.mid)
                    )
                    true
                }

                is WebLinkTarget.ToLive -> {
                    currentOnOpenLive(
                        LiveRoute(
                            roomId = target.roomId,
                            jumpFrom = LiveRouteTool.JUMP_FROM_UNKNOWN
                        )
                    )
                    true
                }

                is WebLinkTarget.External -> {
                    currentOnOpenExternal(requestUrl)
                    true
                }
            }
        }

        val client = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, pageUrl: String?, favicon: Bitmap?) {
                isLoading = true
                progress = 0
            }

            override fun onPageFinished(view: WebView?, pageUrl: String?) {
                isLoading = false
                canGoBack = webView.canGoBack()
            }

            override fun doUpdateVisitedHistory(view: WebView?, pageUrl: String?, isReload: Boolean) {
                canGoBack = webView.canGoBack()
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val requestUrl = request?.url?.toString() ?: return false
                return routeUrl(requestUrl)
            }

            @Deprecated("Deprecated in Java")
            override fun shouldOverrideUrlLoading(view: WebView?, requestUrl: String?): Boolean {
                val url = requestUrl ?: return false
                return routeUrl(url)
            }
        }

        val chrome = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progress = newProgress
            }

            override fun onReceivedTitle(view: WebView?, receivedTitle: String?) {
                title = receivedTitle.orEmpty()
            }
        }

        webView.webViewClient = client
        webView.webChromeClient = chrome
        webView.loadUrl(url)

        onDispose {
            webView.stopLoading()
            webView.webChromeClient = null
            webView.destroy()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = title.ifBlank { url },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                navigationIcon = {
                    if (canGoBack) {
                        IconButton(onClick = { webView.goBack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    } else {
                        IconButton(onClick = currentOnBack) {
                            Icon(Icons.Default.Close, contentDescription = "关闭")
                        }
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "更多")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("在浏览器中打开") },
                                onClick = {
                                    showMenu = false
                                    currentOnOpenExternal(currentUrl)
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            AndroidView(
                factory = { webView },
                modifier = Modifier.fillMaxSize()
            )

            if (isLoading && progress < 100) {
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter),
                )
            }
        }
    }
}
