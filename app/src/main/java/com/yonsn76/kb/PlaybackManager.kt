package com.yonsn76.kb

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.widget.Toast
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

    // Evita bucles infinitos si varias canciones seguidas fallan al reproducir
    private var consecutiveErrors = 0

    // Shuffle real (estilo baraja): cola de indices barajados que se recorre
    // entera sin repetir; al terminar se vuelve a barajar.
    private val shuffleQueue = mutableListOf<Int>()
    private var shufflePos = -1

    // Callback para actualizar la UI en MainActivity
    var onStateChanged: (() -> Unit)? = null

    fun playSong(context: Context, index: Int) {
        if (index < 0 || index >= filteredSongs.size) return

        mediaPlayer?.release()
        mediaPlayer = null
        currentIndex = index
        val song = filteredSongs[index]

        try {
            mediaPlayer = MediaPlayer().apply {
                setOnErrorListener { _, _, _ ->
                    handlePlaybackError(context, song)
                    true
                }
                setDataSource(context, song.uri)
                prepare()
                start()
                setOnCompletionListener { playNext(context) }
            }
            isPlaying = true
            consecutiveErrors = 0
            onStateChanged?.invoke()
            updateService(context)
        } catch (e: Exception) {
            handlePlaybackError(context, song)
        }
    }

    private fun handlePlaybackError(context: Context, song: Song) {
        mediaPlayer?.release()
        mediaPlayer = null
        isPlaying = false
        Toast.makeText(context, "No se pudo reproducir: ${song.title}", Toast.LENGTH_SHORT).show()
        onStateChanged?.invoke()
        // Salta a la siguiente, pero solo si no estan fallando todas
        consecutiveErrors++
        if (consecutiveErrors < filteredSongs.size) {
            playNext(context)
        } else {
            consecutiveErrors = 0
            currentIndex = -1
            onStateChanged?.invoke()
        }
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

    // Crea una nueva baraja con todos los indices. Si avoidFirst es valido,
    // garantiza que la primera de la baraja no sea esa cancion (para no repetir
    // al encadenar barajas).
    private fun buildShuffleQueue(avoidFirst: Int = -1) {
        shuffleQueue.clear()
        shuffleQueue.addAll(filteredSongs.indices.shuffled())
        if (avoidFirst != -1 && shuffleQueue.size > 1 && shuffleQueue.first() == avoidFirst) {
            // intercambia el primero con otro al azar para evitar repetir
            val swap = (1 until shuffleQueue.size).random()
            val tmp = shuffleQueue[0]
            shuffleQueue[0] = shuffleQueue[swap]
            shuffleQueue[swap] = tmp
        }
        shufflePos = -1
    }

    // Asegura que la baraja sea coherente con la lista actual. Si la cola esta
    // vacia o no coincide con el tamano (p.ej. cambio el filtro), la rehace y
    // posiciona el cursor en la cancion actual.
    private fun ensureShuffleQueue() {
        if (shuffleQueue.size != filteredSongs.size || shuffleQueue.isEmpty()) {
            buildShuffleQueue()
            shufflePos = shuffleQueue.indexOf(currentIndex)
        }
    }

    fun playNext(context: Context) {
        if (filteredSongs.isEmpty()) return
        val next: Int = if (isShuffle) {
            ensureShuffleQueue()
            shufflePos++
            if (shufflePos >= shuffleQueue.size) {
                // Recorrimos toda la baraja: rebarajar evitando repetir la ultima
                buildShuffleQueue(avoidFirst = currentIndex)
                shufflePos = 0
            }
            shuffleQueue[shufflePos]
        } else {
            if (currentIndex + 1 >= filteredSongs.size) 0 else currentIndex + 1
        }
        playSong(context, next)
    }

    fun playPrev(context: Context) {
        if (filteredSongs.isEmpty()) return
        // Si ya avanzo mas de 3s en la cancion, "atras" solo reinicia la actual
        mediaPlayer?.let {
            if (it.currentPosition > 3000) {
                it.seekTo(0)
                onStateChanged?.invoke()
                updateService(context)
                return
            }
        }
        val prev: Int = if (isShuffle) {
            ensureShuffleQueue()
            // Retrocede en la misma baraja; al inicio se queda en la primera
            if (shufflePos > 0) shufflePos-- else shufflePos = 0
            shuffleQueue[shufflePos]
        } else {
            if (currentIndex - 1 < 0) filteredSongs.size - 1 else currentIndex - 1
        }
        playSong(context, prev)
    }

    fun toggleShuffle(context: Context) {
        isShuffle = !isShuffle
        if (isShuffle) {
            // Nueva baraja que arranca en la cancion actual
            buildShuffleQueue()
            val pos = shuffleQueue.indexOf(currentIndex)
            if (pos > 0) {
                // mueve la actual al frente para no saltar de cancion al activar
                shuffleQueue.removeAt(pos)
                shuffleQueue.add(0, currentIndex)
            }
            shufflePos = if (currentIndex != -1) 0 else -1
        } else {
            shuffleQueue.clear()
            shufflePos = -1
        }
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
