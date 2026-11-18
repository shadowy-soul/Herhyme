package com.herhyme.player

import android.Manifest
import android.content.ComponentName
import android.content.Context
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
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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

private var AppBackground by mutableStateOf(Color(0xFF100E14))
private var PanelColor by mutableStateOf(Color(0xFF211E29))
private var AccentColor by mutableStateOf(Color(0xFFD0B2FF))
private var MutedText by mutableStateOf(Color(0xFFB9B3C2))
private var PrimaryText by mutableStateOf(Color.White)
private var showArtwork by mutableStateOf(true)
private var showAlbumName by mutableStateOf(true)
private var showSongDuration by mutableStateOf(false)
private var showFolderPaths by mutableStateOf(true)
private var blacklistedFolders by mutableStateOf<Set<String>>(emptySet())

class MainActivity : ComponentActivity() {
    private var songs by mutableStateOf<List<Song>>(emptyList())
    private var controller: MediaController? = null
    private var currentSong by mutableStateOf<Song?>(null)
    private var isPlaying by mutableStateOf(false)
    private var playbackPosition by mutableStateOf(0L)
    private var playbackDuration by mutableStateOf(0L)
    private var repeatMode by mutableIntStateOf(Player.REPEAT_MODE_OFF)
    private var shuffleEnabled by mutableStateOf(false)
    private var favoriteSongIds by mutableStateOf<Set<Long>>(emptySet())

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
        val preferences = getSharedPreferences("herhyme_preferences", Context.MODE_PRIVATE)
        applySavedSettings(preferences)
        favoriteSongIds = preferences
            .getStringSet("favorite_song_ids", emptySet())
            ?.mapNotNull { it.toLongOrNull() }
            ?.toSet() ?: emptySet()
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
                favoriteSongIds = favoriteSongIds,
                onToggleFavorite = { toggleFavorite(it) },
                onSeek = { controller?.seekTo(it) },
                onToggleShuffle = { toggleShuffle() },
                onCycleRepeat = { cycleRepeat() },
                onProgressTick = { updatePlaybackProgress() },
                onShuffleSongs = { playShuffledSongs() }
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

    private fun toggleFavorite(song: Song) {
        val updated = favoriteSongIds.toMutableSet()
        if (!updated.add(song.id)) updated.remove(song.id)
        favoriteSongIds = updated.toSet()
        getSharedPreferences("herhyme_preferences", Context.MODE_PRIVATE)
            .edit()
            .putStringSet("favorite_song_ids", favoriteSongIds.map { it.toString() }.toSet())
            .apply()
    }

    private fun playShuffledSongs() {
        val player = controller ?: return
        if (songs.isEmpty()) return
        val shuffledSongs = songs.shuffled()
        val queue = shuffledSongs.map { item ->
            MediaItem.Builder()
                .setUri(item.uri)
                .setMediaId(item.id.toString())
                .build()
        }
        player.shuffleModeEnabled = false
        player.setMediaItems(queue, 0, 0L)
        currentSong = shuffledSongs.first()
        player.prepare()
        player.play()
        shuffleEnabled = false
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
    favoriteSongIds: Set<Long>,
    onToggleFavorite: (Song) -> Unit,
    onSeek: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onProgressTick: () -> Unit,
    onShuffleSongs: () -> Unit
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
                    isFavorite = favoriteSongIds.contains(currentSong.id),
                    onToggleFavorite = { onToggleFavorite(currentSong) },
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
                    Column(
                        modifier = Modifier.weight(1f).pointerInput(selectedTab) {
                            val swipeTabs = listOf("Home", "Songs", "Folders", "Playlists", "Settings")
                            var dragTotal = 0f
                            detectHorizontalDragGestures(
                                onHorizontalDrag = { change, amount ->
                                    dragTotal += amount
                                    change.consume()
                                },
                                onDragEnd = {
                                    val currentIndex = swipeTabs.indexOf(selectedTab).let { if (it < 0) 0 else it }
                                    if (dragTotal < -70f && currentIndex < swipeTabs.lastIndex) {
                                        selectedTab = swipeTabs[currentIndex + 1]
                                    } else if (dragTotal > 70f && currentIndex > 0) {
                                        selectedTab = swipeTabs[currentIndex - 1]
                                    }
                                    dragTotal = 0f
                                }
                            )
                        }
                    ) {
                        SearchBar(searchText = searchText, onSearchChange = { searchText = it })
                        when (selectedTab) {
                            "Home" -> HomeContent(
                                songs = filteredSongs,
                                onSongSelected = onSongSelected,
                                onShowAll = { selectedTab = "Songs" },
                                onFavorites = { selectedTab = "Favorites" },
                                onShuffle = onShuffleSongs
                            )
                            "Songs" -> SongsContent(songs = filteredSongs.filterNot { blacklistedFolders.contains(it.folderPath) }, onSongSelected = onSongSelected, showDuration = showSongDuration)
                            "Folders" -> FoldersContent(songs = filteredSongs, onSongSelected = onSongSelected, showPaths = showFolderPaths)
                            "Settings" -> SettingsContent(songs = songs)
                            "Favorites" -> FavoritesContent(
                                songs = songs.filter { favoriteSongIds.contains(it.id) },
                                onSongSelected = onSongSelected
                            )
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
            textStyle = androidx.compose.ui.text.TextStyle(color = PrimaryText, fontSize = 16.sp),
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
    onShowAll: () -> Unit,
    onFavorites: () -> Unit,
    onShuffle: () -> Unit
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
                Shortcut(Icons.Default.Favorite, "Favorites", Color(0xFF713A32), onFavorites)
                Shortcut(Icons.Default.Shuffle, "Shuffle", Color(0xFF075C61), onShuffle)
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Recently added songs", color = PrimaryText, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
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
private fun Shortcut(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(76.dp).clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier.size(62.dp).clip(CircleShape).background(color),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = PrimaryText, modifier = Modifier.size(30.dp))
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
            Text(song.title, color = PrimaryText, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artist, color = MutedText, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun FavoritesContent(songs: List<Song>, onSongSelected: (Song) -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Favorites", color = PrimaryText, fontSize = 23.sp, fontWeight = FontWeight.Bold)
            Text("${songs.size} tracks", color = MutedText, fontSize = 12.sp)
        }
        if (songs.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.FavoriteBorder, contentDescription = null, tint = AccentColor, modifier = Modifier.size(42.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("No favorites yet", color = PrimaryText, fontSize = 17.sp)
                    Text("Tap the heart in Now Playing to save a song here.", color = MutedText, fontSize = 12.sp)
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(songs, key = { it.id }) { song ->
                    RecentSongCard(song = song, onClick = { onSongSelected(song) })
                }
            }
        }
    }
}

@Composable
private fun SongsContent(songs: List<Song>, onSongSelected: (Song) -> Unit, showDuration: Boolean) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val preferences = remember { context.getSharedPreferences("herhyme_preferences", Context.MODE_PRIVATE) }
    var sortBy by remember { mutableStateOf(preferences.getString("songs_sort_by", "Title") ?: "Title") }
    var ascending by remember { mutableStateOf(preferences.getBoolean("songs_sort_ascending", true)) }
    var gridColumns by remember { mutableIntStateOf(preferences.getInt("songs_grid_columns", 3).coerceIn(2, 5)) }
    var menuExpanded by remember { mutableStateOf(false) }
    val sortedSongs = remember(songs, sortBy, ascending) {
        val sorted = when (sortBy) {
            "Artist" -> songs.sortedBy { it.artist.lowercase() }
            "Duration" -> songs.sortedBy { it.durationMs }
            "Date added" -> songs.sortedBy { it.dateAdded }
            else -> songs.sortedBy { it.title.lowercase() }
        }
        if (ascending) sorted else sorted.reversed()
    }
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Your Songs", color = PrimaryText, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                Text("${sortedSongs.size} tracks", color = MutedText, fontSize = 12.sp)
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.Menu, contentDescription = "Song options", tint = AccentColor)
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(text = { Text("Sort by", color = PrimaryText) }, onClick = { })
                    listOf("Title", "Artist", "Duration", "Date added").forEach { option ->
                        DropdownMenuItem(
                            text = { Text((if (sortBy == option) "✓ " else "") + option, color = if (sortBy == option) AccentColor else PrimaryText) },
                            onClick = {
                                sortBy = option
                                preferences.edit().putString("songs_sort_by", option).apply()
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(if (ascending) "Ascending ↑" else "Descending ↓", color = PrimaryText) },
                        onClick = {
                            ascending = !ascending
                            preferences.edit().putBoolean("songs_sort_ascending", ascending).apply()
                        },
                        leadingIcon = { Icon(if (ascending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward, contentDescription = null, tint = AccentColor) }
                    )
                    DropdownMenuItem(text = { Text("Grid columns", color = PrimaryText) }, onClick = { })
                    listOf(2, 3, 4, 5).forEach { count ->
                        DropdownMenuItem(
                            text = { Text((if (gridColumns == count) "✓ " else "") + "$count columns", color = if (gridColumns == count) AccentColor else PrimaryText) },
                            onClick = {
                                gridColumns = count
                                preferences.edit().putInt("songs_grid_columns", count).apply()
                            }
                        )
                    }
                }
            }
        }
        if (sortedSongs.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No songs found", color = MutedText)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(gridColumns),
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 14.dp, end = 14.dp, bottom = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(sortedSongs, key = { it.id }) { song ->
                    Column(modifier = Modifier.clickable { onSongSelected(song) }) {
                        AlbumArtwork(song = song, modifier = Modifier.fillMaxWidth().aspectRatio(1f))
                        Spacer(Modifier.height(7.dp))
                        Text(song.title, color = PrimaryText, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(song.artist, color = MutedText, fontSize = 10.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (showDuration) Text(formatTime(song.durationMs), color = MutedText, fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun FoldersContent(songs: List<Song>, onSongSelected: (Song) -> Unit, showPaths: Boolean) {
    var selectedFolder by remember { mutableStateOf<String?>(null) }
    val folders = songs.groupBy { it.folderPath }.toSortedMap()
    val folderSongs = selectedFolder?.let { folders[it].orEmpty() }.orEmpty()

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selectedFolder != null) {
                Text("‹", color = AccentColor, fontSize = 30.sp,
                    modifier = Modifier.clickable { selectedFolder = null }.padding(end = 12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(selectedFolder?.substringAfterLast('/') ?: "Music Folders",
                    color = PrimaryText, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                Text(
                    if (selectedFolder == null) "${folders.size} folders" else "${folderSongs.size} tracks",
                    color = MutedText, fontSize = 12.sp
                )
            }
        }
        if (selectedFolder == null) {
            if (folders.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No music folders found", color = MutedText)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    items(folders.keys.toList(), key = { it }) { path ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                                .clip(RoundedCornerShape(18.dp)).background(PanelColor)
                                .clickable { selectedFolder = path }.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Folder, contentDescription = null, tint = AccentColor,
                                modifier = Modifier.size(34.dp))
                            Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                                Text(path.substringAfterLast('/'), color = PrimaryText, fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold)
                                if (showPaths) Text(path, color = MutedText, fontSize = 10.sp, maxLines = 1,
                                    overflow = TextOverflow.Ellipsis)
                            }
                            Text("${folders[path]?.size ?: 0}", color = MutedText, fontSize = 12.sp)
                        }
                    }
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                items(folderSongs, key = { it.id }) { song ->
                    RecentSongCard(song = song, onClick = { onSongSelected(song) })
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
        if (showArtwork && song.artworkUri != null) {
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
            Text("Playlists are coming next", color = PrimaryText, fontSize = 18.sp)
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
            modifier = Modifier.fillMaxWidth()
                .pointerInput(Unit) {
                    var dragTotal = 0f
                    detectVerticalDragGestures(
                        onVerticalDrag = { change, amount ->
                            dragTotal += amount
                            change.consume()
                        },
                        onDragEnd = {
                            if (dragTotal < -45f) onOpen()
                            dragTotal = 0f
                        }
                    )
                }
                .clickable(onClick = onOpen)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AlbumArtwork(song, Modifier.size(54.dp))
            Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
                Text(song.title, color = PrimaryText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(song.artist, color = MutedText, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onPrevious) {
                Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", tint = PrimaryText)
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
                Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = PrimaryText)
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
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
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
        modifier = Modifier.fillMaxSize()
            .pointerInput(Unit) {
                var dragTotal = 0f
                detectVerticalDragGestures(
                    onVerticalDrag = { change, amount ->
                        dragTotal += amount
                        change.consume()
                    },
                    onDragEnd = {
                        if (dragTotal > 55f) onClose()
                        dragTotal = 0f
                    }
                )
            }
            .background(AppBackground).padding(horizontal = 22.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Close player", tint = PrimaryText)
            }
            Spacer(Modifier.weight(1f))
            Text("NOW PLAYING", color = MutedText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
        }
        Spacer(Modifier.weight(1f))
        if (showArtwork) AlbumArtwork(song, Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(24.dp)))
        Spacer(Modifier.height(30.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(song.title, color = PrimaryText, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(song.artist, color = MutedText, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                    tint = if (isFavorite) Color(0xFFFF6B81) else MutedText,
                    modifier = Modifier.size(27.dp)
                )
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
                Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", tint = PrimaryText, modifier = Modifier.size(34.dp))
            }
            IconButton(onClick = onTogglePlayback, modifier = Modifier.size(68.dp).clip(CircleShape).background(AccentColor)) {
                Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play", tint = AppBackground, modifier = Modifier.size(42.dp))
            }
            IconButton(onClick = onNext) {
                Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = PrimaryText, modifier = Modifier.size(34.dp))
            }
            IconButton(onClick = onCycleRepeat) {
                Icon(if (repeatMode == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                    contentDescription = "Repeat", tint = if (repeatMode == Player.REPEAT_MODE_OFF) MutedText else AccentColor)
            }
        }
        Spacer(Modifier.weight(1f))
        if (showAlbumName) Text(song.album.ifBlank { "Unknown album" }, color = MutedText, fontSize = 12.sp, maxLines = 1)
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
        NavItem("Folders", Icons.Default.Folder, selectedTab == "Folders") { onSelect("Folders") }
        NavItem("Playlists", Icons.Default.QueueMusic, selectedTab == "Playlists") { onSelect("Playlists") }
        NavItem("Settings", Icons.Default.Settings, selectedTab == "Settings") { onSelect("Settings") }
    }
}

@Composable
private fun NavItem(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier.clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = label, tint = if (selected) AccentColor else MutedText, modifier = Modifier.size(24.dp))
        Text(label, color = if (selected) PrimaryText else MutedText, fontSize = 10.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}


private fun applySavedSettings(preferences: android.content.SharedPreferences) {
    showArtwork = preferences.getBoolean("setting_show_artwork", true)
    showAlbumName = preferences.getBoolean("setting_show_album_name", true)
    showSongDuration = preferences.getBoolean("setting_show_song_duration", false)
    showFolderPaths = preferences.getBoolean("setting_show_folder_paths", true)
    blacklistedFolders = preferences.getStringSet("setting_blacklisted_folders", emptySet())?.toSet() ?: emptySet()
    val theme = preferences.getString("setting_theme", "Dark") ?: "Dark"
    val accent = preferences.getString("setting_accent", "Lavender") ?: "Lavender"
    applyTheme(theme)
    applyAccent(accent)
}

private fun applyTheme(theme: String) {
    when (theme) {
        "AMOLED" -> {
            AppBackground = Color.Black
            PanelColor = Color(0xFF111111)
            MutedText = Color(0xFFB0B0B0)
        }
        "Light" -> {
            AppBackground = Color(0xFFF5F2F8)
            PanelColor = Color(0xFFE8E1EF)
            MutedText = Color(0xFF625B6B)
            PrimaryText = Color(0xFF211B28)
        }
        else -> {
            AppBackground = Color(0xFF100E14)
            PanelColor = Color(0xFF211E29)
            MutedText = Color(0xFFB9B3C2)
            PrimaryText = Color.White
        }
    }
}

private fun applyAccent(accent: String) {
    AccentColor = when (accent) {
        "Blue" -> Color(0xFF80BFFF)
        "Mint" -> Color(0xFF80E0C0)
        "Rose" -> Color(0xFFFF9EBC)
        "Amber" -> Color(0xFFFFC66D)
        else -> Color(0xFFD0B2FF)
    }
}

@Composable
private fun SettingsContent(songs: List<Song>) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val preferences = remember { context.getSharedPreferences("herhyme_preferences", Context.MODE_PRIVATE) }
    var expandedSection by remember { mutableStateOf("Themes & Colors") }
    var theme by remember { mutableStateOf(preferences.getString("setting_theme", "Dark") ?: "Dark") }
    var accent by remember { mutableStateOf(preferences.getString("setting_accent", "Lavender") ?: "Lavender") }
    var artwork by remember { mutableStateOf(showArtwork) }
    var albumName by remember { mutableStateOf(showAlbumName) }
    var duration by remember { mutableStateOf(showSongDuration) }
    var folderPaths by remember { mutableStateOf(showFolderPaths) }
    var hiddenFolders by remember { mutableStateOf(blacklistedFolders) }

    fun saveBoolean(key: String, value: Boolean) {
        preferences.edit().putBoolean(key, value).apply()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(Modifier.padding(start = 4.dp, bottom = 4.dp)) {
                Text("Settings", color = PrimaryText, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text("Make Herhyme feel like yours", color = MutedText, fontSize = 13.sp)
            }
        }
        item {
            SettingsSection("Themes & Colors", "Appearance, theme and accent colors", expandedSection == "Themes & Colors", { expandedSection = if (expandedSection == "Themes & Colors") "" else "Themes & Colors" }) {
                SettingsChoiceRow("Theme", listOf("Dark", "AMOLED", "Light"), theme) { selected ->
                    theme = selected
                    preferences.edit().putString("setting_theme", selected).apply()
                    applyTheme(selected)
                }
                SettingsChoiceRow("Accent color", listOf("Lavender", "Blue", "Mint", "Rose", "Amber"), accent) { selected ->
                    accent = selected
                    preferences.edit().putString("setting_accent", selected).apply()
                    applyAccent(selected)
                }
            }
        }
        item {
            SettingsSection("Now Playing", "Customise the player screen", expandedSection == "Now Playing", { expandedSection = if (expandedSection == "Now Playing") "" else "Now Playing" }) {
                SettingsSwitchRow("Show album artwork", "Display cover art in the player", artwork) {
                    artwork = it
                    showArtwork = it
                    saveBoolean("setting_show_artwork", it)
                }
                SettingsSwitchRow("Show album name", "Display the album below playback controls", albumName) {
                    albumName = it
                    showAlbumName = it
                    saveBoolean("setting_show_album_name", it)
                }
            }
        }
        item {
            SettingsSection("Library", "Choose what appears in your music library", expandedSection == "Library", { expandedSection = if (expandedSection == "Library") "" else "Library" }) {
                SettingsSwitchRow("Show song duration", "Display track length in the Songs grid", duration) {
                    duration = it
                    showSongDuration = it
                    saveBoolean("setting_show_song_duration", it)
                }
                SettingsSwitchRow("Show folder paths", "Display the full location under folder names", folderPaths) {
                    folderPaths = it
                    showFolderPaths = it
                    saveBoolean("setting_show_folder_paths", it)
                }
                Spacer(Modifier.height(10.dp))
                Text("Blacklist folders", color = PrimaryText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text("Audio in selected folders will be hidden from the Songs page.", color = MutedText, fontSize = 11.sp)
                val availableFolders = songs.map { it.folderPath }.distinct().sorted()
                if (availableFolders.isEmpty()) {
                    Text("No music folders found yet.", color = MutedText, fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp))
                } else {
                    availableFolders.forEach { folder ->
                        SettingsSwitchRow(folder.substringAfterLast('/').ifBlank { folder }, folder, hiddenFolders.contains(folder)) { hide ->
                            hiddenFolders = if (hide) hiddenFolders + folder else hiddenFolders - folder
                            blacklistedFolders = hiddenFolders
                            preferences.edit().putStringSet("setting_blacklisted_folders", hiddenFolders).apply()
                        }
                    }
                }
            }
        }
        item {
            SettingsSection("Images", "Album art and artwork display", expandedSection == "Images", { expandedSection = if (expandedSection == "Images") "" else "Images" }) {
                SettingsSwitchRow("Display album artwork", "Use artwork supplied by your local music library", artwork) {
                    artwork = it
                    showArtwork = it
                    saveBoolean("setting_show_artwork", it)
                }
            }
        }
        item {
            SettingsSection("Audio", "Playback behaviour", expandedSection == "Audio", { expandedSection = if (expandedSection == "Audio") "" else "Audio" }) {
                Text("Herhyme currently uses Media3 gapless-capable playback. Additional audio processing controls will be added only when they can be connected to the playback engine.", color = MutedText, fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp))
            }
        }
        item {
            SettingsSection("Equalizer", "Sound enhancement", expandedSection == "Equalizer", { expandedSection = if (expandedSection == "Equalizer") "" else "Equalizer" }) {
                Text("Open your device's supported audio effects panel to adjust equalizer settings.", color = MutedText, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(PanelColor)
                        .clickable {
                            val intent = android.content.Intent(android.media.audiofx.AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
                                putExtra(android.media.audiofx.AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                                putExtra(android.media.audiofx.AudioEffect.EXTRA_CONTENT_TYPE, android.media.audiofx.AudioEffect.CONTENT_TYPE_MUSIC)
                            }
                            runCatching { context.startActivity(intent) }
                        }.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Open equalizer", color = AccentColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        item {
            SettingsSection("Playlists", "Playlist storage and transfer", expandedSection == "Playlists", { expandedSection = if (expandedSection == "Playlists") "" else "Playlists" }) {
                Text("Playlist creation, import and export need the playlist library to be implemented first. This section is reserved for those controls; no inactive actions are shown.", color = MutedText, fontSize = 12.sp)
            }
        }
        item {
            Text("Herhyme • Settings", color = MutedText, fontSize = 11.sp, modifier = Modifier.padding(vertical = 12.dp))
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    subtitle: String,
    expanded: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(PanelColor)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Text(title, color = PrimaryText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = MutedText, fontSize = 11.sp)
        }
        if (expanded) {
            Column(modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp)) { content() }
        }
    }
}

@Composable
private fun SettingsChoiceRow(
    label: String,
    choices: List<String>,
    selected: String,
    onSelected: (String) -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(label, color = PrimaryText, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            items(choices) { choice ->
                val active = choice == selected
                Box(
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(if (active) AccentColor else AppBackground)
                        .clickable { onSelected(choice) }.padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(choice, color = if (active) AppBackground else MutedText, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, color = PrimaryText, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(description, color = MutedText, fontSize = 11.sp)
        }
        androidx.compose.material3.Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
