package com.example.casto

import android.content.Context
import android.net.Uri
import android.util.Log
import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Serves the bundled MP4 over the local network so the Cast receiver can download it.
 *
 * A Chromecast cannot read `android.resource://` or `content://` URIs, so the video is
 * copied to the app cache and published as `http://<lan-ip>:<port>/sample_video.mp4`.
 * The same URL is used for local playback, which keeps the media item identical when
 * playback moves between the phone and the remote device.
 */
object LocalMediaServer {

    private const val TAG = "LocalMediaServer"
    private const val PREFERRED_PORT = 8090
    private const val VIDEO_PATH = "/sample_video.mp4"
    private const val VIDEO_NAME = "sample_video.mp4"
    private const val MIME_VIDEO_MP4 = "video/mp4"

    private var server: Server? = null

    /**
     * Starts the server (idempotent) and returns its base URL,
     * for example `http://192.168.1.42:8090`.
     */
    @Synchronized
    fun start(context: Context): String {
        server?.let { return it.baseUrl() }

        val video = copyVideoToCache(context.applicationContext)
        val started = try {
            startOn(PREFERRED_PORT, video)
        } catch (e: IOException) {
            Log.w(TAG, "Puerto $PREFERRED_PORT ocupado, usando uno efímero", e)
            startOn(0, video)
        }
        server = started
        Log.i(TAG, "Servidor de medios en ${started.baseUrl()}")
        return started.baseUrl()
    }

    @Synchronized
    fun stop() {
        server?.stop()
        server = null
    }

    /** HTTP URI of the bundled video, ready for both ExoPlayer and the Cast receiver. */
    fun videoUri(context: Context): Uri = Uri.parse(start(context) + VIDEO_PATH)

    private fun startOn(port: Int, video: File): Server =
        Server(port, video).apply { start(NanoHTTPD.SOCKET_READ_TIMEOUT, false) }

    private fun copyVideoToCache(context: Context): File {
        val target = File(context.cacheDir, VIDEO_NAME)
        if (!target.exists() || target.length() == 0L) {
            context.resources.openRawResource(R.raw.sample_video).use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
        }
        return target
    }

    private class Server(port: Int, private val video: File) : NanoHTTPD(port) {

        fun baseUrl(): String = "http://${lanIpv4Address() ?: "127.0.0.1"}:$listeningPort"

        override fun serve(session: IHTTPSession): Response {
            if (session.uri != VIDEO_PATH) {
                return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")
            }
            if (session.method != Method.GET && session.method != Method.HEAD) {
                return newFixedLengthResponse(
                    Response.Status.METHOD_NOT_ALLOWED,
                    MIME_PLAINTEXT,
                    "Method not allowed"
                )
            }

            val total = video.length()
            if (total <= 0L) {
                return newFixedLengthResponse(
                    Response.Status.NOT_FOUND,
                    MIME_PLAINTEXT,
                    "Video not available"
                )
            }

            val range = HttpRange.parse(session.headers["range"], total)

            val response = if (range == null) {
                newFixedLengthResponse(Response.Status.OK, MIME_VIDEO_MP4, FileInputStream(video), total)
            } else {
                val stream = RangeInputStream(FileInputStream(video), range.start, range.length)
                newFixedLengthResponse(Response.Status.PARTIAL_CONTENT, MIME_VIDEO_MP4, stream, range.length)
                    .also { it.addHeader("Content-Range", "bytes ${range.start}-${range.end}/$total") }
            }

            response.addHeader("Accept-Ranges", "bytes")
            response.addHeader("Access-Control-Allow-Origin", "*")
            return response
        }
    }

    /** Reads [length] bytes from [source], starting at [start]. */
    private class RangeInputStream(
        private val source: InputStream,
        start: Long,
        private var remaining: Long
    ) : InputStream() {

        init {
            var skipped = 0L
            while (skipped < start) {
                val step = source.skip(start - skipped)
                if (step > 0L) {
                    skipped += step
                } else if (source.read() == -1) {
                    break
                } else {
                    skipped++
                }
            }
        }

        override fun read(): Int {
            if (remaining <= 0L) return -1
            val value = source.read()
            if (value >= 0) remaining--
            return value
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (remaining <= 0L) return -1
            val read = source.read(b, off, minOf(len.toLong(), remaining).toInt())
            if (read > 0) remaining -= read
            return read
        }

        override fun close() = source.close()
    }

    /**
     * First site-local IPv4 address of an active interface. The Chromecast can only
     * reach the phone through the home Wi-Fi, so VPN/cellular addresses are avoided.
     */
    private fun lanIpv4Address(): String? {
        return try {
            val addresses = NetworkInterface.getNetworkInterfaces()
                .toList()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()

            val chosen = addresses.firstOrNull { it.isSiteLocalAddress && !it.isLinkLocalAddress }
                ?: addresses.firstOrNull { !it.isLinkLocalAddress }
            chosen?.hostAddress
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo obtener la IP local", e)
            null
        }
    }
}
