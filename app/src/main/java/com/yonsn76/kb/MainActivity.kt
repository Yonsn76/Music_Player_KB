package com.yonsn76.kb

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.widget.addTextChangedListener
import android.media.MediaMetadataRetriever

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
    private lateinit var btnFolder: ImageButton
    private lateinit var seekBar: SeekBar
    private lateinit var pbLoading: ProgressBar
    private lateinit var adapter: SongsAdapter

    private val selectFolderLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (!PlaybackManager.folderUris.contains(it)) {
                PlaybackManager.folderUris.add(it)
                saveFolders()
                loadFolderIncremental(it)
                PlaybackManager.saveSongs(this)
            }
        }
    }

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
        btnFolder = findViewById(R.id.btnFolder)
        seekBar = findViewById(R.id.seekBar)
        pbLoading = findViewById(R.id.pbLoading)

        val btnPrev = findViewById<ImageButton>(R.id.btnPrev)
        val btnNext = findViewById<ImageButton>(R.id.btnNext)

        lvSongs.setOnItemClickListener { _, _, position, _ -> PlaybackManager.playSong(this, position) }
        btnPlay.setOnClickListener { PlaybackManager.togglePlayPause(this) }
        btnPrev.setOnClickListener { PlaybackManager.playPrev(this) }
        btnNext.setOnClickListener { PlaybackManager.playNext(this) }
        
        val shuffleClick = View.OnClickListener { PlaybackManager.toggleShuffle(this) }
        btnShuffle.setOnClickListener(shuffleClick)
        btnShufflePlayer.setOnClickListener(shuffleClick)

        btnFolder.setOnClickListener { showFoldersDialog() }
        btnSearch.setOnClickListener { toggleSearch() }
        etSearch.addTextChangedListener { text -> filterSongs(text.toString()) }

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

        PlaybackManager.onStateChanged = { updateUI() }
        loadFolders()
        checkPermissionAndLoad()
        updateUI()
    }

    private fun updateUI() {
        if (PlaybackManager.currentIndex != -1) {
            val song = PlaybackManager.filteredSongs[PlaybackManager.currentIndex]
            playerBar.visibility = View.VISIBLE
            tvNowTitle.text = song.title
            tvNowArtist.text = song.artist
            btnPlay.setImageResource(if (PlaybackManager.isPlaying) R.drawable.ic_pause else R.drawable.ic_play)
            
            val icon = if (PlaybackManager.isShuffle) R.drawable.ic_shuffle_on else R.drawable.ic_shuffle
            btnShuffle.setImageResource(icon)
            btnShufflePlayer.setImageResource(icon)

            if (::adapter.isInitialized) {
                adapter.currentPlayingIndex = PlaybackManager.currentIndex
                adapter.notifyDataSetChanged()
            }

            val duration = PlaybackManager.mediaPlayer?.duration ?: 0
            seekBar.max = duration
            tvTotalTime.text = formatTime(duration)
            handler.post(updateSeekBar)
        }
    }

    private fun saveFolders() {
        val prefs = getSharedPreferences("KbPrefs", MODE_PRIVATE)
        val uriStrings = PlaybackManager.folderUris.map { it.toString() }.toSet()
        prefs.edit { putStringSet("imported_folders", uriStrings) }
    }

    private fun loadFolders() {
        val prefs = getSharedPreferences("KbPrefs", MODE_PRIVATE)
        val uriStrings = prefs.getStringSet("imported_folders", null)
        uriStrings?.forEach {
            try {
                val uri = Uri.parse(it)
                if (!PlaybackManager.folderUris.contains(uri)) PlaybackManager.folderUris.add(uri)
            } catch (_: Exception) {}
        }
        if (!PlaybackManager.loadSongs(this) && PlaybackManager.folderUris.isNotEmpty()) {
            loadAllSongs()
        } else {
            filterSongs("")
        }
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

    private fun scanDocument(uri: Uri, sourceFolderUri: Uri) {
        val treeId = DocumentsContract.getTreeDocumentId(uri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(uri, treeId)
        contentResolver.query(childrenUri, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val typeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            val nameCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            while (cursor.moveToNext()) {
                val docId = cursor.getString(idCol)
                val mime = cursor.getString(typeCol)
                val name = cursor.getString(nameCol)
                val fileUri = DocumentsContract.buildDocumentUriUsingTree(uri, docId)
                if (DocumentsContract.Document.MIME_TYPE_DIR == mime) scanDocumentRecursive(uri, docId, sourceFolderUri)
                else if (mime.startsWith("audio/")) addSongFromUri(fileUri, name, sourceFolderUri)
            }
        }
    }

    private fun scanDocumentRecursive(parentUri: Uri, docId: String, sourceFolderUri: Uri) {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(parentUri, docId)
        contentResolver.query(childrenUri, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val typeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            val nameCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            while (cursor.moveToNext()) {
                val nextDocId = cursor.getString(idCol)
                val mime = cursor.getString(typeCol)
                val name = cursor.getString(nameCol)
                val fileUri = DocumentsContract.buildDocumentUriUsingTree(parentUri, nextDocId)
                if (DocumentsContract.Document.MIME_TYPE_DIR == mime) scanDocumentRecursive(parentUri, nextDocId, sourceFolderUri)
                else if (mime.startsWith("audio/")) addSongFromUri(fileUri, name, sourceFolderUri)
            }
        }
    }

    private fun addSongFromUri(uri: Uri, fileName: String, sourceFolderUri: Uri) {
        var title = fileName
        var artist = "Carpeta Local"
        var duration = 0L
        val mmr = MediaMetadataRetriever()
        try {
            mmr.setDataSource(this, uri)
            title = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: fileName
            artist = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: "Desconocido"
            duration = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L
        } catch (_: Exception) {} finally { mmr.release() }
        PlaybackManager.allSongs.add(Song(System.currentTimeMillis() + PlaybackManager.allSongs.size, title, artist, duration, uri, sourceFolderUri))
    }

    private fun showFoldersDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_folders, null)
        val lvFolders = dialogView.findViewById<ListView>(R.id.lvFolders)
        val btnAdd = dialogView.findViewById<ImageButton>(R.id.btnAddFolder)
        val tvNoFolders = dialogView.findViewById<TextView>(R.id.tvNoFolders)
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this).setView(dialogView).create()
        lvFolders.adapter = FoldersAdapter(this, PlaybackManager.folderUris, 
            onRefresh = { uri -> refreshFolder(uri) },
            onRemove = { uri ->
                PlaybackManager.folderUris.remove(uri)
                saveFolders()
                PlaybackManager.allSongs.removeAll { it.folderUri == uri }
                PlaybackManager.saveSongs(this)
                filterSongs("")
                if (PlaybackManager.folderUris.isEmpty()) tvNoFolders.visibility = View.VISIBLE
                (lvFolders.adapter as FoldersAdapter).notifyDataSetChanged()
            }
        )
        tvNoFolders.visibility = if (PlaybackManager.folderUris.isEmpty()) View.VISIBLE else View.GONE
        btnAdd.setOnClickListener { dialog.dismiss(); selectFolderLauncher.launch(null) }
        dialog.show()
    }

    private fun loadAllSongs() {
        pbLoading.visibility = View.VISIBLE
        Thread {
            PlaybackManager.allSongs.clear()
            PlaybackManager.folderUris.forEach { scanDocument(it, it) }
            runOnUiThread { pbLoading.visibility = View.GONE; filterSongs(""); PlaybackManager.saveSongs(this) }
        }.start()
    }

    private fun loadFolderIncremental(folderUri: Uri) {
        pbLoading.visibility = View.VISIBLE
        Thread {
            scanDocument(folderUri, folderUri)
            runOnUiThread { pbLoading.visibility = View.GONE; filterSongs(""); PlaybackManager.saveSongs(this) }
        }.start()
    }

    private fun refreshFolder(folderUri: Uri) {
        pbLoading.visibility = View.VISIBLE
        Thread {
            PlaybackManager.allSongs.removeAll { it.folderUri == folderUri }
            scanDocument(folderUri, folderUri)
            runOnUiThread { pbLoading.visibility = View.GONE; filterSongs(""); PlaybackManager.saveSongs(this) }
        }.start()
    }

    private fun filterSongs(query: String) {
        PlaybackManager.filteredSongs.clear()
        if (query.isEmpty()) PlaybackManager.filteredSongs.addAll(PlaybackManager.allSongs)
        else {
            val lowerQuery = query.lowercase()
            PlaybackManager.allSongs.filterTo(PlaybackManager.filteredSongs) { it.title.lowercase().contains(lowerQuery) || it.artist.lowercase().contains(lowerQuery) }
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
            } else adapter.notifyDataSetChanged()
            tvSongCount.text = getString(R.string.songs_count, PlaybackManager.filteredSongs.size)
            tvSongCount.visibility = View.VISIBLE
        }
    }

    private fun checkPermissionAndLoad() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        val toRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (toRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, toRequest.toTypedArray(), 1)
        }
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, perms, results)
        if (code == 1 && results.isNotEmpty() && results[0] == PackageManager.PERMISSION_GRANTED && PlaybackManager.allSongs.isEmpty()) loadAllSongs()
    }

    private fun formatTime(ms: Int): String {
        val totalSec = ms / 1000
        return "%d:%02d".format(totalSec / 60, totalSec % 60)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(updateSeekBar)
        if (isFinishing) {
            stopService(Intent(this, MusicService::class.java))
            PlaybackManager.release()
        }
    }
}