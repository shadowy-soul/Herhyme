package com.herhyme.player

import android.net.Uri

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val uri: Uri,
    val durationMs: Long,
    val artworkUri: Uri?
)
