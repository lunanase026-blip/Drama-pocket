package com.dramapocket.app

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import org.json.JSONArray

private const val CATALOG_URL = "https://reelfren.net/"
private const val PREFS_NAME = "drama_pocket_prefs"
private const val KEY_FAVORITES = "favorites"

data class Drama(
    val title: String,
    val url: String,
    val image: String,
    val description: String = ""
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { DramaPocketApp() }
    }
}

private fun isReelFrenHost(url: String?): Boolean {
    if (url.isNullOrBlank()) return false
    val host = runCatching { Uri.parse(url).host?.lowercase() }.getOrNull() ?: return false
    return host == "reelfren.net" || host.endsWith(".reelfren.net")
}

private fun isAllowedPlayerMainFrame(url: String?): Boolean {
    if (url.isNullOrBlank()) return false
    val host = runCatching { Uri.parse(url).host?.lowercase() }.getOrNull() ?: return false
    return host == "reelfren.net" ||
        host.endsWith(".reelfren.net") ||
        host == "cloudflare.com" ||
        host.endsWith(".cloudflare.com")
}

@SuppressLint("SetJavaScriptEnabled")
private fun configureWebView(webView: WebView, autoplayMedia: Boolean) {
    val cookieManager = CookieManager.getInstance()
    cookieManager.setAcceptCookie(true)
    cookieManager.setAcceptThirdPartyCookies(webView, true)

    webView.settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = true
        loadsImagesAutomatically = true
        mediaPlaybackRequiresUserGesture = !autoplayMedia
        mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        setSupportMultipleWindows(false)
        javaScriptCanOpenWindowsAutomatically = false
        val defaultUa = userAgentString ?: ""
        if (defaultUa.isNotBlank()) {
            userAgentString = defaultUa
                .replace("; wv)", ")")
                .replace("Version/4.0 ", "")
        }
    }
}

@Composable
private fun DramaPocketApp() {
    val context = LocalContext.current
    val prefs = remember(context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    var dramas by remember { mutableStateOf<List<Drama>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var needsVerification by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedDrama by remember { mutableStateOf<Drama?>(null) }
    var search by remember { mutableStateOf("") }
    var favoritesOnly by remember { mutableStateOf(false) }
    var favorites by remember {
        mutableStateOf(prefs.getStringSet(KEY_FAVORITES, emptySet())?.toSet() ?: emptySet())
    }
    var showCatalogLoader by remember { mutableStateOf(true) }
    var reloadKey by remember { mutableIntStateOf(0) }

    fun toggleFavorite(url: String) {
        val next = if (favorites.contains(url)) favorites - url else favorites + url
        favorites = next
        prefs.edit().putStringSet(KEY_FAVORITES, next).apply()
    }

    fun triggerRefresh() {
        loading = true
        needsVerification = false
        error = null
        showCatalogLoader = true
        reloadKey++
    }

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF6750A4),
            background = Color(0xFFFFFBFE),
            surface = Color(0xFFFFFBFE)
        )
    ) {
        val activeDrama = selectedDrama
        if (activeDrama != null) {
            DramaWebPlayer(
                url = activeDrama.url,
                title = activeDrama.title,
                onClose = { selectedDrama = null }
            )
        } else {
            val filteredDramas = remember(dramas, search, favoritesOnly, favorites) {
                dramas.filter { drama ->
                    val matchesSearch = search.isBlank() ||
                        drama.title.contains(search.trim(), ignoreCase = true)
                    val matchesFav = !favoritesOnly || favorites.contains(drama.url)
                    matchesSearch && matchesFav
                }
            }

            Scaffold(
                topBar = {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(Color.White)
                            .padding(horizontal = 18.dp, vertical = 14.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    "DramaPocket",
                                    fontSize = 26.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF33264D)
                                )
                                Text(
                                    "Short dramas, all in one pocket",
                                    color = Color.Gray,
                                    fontSize = 13.sp
                                )
                            }
                            IconButton(onClick = { triggerRefresh() }) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = "Refresh catalog",
                                    tint = Color(0xFF6750A4)
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = search,
                            onValueChange = { search = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Search dramas") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            trailingIcon = {
                                if (search.isNotEmpty()) {
                                    IconButton(onClick = { search = "" }) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear search")
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp)
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TabPill(
                                label = "All (${dramas.size})",
                                selected = !favoritesOnly,
                                onClick = { favoritesOnly = false }
                            )
                            TabPill(
                                label = "Favorites (${favorites.size})",
                                selected = favoritesOnly,
                                onClick = { favoritesOnly = true }
                            )
                        }
                    }
                }
            ) { padding ->
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .background(Color(0xFFFFFBFE))
                ) {
                    if (showCatalogLoader) {
                        Column(Modifier.fillMaxSize()) {
                            if (needsVerification) {
                                Surface(
                                    color = Color(0xFFF3EDF7),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "Complete verification below to load ReelFren.",
                                            color = Color(0xFF33264D),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.weight(1f)
                                        )
                                        TextButton(onClick = { triggerRefresh() }) {
                                            Text("Reload")
                                        }
                                    }
                                }
                            }
                            key(reloadKey) {
                                CatalogWebLoader(
                                    modifier = Modifier.fillMaxSize(),
                                    onVerificationRequired = {
                                        loading = false
                                        error = null
                                        needsVerification = true
                                    },
                                    onCatalogLoaded = { list ->
                                        CookieManager.getInstance().flush()
                                        dramas = list
                                        loading = false
                                        needsVerification = false
                                        error = null
                                        showCatalogLoader = false
                                    },
                                    onFailure = { message ->
                                        loading = false
                                        needsVerification = false
                                        if (dramas.isEmpty()) {
                                            error = message
                                        }
                                    }
                                )
                            }
                        }
                    }

                    when {
                        loading -> {
                            Surface(
                                modifier = Modifier.fillMaxSize(),
                                color = Color(0xFFFFFBFE)
                            ) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        CircularProgressIndicator()
                                        Spacer(Modifier.height(14.dp))
                                        Text("Loading catalog…", color = Color.Gray)
                                    }
                                }
                            }
                        }

                        needsVerification && showCatalogLoader -> {
                            // Keep the WebView visible so the user can complete Cloudflare Turnstile.
                        }

                        error != null && dramas.isEmpty() -> {
                            Surface(
                                modifier = Modifier.fillMaxSize(),
                                color = Color(0xFFFFFBFE)
                            ) {
                                Column(
                                    Modifier
                                        .fillMaxSize()
                                        .padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        error!!,
                                        color = Color.DarkGray,
                                        fontSize = 14.sp
                                    )
                                    Spacer(Modifier.height(14.dp))
                                    Button(onClick = { triggerRefresh() }) {
                                        Text("Try again")
                                    }
                                }
                            }
                        }

                        filteredDramas.isEmpty() -> {
                            Surface(
                                modifier = Modifier.fillMaxSize(),
                                color = Color(0xFFFFFBFE)
                            ) {
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        if (favoritesOnly) "No favorite dramas yet. Tap the heart icon on any drama."
                                        else "No dramas matched your search.",
                                        color = Color.Gray
                                    )
                                }
                            }
                        }

                        else -> {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color(0xFFFFFBFE)),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(filteredDramas, key = { it.url }) { drama ->
                                    DramaCard(
                                        drama = drama,
                                        favorite = favorites.contains(drama.url),
                                        onFavorite = { toggleFavorite(drama.url) },
                                        onClick = { selectedDrama = drama }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TabPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (selected) Color(0xFF6750A4) else Color(0xFFF1ECF8),
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            color = if (selected) Color.White else Color(0xFF33264D),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
        )
    }
}

@Composable
private fun DramaCard(
    drama: Drama,
    favorite: Boolean,
    onFavorite: () -> Unit,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White)
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = drama.image.ifBlank { null },
            contentDescription = drama.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .width(96.dp)
                .height(128.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFEDE7F6))
        )
        Column(
            Modifier
                .weight(1f)
                .padding(start = 12.dp)
        ) {
            Text(
                drama.title,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(8.dp))
            Text("Tap to view drama and episodes", color = Color.Gray, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            Text("Watch", color = Color(0xFF6750A4), fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
        IconButton(onClick = onFavorite) {
            Icon(
                if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = "Favorite",
                tint = if (favorite) Color.Red else Color.Gray
            )
        }
    }
}

private class CatalogBridge(
    private val onVerification: () -> Unit,
    private val onCatalog: (List<Drama>) -> Unit,
    private val onError: (String) -> Unit
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun onVerificationRequired() {
        mainHandler.post { onVerification() }
    }

    @JavascriptInterface
    fun onCatalog(json: String) {
        val parsed = runCatching {
            val array = JSONArray(json)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val title = item.optString("title").trim()
                    val url = item.optString("url").trim()
                    val image = item.optString("image").trim()
                    val description = item.optString("description").trim()
                    if (title.length > 2 && isReelFrenHost(url)) {
                        add(Drama(title, url, image, description))
                    }
                }
            }.distinctBy { it.url }
        }.getOrElse { emptyList() }

        mainHandler.post {
            if (parsed.isNotEmpty()) {
                onCatalog(parsed)
            } else {
                onError("The browser opened ReelFren, but no drama cards could be read. The website may have changed its layout.")
            }
        }
    }

    @JavascriptInterface
    fun onFailure(message: String) {
        mainHandler.post { onError(message) }
    }
}

@Composable
private fun CatalogWebLoader(
    modifier: Modifier = Modifier,
    onVerificationRequired: () -> Unit,
    onCatalogLoaded: (List<Drama>) -> Unit,
    onFailure: (String) -> Unit
) {
    val latestOnVerification by rememberUpdatedState(onVerificationRequired)
    val latestOnCatalog by rememberUpdatedState(onCatalogLoaded)
    val latestOnFailure by rememberUpdatedState(onFailure)
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            webViewRef?.apply {
                stopLoading()
                destroy()
            }
            webViewRef = null
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                configureWebView(this, autoplayMedia = false)
                webChromeClient = WebChromeClient()
                var sawHttp403 = false

                addJavascriptInterface(
                    CatalogBridge(
                        onVerification = { latestOnVerification() },
                        onCatalog = { list -> latestOnCatalog(list) },
                        onError = { msg -> latestOnFailure(msg) }
                    ),
                    "DramaPocketBridge"
                )

                webViewClient = object : WebViewClient() {
                    override fun onReceivedHttpError(
                        view: WebView,
                        request: WebResourceRequest,
                        errorResponse: WebResourceResponse
                    ) {
                        if (request.isForMainFrame && errorResponse.statusCode == 403) {
                            sawHttp403 = true
                        }
                    }

                    override fun onPageFinished(view: WebView, url: String) {
                        super.onPageFinished(view, url)
                        if (!isReelFrenHost(url)) return
                        val script = """
                            (function() {
                              if (window.__dpTimer) {
                                clearInterval(window.__dpTimer);
                              }
                              let attempts = 0;
                              let verificationNotified = false;
                              const isChallenge = function() {
                                const title = (document.title || '').toLowerCase();
                                return title.indexOf('just a moment') !== -1 ||
                                  !!document.querySelector('#challenge-running, #challenge-form, .cf-turnstile, iframe[src*="challenges.cloudflare.com"]');
                              };
                              const collect = function() {
                                try {
                                  if (isChallenge()) {
                                    if (!verificationNotified) {
                                      verificationNotified = true;
                                      window.DramaPocketBridge.onVerificationRequired();
                                    }
                                    return false;
                                  }
                                  const preferred = Array.from(document.querySelectorAll('a[href*="/drama/"], a[href*="/series/"], a[href*="/watch/"]'));
                                  const fallback = preferred.length > 0
                                    ? preferred
                                    : Array.from(document.querySelectorAll('a[href]')).filter(function(a) {
                                        return !!a.querySelector('img');
                                      });
                                  const blocked = ['/genre', '/tag', '/login', '/register', '/search', '/privacy', '/terms', '/about', '/contact', '/language'];
                                  const seen = new Set();
                                  const cards = [];
                                  for (const a of fallback) {
                                    const raw = a.getAttribute('href');
                                    if (!raw || raw.startsWith('#') || raw.startsWith('javascript:')) continue;
                                    const href = new URL(raw, location.href).href;
                                    const parsedUrl = new URL(href);
                                    if (!parsedUrl.hostname.endsWith('reelfren.net')) continue;
                                    if (parsedUrl.pathname === '/' || blocked.some(function(p) { return parsedUrl.pathname.startsWith(p); })) continue;
                                    const img = a.querySelector('img');
                                    const titleEl = a.querySelector('h1, h2, h3, h4, [class*="title"], [class*="name"]');
                                    const title = (
                                      a.getAttribute('title') ||
                                      (titleEl && titleEl.innerText) ||
                                      (img && (img.getAttribute('alt') || img.getAttribute('title'))) ||
                                      a.innerText ||
                                      ''
                                    ).trim().replace(/\s+/g, ' ');
                                    const image = img ? (
                                      img.currentSrc ||
                                      img.src ||
                                      img.getAttribute('data-src') ||
                                      img.getAttribute('data-lazy-src') ||
                                      img.getAttribute('data-original') ||
                                      ''
                                    ) : '';
                                    if (title.length > 2 && !seen.has(href)) {
                                      seen.add(href);
                                      cards.push({title: title, url: href, image: image, description: ''});
                                    }
                                  }
                                  if (cards.length > 0) {
                                    window.DramaPocketBridge.onCatalog(JSON.stringify(cards));
                                    return true;
                                  }
                                  return false;
                                } catch (e) {
                                  window.DramaPocketBridge.onFailure('Could not read the ReelFren catalog page: ' + e.message);
                                  return true;
                                }
                              };
                              if (collect()) return;
                              const maxAttempts = ${if (sawHttp403) 45 else 25};
                              window.__dpTimer = setInterval(function() {
                                attempts++;
                                if (collect()) {
                                  clearInterval(window.__dpTimer);
                                  return;
                                }
                                if (attempts >= maxAttempts && !verificationNotified) {
                                  clearInterval(window.__dpTimer);
                                  window.DramaPocketBridge.onFailure(
                                    'ReelFren did not return any drama listings. Tap Try again to reload.'
                                  );
                                }
                              }, 1000);
                            })();
                        """.trimIndent()
                        view.evaluateJavascript(script, null)
                    }
                }
                webViewRef = this
                loadUrl(CATALOG_URL)
            }
        }
    )
}

@Composable
private fun DramaWebPlayer(
    url: String,
    title: String,
    onClose: () -> Unit
) {
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }
    var customView by remember { mutableStateOf<View?>(null) }
    var customViewCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }

    fun exitFullscreen() {
        customViewCallback?.onCustomViewHidden()
        customViewCallback = null
        customView = null
    }

    BackHandler {
        when {
            customView != null -> exitFullscreen()
            webViewRef?.canGoBack() == true -> webViewRef?.goBack()
            else -> onClose()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exitFullscreen()
            webViewRef?.apply {
                stopLoading()
                onPause()
                destroy()
            }
            webViewRef = null
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Column(Modifier.fillMaxSize().background(Color.White)) {
            if (customView == null) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(Color.White)
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        if (canGoBack && webViewRef?.canGoBack() == true) {
                            webViewRef?.goBack()
                        } else {
                            onClose()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Text(
                        text = title.ifBlank { "DramaPocket Player" },
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { webViewRef?.reload() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reload")
                    }
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Close player")
                    }
                }
                if (progress in 1..99) {
                    LinearProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                factory = { context ->
                    WebView(context).apply {
                        configureWebView(this, autoplayMedia = true)
                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                progress = newProgress
                            }

                            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                                if (view == null) return
                                customViewCallback?.onCustomViewHidden()
                                customView = view
                                customViewCallback = callback
                            }

                            override fun onHideCustomView() {
                                customView = null
                                customViewCallback = null
                            }
                        }
                        webViewClient = object : WebViewClient() {
                            override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                                super.doUpdateVisitedHistory(view, url, isReload)
                                canGoBack = view.canGoBack()
                            }

                            override fun onPageFinished(view: WebView, url: String) {
                                super.onPageFinished(view, url)
                                canGoBack = view.canGoBack()
                            }

                            override fun shouldOverrideUrlLoading(
                                view: WebView,
                                request: WebResourceRequest
                            ): Boolean {
                                val target = request.url
                                val scheme = target.scheme?.lowercase()
                                // Block external deep links & ad redirects (e.g. shopeeph://, intent://)
                                // so playback is not interrupted or kicked out to another app.
                                if (scheme != "http" && scheme != "https") {
                                    return true
                                }
                                if (request.isForMainFrame && !isAllowedPlayerMainFrame(target.toString())) {
                                    return true
                                }
                                return false
                            }
                        }
                        webViewRef = this
                        loadUrl(url)
                    }
                },
                update = { view ->
                    if (view.url != url) {
                        view.loadUrl(url)
                    }
                }
            )
        }

        val fsView = customView
        if (fsView != null) {
            key(fsView) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        FrameLayout(context).apply {
                            (fsView.parent as? ViewGroup)?.removeView(fsView)
                            addView(
                                fsView,
                                FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                            )
                        }
                    }
                )
            }
        }
    }
}
