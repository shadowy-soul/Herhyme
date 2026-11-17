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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors

class MainActivity : ComponentActivity() {
    private var songs by mutableStateOf<List<Song>>(emptyList())
    private var controller: MediaController? = null
    private var currentSong by mutableStateOf<Song?>(null)
    private var isPlaying by mutableStateOf(false)

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(playing: Boolean) {
            isPlaying = playing
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
                onTogglePlayback = { togglePlayback() }
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
        }, MoreExecutors.directExecutor())
    }

    private fun playSong(song: Song) {
        val mediaController = controller ?: return
        currentSong = song
        mediaController.setMediaItem(
            MediaItem.Builder().setUri(song.uri).setMediaId(song.id.toString()).build()
        )
        mediaController.prepare()
        mediaController.play()
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
    onTogglePlayback: () -> Unit
) {
    val background = Color(0xFF101014)
    val surface = Color(0xFF1B1B22)
    val accent = Color(0xFFC7A6FF)

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = background) {
            Column(modifier = Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 28.dp)) {
                Text("HERHYME", color = Color.White, fontSize = 13.sp, letterSpacing = 4.sp)
                Text(
                    "Your music,\\nyour space.",
                    color = Color.White,
                    fontSize = 32.sp,
                    lineHeight = 38.sp,
                    modifier = Modifier.padding(top = 18.dp, bottom = 24.dp)
                )
                Text("${songs.size} SONGS", color = accent, fontSize = 11.sp, letterSpacing = 2.sp)
                Spacer(modifier = Modifier.padding(top = 12.dp))
                if (songs.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("♪", color = accent, fontSize = 42.sp)
                        Text("Your library is waiting", color = Color.White, fontSize = 18.sp)
                        Text(
                            "Add audio files to your phone, then reopen Herhyme.",
                            color = Color.LightGray,
                            fontSize = 13.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(songs, key = { it.id }) { song ->
                            Row(
                                modifier = Modifier.fillMaxWidth()
                                    .background(surface, RoundedCornerShape(14.dp))
                                    .clickable { onSongSelected(song) }
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("♪", color = accent, fontSize = 22.sp)
                                Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                                    Text(song.title, color = Color.White, maxLines = 1)
                                    Text(song.artist, color = Color.LightGray, fontSize = 12.sp, maxLines = 1)
                                }
                            }
                        }
                    }
                }

                if (currentSong != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .background(surface, RoundedCornerShape(14.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                if (isPlaying) "NOW PLAYING" else "PAUSED",
                                color = accent,
                                fontSize = 10.sp,
                                letterSpacing = 1.5.sp
                            )
                            Text(currentSong.title, color = Color.White, maxLines = 1)
                            Text(currentSong.artist, color = Color.LightGray, fontSize = 12.sp, maxLines = 1)
                        }
                        Button(onClick = onTogglePlayback) {
                            Text(if (isPlaying) "Pause" else "Resume")
                        }
                    }
                }

                Text(
                    "HERHYME  •  OFFLINE PLAYER",
                    color = Color.Gray,
                    fontSize = 10.sp,
                    letterSpacing = 1.5.sp,
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 14.dp)
                )
            }
        }
    }
}
