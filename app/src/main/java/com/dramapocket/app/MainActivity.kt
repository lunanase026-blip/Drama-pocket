package com.dramapocket.app

import android.content.Context
import android.os.Bundle
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Link
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
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private const val PREFS_NAME = "drama_pocket_vertical_v2"
private const val KEY_FAVORITES = "favorites"
private const val KEY_CUSTOM_DRAMAS = "custom_dramas"
private const val KEY_FEED_URL = "feed_url"
private const val KEY_REMOTE_CATALOG = "remote_catalog"
private const val KEY_PROGRESS_PREFIX = "ep_progress_"

private val PROVIDERS = listOf(
    "All",
    "Melolo",
    "Sereal+",
    "DramaBox",
    "ReelShort",
    "DramaWave",
    "NetShort",
    "ShortMax",
    "FlexTV",
    "Vigloo",
    "PineDrama",
    "MoboReels",
    "WeTV",
    "iQIYI",
    "FreeReels",
    "MovieBox Shorts"
)

data class DramaEpisode(
    val number: Int,
    val title: String,
    val videoUrl: String
)

data class DramaSeries(
    val id: String,
    val title: String,
    val provider: String,
    val genre: String,
    val synopsis: String,
    val posterUrl: String,
    val accentTop: Color,
    val accentBottom: Color,
    val episodes: List<DramaEpisode>,
    val isCustom: Boolean = false
)

private enum class AppTab {
    DISCOVER,
    SAVED,
    SOURCES
}

private val STARTER_CATALOG = emptyList<DramaSeries>()

private fun parseCatalogJson(raw: String, isCustom: Boolean): List<DramaSeries> {
    if (raw.isBlank()) return emptyList()
    return runCatching {
        val trimmed = raw.trim()
        val array = if (trimmed.startsWith("{")) {
            JSONObject(trimmed).optJSONArray("series")
                ?: JSONObject(trimmed).optJSONArray("dramas")
                ?: JSONObject(trimmed).optJSONArray("items")
                ?: JSONArray()
        } else {
            JSONArray(trimmed)
        }
        val palettes = listOf(
            Color(0xFF4C1D95) to Color(0xFF0F172A),
            Color(0xFF831843) to Color(0xFF1E1B4B),
            Color(0xFF1E3A8A) to Color(0xFF09090B),
            Color(0xFF7F1D1D) to Color(0xFF18181B),
            Color(0xFF0F766E) to Color(0xFF111827)
        )
        buildList {
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val title = obj.optString("title").trim()
                val provider = obj.optString("provider", "Melolo").trim().ifBlank { "Melolo" }
                val genre = obj.optString("genre", "Short Drama").trim().ifBlank { "Short Drama" }
                val synopsis = obj.optString("synopsis", "").trim()
                val poster = obj.optString("poster", obj.optString("image", "")).trim()
                val id = obj.optString("id", "").trim().ifBlank {
                    "${provider.lowercase()}-${title.lowercase().replace(Regex("[^a-z0-9]+"), "-")}"
                }
                val epArray = obj.optJSONArray("episodes") ?: JSONArray()
                val episodes = buildList {
                    for (j in 0 until epArray.length()) {
                        val epObj = epArray.optJSONObject(j) ?: continue
                        val videoUrl = epObj.optString("videoUrl", epObj.optString("url", "")).trim()
                        if (videoUrl.startsWith("https://") || videoUrl.startsWith("http://")) {
                            add(
                                DramaEpisode(
                                    number = epObj.optInt("number", j + 1),
                                    title = epObj.optString("title", "Episode ${j + 1}").trim()
                                        .ifBlank { "Episode ${j + 1}" },
                                    videoUrl = videoUrl
                                )
                            )
                        }
                    }
                }
                val singleVideo = obj.optString("videoUrl", "").trim()
                val finalEpisodes = if (episodes.isNotEmpty()) {
                    episodes
                } else if (singleVideo.startsWith("https://") || singleVideo.startsWith("http://")) {
                    listOf(DramaEpisode(1, "Episode 1", singleVideo))
                } else {
                    emptyList()
                }
                if (title.isNotBlank() && finalEpisodes.isNotEmpty()) {
                    val palette = palettes[i % palettes.size]
                    add(
                        DramaSeries(
                            id = id,
                            title = title,
                            provider = provider,
                            genre = genre,
                            synopsis = synopsis,
                            posterUrl = poster,
                            accentTop = palette.first,
                            accentBottom = palette.second,
                            episodes = finalEpisodes,
                            isCustom = isCustom
                        )
                    )
                }
            }
        }
    }.getOrDefault(emptyList())
}

private fun serializeCatalogJson(seriesList: List<DramaSeries>): String {
    val array = JSONArray()
    seriesList.forEach { series ->
        val epArray = JSONArray()
        series.episodes.forEach { ep ->
            epArray.put(JSONObject().apply {
                put("number", ep.number)
                put("title", ep.title)
                put("videoUrl", ep.videoUrl)
            })
        }
        array.put(JSONObject().apply {
            put("id", series.id)
            put("title", series.title)
            put("provider", series.provider)
            put("genre", series.genre)
            put("synopsis", series.synopsis)
            put("poster", series.posterUrl)
            put("episodes", epArray)
        })
    }
    return array.toString()
}

private suspend fun fetchCleanFeed(feedUrl: String): Result<List<DramaSeries>> =
    withContext(Dispatchers.IO) {
        runCatching {
            val conn = (URL(feedUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 12_000
                setRequestProperty("Accept", "application/json")
            }
            val code = conn.responseCode
            if (code !in 200..299) {
                error("Feed returned HTTP $code")
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val parsed = parseCatalogJson(body, isCustom = false)
            if (parsed.isEmpty()) {
                error("No valid series with episode video URLs found in JSON feed.")
            }
            parsed
        }
    }

private fun formatDuration(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val totalSec = ms / 1000L
    val mins = totalSec / 60L
    val secs = totalSec % 60L
    return "%d:%02d".format(mins, secs)
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
    val scope = rememberCoroutineScope()

    var activeTab by remember { mutableStateOf(AppTab.DISCOVER) }
    var selectedProvider by remember { mutableStateOf("All") }
    var search by remember { mutableStateOf("") }

    var remoteCatalog by remember {
        mutableStateOf(
            parseCatalogJson(prefs.getString(KEY_REMOTE_CATALOG, "") ?: "", isCustom = false)
        )
    }
    var customSeries by remember {
        mutableStateOf(
            parseCatalogJson(prefs.getString(KEY_CUSTOM_DRAMAS, "") ?: "", isCustom = true)
        )
    }
    var favorites by remember {
        mutableStateOf(prefs.getStringSet(KEY_FAVORITES, emptySet())?.toSet() ?: emptySet())
    }
    var feedUrl by remember {
        mutableStateOf(prefs.getString(KEY_FEED_URL, "") ?: "")
    }
    var syncMessage by remember { mutableStateOf<String?>(null) }
    var syncing by remember { mutableStateOf(false) }

    var activePlayerDrama by remember { mutableStateOf<DramaSeries?>(null) }
    var activeStartEpisode by remember { mutableIntStateOf(0) }

    val allCatalog = remember(remoteCatalog, customSeries) {
        val base = remoteCatalog
        (customSeries + base).distinctBy { it.id }
    }

    fun toggleFavorite(seriesId: String) {
        val next = if (favorites.contains(seriesId)) favorites - seriesId else favorites + seriesId
        favorites = next
        prefs.edit().putStringSet(KEY_FAVORITES, next).apply()
    }

    fun openPlayer(series: DramaSeries) {
        val savedIndex = prefs.getInt("$KEY_PROGRESS_PREFIX${series.id}", 0)
            .coerceIn(0, (series.episodes.size - 1).coerceAtLeast(0))
        activeStartEpisode = savedIndex
        activePlayerDrama = series
    }

    fun saveEpisodeProgress(seriesId: String, index: Int) {
        prefs.edit().putInt("$KEY_PROGRESS_PREFIX$seriesId", index).apply()
    }

    fun addCustomSeries(series: DramaSeries) {
        val updated = listOf(series) + customSeries.filterNot { it.id == series.id }
        customSeries = updated
        prefs.edit().putString(KEY_CUSTOM_DRAMAS, serializeCatalogJson(updated)).apply()
    }

    fun syncRemoteFeed(url: String) {
        val clean = url.trim()
        if (!clean.startsWith("https://") && !clean.startsWith("http://")) {
            syncMessage = "Enter a valid https:// JSON catalog URL."
            return
        }
        syncing = true
        syncMessage = null
        prefs.edit().putString(KEY_FEED_URL, clean).apply()
        feedUrl = clean
        scope.launch {
            val result = fetchCleanFeed(clean)
            syncing = false
            result.onSuccess { list ->
                remoteCatalog = list
                prefs.edit().putString(KEY_REMOTE_CATALOG, serializeCatalogJson(list)).apply()
                syncMessage = "Loaded ${list.size} playable series."
                activeTab = AppTab.DISCOVER
            }.onFailure { err ->
                syncMessage = err.message ?: "Failed to load JSON feed."
            }
        }
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
        val playing = activePlayerDrama
        if (playing != null) {
            VerticalDramaPlayer(
                drama = playing,
                initialEpisodeIndex = activeStartEpisode,
                isFavorite = favorites.contains(playing.id),
                onToggleFavorite = { toggleFavorite(playing.id) },
                onEpisodeChanged = { index -> saveEpisodeProgress(playing.id, index) },
                onClose = { activePlayerDrama = null }
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
                            selected = activeTab == AppTab.DISCOVER,
                            onClick = { activeTab = AppTab.DISCOVER },
                            icon = { Icon(Icons.Default.Explore, contentDescription = "Discover") },
                            label = { Text("Discover") }
                        )
                        NavigationBarItem(
                            selected = activeTab == AppTab.SAVED,
                            onClick = { activeTab = AppTab.SAVED },
                            icon = { Icon(Icons.Default.Bookmark, contentDescription = "Saved") },
                            label = { Text("Saved (${favorites.size})") }
                        )
                        NavigationBarItem(
                            selected = activeTab == AppTab.SOURCES,
                            onClick = { activeTab = AppTab.SOURCES },
                            icon = { Icon(Icons.Default.Link, contentDescription = "Sources") },
                            label = { Text("Sources") }
                        )
                    }
                }
            ) { padding ->
                when (activeTab) {
                    AppTab.DISCOVER -> DiscoverScreen(
                        modifier = Modifier.padding(padding),
                        catalog = allCatalog,
                        selectedProvider = selectedProvider,
                        onSelectProvider = { selectedProvider = it },
                        search = search,
                        onSearchChange = { search = it },
                        favorites = favorites,
                        onToggleFavorite = ::toggleFavorite,
                        onWatch = ::openPlayer
                    )

                    AppTab.SAVED -> SavedScreen(
                        modifier = Modifier.padding(padding),
                        savedDramas = allCatalog.filter { favorites.contains(it.id) },
                        onToggleFavorite = ::toggleFavorite,
                        onWatch = ::openPlayer,
                        onBrowseDiscover = { activeTab = AppTab.DISCOVER }
                    )

                    AppTab.SOURCES -> SourcesScreen(
                        modifier = Modifier.padding(padding),
                        feedUrl = feedUrl,
                        syncing = syncing,
                        syncMessage = syncMessage,
                        hasRemoteCatalog = remoteCatalog.isNotEmpty(),
                        onSyncFeed = ::syncRemoteFeed,
                        onResetToStarter = {
                            remoteCatalog = emptyList()
                            prefs.edit().remove(KEY_REMOTE_CATALOG).apply()
                            syncMessage = "Remote catalog cleared. No sample videos are included."
                        },
                        onAddCustomSeries = { newSeries ->
                            addCustomSeries(newSeries)
                            activeTab = AppTab.DISCOVER
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun DiscoverScreen(
    modifier: Modifier = Modifier,
    catalog: List<DramaSeries>,
    selectedProvider: String,
    onSelectProvider: (String) -> Unit,
    search: String,
    onSearchChange: (String) -> Unit,
    favorites: Set<String>,
    onToggleFavorite: (String) -> Unit,
    onWatch: (DramaSeries) -> Unit
) {
    val filtered = remember(catalog, selectedProvider, search) {
        val query = search.trim()
        catalog.filter { series ->
            val matchesProvider = selectedProvider == "All" ||
                series.provider.equals(selectedProvider, ignoreCase = true)
            val matchesQuery = query.isBlank() ||
                series.title.contains(query, ignoreCase = true) ||
                series.genre.contains(query, ignoreCase = true) ||
                series.provider.contains(query, ignoreCase = true)
            matchesProvider && matchesQuery
        }
    }
    val featured = filtered.firstOrNull()

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier
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
                            "DramaPocket",
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            "Native vertical player · No embedded webpages",
                            fontSize = 12.sp,
                            color = Color(0xFFA1A1AA)
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
                    items(PROVIDERS) { provider ->
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

        if (featured != null && search.isBlank()) {
            item(span = { GridItemSpan(2) }) {
                FeaturedSpotlightCard(
                    drama = featured,
                    isFavorite = favorites.contains(featured.id),
                    onToggleFavorite = { onToggleFavorite(featured.id) },
                    onWatch = { onWatch(featured) }
                )
            }
        }

        if (filtered.isEmpty()) {
            item(span = { GridItemSpan(2) }) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (catalog.isEmpty()) "No verified playable dramas yet. Add a permitted catalog feed in Sources." else "No dramas found in $selectedProvider.",
                        color = Color(0xFFA1A1AA),
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            items(filtered, key = { it.id }) { drama ->
                PortraitDramaCard(
                    drama = drama,
                    isFavorite = favorites.contains(drama.id),
                    onToggleFavorite = { onToggleFavorite(drama.id) },
                    onClick = { onWatch(drama) }
                )
            }
        }
    }
}

@Composable
private fun FeaturedSpotlightCard(
    drama: DramaSeries,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onWatch: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(210.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(drama.accentTop, drama.accentBottom)))
            .clickable(onClick = onWatch)
    ) {
        if (drama.posterUrl.isNotBlank()) {
            AsyncImage(
                model = drama.posterUrl,
                contentDescription = drama.title,
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
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${drama.provider} · ${drama.genre}",
                    color = Color(0xFFE4E4E7),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        if (isFavorite) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        contentDescription = "Save",
                        tint = if (isFavorite) Color(0xFFFF3D71) else Color.White
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = drama.title,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (drama.synopsis.isNotBlank()) {
                    Text(
                        text = drama.synopsis,
                        color = Color(0xFFD4D4D8),
                        fontSize = 12.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = onWatch,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF3D71)),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Watch Now", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                    Text(
                        text = "${drama.episodes.size} episodes",
                        color = Color(0xFFE4E4E7),
                        fontSize = 12.sp
                    )
                }
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
                .background(Brush.verticalGradient(listOf(drama.accentTop, drama.accentBottom)))
        ) {
            if (drama.posterUrl.isNotBlank()) {
                AsyncImage(
                    model = drama.posterUrl,
                    contentDescription = drama.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = drama.title,
                        color = Color.White.copy(alpha = 0.88f),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

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
                text = "${drama.provider} · ${drama.episodes.size} eps",
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
            Spacer(Modifier.height(3.dp))
            Text(
                text = drama.genre,
                color = Color(0xFFA1A1AA),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SavedScreen(
    modifier: Modifier = Modifier,
    savedDramas: List<DramaSeries>,
    onToggleFavorite: (String) -> Unit,
    onWatch: (DramaSeries) -> Unit,
    onBrowseDiscover: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0B0B10))
            .padding(16.dp)
    ) {
        Text(
            "Saved Series",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(
            "Jump straight back into your vertical watchlist",
            fontSize = 13.sp,
            color = Color(0xFFA1A1AA)
        )
        Spacer(Modifier.height(16.dp))

        if (savedDramas.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No saved dramas yet.", color = Color(0xFFA1A1AA))
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = onBrowseDiscover,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF3D71))
                    ) {
                        Text("Explore Discover")
                    }
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(savedDramas, key = { it.id }) { drama ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF14141D))
                            .clickable { onWatch(drama) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .width(68.dp)
                                .height(92.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Brush.verticalGradient(listOf(drama.accentTop, drama.accentBottom))),
                            contentAlignment = Alignment.Center
                        ) {
                            if (drama.posterUrl.isNotBlank()) {
                                AsyncImage(
                                    model = drama.posterUrl,
                                    contentDescription = drama.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
                            }
                        }
                        Column(
                            Modifier
                                .weight(1f)
                                .padding(horizontal = 12.dp)
                        ) {
                            Text(
                                drama.title,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "${drama.provider} · ${drama.episodes.size} episodes",
                                color = Color(0xFFA1A1AA),
                                fontSize = 12.sp
                            )
                        }
                        IconButton(onClick = { onToggleFavorite(drama.id) }) {
                            Icon(Icons.Default.Bookmark, contentDescription = "Remove", tint = Color(0xFFFF3D71))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SourcesScreen(
    modifier: Modifier = Modifier,
    feedUrl: String,
    syncing: Boolean,
    syncMessage: String?,
    hasRemoteCatalog: Boolean,
    onSyncFeed: (String) -> Unit,
    onResetToStarter: () -> Unit,
    onAddCustomSeries: (DramaSeries) -> Unit
) {
    var feedInput by remember(feedUrl) { mutableStateOf(feedUrl) }
    var titleInput by remember { mutableStateOf("") }
    var providerInput by remember { mutableStateOf("Melolo") }
    var genreInput by remember { mutableStateOf("Romance") }
    var posterInput by remember { mutableStateOf("") }
    var videoInput by remember { mutableStateOf("") }
    var formNotice by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0B0B10))
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Column {
            Text(
                "Catalog Sources",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                "Connect a permitted JSON catalog containing direct MP4/HLS episode streams. DramaPocket uses its own native catalog and player; it does not display provider webpages.",
                fontSize = 13.sp,
                color = Color(0xFFA1A1AA)
            )
        }

        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF14141D))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Remote Catalog JSON Feed", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            OutlinedTextField(
                value = feedInput,
                onValueChange = { feedInput = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("https://.../catalog.json", color = Color(0xFF71717A)) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFFFF3D71),
                    unfocusedBorderColor = Color(0xFF27272A),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )
            if (syncMessage != null) {
                Text(syncMessage, color = Color(0xFFD4D4D8), fontSize = 12.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { onSyncFeed(feedInput) },
                    enabled = !syncing,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF3D71))
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (syncing) "Loading…" else "Sync JSON")
                }
                if (hasRemoteCatalog) {
                    OutlinedButton(onClick = onResetToStarter) {
                        Text("Clear Catalog", color = Color.White)
                    }
                }
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF14141D))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Add Series Manually", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            OutlinedTextField(
                value = titleInput,
                onValueChange = { titleInput = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Series title") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFFFF3D71),
                    unfocusedBorderColor = Color(0xFF27272A),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(PROVIDERS.drop(1)) { provider ->
                    val selected = provider == providerInput
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (selected) Color(0xFF8B5CF6) else Color(0xFF1F1F2B),
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable { providerInput = provider }
                    ) {
                        Text(
                            provider,
                            color = Color.White,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
            OutlinedTextField(
                value = genreInput,
                onValueChange = { genreInput = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Genre") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFFFF3D71),
                    unfocusedBorderColor = Color(0xFF27272A),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )
            OutlinedTextField(
                value = posterInput,
                onValueChange = { posterInput = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Poster image URL (optional)") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFFFF3D71),
                    unfocusedBorderColor = Color(0xFF27272A),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )
            OutlinedTextField(
                value = videoInput,
                onValueChange = { videoInput = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Episode 1 stream URL (.mp4 or .m3u8)") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFFFF3D71),
                    unfocusedBorderColor = Color(0xFF27272A),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )
            if (formNotice != null) {
                Text(formNotice!!, color = Color(0xFFFCA5A5), fontSize = 12.sp)
            }
            Button(
                onClick = {
                    val t = titleInput.trim()
                    val v = videoInput.trim()
                    if (t.length < 2) {
                        formNotice = "Enter a series title."
                        return@Button
                    }
                    if (!v.startsWith("https://") && !v.startsWith("http://")) {
                        formNotice = "Enter a direct https:// .mp4 or .m3u8 stream URL."
                        return@Button
                    }
                    val series = DramaSeries(
                        id = "custom-${System.currentTimeMillis()}",
                        title = t,
                        provider = providerInput,
                        genre = genreInput.trim().ifBlank { "Short Drama" },
                        synopsis = "",
                        posterUrl = posterInput.trim(),
                        accentTop = Color(0xFF4C1D95),
                        accentBottom = Color(0xFF09090B),
                        episodes = listOf(DramaEpisode(1, "Episode 1", v)),
                        isCustom = true
                    )
                    onAddCustomSeries(series)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6))
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Add to Discover")
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun VerticalDramaPlayer(
    drama: DramaSeries,
    initialEpisodeIndex: Int,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onEpisodeChanged: (Int) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val episodes = drama.episodes
    val pagerState = rememberPagerState(
        initialPage = initialEpisodeIndex.coerceIn(0, (episodes.size - 1).coerceAtLeast(0)),
        pageCount = { episodes.size }
    )

    val exoPlayer = remember(context) {
        ExoPlayer.Builder(context).build().apply {
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
    var showControls by remember { mutableStateOf(true) }

    BackHandler {
        if (showEpisodeSheet) {
            showEpisodeSheet = false
        } else {
            onClose()
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
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    LaunchedEffect(pagerState.currentPage, drama.id) {
        val ep = episodes.getOrNull(pagerState.currentPage) ?: return@LaunchedEffect
        onEpisodeChanged(pagerState.currentPage)
        exoPlayer.setMediaItem(MediaItem.fromUri(ep.videoUrl))
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
        exoPlayer.volume = if (isMuted) 0f else 1f
        exoPlayer.playbackParameters = PlaybackParameters(speed)
    }

    LaunchedEffect(exoPlayer) {
        while (true) {
            positionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            durationMs = exoPlayer.duration.coerceAtLeast(0L)
            delay(350)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        VerticalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val ep = episodes[page]
            val isCurrent = page == pagerState.currentPage

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        if (isCurrent) {
                            if (exoPlayer.isPlaying) {
                                exoPlayer.pause()
                                showControls = true
                            } else {
                                exoPlayer.play()
                            }
                        }
                    }
            ) {
                if (isCurrent) {
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
                            if (view.player !== exoPlayer) {
                                view.player = exoPlayer
                            }
                        }
                    )
                } else {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Brush.verticalGradient(listOf(drama.accentTop, drama.accentBottom))),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Episode ${ep.number}: ${ep.title}",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Bottom gradient scrim for legible native controls
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                            )
                        )
                )

                if (isCurrent && !isPlaying) {
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
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)
                    )
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
                    text = drama.title,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val currentEp = episodes.getOrNull(pagerState.currentPage)
                Text(
                    text = "${drama.provider} · Ep ${currentEp?.number ?: 1} / ${episodes.size}",
                    color = Color(0xFFD4D4D8),
                    fontSize = 12.sp
                )
            }
        }

        // Right-side vertical action rail
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

        // Bottom Episode Info + Seek Bar
        if (showControls) {
            val currentEp = episodes.getOrNull(pagerState.currentPage)
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
                            if (durationMs > 0L) {
                                exoPlayer.seekTo((fraction * durationMs).toLong())
                            }
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
        }

        // Slide-up Episode Drawer
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
