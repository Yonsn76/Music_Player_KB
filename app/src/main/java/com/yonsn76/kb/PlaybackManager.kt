package com.yonsn76.kb

import android.media.MediaPlayer

object PlaybackManager {
    val allSongs = mutableListOf<Song>()
    val filteredSongs = mutableListOf<Song>()
    var mediaPlayer: MediaPlayer? = null
    var currentIndex = -1
    var isPlaying = false
    var isShuffle = false
    
    fun release() {
        mediaPlayer?.release()
        mediaPlayer = null
    }
}