package com.herhyme.player

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore

class MusicRepository(private val context: Context) {
    fun loadSongs(): List<Song> {
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val songs = mutableListOf<Song>()

        context.contentResolver.query(
            collection,
            projection,
            selection,
            null,
            "${MediaStore.Audio.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val albumId = cursor.getLong(albumIdCol)
                songs += Song(
                    id = id,
                    title = cursor.getString(titleCol) ?: "Unknown title",
                    artist = cursor.getString(artistCol) ?: "Unknown artist",
                    album = cursor.getString(albumCol) ?: "Unknown album",
                    uri = ContentUris.withAppendedId(collection, id),
                    durationMs = cursor.getLong(durationCol),
                    artworkUri = if (albumId > 0) {
                        Uri.parse("content://media/external/audio/albumart/$albumId")
                    } else null,
                    folderPath = cursor.getString(dataCol)?.let { java.io.File(it).parentFile?.absolutePath }
                        ?: "Unknown folder"
                )
            }
        }
        return songs
    }
}
