package com.dramapocket.app

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImage
import org.json.JSONArray

private const val CATALOG_URL = "https://reelfren.net/"

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

@Composable
private fun DramaPocketApp() {
    var dramas by remember { mutableStateOf<List<Drama>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedUrl by remember { mutableStateOf<String?>(null) }
    var search by remember { mutableStateOf("") }
    var favorites by remember { mutableStateOf(setOf<String>()) }
    var reloadKey by remember { mutableIntStateOf(0) }

    BackHandler(selectedUrl != null) { selectedUrl = null }

    MaterialTheme(colorScheme = lightColorScheme(
        primary = Color(0xFF6750A4),
        background = Color(0xFFFFFBFE),
        surface = Color(0xFFFFFBFE)
    )) {
        if (selectedUrl != null) {
            Column(Modifier.fillMaxSize().background(Color.White)) {
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { selectedUrl = null }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                    Text("DramaPocket Player", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                }
                DramaWebPlayer(selectedUrl!!, Modifier.weight(1f))
            }
        } else {
            Scaffold(
                topBar = {
                    Column(Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 18.dp, vertical = 16.dp)) {
                        Text("DramaPocket", fontSize = 27.sp, fontWeight = FontWeight.Bold, color = Color(0xFF33264D))
                        Text("Short dramas, all in one pocket", color = Color.Gray, fontSize = 13.sp)
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = search,
                            onValueChange = { search = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Search dramas") },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp)
                        )
                    }
                }
            ) { padding ->
                Box(Modifier.fillMaxSize().padding(padding).background(Color(0xFFFFFBFE))) {
                    Column(Modifier.fillMaxSize()) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Discover", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            TextButton(onClick = {
                                loading = true
                                error = null
                                reloadKey++
                            }) { Text("Refresh catalog") }
                        }
                        when {
                            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                                Text("Loading catalog…", modifier = Modifier.padding(top = 64.dp), color = Color.Gray)
                            }
                            error != null -> Column(
                                Modifier.fillMaxWidth().padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(error!!, color = Color.DarkGray)
                                Spacer(Modifier.height(12.dp))
                                Button(onClick = {
                                    loading = true
                                    error = null
                                    reloadKey++
                                }) { Text("Try again") }
                            }
                            else -> LazyColumn(
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(dramas.filter { it.title.contains(search, ignoreCase = true) }) { drama ->
                                    DramaCard(
                                        drama = drama,
                                        favorite = favorites.contains(drama.url),
                                        onFavorite = {
                                            favorites = if (favorites.contains(drama.url)) favorites - drama.url else favorites + drama.url
                                        },
                                        onClick = { selectedUrl = drama.url }
                                    )
                                }
                            }
                        }
                    }

                    // Load the public catalog through Android's browser engine, then pass
                    // only the visible drama-card metadata to the native Compose interface.
                    key(reloadKey) {
                        CatalogWebLoader(
                            modifier = if (error != null) Modifier.fillMaxSize() else Modifier.size(1.dp),
                            onResult = { result ->
                                loading = false
                                if (result.first.isNotEmpty()) {
                                    dramas = result.first
                                    error = null
                                } else {
                                    error = result.second ?: "ReelFren did not provide readable drama listings. Please try again later."
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DramaCard(drama: Drama, favorite: Boolean, onFavorite: () -> Unit, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White).clickable(onClick = onClick).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = drama.image.ifBlank { null },
            contentDescription = drama.title,
            modifier = Modifier.width(104.dp).height(138.dp).clip(RoundedCornerShape(12.dp))
        )
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(drama.title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 3)
            Spacer(Modifier.height(8.dp))
            Text("Tap to view drama and episodes", color = Color.Gray, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            Text("Watch", color = Color(0xFF6750A4), fontWeight = FontWeight.Bold)
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
    private val onResult: (List<Drama>, String?) -> Unit
) {
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
                    if (title.length > 2 && url.startsWith("https://reelfren.net/")) {
                        add(Drama(title, url, image, description))
                    }
                }
            }.distinctBy { it.url }
        }.getOrElse { emptyList() }

        Handler(Looper.getMainLooper()).post {
            if (parsed.isNotEmpty()) onResult(parsed, null)
            else onResult(emptyList(), "The browser opened ReelFren, but no drama cards could be read. The website may have changed its layout.")
        }
    }

    @JavascriptInterface
    fun onFailure(message: String) {
        Handler(Looper.getMainLooper()).post {
            onResult(emptyList(), message)
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun CatalogWebLoader(
    modifier: Modifier = Modifier,
    onResult: (Pair<List<Drama>, String?>) -> Unit
) {
    val latestCallback by rememberUpdatedState(onResult)
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.loadsImagesAutomatically = true
                settings.mediaPlaybackRequiresUserGesture = true
                webChromeClient = WebChromeClient()
                addJavascriptInterface(CatalogBridge { list, message ->
                    latestCallback(list to message)
                }, "DramaPocketBridge")
                webViewClient = object : WebViewClient() {
                    override fun onReceivedHttpError(
                        view: WebView,
                        request: WebResourceRequest,
                        errorResponse: android.webkit.WebResourceResponse
                    ) {
                        if (request.isForMainFrame && errorResponse.statusCode == 403) {
                            latestCallback(emptyList<Drama>() to "ReelFren returned HTTP 403 in the browser too. The source is refusing this request, so DramaPocket cannot load its catalog right now.")
                        }
                    }

                    override fun onPageFinished(view: WebView, url: String) {
                        super.onPageFinished(view, url)
                        if (!url.startsWith(CATALOG_URL)) return
                        val script = """
                            (function() {
                              // Wait for the site's normal client-side rendering to finish.
                              // If ReelFren presents a human verification page, this WebView
                              // becomes visible so the user can complete it manually.
                              let attempts = 0;
                              const collect = function() {
                                try {
                                  const links = Array.from(document.querySelectorAll('a[href*="/drama/"]'));
                                  const seen = new Set();
                                  const cards = [];
                                  for (const a of links) {
                                    const raw = a.getAttribute('href');
                                    if (!raw) continue;
                                    const href = new URL(raw, location.href).href;
                                    const img = a.querySelector('img');
                                    const title = (a.getAttribute('title') ||
                                      (img && (img.getAttribute('alt') || img.getAttribute('title'))) ||
                                      a.innerText || '').trim().replace(/\\s+/g, ' ');
                                    const image = img ? (img.currentSrc || img.src ||
                                      img.getAttribute('data-src') || img.getAttribute('data-lazy-src') || '') : '';
                                    if (href.startsWith('https://reelfren.net/drama/') &&
                                        title.length > 2 && !seen.has(href)) {
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
                              const timer = setInterval(function() {
                                attempts++;
                                if (collect() || attempts >= 30) {
                                  clearInterval(timer);
                                  if (attempts >= 30) {
                                    window.DramaPocketBridge.onFailure(
                                      'No drama cards appeared. If ReelFren shows a human verification page, complete it in this browser view, then tap Refresh catalog.'
                                    );
                                  }
                                }
                              }, 1000);
                            })();
                        """.trimIndent()
                        view.evaluateJavascript(script, null)
                    }
                }
                loadUrl(CATALOG_URL)
            }
        }
    )
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun DramaWebPlayer(url: String, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = true
                webChromeClient = WebChromeClient()
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView,
                        request: WebResourceRequest
                    ): Boolean {
                        val target = request.url
                        val scheme = target.scheme?.lowercase()
                        if (scheme == "http" || scheme == "https") return false

                        // Partner deep links (for example shopeeph://) must be
                        // handed to Android, not loaded as a WebView page.
                        return try {
                            val intent = android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                target
                            )
                            context.startActivity(intent)
                            true
                        } catch (_: Exception) {
                            val fallback = target.getQueryParameter("navigate_url")
                            if (!fallback.isNullOrBlank() &&
                                (fallback.startsWith("https://") || fallback.startsWith("http://"))
                            ) {
                                try {
                                    context.startActivity(
                                        android.content.Intent(
                                            android.content.Intent.ACTION_VIEW,
                                            android.net.Uri.parse(fallback)
                                        )
                                    )
                                } catch (_: Exception) {
                                    // Keep the player open if no app can handle the link.
                                }
                            }
                            true
                        }
                    }
                }
                loadUrl(url)
            }
        },
        update = { view -> if (view.url != url) view.loadUrl(url) }
    )
}
