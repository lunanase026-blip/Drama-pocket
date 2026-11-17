package com.dramapocket.app

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.JsPromptResult
import android.webkit.JsResult
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
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream

private const val CATALOG_URL = "https://reelfren.net/"
private const val PREFS_NAME = "drama_pocket_reelfren_v3"
private const val KEY_FAVORITES = "favorites"
private const val KEY_CATALOG_CACHE = "catalog_cache_v3"
private const val KEY_EP_PROGRESS_PREFIX = "ep_progress_"

private val BASE_PROVIDERS = listOf(
    "All",
    "Melolo",
    "DramaBox",
    "ReelShort",
    "ShortMax",
    "FlexTV",
    "NetShort",
    "Vigloo"
)

private val BLOCKED_AD_DOMAINS = setOf(
    "doubleclick.net",
    "googlesyndication.com",
    "googleadservices.com",
    "adservice.google.com",
    "popads.net",
    "popcash.net",
    "propellerads.com",
    "propu.sh",
    "monetag.com",
    "alwingulla.com",
    "highperformanceformat.com",
    "topcreativeformat.com",
    "profitabledisplaynetwork.com",
    "effectivegatecpm.com",
    "adsterra.com",
    "adsterratools.com",
    "hilltopads.net",
    "clickadu.com",
    "exoclick.com",
    "realsrv.com",
    "tsyndicate.com",
    "juicyads.com",
    "adsco.re",
    "onclicka.com",
    "onclckds.com",
    "acscdn.com",
    "adtng.com",
    "trafficjunky.com",
    "trafficstars.com",
    "mgid.com",
    "taboola.com",
    "outbrain.com",
    "criteo.com",
    "pubmatic.com",
    "rubiconproject.com",
    "openx.net",
    "adnxs.com",
    "adsrvr.org",
    "casalemedia.com",
    "smartadserver.com",
    "mc.yandex.ru",
    "whos.amung.us",
    "histats.com",
    "statcounter.com",
    "shopee.ph",
    "shope.ee",
    "shp.ee",
    "lazada.com.ph",
    "invol.co",
    "invle.co",
    "go.skimresources.com",
    "awin1.com",
    "admitad.com"
)

data class DramaSeries(
    val title: String,
    val url: String,
    val image: String,
    val provider: String = "ReelFren",
    val category: String = "Short Drama"
)

data class ResolvedEpisode(
    val number: Int,
    val title: String,
    val pageUrl: String,
    val videoUrl: String = ""
)

private enum class MainTab {
    DISCOVER,
    SAVED
}

private fun isReelFrenHost(url: String?): Boolean {
    if (url.isNullOrBlank()) return false
    val host = runCatching { Uri.parse(url).host?.lowercase() }.getOrNull() ?: return false
    return host == "reelfren.net" || host.endsWith(".reelfren.net")
}

private fun isAllowedMainFrame(url: String?): Boolean {
    if (url.isNullOrBlank()) return false
    val host = runCatching { Uri.parse(url).host?.lowercase() }.getOrNull() ?: return false
    return host == "reelfren.net" ||
        host.endsWith(".reelfren.net") ||
        host == "cloudflare.com" ||
        host.endsWith(".cloudflare.com")
}

private fun isBlockedAdUri(uri: Uri?): Boolean {
    if (uri == null) return false
    val host = uri.host?.lowercase() ?: return false
    if (host == "cloudflare.com" || host.endsWith(".cloudflare.com")) return false
    if (BLOCKED_AD_DOMAINS.any { domain -> host == domain || host.endsWith(".$domain") }) {
        return true
    }
    val full = uri.toString().lowercase()
    return full.contains("popunder") || full.contains("pagead/js/adsbygoogle.js")
}

private fun isVideoStreamUrl(url: String?): Boolean {
    if (url.isNullOrBlank()) return false
    val lower = url.lowercase()
    if (!lower.startsWith("http://") && !lower.startsWith("https://")) return false
    if (isBlockedAdUri(Uri.parse(url))) return false
    val path = runCatching { Uri.parse(url).path?.lowercase().orEmpty() }.getOrDefault("")
    return path.endsWith(".m3u8") ||
        path.endsWith(".mp4") ||
        path.endsWith(".mpd") ||
        lower.contains(".m3u8?") ||
        lower.contains(".mp4?") ||
        lower.contains("/hls/") ||
        lower.contains("mime=video")
}

private fun emptyBlockedResponse(): WebResourceResponse =
    WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))

private fun readCachedCatalog(context: Context): List<DramaSeries> {
    val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getString(KEY_CATALOG_CACHE, null) ?: return emptyList()
    return runCatching {
        val array = JSONArray(raw)
        buildList {
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val title = obj.optString("title").trim()
                val url = obj.optString("url").trim()
                if (title.isNotBlank() && isReelFrenHost(url)) {
                    add(
                        DramaSeries(
                            title = title,
                            url = url,
                            image = obj.optString("image").trim(),
                            provider = obj.optString("provider", "ReelFren").trim().ifBlank { "ReelFren" },
                            category = obj.optString("category", "Short Drama").trim().ifBlank { "Short Drama" }
                        )
                    )
                }
            }
        }.distinctBy { it.url }
    }.getOrDefault(emptyList())
}

private fun writeCachedCatalog(context: Context, dramas: List<DramaSeries>) {
    val array = JSONArray()
    dramas.forEach { drama ->
        array.put(JSONObject().apply {
            put("title", drama.title)
            put("url", drama.url)
            put("image", drama.image)
            put("provider", drama.provider)
            put("category", drama.category)
        })
    }
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putString(KEY_CATALOG_CACHE, array.toString())
        .apply()
}

private fun formatDuration(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val totalSec = ms / 1000L
    return "%d:%02d".format(totalSec / 60L, totalSec % 60L)
}

@SuppressLint("SetJavaScriptEnabled")
private fun configureWebView(webView: WebView, autoplayMedia: Boolean): String {
    val cookieManager = CookieManager.getInstance()
    cookieManager.setAcceptCookie(true)
    cookieManager.setAcceptThirdPartyCookies(webView, true)

    webView.settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = true
        loadsImagesAutomatically = true
        mediaPlaybackRequiresUserGesture = !autoplayMedia
        mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        setSupportMultipleWindows(true)
        javaScriptCanOpenWindowsAutomatically = false
        val defaultUa = userAgentString ?: ""
        if (defaultUa.isNotBlank()) {
            userAgentString = defaultUa
                .replace("; wv)", ")")
                .replace("Version/4.0 ", "")
        }
    }
    return webView.settings.userAgentString ?: ""
}

private val POPUP_SHIELD_JS = """
    (function() {
      if (!window.__dpPopupShieldInstalled) {
        window.__dpPopupShieldInstalled = true;
        window.open = function(u) {
          try {
            if (u && typeof u === 'string') {
              var abs = new URL(u, location.href);
              var h = abs.hostname.toLowerCase();
              if ((h === 'reelfren.net' || h.endsWith('.reelfren.net')) &&
                  !/^\/(go|out|redirect|pop|ads?)\b/i.test(abs.pathname)) {
                location.href = abs.href;
              }
            }
          } catch (_) {}
          return null;
        };
        if (window.Notification) {
          try {
            window.Notification.requestPermission = function() {
              return Promise.resolve('denied');
            };
          } catch (_) {}
        }
        document.addEventListener('click', function(e) {
          try {
            var a = e.target && e.target.closest ? e.target.closest('a[href]') : null;
            if (a) {
              var raw = a.getAttribute('href') || '';
              if (raw && !raw.startsWith('#') && !raw.startsWith('javascript:')) {
                var u = new URL(raw, location.href);
                var h = u.hostname.toLowerCase();
                var sameSite = h === 'reelfren.net' || h.endsWith('.reelfren.net');
                if (!sameSite) {
                  e.preventDefault();
                  e.stopPropagation();
                  e.stopImmediatePropagation();
                  a.style.pointerEvents = 'none';
                  return false;
                }
                if (a.getAttribute('target') === '_blank') {
                  a.removeAttribute('target');
                }
              }
            }
            var el = e.target;
            if (el && el.nodeType === 1 && el !== document.body && el !== document.documentElement) {
              var cs = window.getComputedStyle(el);
              var rect = el.getBoundingClientRect();
              var covers = rect.width >= window.innerWidth * 0.55 && rect.height >= window.innerHeight * 0.45;
              var noMedia = !el.querySelector('video, iframe[src*="reelfren"], input, button');
              var transparent = cs.opacity === '0' || cs.backgroundColor === 'rgba(0, 0, 0, 0)' || cs.backgroundColor === 'transparent';
              if ((cs.position === 'fixed' || cs.position === 'absolute') && covers && noMedia && transparent && (parseInt(cs.zIndex, 10) || 0) >= 50) {
                e.preventDefault();
                e.stopPropagation();
                e.stopImmediatePropagation();
                el.style.pointerEvents = 'none';
                el.style.display = 'none';
                return false;
              }
            }
          } catch (_) {}
        }, true);
      }
      var sweep = function() {
        try {
          if (!document.getElementById('dp-adblock-style') && document.head) {
            var st = document.createElement('style');
            st.id = 'dp-adblock-style';
            st.textContent = [
              'ins.adsbygoogle',
              'iframe[src*="doubleclick"]',
              'iframe[src*="googlesyndication"]',
              'iframe[src*="adsterra"]',
              'iframe[src*="highperformanceformat"]',
              'iframe[src*="topcreativeformat"]',
              'iframe[src*="effectivegatecpm"]',
              'iframe[src*="popads"]',
              'iframe[src*="monetag"]',
              'iframe[src*="hilltopads"]',
              'a[href*="shopee"]',
              'a[href*="lazada"]',
              '[class*="popunder"]',
              '[class*="ad-overlay"]',
              '[class*="ad_overlay"]',
              '[id*="ad-overlay"]',
              '[id*="popunder"]'
            ].join(', ') + ' { display: none !important; pointer-events: none !important; visibility: hidden !important; }';
            document.head.appendChild(st);
          }
          var anchors = document.querySelectorAll('a[href*="shopee"], a[href*="lazada"], a[target="_blank"]');
          for (var i = 0; i < anchors.length; i++) {
            var link = anchors[i];
            try {
              var u = new URL(link.getAttribute('href') || '', location.href);
              var h = u.hostname.toLowerCase();
              if (h && h !== 'reelfren.net' && !h.endsWith('.reelfren.net')) {
                link.style.pointerEvents = 'none';
                if (link.textContent.trim().length === 0 && !link.querySelector('img')) {
                  link.style.display = 'none';
                }
              }
            } catch (_) {}
          }
        } catch (_) {}
      };
      sweep();
      if (!window.__dpMutationObserver && document.documentElement) {
        window.__dpMutationObserver = new MutationObserver(sweep);
        window.__dpMutationObserver.observe(document.documentElement, { childList: true, subtree: true });
      }
    })();
""".trimIndent()

private fun buildAntiPopupChromeClient(
    onProgress: (Int) -> Unit = {},
    onShowCustom: (View?, WebChromeClient.CustomViewCallback?) -> Unit = { _, _ -> },
    onHideCustom: () -> Unit = {}
): WebChromeClient = object : WebChromeClient() {
    override fun onProgressChanged(view: WebView?, newProgress: Int) {
        onProgress(newProgress)
    }

    override fun onCreateWindow(
        view: WebView?,
        isDialog: Boolean,
        isUserGesture: Boolean,
        resultMsg: Message?
    ): Boolean {
        val hitUrl = view?.hitTestResult?.extra
        if (isUserGesture && hitUrl != null && isReelFrenHost(hitUrl) && !isBlockedAdUri(Uri.parse(hitUrl))) {
            view.loadUrl(hitUrl)
        }
        return false
    }

    override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
        result?.cancel()
        return true
    }

    override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
        result?.cancel()
        return true
    }

    override fun onJsPrompt(
        view: WebView?,
        url: String?,
        message: String?,
        defaultValue: String?,
        result: JsPromptResult?
    ): Boolean {
        result?.cancel()
        return true
    }

    override fun onJsBeforeUnload(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
        result?.confirm()
        return true
    }

    override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
        onShowCustom(view, callback)
    }

    override fun onHideCustomView() {
        onHideCustom()
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { DramaPocketApp() }
    }
}

@Composable
private fun DramaPocketApp() {
    val context = LocalContext.current
    val prefs = remember(context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    var dramas by remember { mutableStateOf(readCachedCatalog(context)) }
    var loading by remember { mutableStateOf(dramas.isEmpty()) }
    var needsVerification by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedDrama by remember { mutableStateOf<DramaSeries?>(null) }
    var selectedProvider by remember { mutableStateOf("All") }
    var search by remember { mutableStateOf("") }
    var activeTab by remember { mutableStateOf(MainTab.DISCOVER) }
    var favorites by remember {
        mutableStateOf(prefs.getStringSet(KEY_FAVORITES, emptySet())?.toSet() ?: emptySet())
    }
    var showCatalogLoader by remember { mutableStateOf(true) }
    var reloadKey by remember { mutableIntStateOf(0) }

    val providerList = remember(dramas) {
        val dynamic = dramas.map { it.provider }
            .filter { it.isNotBlank() && !it.equals("ReelFren", ignoreCase = true) }
            .distinct()
        (BASE_PROVIDERS + dynamic).distinct()
    }

    fun toggleFavorite(url: String) {
        val next = if (favorites.contains(url)) favorites - url else favorites + url
        favorites = next
        prefs.edit().putStringSet(KEY_FAVORITES, next).apply()
    }

    fun refreshCatalog() {
        loading = dramas.isEmpty()
        needsVerification = false
        error = null
        showCatalogLoader = true
        reloadKey++
    }

    val darkScheme = darkColorScheme(
        primary = Color(0xFFFF3D71),
        secondary = Color(0xFF8B5CF6),
        background = Color(0xFF0B0B10),
        surface = Color(0xFF14141D),
        onBackground = Color(0xFFF4F4F5),
        onSurface = Color(0xFFF4F4F5)
    )

    MaterialTheme(colorScheme = darkScheme) {
        val activeDrama = selectedDrama
        if (activeDrama != null) {
            DramaPlayerHost(
                drama = activeDrama,
                prefs = prefs,
                isFavorite = favorites.contains(activeDrama.url),
                onToggleFavorite = { toggleFavorite(activeDrama.url) },
                onClose = { selectedDrama = null }
            )
        } else {
            Scaffold(
                containerColor = Color(0xFF0B0B10),
                bottomBar = {
                    NavigationBar(
                        containerColor = Color(0xFF12121A),
                        contentColor = Color.White
                    ) {
                        NavigationBarItem(
                            selected = activeTab == MainTab.DISCOVER,
                            onClick = { activeTab = MainTab.DISCOVER },
                            icon = { Icon(Icons.Default.Explore, contentDescription = "Discover") },
                            label = { Text("Discover") }
                        )
                        NavigationBarItem(
                            selected = activeTab == MainTab.SAVED,
                            onClick = { activeTab = MainTab.SAVED },
                            icon = { Icon(Icons.Default.Bookmark, contentDescription = "Saved") },
                            label = { Text("Saved (${favorites.size})") }
                        )
                    }
                }
            ) { padding ->
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .background(Color(0xFF0B0B10))
                ) {
                    if (showCatalogLoader) {
                        Column(Modifier.fillMaxSize()) {
                            if (needsVerification) {
                                Surface(
                                    color = Color(0xFF1E1B2E),
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
                                            "ReelFren verification needed once to sync catalog.",
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            modifier = Modifier.weight(1f)
                                        )
                                        TextButton(onClick = { refreshCatalog() }) {
                                            Text("Reload", color = Color(0xFFFF3D71))
                                        }
                                    }
                                }
                            }
                            key(reloadKey) {
                                CatalogWebLoader(
                                    modifier = if (dramas.isEmpty() || needsVerification) Modifier.fillMaxSize() else Modifier.size(1.dp),
                                    onVerificationRequired = {
                                        loading = false
                                        error = null
                                        needsVerification = true
                                    },
                                    onCatalogLoaded = { list ->
                                        CookieManager.getInstance().flush()
                                        dramas = list
                                        writeCachedCatalog(context, list)
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
                                        showCatalogLoader = false
                                    }
                                )
                            }
                        }
                    }

                    when {
                        needsVerification && showCatalogLoader -> {
                            // Keep WebView visible for one-time ReelFren verification.
                        }

                        loading && dramas.isEmpty() -> {
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .background(Color(0xFF0B0B10)),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(color = Color(0xFFFF3D71))
                                    Spacer(Modifier.height(14.dp))
                                    Text("Loading ReelFren catalog…", color = Color(0xFFA1A1AA))
                                }
                            }
                        }

                        error != null && dramas.isEmpty() -> {
                            Column(
                                Modifier
                                    .fillMaxSize()
                                    .background(Color(0xFF0B0B10))
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(error!!, color = Color(0xFFD4D4D8), fontSize = 14.sp)
                                Spacer(Modifier.height(14.dp))
                                Button(
                                    onClick = { refreshCatalog() },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF3D71))
                                ) {
                                    Text("Try again")
                                }
                            }
                        }

                        else -> {
                            val filtered = remember(dramas, selectedProvider, search, activeTab, favorites) {
                                val q = search.trim()
                                dramas.filter { item ->
                                    val matchesTab = activeTab != MainTab.SAVED || favorites.contains(item.url)
                                    val matchesProvider = selectedProvider == "All" ||
                                        item.provider.equals(selectedProvider, ignoreCase = true) ||
                                        item.title.contains(selectedProvider, ignoreCase = true) ||
                                        item.url.contains(selectedProvider, ignoreCase = true)
                                    val matchesSearch = q.isBlank() ||
                                        item.title.contains(q, ignoreCase = true) ||
                                        item.provider.contains(q, ignoreCase = true)
                                    matchesTab && matchesProvider && matchesSearch
                                }
                            }

                            DiscoverGridScreen(
                                dramas = filtered,
                                isSavedTab = activeTab == MainTab.SAVED,
                                providers = providerList,
                                selectedProvider = selectedProvider,
                                onSelectProvider = { selectedProvider = it },
                                search = search,
                                onSearchChange = { search = it },
                                favorites = favorites,
                                onToggleFavorite = ::toggleFavorite,
                                onRefresh = { refreshCatalog() },
                                onSelectDrama = { selectedDrama = it }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiscoverGridScreen(
    dramas: List<DramaSeries>,
    isSavedTab: Boolean,
    providers: List<String>,
    selectedProvider: String,
    onSelectProvider: (String) -> Unit,
    search: String,
    onSearchChange: (String) -> Unit,
    favorites: Set<String>,
    onToggleFavorite: (String) -> Unit,
    onRefresh: () -> Unit,
    onSelectDrama: (DramaSeries) -> Unit
) {
    val featured = if (!isSavedTab && search.isBlank()) dramas.firstOrNull() else null

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0B10)),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(span = { GridItemSpan(2) }) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            if (isSavedTab) "Saved Dramas" else "DramaPocket",
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            "ReelFren catalog · Ad-free vertical player",
                            fontSize = 12.sp,
                            color = Color(0xFFA1A1AA)
                        )
                    }
                    IconButton(onClick = onRefresh) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Refresh catalog",
                            tint = Color(0xFFFF3D71)
                        )
                    }
                }

                OutlinedTextField(
                    value = search,
                    onValueChange = onSearchChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search Melolo, DramaBox, titles…", color = Color(0xFF71717A)) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFFA1A1AA)) },
                    trailingIcon = {
                        if (search.isNotEmpty()) {
                            IconButton(onClick = { onSearchChange("") }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color(0xFFA1A1AA))
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF14141D),
                        unfocusedContainerColor = Color(0xFF14141D),
                        focusedBorderColor = Color(0xFFFF3D71),
                        unfocusedBorderColor = Color(0xFF27272A),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(providers) { provider ->
                        val active = provider == selectedProvider
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (active) Color(0xFFFF3D71) else Color(0xFF181822),
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable { onSelectProvider(provider) }
                        ) {
                            Text(
                                text = provider,
                                color = if (active) Color.White else Color(0xFFD4D4D8),
                                fontSize = 13.sp,
                                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }
        }

        if (featured != null) {
            item(span = { GridItemSpan(2) }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(210.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF1E1B2E))
                        .clickable { onSelectDrama(featured) }
                ) {
                    AsyncImage(
                        model = featured.image.ifBlank { null },
                        contentDescription = featured.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Black.copy(alpha = 0.25f), Color.Black.copy(alpha = 0.88f))
                                )
                            )
                    )
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${featured.provider} · Featured",
                                color = Color(0xFFE4E4E7),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                            IconButton(onClick = { onToggleFavorite(featured.url) }) {
                                Icon(
                                    if (favorites.contains(featured.url)) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                    contentDescription = "Save",
                                    tint = if (favorites.contains(featured.url)) Color(0xFFFF3D71) else Color.White
                                )
                            }
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = featured.title,
                                color = Color.White,
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Button(
                                onClick = { onSelectDrama(featured) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF3D71)),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Watch Vertical", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }

        if (dramas.isEmpty()) {
            item(span = { GridItemSpan(2) }) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (isSavedTab) "No saved dramas yet." else "No dramas found for $selectedProvider.",
                        color = Color(0xFFA1A1AA),
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            items(dramas, key = { it.url }) { drama ->
                PortraitDramaCard(
                    drama = drama,
                    isFavorite = favorites.contains(drama.url),
                    onToggleFavorite = { onToggleFavorite(drama.url) },
                    onClick = { onSelectDrama(drama) }
                )
            }
        }
    }
}

@Composable
private fun PortraitDramaCard(
    drama: DramaSeries,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF14141D))
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .background(Color(0xFF1E1B2E))
        ) {
            AsyncImage(
                model = drama.image.ifBlank { null },
                contentDescription = drama.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.55f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.82f)
                        )
                    )
            )
            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
            ) {
                Icon(
                    if (isFavorite) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                    contentDescription = "Save",
                    tint = if (isFavorite) Color(0xFFFF3D71) else Color.White
                )
            }
            Text(
                text = drama.provider,
                color = Color(0xFFE4E4E7),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(10.dp)
            )
        }
        Column(Modifier.padding(10.dp)) {
            Text(
                text = drama.title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private class CatalogBridge(
    private val onVerification: () -> Unit,
    private val onCatalog: (List<DramaSeries>) -> Unit,
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
                    val provider = item.optString("provider", "ReelFren").trim().ifBlank { "ReelFren" }
                    if (title.length > 2 && isReelFrenHost(url)) {
                        add(DramaSeries(title = title, url = url, image = image, provider = provider))
                    }
                }
            }.distinctBy { it.url }
        }.getOrElse { emptyList() }

        mainHandler.post {
            if (parsed.isNotEmpty()) {
                onCatalog(parsed)
            } else {
                onError("ReelFren loaded, but no drama cards were detected.")
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
    onCatalogLoaded: (List<DramaSeries>) -> Unit,
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
                webChromeClient = buildAntiPopupChromeClient()
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
                    override fun shouldInterceptRequest(
                        view: WebView,
                        request: WebResourceRequest
                    ): WebResourceResponse? {
                        if (isBlockedAdUri(request.url)) return emptyBlockedResponse()
                        return super.shouldInterceptRequest(view, request)
                    }

                    override fun shouldOverrideUrlLoading(
                        view: WebView,
                        request: WebResourceRequest
                    ): Boolean {
                        val target = request.url
                        val scheme = target.scheme?.lowercase()
                        if (scheme != "http" && scheme != "https") return true
                        if (isBlockedAdUri(target)) return true
                        if (request.isForMainFrame && !isAllowedMainFrame(target.toString())) return true
                        return false
                    }

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
                              if (window.__dpTimer) clearInterval(window.__dpTimer);
                              let attempts = 0;
                              let verificationNotified = false;
                              const knownProviders = [
                                'Melolo', 'DramaBox', 'DramaWave', 'ReelShort', 'ShortMax',
                                'FlexTV', 'NetShort', 'Vigloo', 'WeTV', 'GoodShort',
                                'MoboReels', 'Kalos', 'Sereal+', 'iQIYI', 'PineDrama',
                                'ShortTV', 'StardustTV', 'MiniDrama', 'TopShort'
                              ];
                              const detectProvider = function(text, href) {
                                const hay = (text + ' ' + href).toLowerCase();
                                for (const p of knownProviders) {
                                  if (hay.indexOf(p.toLowerCase()) !== -1) return p;
                                }
                                return 'ReelFren';
                              };
                              const stateKey = '__dp_reelfren_catalog_crawl_v2';
                              let crawl;
                              try {
                                crawl = JSON.parse(sessionStorage.getItem(stateKey) || 'null');
                              } catch (_) { crawl = null; }
                              if (!crawl || !Array.isArray(crawl.items) || !Array.isArray(crawl.visited)) {
                                crawl = { items: [], seen: [], visited: [], pages: 0 };
                              }
                              const isChallenge = function() {
                                const title = (document.title || '').toLowerCase();
                                return title.indexOf('just a moment') !== -1 ||
                                  !!document.querySelector('#challenge-running, #challenge-form, .cf-turnstile, iframe[src*="challenges.cloudflare.com"]');
                              };
                              const collect = function() {
                                if (isChallenge()) {
                                  window.DramaPocketBridge.onVerificationRequired();
                                  return false;
                                }
                                try {
                                  const preferred = Array.from(document.querySelectorAll(
                                    'a[href*="/drama/"], a[href*="/series/"], a[href*="/watch/"]'
                                  ));
                                  const fallback = preferred.length > 0
                                    ? preferred
                                    : Array.from(document.querySelectorAll('a[href]')).filter(function(a) {
                                        return !!a.querySelector('img');
                                      });
                                  const blocked = ['/genre', '/tag', '/login', '/register', '/search',
                                    '/privacy', '/terms', '/about', '/contact', '/language', '/category'];
                                  for (const a of fallback) {
                                    const raw = a.getAttribute('href');
                                    if (!raw || raw.startsWith('#') || raw.startsWith('javascript:')) continue;
                                    const parsedUrl = new URL(raw, location.href);
                                    if (parsedUrl.protocol !== 'https:' && parsedUrl.protocol !== 'http:') continue;
                                    if (!(parsedUrl.hostname === 'reelfren.net' || parsedUrl.hostname.endsWith('.reelfren.net'))) continue;
                                    if (parsedUrl.pathname === '/' || blocked.some(function(x) { return parsedUrl.pathname.startsWith(x); })) continue;
                                    const img = a.querySelector('img');
                                    const titleEl = a.querySelector('h1, h2, h3, h4, [class*="title"], [class*="name"]');
                                    const card = a.closest('article, li, [class*="item"], [class*="card"]') || a;
                                    const cardText = card.innerText || a.innerText || '';
                                    const title = (a.getAttribute('title') ||
                                      (titleEl && titleEl.innerText) ||
                                      (img && (img.getAttribute('alt') || img.getAttribute('title'))) ||
                                      a.innerText || '').trim().replace(/\s+/g, ' ');
                                    const image = img ? (img.currentSrc || img.src ||
                                      img.getAttribute('data-src') || img.getAttribute('data-lazy-src') ||
                                      img.getAttribute('data-original') || '') : '';
                                    const href = parsedUrl.href.split('#')[0];
                                    if (title.length > 2 && !crawl.seen.includes(href)) {
                                      crawl.seen.push(href);
                                      crawl.items.push({
                                        title: title, url: href, image: image,
                                        provider: detectProvider(cardText, href)
                                      });
                                    }
                                  }
                                  return true;
                                } catch (e) {
                                  window.DramaPocketBridge.onFailure('Catalog scan failed: ' + e.message);
                                  return false;
                                }
                              };
                              const saveCrawl = function() {
                                try { sessionStorage.setItem(stateKey, JSON.stringify(crawl)); } catch (_) {}
                              };
                              const nextPage = function() {
                                const links = Array.from(document.querySelectorAll('a[href]'));
                                const candidates = links.filter(function(a) {
                                  const label = ((a.innerText || '') + ' ' +
                                    (a.getAttribute('aria-label') || '') + ' ' +
                                    (a.getAttribute('title') || '')).trim().toLowerCase();
                                  const rel = (a.getAttribute('rel') || '').toLowerCase();
                                  return rel.split(/\s+/).includes('next') ||
                                    /^(next|older|›|»|→|下一页)$/.test(label);
                                });
                                for (const a of candidates) {
                                  try {
                                    const u = new URL(a.href, location.href);
                                    if ((u.hostname === 'reelfren.net' || u.hostname.endsWith('.reelfren.net')) &&
                                        u.origin === location.origin && !crawl.visited.includes(u.href)) return u.href;
                                  } catch (_) {}
                                }
                                return '';
                              };
                              const current = location.href.split('#')[0];
                              if (!crawl.visited.includes(current)) {
                                crawl.visited.push(current);
                                crawl.pages++;
                              }
                              let stablePasses = 0;
                              let scrollPasses = 0;
                              const crawlStep = function() {
                                if (!collect()) return;
                                scrollPasses++;
                                const oldHeight = document.documentElement.scrollHeight;
                                window.scrollTo(0, document.documentElement.scrollHeight);
                                setTimeout(function() {
                                  if (!collect()) return;
                                  const newHeight = document.documentElement.scrollHeight;
                                  if (newHeight > oldHeight + 20) {
                                    stablePasses = 0;
                                  } else {
                                    stablePasses++;
                                  }
                                  const loadMore = Array.from(document.querySelectorAll('button, a, [role="button"]'))
                                    .find(function(el) {
                                      const t = (el.innerText || '').trim().toLowerCase();
                                      return /^(load more|show more|view more)$/.test(t) && el.offsetParent !== null;
                                    });
                                  if (loadMore && stablePasses >= 1 && scrollPasses < 12) {
                                    loadMore.click();
                                    stablePasses = 0;
                                  }
                                  if (stablePasses < 3 && scrollPasses < 12) {
                                    crawlStep();
                                    return;
                                  }
                                  const next = nextPage();
                                  if (next && crawl.pages < 80) {
                                    saveCrawl();
                                    location.href = next;
                                    return;
                                  }
                                  saveCrawl();
                                  try { sessionStorage.removeItem(stateKey); } catch (_) {}
                                  if (crawl.items.length > 0) {
                                    window.DramaPocketBridge.onCatalog(JSON.stringify(crawl.items));
                                  } else {
                                    window.DramaPocketBridge.onFailure('ReelFren loaded, but no drama cards were detected.');
                                  }
                                }, 700);
                              };
                              crawlStep();                            })();
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

private class StreamResolverBridge(
    private val onVerification: () -> Unit,
    private val onEpisodes: (List<ResolvedEpisode>) -> Unit,
    private val onStreamUrl: (String, String) -> Unit
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun onVerificationRequired() {
        mainHandler.post { onVerification() }
    }

    @JavascriptInterface
    fun onEpisodesFound(json: String) {
        val parsed = runCatching {
            val array = JSONArray(json)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val pageUrl = obj.optString("url").trim()
                    val title = obj.optString("title").trim().ifBlank { "Episode ${i + 1}" }
                    val num = obj.optInt("number", i + 1)
                    if (pageUrl.startsWith("http")) {
                        add(ResolvedEpisode(number = num, title = title, pageUrl = pageUrl))
                    }
                }
            }.distinctBy { it.pageUrl }
        }.getOrDefault(emptyList())

        if (parsed.isNotEmpty()) {
            mainHandler.post { onEpisodes(parsed) }
        }
    }

    @JavascriptInterface
    fun onStreamFound(streamUrl: String, pageUrl: String) {
        val clean = streamUrl.trim()
        if (clean.startsWith("http") && !isBlockedAdUri(Uri.parse(clean))) {
            mainHandler.post { onStreamUrl(clean, pageUrl.trim()) }
        }
    }
}

@Composable
private fun DramaPlayerHost(
    drama: DramaSeries,
    prefs: android.content.SharedPreferences,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onClose: () -> Unit
) {
    val startEpIndex = remember(drama.url) {
        prefs.getInt("$KEY_EP_PROGRESS_PREFIX${drama.url.hashCode()}", 0).coerceAtLeast(0)
    }

    var episodes by remember(drama.url) {
        mutableStateOf(listOf(ResolvedEpisode(1, "Episode 1", drama.url)))
    }
    var resolvedUrls by remember(drama.url) { mutableStateOf<Map<String, String>>(emptyMap()) }
    var targetLoadUrl by remember(drama.url) { mutableStateOf(drama.url) }
    var activeEpisodeIndex by remember(drama.url) { mutableIntStateOf(0) }
    var needsVerification by remember(drama.url) { mutableStateOf(false) }
    var useCleanWebFallback by remember(drama.url) { mutableStateOf(false) }
    var resolverTimeout by remember(drama.url) { mutableStateOf(false) }
    var userAgentString by remember { mutableStateOf("") }

    val currentEp = episodes.getOrNull(activeEpisodeIndex) ?: episodes.first()
    val currentStreamUrl = resolvedUrls[currentEp.pageUrl] ?: resolvedUrls[targetLoadUrl]

    LaunchedEffect(targetLoadUrl, resolvedUrls[targetLoadUrl]) {
        resolverTimeout = false
        if (resolvedUrls[targetLoadUrl] == null) {
            delay(11_000)
            if (resolvedUrls[targetLoadUrl] == null && !needsVerification) {
                resolverTimeout = true
            }
        }
    }

    LaunchedEffect(episodes.size, startEpIndex) {
        if (startEpIndex in episodes.indices && activeEpisodeIndex == 0 && startEpIndex > 0) {
            activeEpisodeIndex = startEpIndex
            targetLoadUrl = episodes[startEpIndex].pageUrl
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // Hidden ad-blocked ReelFren resolver that extracts episode lists & real video streams
        // (and becomes visible automatically ONLY if Cloudflare asks for verification or fallback is chosen).
        val showResolverVisible = needsVerification || useCleanWebFallback

        ReelFrenResolverWebView(
            targetUrl = targetLoadUrl,
            visible = showResolverVisible,
            onUserAgentReady = { ua -> userAgentString = ua },
            onVerificationRequired = { needsVerification = true },
            onEpisodesFound = { found ->
                episodes = found
                val current = found.getOrNull(activeEpisodeIndex)
                if (current != null && resolvedUrls[current.pageUrl] == null && current.pageUrl != targetLoadUrl) {
                    targetLoadUrl = current.pageUrl
                }
            },
            onStreamUrlCaptured = { streamUrl, sourcePageUrl ->
                needsVerification = false
                resolverTimeout = false
                val alreadyAssignedElsewhere = resolvedUrls.entries.any {
                    it.key != targetLoadUrl && it.value == streamUrl
                }
                if (!alreadyAssignedElsewhere || resolvedUrls.isEmpty()) {
                    val key = sourcePageUrl.ifBlank { targetLoadUrl }
                    resolvedUrls = resolvedUrls + (targetLoadUrl to streamUrl) +
                        if (resolvedUrls[key] == null) mapOf(key to streamUrl) else emptyMap()
                }
            },
            onClose = onClose
        )

        if (!showResolverVisible) {
            if (currentStreamUrl != null) {
                NativeVerticalDramaPlayer(
                    dramaTitle = drama.title,
                    provider = drama.provider,
                    episodes = episodes,
                    resolvedStreams = resolvedUrls,
                    activeIndex = activeEpisodeIndex,
                    userAgent = userAgentString,
                    isFavorite = isFavorite,
                    onToggleFavorite = onToggleFavorite,
                    onSelectEpisode = { idx ->
                        activeEpisodeIndex = idx
                        prefs.edit().putInt("$KEY_EP_PROGRESS_PREFIX${drama.url.hashCode()}", idx).apply()
                        val epUrl = episodes.getOrNull(idx)?.pageUrl ?: drama.url
                        if (resolvedUrls[epUrl] == null) {
                            targetLoadUrl = epUrl
                        }
                    },
                    onOpenAdFreeWebFallback = { useCleanWebFallback = true },
                    onClose = onClose
                )
            } else {
                // Resolving real ReelFren stream overlay
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF0B0B10))
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .align(Alignment.Start),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onClose) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                        Text(
                            drama.title,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    if (!resolverTimeout) {
                        CircularProgressIndicator(color = Color(0xFFFF3D71))
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Loading ${drama.provider} stream from ReelFren…",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Blocking all ads and pop-ups",
                            color = Color(0xFFA1A1AA),
                            fontSize = 12.sp
                        )
                    } else {
                        Text(
                            "This episode uses an embedded player format.",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = { useCleanWebFallback = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF3D71))
                        ) {
                            Text("Play in Ad-Blocked Player")
                        }
                    }
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ReelFrenResolverWebView(
    targetUrl: String,
    visible: Boolean,
    onUserAgentReady: (String) -> Unit,
    onVerificationRequired: () -> Unit,
    onEpisodesFound: (List<ResolvedEpisode>) -> Unit,
    onStreamUrlCaptured: (String, String) -> Unit,
    onClose: () -> Unit
) {
    val latestOnVerify by rememberUpdatedState(onVerificationRequired)
    val latestOnEpisodes by rememberUpdatedState(onEpisodesFound)
    val latestOnStream by rememberUpdatedState(onStreamUrlCaptured)
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var customView by remember { mutableStateOf<View?>(null) }
    var customCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }

    BackHandler(enabled = visible) {
        if (customView != null) {
            customCallback?.onCustomViewHidden()
            customView = null
            customCallback = null
        } else if (webViewRef?.canGoBack() == true) {
            webViewRef?.goBack()
        } else {
            onClose()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            customCallback?.onCustomViewHidden()
            webViewRef?.apply {
                stopLoading()
                onPause()
                destroy()
            }
            webViewRef = null
        }
    }

    Column(
        modifier = if (visible) Modifier.fillMaxSize().background(Color(0xFF0B0B10)) else Modifier.size(1.dp)
    ) {
        if (visible && customView == null) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF14141D))
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Text(
                    "ReelFren Ad-Blocked Player",
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { webViewRef?.reload() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Reload", tint = Color.White)
                }
            }
        }

        Box(Modifier.fillMaxSize()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    WebView(context).apply {
                        val ua = configureWebView(this, autoplayMedia = true)
                        onUserAgentReady(ua)
                        webChromeClient = buildAntiPopupChromeClient(
                            onShowCustom = { view, cb ->
                                if (view != null) {
                                    customCallback?.onCustomViewHidden()
                                    customView = view
                                    customCallback = cb
                                }
                            },
                            onHideCustom = {
                                customView = null
                                customCallback = null
                            }
                        )
                        addJavascriptInterface(
                            StreamResolverBridge(
                                onVerification = { latestOnVerify() },
                                onEpisodes = { eps -> latestOnEpisodes(eps) },
                                onStreamUrl = { stream, page -> latestOnStream(stream, page) }
                            ),
                            "DramaStreamBridge"
                        )
                        webViewClient = object : WebViewClient() {
                            override fun shouldInterceptRequest(
                                view: WebView,
                                request: WebResourceRequest
                            ): WebResourceResponse? {
                                val reqUrl = request.url.toString()
                                if (isBlockedAdUri(request.url)) {
                                    return emptyBlockedResponse()
                                }
                                if (isVideoStreamUrl(reqUrl)) {
                                    val currentPage = view.url ?: targetUrl
                                    Handler(Looper.getMainLooper()).post {
                                        latestOnStream(reqUrl, currentPage)
                                    }
                                }
                                return super.shouldInterceptRequest(view, request)
                            }

                            override fun shouldOverrideUrlLoading(
                                view: WebView,
                                request: WebResourceRequest
                            ): Boolean {
                                val target = request.url
                                val scheme = target.scheme?.lowercase()
                                if (scheme != "http" && scheme != "https") return true
                                if (isBlockedAdUri(target)) return true
                                if (request.isForMainFrame && !isAllowedMainFrame(target.toString())) return true
                                return false
                            }

                            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                                view.evaluateJavascript(POPUP_SHIELD_JS, null)
                            }

                            override fun onPageFinished(view: WebView, url: String) {
                                super.onPageFinished(view, url)
                                view.evaluateJavascript(POPUP_SHIELD_JS, null)
                                if (!isReelFrenHost(url)) return
                                val extractJs = """
                                    (function() {
                                      const title = (document.title || '').toLowerCase();
                                      if (title.indexOf('just a moment') !== -1 ||
                                          document.querySelector('#challenge-running, #challenge-form, .cf-turnstile, iframe[src*="challenges.cloudflare.com"]')) {
                                        window.DramaStreamBridge.onVerificationRequired();
                                        return;
                                      }
                                      if (!window.__dpMediaHooked) {
                                        window.__dpMediaHooked = true;
                                        const reportIfMedia = function(u) {
                                          if (typeof u === 'string' && (u.indexOf('.m3u8') !== -1 || u.indexOf('.mp4') !== -1 || u.indexOf('.mpd') !== -1)) {
                                            try {
                                              window.DramaStreamBridge.onStreamFound(new URL(u, location.href).href, location.href);
                                            } catch (_) {}
                                          }
                                        };
                                        const origFetch = window.fetch;
                                        if (origFetch) {
                                          window.fetch = function() {
                                            try { reportIfMedia(typeof arguments[0] === 'string' ? arguments[0] : (arguments[0] && arguments[0].url)); } catch (_) {}
                                            return origFetch.apply(this, arguments);
                                          };
                                        }
                                        const origOpen = XMLHttpRequest.prototype.open;
                                        XMLHttpRequest.prototype.open = function(m, u) {
                                          try { reportIfMedia(u); } catch (_) {}
                                          return origOpen.apply(this, arguments);
                                        };
                                      }
                                      const scan = function() {
                                        try {
                                          const videos = Array.from(document.querySelectorAll('video, video source'));
                                          for (const el of videos) {
                                            const src = el.currentSrc || el.src || el.getAttribute('src') || '';
                                            if (src && !src.startsWith('blob:') && src.startsWith('http')) {
                                              window.DramaStreamBridge.onStreamFound(src, location.href);
                                            }
                                            if (el.tagName === 'VIDEO' && !window.__dpKeepWebAudio) {
                                              el.muted = true;
                                              el.pause();
                                            }
                                          }
                                          const baseNoHash = location.href.split('#')[0];
                                          const epAnchors = Array.from(document.querySelectorAll('a[href*="ep"], a[href*="episode"], [class*="episode"] a'));
                                          const seen = new Set();
                                          const eps = [];
                                          epAnchors.forEach(function(a, idx) {
                                            const raw = a.getAttribute('href');
                                            if (!raw || raw.startsWith('#')) return;
                                            const href = new URL(raw, location.href).href;
                                            if (!href.includes('reelfren.net') || seen.has(href)) return;
                                            seen.add(href);
                                            const txt = (a.innerText || a.getAttribute('title') || '').trim().replace(/\s+/g, ' ');
                                            eps.push({ number: idx + 1, title: txt || ('Episode ' + (idx + 1)), url: href });
                                          });
                                          if (eps.length === 0) {
                                            const btns = Array.from(document.querySelectorAll('[class*="episode"] button, button[data-episode], button[data-ep]'));
                                            if (btns.length > 1) {
                                              window.__dpEpButtons = btns;
                                              window.__dpClickEpisode = function(i) {
                                                try {
                                                  document.querySelectorAll('video').forEach(function(v) { v.pause(); v.removeAttribute('src'); });
                                                  if (window.__dpEpButtons && window.__dpEpButtons[i]) {
                                                    window.__dpEpButtons[i].click();
                                                  }
                                                } catch (_) {}
                                              };
                                              btns.forEach(function(b, idx) {
                                                const txt = (b.innerText || '').trim().replace(/\s+/g, ' ');
                                                eps.push({ number: idx + 1, title: txt || ('Episode ' + (idx + 1)), url: baseNoHash + '#dp-ep-' + (idx + 1) });
                                              });
                                            }
                                          }
                                          if (eps.length > 0) {
                                            window.DramaStreamBridge.onEpisodesFound(JSON.stringify(eps));
                                          }
                                        } catch (_) {}
                                      };
                                      scan();
                                      if (window.__dpScanTimer) clearInterval(window.__dpScanTimer);
                                      let ticks = 0;
                                      window.__dpScanTimer = setInterval(function() {
                                        ticks++;
                                        scan();
                                        if (ticks >= 20) clearInterval(window.__dpScanTimer);
                                      }, 600);
                                    })();
                                """.trimIndent()
                                view.evaluateJavascript(extractJs, null)
                                if (visible) {
                                    view.evaluateJavascript("window.__dpKeepWebAudio = true;", null)
                                }
                            }
                        }
                        webViewRef = this
                        loadUrl(targetUrl)
                    }
                },
                update = { view ->
                    view.evaluateJavascript("window.__dpKeepWebAudio = ${if (visible) "true" else "false"};", null)
                    val epMarker = "#dp-ep-"
                    val markerIdx = targetUrl.indexOf(epMarker)
                    if (markerIdx != -1) {
                        val epNum = targetUrl.substring(markerIdx + epMarker.length).toIntOrNull()
                        if (epNum != null && epNum >= 1) {
                            view.evaluateJavascript("window.__dpClickEpisode && window.__dpClickEpisode(${epNum - 1});", null)
                        }
                    } else if (view.url != targetUrl) {
                        view.evaluateJavascript("document.querySelectorAll('video').forEach(function(v){v.pause();v.removeAttribute('src');});", null)
                        view.loadUrl(targetUrl)
                    }
                }
            )

            val fsView = customView
            if (fsView != null) {
                key(fsView) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            FrameLayout(ctx).apply {
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
}

@OptIn(UnstableApi::class)
@Composable
private fun NativeVerticalDramaPlayer(
    dramaTitle: String,
    provider: String,
    episodes: List<ResolvedEpisode>,
    resolvedStreams: Map<String, String>,
    activeIndex: Int,
    userAgent: String,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onSelectEpisode: (Int) -> Unit,
    onOpenAdFreeWebFallback: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(
        initialPage = activeIndex.coerceIn(0, (episodes.size - 1).coerceAtLeast(0)),
        pageCount = { episodes.size }
    )

    val exoPlayer = remember(context, userAgent) {
        val cookieHeader = CookieManager.getInstance().getCookie(CATALOG_URL).orEmpty()
        val headers = buildMap {
            put("Referer", CATALOG_URL)
            put("Origin", "https://reelfren.net")
            if (cookieHeader.isNotBlank()) put("Cookie", cookieHeader)
        }
        val factory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(headers)
            .apply {
                if (userAgent.isNotBlank()) setUserAgent(userAgent)
            }
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(factory))
            .build()
            .apply {
                repeatMode = Player.REPEAT_MODE_OFF
                playWhenReady = true
            }
    }

    var isPlaying by remember { mutableStateOf(true) }
    var isMuted by remember { mutableStateOf(false) }
    var speed by remember { mutableFloatStateOf(1.0f) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var showEpisodeSheet by remember { mutableStateOf(false) }

    BackHandler {
        if (showEpisodeSheet) showEpisodeSheet = false else onClose()
    }

    LaunchedEffect(activeIndex) {
        if (pagerState.currentPage != activeIndex && activeIndex in episodes.indices) {
            pagerState.scrollToPage(activeIndex)
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage != activeIndex) {
            onSelectEpisode(pagerState.currentPage)
        }
    }

    val currentEp = episodes.getOrNull(pagerState.currentPage)
    val streamUrl = currentEp?.let { resolvedStreams[it.pageUrl] }

    LaunchedEffect(streamUrl, pagerState.currentPage) {
        if (!streamUrl.isNullOrBlank()) {
            exoPlayer.setMediaItem(MediaItem.fromUri(streamUrl))
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
            exoPlayer.volume = if (isMuted) 0f else 1f
            exoPlayer.playbackParameters = PlaybackParameters(speed)
        } else {
            exoPlayer.stop()
        }
    }

    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    val nextPage = pagerState.currentPage + 1
                    if (nextPage < episodes.size) {
                        scope.launch { pagerState.animateScrollToPage(nextPage) }
                    }
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                onOpenAdFreeWebFallback()
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    LaunchedEffect(exoPlayer) {
        while (true) {
            positionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            durationMs = exoPlayer.duration.coerceAtLeast(0L)
            delay(350)
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        VerticalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val ep = episodes[page]
            val isCurrent = page == pagerState.currentPage
            val pageStream = resolvedStreams[ep.pageUrl]

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        if (isCurrent && !pageStream.isNullOrBlank()) {
                            if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                        }
                    }
            ) {
                if (isCurrent && !pageStream.isNullOrBlank()) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                player = exoPlayer
                                useController = false
                                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            }
                        },
                        update = { view ->
                            if (view.player !== exoPlayer) view.player = exoPlayer
                        }
                    )
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Color(0xFFFF3D71))
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "Loading ${ep.title}…",
                                color = Color.White,
                                fontSize = 14.sp
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(230.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                            )
                        )
                )

                if (isCurrent && !pageStream.isNullOrBlank() && !isPlaying) {
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .align(Alignment.Center)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.55f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "Play",
                            tint = Color.White,
                            modifier = Modifier.size(38.dp)
                        )
                    }
                }
            }
        }

        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.72f), Color.Transparent))
                )
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = dramaTitle,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "$provider · Ep ${currentEp?.number ?: 1} / ${episodes.size}",
                    color = Color(0xFFD4D4D8),
                    fontSize = 12.sp
                )
            }
        }

        // Right Action Rail
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 12.dp, bottom = 104.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            PlayerRailButton(
                icon = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                label = if (isFavorite) "Saved" else "Save",
                tint = if (isFavorite) Color(0xFFFF3D71) else Color.White,
                onClick = onToggleFavorite
            )
            PlayerRailButton(
                icon = Icons.AutoMirrored.Filled.FormatListBulleted,
                label = "Episodes",
                onClick = { showEpisodeSheet = true }
            )
            PlayerRailButton(
                icon = Icons.Default.Speed,
                label = "${speed}x",
                onClick = {
                    val next = when (speed) {
                        1.0f -> 1.25f
                        1.25f -> 1.5f
                        1.5f -> 2.0f
                        else -> 1.0f
                    }
                    speed = next
                    exoPlayer.playbackParameters = PlaybackParameters(next)
                }
            )
            PlayerRailButton(
                icon = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                label = if (isMuted) "Muted" else "Sound",
                onClick = {
                    isMuted = !isMuted
                    exoPlayer.volume = if (isMuted) 0f else 1f
                }
            )
        }

        // Bottom Controls
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 14.dp)
        ) {
            Text(
                text = "Ep ${currentEp?.number ?: 1} · ${currentEp?.title.orEmpty()}",
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(end = 68.dp)
            )
            Text(
                text = "Swipe up for next episode",
                color = Color(0xFFA1A1AA),
                fontSize = 12.sp
            )
            Spacer(Modifier.height(6.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = {
                        if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play or pause",
                        tint = Color.White
                    )
                }
                Slider(
                    value = if (durationMs > 0L) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f,
                    onValueChange = { fraction ->
                        if (durationMs > 0L) exoPlayer.seekTo((fraction * durationMs).toLong())
                    },
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFFFF3D71),
                        activeTrackColor = Color(0xFFFF3D71),
                        inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                    )
                )
                Text(
                    text = "${formatDuration(positionMs)} / ${formatDuration(durationMs)}",
                    color = Color(0xFFE4E4E7),
                    fontSize = 11.sp
                )
            }
        }

        // Episodes Drawer
        AnimatedVisibility(
            visible = showEpisodeSheet,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .clickable { showEpisodeSheet = false },
                contentAlignment = Alignment.BottomCenter
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                        .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                        .background(Color(0xFF15151F))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {}
                        .padding(18.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Episodes (${episodes.size})",
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = { showEpisodeSheet = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(episodes.size) { index ->
                            val ep = episodes[index]
                            val current = index == pagerState.currentPage
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (current) Color(0xFFFF3D71).copy(alpha = 0.18f) else Color(0xFF1E1E2A))
                                    .border(
                                        width = 1.dp,
                                        color = if (current) Color(0xFFFF3D71) else Color.Transparent,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .clickable {
                                        showEpisodeSheet = false
                                        scope.launch { pagerState.animateScrollToPage(index) }
                                    }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Ep ${ep.number} · ${ep.title}",
                                    color = Color.White,
                                    fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal,
                                    fontSize = 14.sp
                                )
                                if (current) {
                                    Text("Playing", color = Color(0xFFFF3D71), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
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
private fun PlayerRailButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color = Color.White,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(6.dp)
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(28.dp))
        Spacer(Modifier.height(2.dp))
        Text(label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}