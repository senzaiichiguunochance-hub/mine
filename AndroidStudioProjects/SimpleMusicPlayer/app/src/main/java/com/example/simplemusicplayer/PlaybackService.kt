package com.example.simplemusicplayer

import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

class PlaybackService : MediaSessionService() {

    private lateinit var store: PlaybackStore
    private lateinit var player: ExoPlayer
    private var mediaSession: MediaSession? = null

    private val handler = Handler(Looper.getMainLooper())
    private val periodicSave = object : Runnable {
        override fun run() {
            saveCurrentPosition()
            handler.postDelayed(this, SAVE_INTERVAL_MS)
        }
    }

    private val playerListener = object : Player.Listener {

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            handler.removeCallbacks(periodicSave)
            if (isPlaying) {
                handler.postDelayed(periodicSave, SAVE_INTERVAL_MS)
            } else {
                saveCurrentPosition()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                player.currentMediaItem?.let { store.removePosition(it.mediaId) }
            }
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            val oldItem = oldPosition.mediaItem ?: return
            if (oldItem.mediaId == newPosition.mediaItem?.mediaId) return

            if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION) {
                store.removePosition(oldItem.mediaId)
            } else {
                store.savePosition(oldItem.mediaId, oldPosition.positionMs, C.TIME_UNSET)
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (mediaItem == null || reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) return

            store.lastUri = mediaItem.mediaId
            val savedPosition = store.getPosition(mediaItem.mediaId)
            if (savedPosition > 0) player.seekTo(savedPosition)
        }
    }

    override fun onCreate() {
        super.onCreate()
        store = PlaybackStore(this)

        player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
            .apply {
                setPlaybackSpeed(store.speed)
                addListener(playerListener)
            }

        mediaSession = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacks(periodicSave)
        saveCurrentPosition()
        mediaSession?.release()
        mediaSession = null
        player.release()
        super.onDestroy()
    }

    private fun saveCurrentPosition() {
        val item = player.currentMediaItem ?: return
        if (player.playbackState == Player.STATE_ENDED) return

        store.lastUri = item.mediaId
        store.savePosition(item.mediaId, player.currentPosition, player.duration)
    }

    private companion object {
        const val SAVE_INTERVAL_MS = 5_000L
    }
}
