package com.example.casto

import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.mediarouter.app.MediaRouteButton
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.MediaRouteButtonFactory
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

// Media3 Cast APIs are annotated with @UnstableApi; opt in for the whole class.
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class MainActivity : AppCompatActivity() {

    private lateinit var castPlayer: CastPlayer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1) Cast button. Media3 resolves the Cast MediaRouteSelector, so the button
        //    only needs to be handed the view and it starts discovering devices.
        val routeButton = findViewById<MediaRouteButton>(R.id.media_route_button)
        val routeButtonSetup = MediaRouteButtonFactory.setUpMediaRouteButton(this, routeButton)
        routeButtonSetup.addListener({
            try {
                routeButtonSetup.get()
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo configurar el botón Cast", e)
            }
        }, ContextCompat.getMainExecutor(this))

        // 2) Playback stack: local ExoPlayer wrapped by CastPlayer. CastPlayer plays
        //    locally when there is no Cast session and on the remote device when there is one.
        val exoPlayer = ExoPlayer.Builder(this).build()
        castPlayer = CastPlayer.Builder(this)
            .setLocalPlayer(exoPlayer)
            .build()

        val playerView = findViewById<PlayerView>(R.id.player_view)
        playerView.player = castPlayer

        // 3) Local MP4 video. Replace res/raw/sample_video.mp4 with a real video file.
        val videoUri = Uri.parse("android.resource://$packageName/${R.raw.sample_video}")
        castPlayer.setMediaItem(MediaItem.fromUri(videoUri))
        castPlayer.prepare()
        castPlayer.playWhenReady = true
    }

    override fun onDestroy() {
        super.onDestroy()
        // CastPlayer.release() releases both the local ExoPlayer and the remote player.
        castPlayer.release()
    }

    private companion object {
        const val TAG = "CastoMainActivity"
    }
}
