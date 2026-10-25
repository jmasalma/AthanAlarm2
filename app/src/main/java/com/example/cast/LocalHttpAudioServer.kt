package com.example.cast

import android.content.Context
import android.util.Log
import java.io.BufferedInputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections
import kotlin.concurrent.thread

/**
 * Lightweight embedded HTTP server that serves local audio files or raw bundled assets
 * to Chromecast devices over the local Wi-Fi network.
 */
class LocalHttpAudioServer {

    private var serverSocket: ServerSocket? = null
    private var isRunning = false
    private var port = 0
    private var audioStreamSupplier: (() -> InputStream?)? = null
    private var audioContentLength: Long = -1L

    companion object {
        private const val TAG = "LocalHttpAudioServer"

        fun getLocalIpAddress(): String {
            try {
                val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
                for (intf in interfaces) {
                    val addrs = Collections.list(intf.inetAddresses)
                    for (addr in addrs) {
                        if (!addr.isLoopbackAddress && addr is Inet4Address) {
                            val host = addr.hostAddress ?: continue
                            if (host.indexOf(':') < 0) {
                                return host
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error resolving local IP address", e)
            }
            return "127.0.0.1"
        }
    }

    @Synchronized
    fun start(streamSupplier: () -> InputStream?, contentLength: Long): String {
        stop()

        this.audioStreamSupplier = streamSupplier
        this.audioContentLength = contentLength

        val socket = ServerSocket(0)
        this.serverSocket = socket
        this.port = socket.localPort
        this.isRunning = true

        thread(name = "LocalHttpAudioServerThread", isDaemon = true) {
            listenLoop(socket)
        }

        val ip = getLocalIpAddress()
        val url = "http://$ip:$port/athan.mp3"
        Log.d(TAG, "Server started at $url")
        return url
    }

    @Synchronized
    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null
        audioStreamSupplier = null
    }

    private fun listenLoop(socket: ServerSocket) {
        while (isRunning && !socket.isClosed) {
            try {
                val client = socket.accept()
                thread(name = "LocalHttpClientThread", isDaemon = true) {
                    handleClient(client)
                }
            } catch (e: Exception) {
                if (isRunning) {
                    Log.w(TAG, "Exception while accepting client connection: ${e.message}")
                }
                break
            }
        }
    }

    private fun handleClient(client: Socket) {
        try {
            client.soTimeout = 15000
            val input = client.getInputStream().bufferedReader()
            val requestLine = input.readLine() ?: return
            Log.d(TAG, "Received client request: $requestLine")

            // Read remaining headers
            var line: String? = input.readLine()
            var rangeHeader: String? = null
            while (!line.isNullOrEmpty()) {
                if (line.startsWith("Range:", ignoreCase = true)) {
                    rangeHeader = line
                }
                line = input.readLine()
            }

            val supplier = audioStreamSupplier ?: return
            val audioStream = supplier.invoke() ?: return

            val output = client.getOutputStream()

            if (rangeHeader != null && audioContentLength > 0) {
                handleRangeRequest(rangeHeader, audioStream, audioContentLength, output)
            } else {
                handleFullRequest(audioStream, audioContentLength, output)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Client socket handler finished with: ${e.message}")
        } finally {
            try {
                client.close()
            } catch (_: Exception) {}
        }
    }

    private fun handleFullRequest(audioStream: InputStream, length: Long, output: OutputStream) {
        val headerBuilder = StringBuilder()
        headerBuilder.append("HTTP/1.1 200 OK\r\n")
        headerBuilder.append("Content-Type: audio/mpeg\r\n")
        headerBuilder.append("Accept-Ranges: bytes\r\n")
        if (length > 0) {
            headerBuilder.append("Content-Length: $length\r\n")
        }
        headerBuilder.append("Connection: close\r\n\r\n")

        output.write(headerBuilder.toString().toByteArray(Charsets.US_ASCII))
        output.flush()

        val buffer = ByteArray(16384)
        BufferedInputStream(audioStream).use { bis ->
            var read = bis.read(buffer)
            while (read != -1 && isRunning) {
                output.write(buffer, 0, read)
                read = bis.read(buffer)
            }
            output.flush()
        }
    }

    private fun handleRangeRequest(rangeHeader: String, audioStream: InputStream, length: Long, output: OutputStream) {
        // e.g. "Range: bytes=0-"
        val parts = rangeHeader.substringAfter("bytes=").split("-")
        val start = parts.getOrNull(0)?.toLongOrNull() ?: 0L
        val end = if (parts.size > 1 && parts[1].isNotBlank()) parts[1].toLong() else (length - 1)
        val rangeLength = (end - start + 1).coerceAtLeast(0)

        val headerBuilder = StringBuilder()
        headerBuilder.append("HTTP/1.1 206 Partial Content\r\n")
        headerBuilder.append("Content-Type: audio/mpeg\r\n")
        headerBuilder.append("Content-Range: bytes $start-$end/$length\r\n")
        headerBuilder.append("Content-Length: $rangeLength\r\n")
        headerBuilder.append("Accept-Ranges: bytes\r\n")
        headerBuilder.append("Connection: close\r\n\r\n")

        output.write(headerBuilder.toString().toByteArray(Charsets.US_ASCII))
        output.flush()

        BufferedInputStream(audioStream).use { bis ->
            bis.skip(start)
            val buffer = ByteArray(16384)
            var bytesRemaining = rangeLength
            while (bytesRemaining > 0 && isRunning) {
                val toRead = minOf(buffer.size.toLong(), bytesRemaining).toInt()
                val read = bis.read(buffer, 0, toRead)
                if (read == -1) break
                output.write(buffer, 0, read)
                bytesRemaining -= read
            }
            output.flush()
        }
    }
}
