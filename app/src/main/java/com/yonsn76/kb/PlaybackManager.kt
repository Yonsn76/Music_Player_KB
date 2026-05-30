package com.yonsn76.kb

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object PlaybackManager {
    val allSongs = mutableListOf<Song>()
    val filteredSongs = mutableListOf<Song>()
    var mediaPlayer: MediaPlayer? = null
    var currentIndex = -1
    var isPlaying = false
    var isShuffle = false
    val folderUris = mutableListOf<Uri>()

    private const val SONGS_FILE = "songs_cache.json"

    // Callback para actualizar la UI en MainActivity
    var onStateChanged: (() -> Unit)? = null

    fun playSong(context: Context, index: Int) {
        if (index < 0 || index >= filteredSongs.size) return

        mediaPlayer?.release()
        currentIndex = index
        val song = filteredSongs[index]

        mediaPlayer = MediaPlayer().apply {
            setDataSource(context, song.uri)
            prepare()
            start()
            setOnCompletionListener { playNext(context) }
        }

        isPlaying = true
        onStateChanged?.invoke()
        updateService(context)
    }

    fun togglePlayPause(context: Context) {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                isPlaying = false
            } else {
                it.start()
                isPlaying = true
            }
            onStateChanged?.invoke()
            updateService(context)
        }
    }

    fun playNext(context: Context) {
        if (filteredSongs.isEmpty()) return
        val next = if (isShuffle) {
            var r: Int
            do { r = (filteredSongs.indices).random() } while (r == currentIndex && filteredSongs.size > 1)
            r
        } else {
            if (currentIndex + 1 >= filteredSongs.size) 0 else currentIndex + 1
        }
        playSong(context, next)
    }

    fun playPrev(context: Context) {
        if (filteredSongs.isEmpty()) return
        mediaPlayer?.let {
            if (it.currentPosition > 3000) {
                it.seekTo(0)
                onStateChanged?.invoke()
                updateService(context)
                return
            }
        }
        val prev = if (isShuffle) {
            (filteredSongs.indices).random()
        } else {
            if (currentIndex - 1 < 0) filteredSongs.size - 1 else currentIndex - 1
        }
        playSong(context, prev)
    }

    fun toggleShuffle(context: Context) {
        isShuffle = !isShuffle
        onStateChanged?.invoke()
        updateService(context)
    }

    private fun updateService(context: Context) {
        if (currentIndex == -1) return
        val song = filteredSongs[currentIndex]
        val serviceIntent = Intent(context, MusicService::class.java).apply {
            putExtra("title", song.title)
            putExtra("artist", song.artist)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }

    fun saveSongs(context: Context) {
        try {
            val jsonArray = JSONArray()
            allSongs.forEach { song ->
                val songObj = JSONObject().apply {
                    put("id", song.id)
                    put("title", song.title)
                    put("artist", song.artist)
                    put("duration", song.duration)
                    put("uri", song.uri.toString())
                    put("folderUri", song.folderUri.toString())
                }
                jsonArray.put(songObj)
            }
            File(context.filesDir, SONGS_FILE).writeText(jsonArray.toString())
        } catch (_: Exception) {}
    }

    fun loadSongs(context: Context): Boolean {
        return try {
            val file = File(context.filesDir, SONGS_FILE)
            if (!file.exists()) return false
            val jsonArray = JSONArray(file.readText())
            allSongs.clear()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                allSongs.add(Song(
                    obj.getLong("id"),
                    obj.getString("title"),
                    obj.getString("artist"),
                    obj.getLong("duration"),
                    Uri.parse(obj.getString("uri")),
                    Uri.parse(obj.getString("folderUri"))
                ))
            }
            true
        } catch (_: Exception) { false }
    }

    fun release() {
        mediaPlayer?.release()
        mediaPlayer = null
    }
}