package com.example.casto

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.cast.CastPlayer
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * MediaSessionService that owns the playback stack:
 * a CastPlayer (local ExoPlayer + remote Cast receiver) exposed through a MediaSession.
 *
 * Media3 takes care of the foreground notification and of forwarding media commands
 * from controllers (notification, Bluetooth, Cast, etc.) to the session player.
 */
// Media3 Cast APIs are annotated with @UnstableApi; opt in for the whole class.
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()

        // Publish the bundled MP4 on the LAN so the Cast receiver can download it.
        LocalMediaServer.start(this)

        val exoPlayer = ExoPlayer.Builder(this).build()
        val castPlayer = CastPlayer.Builder(this)
            .setLocalPlayer(exoPlayer)
            .build()

        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaSession.Builder(this, castPlayer)
            .setSessionActivity(sessionActivity)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        LocalMediaServer.stop()
        super.onDestroy()
    }
}
