package com.example.casto

import android.content.ComponentName
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.mediarouter.app.MediaRouteButton
import androidx.media3.cast.MediaRouteButtonFactory
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.PlayerView
import com.google.common.util.concurrent.ListenableFuture

/**
 * UI only: the Cast button and a PlayerView bound to a MediaController.
 *
 * Playback itself lives in [PlaybackService], which owns the single CastPlayer
 * (local ExoPlayer + Cast receiver) exposed through a MediaSession.
 */
// Media3 Cast APIs are annotated with @UnstableApi; opt in for the whole class.
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class MainActivity : AppCompatActivity() {

    private lateinit var playerView: PlayerView
    private var controllerFuture: ListenableFuture<MediaController>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        playerView = findViewById(R.id.player_view)

        // Cast button: Media3 resolves the Cast MediaRouteSelector and wires the
        // button to the global Cast session, so selecting a device affects the
        // player held by PlaybackService.
        val routeButton = findViewById<MediaRouteButton>(R.id.media_route_button)
        val routeButtonSetup = MediaRouteButtonFactory.setUpMediaRouteButton(this, routeButton)
        routeButtonSetup.addListener({
            try {
                routeButtonSetup.get()
            } catch (e: Exception) {
                Log.w(TAG, "No se pudo configurar el botón Cast", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onStart() {
        super.onStart()
        connectToPlaybackService()
    }

    override fun onStop() {
        super.onStop()
        playerView.player = null
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
    }

    private fun connectToPlaybackService() {
        val sessionToken = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val future = MediaController.Builder(this, sessionToken)
            .setListener(object : MediaController.Listener {
                override fun onDisconnected(controller: MediaController) {
                    playerView.player = null
                }
            })
            .buildAsync()
        controllerFuture = future

        future.addListener({
            try {
                val controller = future.get()
                playerView.player = controller
                startLocalVideoIfNeeded(controller)
            } catch (e: Exception) {
                Log.e(TAG, "No se pudo conectar con PlaybackService", e)
                Toast.makeText(this, R.string.playback_service_error, Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    /**
     * First launch only: queue the bundled MP4. If the service is already playing
     * (e.g. after a configuration change), playback is left untouched.
     */
    private fun startLocalVideoIfNeeded(controller: MediaController) {
        if (controller.currentMediaItem != null) return

        // The bundled MP4 is served over HTTP (LocalMediaServer) so the exact same URL
        // works for local playback and for the Cast receiver, which cannot read
        // android.resource:// URIs. Replace res/raw/sample_video.mp4 with a real MP4.
        val mediaItem = MediaItem.Builder()
            .setUri(LocalMediaServer.videoUri(this))
            .setMimeType("video/mp4")
            .build()

        controller.setMediaItem(mediaItem)
        controller.prepare()
        controller.playWhenReady = true
    }

    private companion object {
        const val TAG = "CastoMainActivity"
    }
}
