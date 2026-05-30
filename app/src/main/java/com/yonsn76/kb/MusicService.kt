package com.yonsn76.kb

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat

class MusicService : Service() {

    private var mediaSession: MediaSessionCompat? = null

    companion object {
        const val CHANNEL_ID = "MusicChannel"
        const val NOTIFICATION_ID = 1
        const val ACTION_PREV = "com.yonsn76.kb.PREV"
        const val ACTION_PLAY = "com.yonsn76.kb.PLAY"
        const val ACTION_NEXT = "com.yonsn76.kb.NEXT"
        const val ACTION_SHUFFLE = "com.yonsn76.kb.SHUFFLE"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        mediaSession = MediaSessionCompat(this, "KbMusicSession").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() { PlaybackManager.togglePlayPause(this@MusicService) }
                override fun onPause() { PlaybackManager.togglePlayPause(this@MusicService) }
                override fun onSkipToNext() { PlaybackManager.playNext(this@MusicService) }
                override fun onSkipToPrevious() { PlaybackManager.playPrev(this@MusicService) }
                override fun onSeekTo(pos: Long) { 
                    PlaybackManager.mediaPlayer?.seekTo(pos.toInt())
                    updatePlaybackState()
                }
            })
            isActive = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PREV -> PlaybackManager.playPrev(this)
            ACTION_PLAY -> PlaybackManager.togglePlayPause(this)
            ACTION_NEXT -> PlaybackManager.playNext(this)
            ACTION_SHUFFLE -> PlaybackManager.toggleShuffle(this)
        }

        updateMetadata()
        updatePlaybackState()

        val title = intent?.getStringExtra("title") ?: if (PlaybackManager.currentIndex != -1) PlaybackManager.filteredSongs[PlaybackManager.currentIndex].title else "Kb Music"
        val artist = intent?.getStringExtra("artist") ?: if (PlaybackManager.currentIndex != -1) PlaybackManager.filteredSongs[PlaybackManager.currentIndex].artist else ""

        val notification = createNotification(title, artist)
        startForeground(NOTIFICATION_ID, notification)

        return START_NOT_STICKY
    }

    private fun updateMetadata() {
        if (PlaybackManager.currentIndex == -1) return
        val song = PlaybackManager.filteredSongs[PlaybackManager.currentIndex]
        mediaSession?.setMetadata(MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, song.title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, song.artist)
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, song.duration)
            .build())
    }

    private fun updatePlaybackState() {
        val state = if (PlaybackManager.isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        val position = PlaybackManager.mediaPlayer?.currentPosition?.toLong() ?: 0L
        
        val actions = PlaybackStateCompat.ACTION_PLAY or 
                     PlaybackStateCompat.ACTION_PAUSE or
                     PlaybackStateCompat.ACTION_SKIP_TO_NEXT or 
                     PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                     PlaybackStateCompat.ACTION_SEEK_TO
        
        val playbackState = PlaybackStateCompat.Builder()
            .setActions(actions)
            .setState(state, position, 1.0f)
            .build()
        
        mediaSession?.setPlaybackState(playbackState)
    }

    private fun createNotification(title: String, artist: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val playIcon = if (PlaybackManager.isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        val shuffleIcon = if (PlaybackManager.isShuffle) R.drawable.ic_shuffle_on else R.drawable.ic_shuffle

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(artist)
            .setSmallIcon(R.drawable.ic_music)
            .setContentIntent(openIntent)
            .setOngoing(PlaybackManager.isPlaying)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setStyle(androidx.media.app.NotificationCompat.MediaStyle()
                .setMediaSession(mediaSession?.sessionToken)
                .setShowActionsInCompactView(1, 2, 3))
            .addAction(shuffleIcon, "Shuffle", getActionIntent(ACTION_SHUFFLE))
            .addAction(R.drawable.ic_prev, "Prev", getActionIntent(ACTION_PREV))
            .addAction(playIcon, "Play", getActionIntent(ACTION_PLAY))
            .addAction(R.drawable.ic_next, "Next", getActionIntent(ACTION_NEXT))

        return builder.build()
    }

    private fun getActionIntent(action: String): PendingIntent {
        val intent = Intent(this, MusicService::class.java).apply { this.action = action }
        return PendingIntent.getService(this, action.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Música", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        mediaSession?.release()
        super.onDestroy()
    }
}