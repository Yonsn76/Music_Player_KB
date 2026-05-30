package com.yonsn76.kb

import android.Manifest
import android.content.ContentUris
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.widget.addTextChangedListener

class MainActivity : AppCompatActivity() {

    private var isSearchOpen = false

    private lateinit var lvSongs: ListView
    private lateinit var tvEmpty: TextView
    private lateinit var tvSongCount: TextView
    private lateinit var playerBar: LinearLayout
    private lateinit var layoutTitle: LinearLayout
    private lateinit var layoutSearch: LinearLayout
    private lateinit var etSearch: EditText
    private lateinit var btnSearch: ImageButton
    private lateinit var tvNowTitle: TextView
    private lateinit var tvNowArtist: TextView
    private lateinit var tvCurrentTime: TextView
    private lateinit var tvTotalTime: TextView
    private lateinit var btnPlay: ImageButton
    private lateinit var btnShuffle: ImageButton
    private lateinit var btnShufflePlayer: ImageButton
    private lateinit var seekBar: SeekBar
    private lateinit var adapter: SongsAdapter

    private val handler = Handler(Looper.getMainLooper())
    private val updateSeekBar = object : Runnable {
        override fun run() {
            PlaybackManager.mediaPlayer?.let {
                if (it.isPlaying) {
                    seekBar.progress = it.currentPosition
                    tvCurrentTime.text = formatTime(it.currentPosition)
                    handler.postDelayed(this, 500)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_Kb)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        lvSongs = findViewById(R.id.lvSongs)
        tvEmpty = findViewById(R.id.tvEmpty)
        tvSongCount = findViewById(R.id.tvSongCount)
        playerBar = findViewById(R.id.playerBar)
        layoutTitle = findViewById(R.id.layoutTitle)
        layoutSearch = findViewById(R.id.layoutSearch)
        etSearch = findViewById(R.id.etSearch)
        btnSearch = findViewById(R.id.btnSearch)
        tvNowTitle = findViewById(R.id.tvNowTitle)
        tvNowArtist = findViewById(R.id.tvNowArtist)
        tvCurrentTime = findViewById(R.id.tvCurrentTime)
        tvTotalTime = findViewById(R.id.tvTotalTime)
        btnPlay = findViewById(R.id.btnPlay)
        btnShuffle = findViewById(R.id.btnShuffle)
        btnShufflePlayer = findViewById(R.id.btnShufflePlayer)
        seekBar = findViewById(R.id.seekBar)

        val btnPrev = findViewById<ImageButton>(R.id.btnPrev)
        val btnNext = findViewById<ImageButton>(R.id.btnNext)

        lvSongs.setOnItemClickListener { _, _, position, _ ->
            playSong(position)
        }

        btnPlay.setOnClickListener { togglePlayPause() }
        btnPrev.setOnClickListener { playPrev() }
        btnNext.setOnClickListener { playNext() }

        val shuffleClick = View.OnClickListener { toggleShuffle() }
        btnShuffle.setOnClickListener(shuffleClick)
        btnShufflePlayer.setOnClickListener(shuffleClick)

        btnSearch.setOnClickListener { toggleSearch() }
        etSearch.addTextChangedListener { text ->
            filterSongs(text.toString())
        }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    PlaybackManager.mediaPlayer?.seekTo(progress)
                    tvCurrentTime.text = formatTime(progress)
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        checkPermissionAndLoad()
    }

    private fun toggleShuffle() {
        PlaybackManager.isShuffle = !PlaybackManager.isShuffle
        val icon = if (PlaybackManager.isShuffle) R.drawable.ic_shuffle_on else R.drawable.ic_shuffle
        btnShuffle.setImageResource(icon)
        btnShufflePlayer.setImageResource(icon)
        val msg = if (PlaybackManager.isShuffle) R.string.shuffle_on else R.string.shuffle_off
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    private fun toggleSearch() {
        isSearchOpen = !isSearchOpen
        if (isSearchOpen) {
            layoutTitle.visibility = View.GONE
            layoutSearch.visibility = View.VISIBLE
            btnSearch.setImageResource(R.drawable.ic_close)
            etSearch.requestFocus()
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(etSearch, InputMethodManager.SHOW_IMPLICIT)
        } else {
            layoutTitle.visibility = View.VISIBLE
            layoutSearch.visibility = View.GONE
            btnSearch.setImageResource(R.drawable.ic_search)
            etSearch.text.clear()
            filterSongs("")
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(etSearch.windowToken, 0)
        }
    }

    private fun filterSongs(query: String) {
        PlaybackManager.filteredSongs.clear()
        if (query.isEmpty()) {
            PlaybackManager.filteredSongs.addAll(PlaybackManager.allSongs)
        } else {
            val lowerQuery = query.lowercase()
            PlaybackManager.allSongs.filterTo(PlaybackManager.filteredSongs) {
                it.title.lowercase().contains(lowerQuery) || it.artist.lowercase().contains(lowerQuery)
            }
        }

        if (PlaybackManager.filteredSongs.isEmpty()) {
            tvEmpty.visibility = View.VISIBLE
            tvEmpty.setText(if (query.isEmpty()) R.string.no_songs else R.string.no_results)
            lvSongs.visibility = View.GONE
            tvSongCount.visibility = View.GONE
        } else {
            tvEmpty.visibility = View.GONE
            lvSongs.visibility = View.VISIBLE
            if (!::adapter.isInitialized) {
                adapter = SongsAdapter(this, PlaybackManager.filteredSongs)
                lvSongs.adapter = adapter
            } else {
                adapter.notifyDataSetChanged()
            }
            tvSongCount.text = getString(R.string.songs_count, PlaybackManager.filteredSongs.size)
            tvSongCount.visibility = View.VISIBLE
        }
    }

    private fun checkPermissionAndLoad() {
        val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.READ_MEDIA_AUDIO
        else
            Manifest.permission.READ_EXTERNAL_STORAGE

        if (ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED) {
            loadSongs()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(perm), 1)
        }
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        if (code == 1 && results.isNotEmpty() && results[0] == PackageManager.PERMISSION_GRANTED) {
            loadSongs()
        } else {
            Toast.makeText(this, R.string.permission_needed, Toast.LENGTH_LONG).show()
            tvEmpty.visibility = View.VISIBLE
            tvEmpty.setText(R.string.permission_needed)
        }
    }

    private fun loadSongs() {
        PlaybackManager.allSongs.clear()

        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.IS_MUSIC
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        contentResolver.query(uri, projection, selection, null, sortOrder)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val title = cursor.getString(titleCol)
                val artist = cursor.getString(artistCol) ?: "Desconocido"
                val duration = cursor.getLong(durationCol)
                val contentUri = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id
                )
                PlaybackManager.allSongs.add(Song(id, title, artist, duration, contentUri))
            }
        }
        filterSongs("")
    }

    private fun playSong(index: Int) {
        if (index < 0 || index >= PlaybackManager.filteredSongs.size) return

        PlaybackManager.mediaPlayer?.release()
        PlaybackManager.currentIndex = index
        val song = PlaybackManager.filteredSongs[index]

        PlaybackManager.mediaPlayer = MediaPlayer().apply {
            setDataSource(this@MainActivity, song.uri)
            prepare()
            start()
            setOnCompletionListener { playNext() }
        }

        PlaybackManager.isPlaying = true
        playerBar.visibility = View.VISIBLE
        tvNowTitle.text = song.title
        tvNowArtist.text = song.artist
        btnPlay.setImageResource(R.drawable.ic_pause)

        // Reset search if open
        if (isSearchOpen) toggleSearch()

        if (::adapter.isInitialized) {
            adapter.currentPlayingIndex = index
            adapter.notifyDataSetChanged()
        }

        val duration = PlaybackManager.mediaPlayer?.duration ?: 0
        seekBar.max = duration
        tvTotalTime.text = formatTime(duration)
        tvCurrentTime.text = getString(R.string.initial_time)
        handler.post(updateSeekBar)
    }

    private fun togglePlayPause() {
        PlaybackManager.mediaPlayer?.let { mp ->
            if (mp.isPlaying) {
                mp.pause()
                PlaybackManager.isPlaying = false
                btnPlay.setImageResource(R.drawable.ic_play)
            } else {
                mp.start()
                PlaybackManager.isPlaying = true
                btnPlay.setImageResource(R.drawable.ic_pause)
                handler.post(updateSeekBar)
            }
        }
    }

    private fun playNext() {
        if (PlaybackManager.filteredSongs.isEmpty()) return
        val next = if (PlaybackManager.isShuffle) {
            var r: Int
            do { r = (PlaybackManager.filteredSongs.indices).random() } while (r == PlaybackManager.currentIndex && PlaybackManager.filteredSongs.size > 1)
            r
        } else {
            if (PlaybackManager.currentIndex + 1 >= PlaybackManager.filteredSongs.size) 0 else PlaybackManager.currentIndex + 1
        }
        playSong(next)
    }

    private fun playPrev() {
        if (PlaybackManager.filteredSongs.isEmpty()) return
        PlaybackManager.mediaPlayer?.let {
            if (it.currentPosition > 3000) {
                it.seekTo(0)
                seekBar.progress = 0
                tvCurrentTime.text = getString(R.string.initial_time)
                return
            }
        }
        val prev = if (PlaybackManager.isShuffle) {
            (PlaybackManager.filteredSongs.indices).random()
        } else {
            if (PlaybackManager.currentIndex - 1 < 0) PlaybackManager.filteredSongs.size - 1 else PlaybackManager.currentIndex - 1
        }
        playSong(prev)
    }

    private fun formatTime(ms: Int): String {
        val totalSec = ms / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        return "%d:%02d".format(min, sec)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(updateSeekBar)
        // Only release if the activity is finishing and not just recreating
        if (isFinishing) {
            PlaybackManager.release()
        }
    }
}
