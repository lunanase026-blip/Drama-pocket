package com.dramapocket.app

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebChromeClient
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
import androidx.lifecycle.lifecycleScope
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

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

    suspend fun refreshCatalog() {
        loading = true
        error = null
        try {
            dramas = withContext(Dispatchers.IO) {
                val doc = Jsoup.connect(CATALOG_URL)
                    .userAgent("Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36")
                    .timeout(20000)
                    .get()
                val found = linkedMapOf<String, Drama>()
                doc.select("a[href*=/drama/]").forEach { a ->
                    val href = a.absUrl("href")
                    val title = (a.attr("title").ifBlank { a.text() }).trim()
                    val img = a.selectFirst("img")
                    val image = img?.let {
                        it.absUrl("src").ifBlank { it.absUrl("data-src") }
                    }.orEmpty()
                    if (href.isNotBlank() && title.length > 2 && !found.containsKey(href)) {
                        found[href] = Drama(title, href, image)
                    }
                }
                found.values.toList()
            }
            if (dramas.isEmpty()) error = "The catalog returned no readable drama cards. The source website may have changed."
        } catch (e: Exception) {
            error = e.localizedMessage ?: "Unable to load the catalog."
        } finally {
            loading = false
        }
    }

    LaunchedEffect(Unit) { refreshCatalog() }
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
                Column(Modifier.fillMaxSize().padding(padding).background(Color(0xFFFFFBFE))) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Discover", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        TextButton(onClick = { /* refreshed by pull/reopen in this starter */ }) { Text("Catalog") }
                    }
                    when {
                        loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                        error != null -> Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(error!!, color = Color.DarkGray)
                            Spacer(Modifier.height(12.dp))
                            Button(onClick = { /* activity recreation or later refresh control */ }) { Text("Try again") }
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
            Icon(Icons.Default.FavoriteBorder, contentDescription = "Favorite", tint = if (favorite) Color.Red else Color.Gray)
        }
    }
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
                webViewClient = WebViewClient()
                loadUrl(url)
            }
        },
        update = { view -> if (view.url != url) view.loadUrl(url) }
    )
}
