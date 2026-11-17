package com.herhyme.player

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.width
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import coil.compose.AsyncImage
import com.google.common.util.concurrent.MoreExecutors

private val AppBackground = Color(0xFF100E14)
private val PanelColor = Color(0xFF211E29)
private val AccentColor = Color(0xFFD0B2FF)
private val MutedText = Color(0xFFB9B3C2)

class MainActivity : ComponentActivity() {
    private var songs by mutableStateOf<List<Song>>(emptyList())
    private var controller: MediaController? = null
    private var currentSong by mutableStateOf<Song?>(null)
    private var isPlaying by mutableStateOf(false)
    private var playbackPosition by mutableStateOf(0L)
    private var playbackDuration by mutableStateOf(0L)
    private var repeatMode by mutableIntStateOf(Player.REPEAT_MODE_OFF)
    private var shuffleEnabled by mutableStateOf(false)

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(playing: Boolean) {
            isPlaying = playing
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            currentSong = songs.firstOrNull { it.id.toString() == mediaItem?.mediaId }
        }
    }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) scanMusic()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestMusicPermission()
        connectController()
        setContent {
            HerhymeScreen(
                songs = songs,
                currentSong = currentSong,
                isPlaying = isPlaying,
                onSongSelected = { song -> playSong(song) },
                onTogglePlayback = { togglePlayback() },
                onNext = { controller?.seekToNextMediaItem() },
                onPrevious = { controller?.seekToPreviousMediaItem() },
                positionMs = playbackPosition,
                durationMs = playbackDuration,
                repeatMode = repeatMode,
                shuffleEnabled = shuffleEnabled,
                onSeek = { controller?.seekTo(it) },
                onToggleShuffle = { toggleShuffle() },
                onCycleRepeat = { cycleRepeat() },
                onProgressTick = { updatePlaybackProgress() }
            )
        }
    }

    private fun requestMusicPermission() {
        val permission = if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            scanMusic()
        } else {
            permissionLauncher.launch(permission)
        }
    }

    private fun scanMusic() {
        songs = MusicRepository(this).loadSongs()
    }

    private fun connectController() {
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        future.addListener({
            controller = future.get()
            controller?.addListener(playerListener)
            isPlaying = controller?.isPlaying == true
            currentSong = songs.firstOrNull { it.id.toString() == controller?.currentMediaItem?.mediaId }
            updatePlaybackProgress()
        }, MoreExecutors.directExecutor())
    }

    private fun playSong(song: Song) {
        val mediaController = controller ?: return
        val startIndex = songs.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
        val queue = songs.map { item ->
            MediaItem.Builder()
                .setUri(item.uri)
                .setMediaId(item.id.toString())
                .build()
        }
        currentSong = song
        mediaController.setMediaItems(queue, startIndex, 0L)
        mediaController.prepare()
        mediaController.play()
    }

    private fun updatePlaybackProgress() {
        val player = controller ?: return
        playbackPosition = player.currentPosition.coerceAtLeast(0L)
        playbackDuration = player.duration.takeIf { it > 0L && it != androidx.media3.common.C.TIME_UNSET }
            ?: (currentSong?.durationMs ?: 0L)
        repeatMode = player.repeatMode
        shuffleEnabled = player.shuffleModeEnabled
    }

    private fun toggleShuffle() {
        val player = controller ?: return
        player.shuffleModeEnabled = !player.shuffleModeEnabled
        shuffleEnabled = player.shuffleModeEnabled
    }

    private fun cycleRepeat() {
        val player = controller ?: return
        player.repeatMode = when (player.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        repeatMode = player.repeatMode
    }

    private fun togglePlayback() {
        val mediaController = controller ?: return
        if (mediaController.isPlaying) {
            mediaController.pause()
        } else if (mediaController.mediaItemCount > 0) {
            mediaController.play()
        }
    }

    override fun onDestroy() {
        controller?.removeListener(playerListener)
        controller?.release()
        super.onDestroy()
    }
}

@Composable
private fun HerhymeScreen(
    songs: List<Song>,
    currentSong: Song?,
    isPlaying: Boolean,
    onSongSelected: (Song) -> Unit,
    onTogglePlayback: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    positionMs: Long,
    durationMs: Long,
    repeatMode: Int,
    shuffleEnabled: Boolean,
    onSeek: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onProgressTick: () -> Unit
) {
    var selectedTab by remember { mutableStateOf("Home") }
    var searchText by remember { mutableStateOf("") }
    var showNowPlaying by remember { mutableStateOf(false) }

    LaunchedEffect(currentSong, isPlaying, showNowPlaying) {
        while (currentSong != null) {
            onProgressTick()
            delay(500)
        }
    }
    val filteredSongs = songs.filter {
        it.title.contains(searchText, true) ||
            it.artist.contains(searchText, true) ||
            it.album.contains(searchText, true)
    }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = AppBackground) {
            if (showNowPlaying && currentSong != null) {
                NowPlayingScreen(
                    song = currentSong,
                    isPlaying = isPlaying,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    repeatMode = repeatMode,
                    shuffleEnabled = shuffleEnabled,
                    onClose = { showNowPlaying = false },
                    onTogglePlayback = onTogglePlayback,
                    onNext = onNext,
                    onPrevious = onPrevious,
                    onSeek = onSeek,
                    onToggleShuffle = onToggleShuffle,
                    onCycleRepeat = onCycleRepeat
                )
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.weight(1f)) {
                        SearchBar(searchText = searchText, onSearchChange = { searchText = it })
                        when (selectedTab) {
                            "Home" -> HomeContent(
                                songs = filteredSongs,
                                onSongSelected = onSongSelected,
                                onShowAll = { selectedTab = "Songs" }
                            )
                            "Songs" -> SongsContent(songs = filteredSongs, onSongSelected = onSongSelected)
                            else -> PlaylistsContent()
                        }
                    }

                    if (currentSong != null) {
                        MiniPlayer(
                            song = currentSong,
                            isPlaying = isPlaying,
                            onTogglePlayback = onTogglePlayback,
                            onNext = onNext,
                            onPrevious = onPrevious,
                            onOpen = { showNowPlaying = true }
                        )
                    }

                    BottomNavigation(selectedTab = selectedTab, onSelect = { selectedTab = it })
                }
            }
        }
    }
}

@Composable
private fun SearchBar(searchText: String, onSearchChange: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 14.dp)
            .clip(CircleShape)
            .background(PanelColor)
            .padding(horizontal = 18.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Search, contentDescription = "Search", tint = MutedText)
        Spacer(Modifier.size(12.dp))
        BasicTextField(
            value = searchText,
            onValueChange = onSearchChange,
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 16.sp),
            decorationBox = { inner ->
                Box {
                    if (searchText.isEmpty()) Text("Search your music", color = MutedText, fontSize = 16.sp)
                    inner()
                }
            },
            modifier = Modifier.weight(1f)
        )
        if (searchText.isNotEmpty()) {
            Text("Clear", color = AccentColor, modifier = Modifier.clickable { onSearchChange("") })
        }
    }
}

@Composable
private fun HomeContent(
    songs: List<Song>,
    onSongSelected: (Song) -> Unit,
    onShowAll: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Shortcut(Icons.Default.History, "History", Color(0xFF49336E))
                Shortcut(Icons.Default.Favorite, "Favorites", Color(0xFF713A32))
                Shortcut(Icons.Default.TrendingUp, "Most played", Color(0xFF46520C))
                Shortcut(Icons.Default.Shuffle, "Shuffle", Color(0xFF075C61))
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Recently added songs", color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
                Text("→", color = AccentColor, fontSize = 28.sp, modifier = Modifier.clickable { onShowAll() })
            }
        }
        if (songs.isEmpty()) {
            item {
                Text("Your music library is waiting", color = MutedText, modifier = Modifier.padding(22.dp))
            }
        } else {
            items(songs.take(12), key = { it.id }) { song ->
                RecentSongCard(song = song, onClick = { onSongSelected(song) })
            }
        }
    }
}

@Composable
private fun Shortcut(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(76.dp)) {
        Box(
            modifier = Modifier.size(62.dp).clip(CircleShape).background(color),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.height(7.dp))
        Text(label, color = MutedText, fontSize = 11.sp, maxLines = 1)
    }
}

@Composable
private fun RecentSongCard(song: Song, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .padding(horizontal = 18.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(PanelColor)
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AlbumArtwork(song = song, modifier = Modifier.size(72.dp))
        Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
            Text(song.title, color = Color.White, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artist, color = MutedText, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun SongsContent(songs: List<Song>, onSongSelected: (Song) -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Your Songs", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
            Text("${songs.size} tracks", color = MutedText, fontSize = 12.sp)
        }
        if (songs.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No songs found", color = MutedText)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 14.dp, end = 14.dp, bottom = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(songs, key = { it.id }) { song ->
                    Column(modifier = Modifier.clickable { onSongSelected(song) }) {
                        AlbumArtwork(song = song, modifier = Modifier.fillMaxWidth().aspectRatio(1f))
                        Spacer(Modifier.height(7.dp))
                        Text(song.title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(song.artist, color = MutedText, fontSize = 10.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun AlbumArtwork(song: Song, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.clip(RoundedCornerShape(16.dp)).background(Color(0xFF302A3A)),
        contentAlignment = Alignment.Center
    ) {
        if (song.artworkUri != null) {
            AsyncImage(
                model = song.artworkUri,
                contentDescription = "Album artwork for ${song.title}",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Text("♫", color = AccentColor, fontSize = 34.sp)
        }
    }
}

@Composable
private fun PlaylistsContent() {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.QueueMusic, contentDescription = null, tint = AccentColor, modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(12.dp))
            Text("Playlists are coming next", color = Color.White, fontSize = 18.sp)
            Text("Your music collections will live here.", color = MutedText, fontSize = 13.sp)
        }
    }
}

@Composable
private fun MiniPlayer(
    song: Song,
    isPlaying: Boolean,
    onTogglePlayback: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onOpen: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().background(PanelColor)) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AlbumArtwork(song, Modifier.size(54.dp))
            Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
                Text(song.title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(song.artist, color = MutedText, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onPrevious) {
                Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", tint = Color.White)
            }
            IconButton(onClick = onTogglePlayback) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = AccentColor,
                    modifier = Modifier.size(30.dp)
                )
            }
            IconButton(onClick = onNext) {
                Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = Color.White)
            }
        }
        Box(Modifier.fillMaxWidth().height(3.dp).background(AccentColor))
    }
}


@Composable
private fun NowPlayingScreen(
    song: Song,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    repeatMode: Int,
    shuffleEnabled: Boolean,
    onClose: () -> Unit,
    onTogglePlayback: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit
) {
    val safeDuration = durationMs.coerceAtLeast(0L)
    val safePosition = positionMs.coerceIn(0L, safeDuration.coerceAtLeast(1L))
    Column(
        modifier = Modifier.fillMaxSize().background(AppBackground).padding(horizontal = 22.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Close player", tint = Color.White)
            }
            Spacer(Modifier.weight(1f))
            Text("NOW PLAYING", color = MutedText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = {}) {
                Icon(Icons.Default.QueueMusic, contentDescription = "Queue", tint = MutedText)
            }
        }
        Spacer(Modifier.weight(1f))
        AlbumArtwork(song, Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(24.dp)))
        Spacer(Modifier.height(30.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(song.title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(song.artist, color = MutedText, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = {}) {
                Icon(Icons.Default.Favorite, contentDescription = "Favorite (coming soon)", tint = MutedText)
            }
        }
        Spacer(Modifier.height(18.dp))
        Slider(
            value = if (safeDuration > 0L) safePosition.toFloat() / safeDuration.toFloat() else 0f,
            onValueChange = { fraction -> if (safeDuration > 0L) onSeek((fraction * safeDuration).toLong()) },
            modifier = Modifier.fillMaxWidth()
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(safePosition), color = MutedText, fontSize = 12.sp)
            Text(formatTime(safeDuration), color = MutedText, fontSize = 12.sp)
        }
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onToggleShuffle) {
                Icon(Icons.Default.Shuffle, contentDescription = "Shuffle", tint = if (shuffleEnabled) AccentColor else MutedText)
            }
            IconButton(onClick = onPrevious) {
                Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", tint = Color.White, modifier = Modifier.size(34.dp))
            }
            IconButton(onClick = onTogglePlayback, modifier = Modifier.size(68.dp).clip(CircleShape).background(AccentColor)) {
                Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play", tint = AppBackground, modifier = Modifier.size(42.dp))
            }
            IconButton(onClick = onNext) {
                Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = Color.White, modifier = Modifier.size(34.dp))
            }
            IconButton(onClick = onCycleRepeat) {
                Icon(if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                    contentDescription = "Repeat", tint = if (repeatMode == Player.REPEAT_MODE_OFF) MutedText else AccentColor)
            }
        }
        Spacer(Modifier.weight(1f))
        Text(song.album.ifBlank { "Unknown album" }, color = MutedText, fontSize = 12.sp, maxLines = 1)
        Spacer(Modifier.height(12.dp))
    }
}

private fun formatTime(milliseconds: Long): String {
    val totalSeconds = (milliseconds / 1000L).coerceAtLeast(0L)
    return "%d:%02d".format(totalSeconds / 60L, totalSeconds % 60L)
}

@Composable
private fun BottomNavigation(selectedTab: String, onSelect: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().background(PanelColor).padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        NavItem("Home", Icons.Default.Home, selectedTab == "Home") { onSelect("Home") }
        NavItem("Songs", Icons.Default.LibraryMusic, selectedTab == "Songs") { onSelect("Songs") }
        NavItem("Playlists", Icons.Default.QueueMusic, selectedTab == "Playlists") { onSelect("Playlists") }
    }
}

@Composable
private fun NavItem(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier.clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = label, tint = if (selected) AccentColor else MutedText, modifier = Modifier.size(24.dp))
        Text(label, color = if (selected) Color.White else MutedText, fontSize = 12.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}
