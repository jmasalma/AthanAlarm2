package com.example.cast

import android.content.Context
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.util.Log
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections
import java.util.Locale
import kotlin.concurrent.thread

/**
 * Robust embedded HTTP server that serves audio to Google Cast / Google Home devices
 * over the local Wi-Fi network with full CORS and byte-range support.
 */
class LocalHttpAudioServer {

    private var serverSocket: ServerSocket? = null
    private var isRunning = false
    private var port = 0
    private var audioData: ByteArray? = null
    private var contentType: String = "audio/mpeg"

    companion object {
        private const val TAG = "LocalHttpAudioServer"

        /**
         * Resolves the device's local Wi-Fi IPv4 address, prioritizing ConnectivityManager
         * active network link addresses, then Wi-Fi interfaces over mobile cellular (rmnet/CGNAT).
         */
        fun getLocalIpAddress(context: Context? = null): String {
            // 1. Try ConnectivityManager active network first (zero permissions needed, 100% reliable)
            if (context != null) {
                try {
                    val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                    val activeNetwork = cm?.activeNetwork
                    if (activeNetwork != null) {
                        val linkProperties = cm.getLinkProperties(activeNetwork)
                        for (linkAddress in linkProperties?.linkAddresses ?: emptyList()) {
                            val addr = linkAddress.address
                            if (addr is Inet4Address && !addr.isLoopbackAddress) {
                                val host = addr.hostAddress
                                if (!host.isNullOrBlank() && host != "127.0.0.1") {
                                    Log.d(TAG, "Resolved IP via ConnectivityManager active network: $host")
                                    return host
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "ConnectivityManager lookup failed: ${e.message}")
                }
            }

            // 2. Try WifiManager
            if (context != null) {
                try {
                    val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                    @Suppress("DEPRECATION")
                    val ipInt = wifiManager?.connectionInfo?.ipAddress ?: 0
                    if (ipInt != 0) {
                        val ip = String.format(
                            Locale.US,
                            "%d.%d.%d.%d",
                            ipInt and 0xff,
                            ipInt shr 8 and 0xff,
                            ipInt shr 16 and 0xff,
                            ipInt shr 24 and 0xff
                        )
                        if (ip != "0.0.0.0" && ip != "127.0.0.1") {
                            Log.d(TAG, "Resolved IP via WifiManager: $ip")
                            return ip
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "WifiManager IP lookup failed: ${e.message}")
                }
            }

            // 3. Iterate network interfaces, prioritizing wlan and eth
            try {
                val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
                val sortedInterfaces = interfaces.sortedByDescending {
                    val name = it.name.lowercase(Locale.US)
                    when {
                        name.startsWith("wlan") -> 100
                        name.startsWith("eth") -> 90
                        name.startsWith("ap") -> 80
                        name.startsWith("rmnet") -> 0
                        name.startsWith("ccmni") -> 0
                        name.startsWith("tun") -> 0
                        else -> 10
                    }
                }

                for (intf in sortedInterfaces) {
                    if (!intf.isUp || intf.isLoopback) continue
                    for (addr in Collections.list(intf.inetAddresses)) {
                        if (!addr.isLoopbackAddress && addr is Inet4Address) {
                            val host = addr.hostAddress ?: continue
                            if (!host.contains(':') && host != "127.0.0.1") {
                                if (intf.name.startsWith("wlan") || intf.name.startsWith("eth") ||
                                    host.startsWith("192.168.") || host.startsWith("10.") || host.startsWith("172.")
                                ) {
                                    Log.d(TAG, "Resolved IP via interface ${intf.name}: $host")
                                    return host
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error resolving network interfaces: ${e.message}", e)
            }

            return "127.0.0.1"
        }
    }

    /**
     * Starts the HTTP server serving pre-loaded in-memory audio bytes.
     */
    @Synchronized
    fun startWithBytes(bytes: ByteArray, mimeType: String, context: Context? = null): String {
        stop()

        this.audioData = bytes
        this.contentType = if (mimeType.isNotBlank()) mimeType else "audio/mpeg"

        val socket = ServerSocket(0)
        this.serverSocket = socket
        this.port = socket.localPort
        this.isRunning = true

        thread(name = "LocalHttpAudioServerThread", isDaemon = true) {
            listenLoop(socket)
        }

        val ip = getLocalIpAddress(context)
        val ext = if (contentType.contains("wav")) "wav" else "mp3"
        val url = "http://$ip:$port/athan.$ext"
        Log.d(TAG, "Server started at $url (Bytes: ${bytes.size}, Content-Type: $contentType)")
        return url
    }

    /**
     * Starts the server from a stream supplier (reads entire stream into memory for fast, reliable serving).
     */
    @Synchronized
    fun start(streamSupplier: () -> InputStream?, contentLength: Long, context: Context? = null): String {
        val bytes = try {
            streamSupplier.invoke()?.use { it.readBytes() } ?: ByteArray(0)
        } catch (e: Exception) {
            Log.e(TAG, "Failed reading stream supplier: ${e.message}", e)
            ByteArray(0)
        }
        return startWithBytes(bytes, "audio/mpeg", context)
    }

    @Synchronized
    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null
        audioData = null
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
            val input = client.getInputStream().bufferedReader(Charsets.US_ASCII)
            val requestLine = input.readLine() ?: return
            Log.d(TAG, "Received client request: $requestLine")

            val parts = requestLine.split(" ")
            val method = parts.getOrNull(0)?.uppercase(Locale.US) ?: "GET"

            // Read remaining HTTP request headers
            var line: String? = input.readLine()
            var rangeHeader: String? = null
            while (!line.isNullOrEmpty()) {
                if (line.startsWith("Range:", ignoreCase = true)) {
                    rangeHeader = line
                }
                line = input.readLine()
            }

            val data = audioData ?: ByteArray(0)
            val totalLength = data.size.toLong()
            val output = client.getOutputStream()

            when (method) {
                "OPTIONS" -> {
                    // Pre-flight CORS request from Chrome / Cast Web Receiver
                    val response = "HTTP/1.1 200 OK\r\n" +
                            "Access-Control-Allow-Origin: *\r\n" +
                            "Access-Control-Allow-Methods: GET, HEAD, OPTIONS\r\n" +
                            "Access-Control-Allow-Headers: Range, Content-Type, Accept, Origin\r\n" +
                            "Access-Control-Expose-Headers: Content-Length, Content-Range, Accept-Ranges\r\n" +
                            "Content-Length: 0\r\n" +
                            "Connection: close\r\n\r\n"
                    output.write(response.toByteArray(Charsets.US_ASCII))
                    output.flush()
                }

                "HEAD" -> {
                    // Metadata check
                    val header = StringBuilder()
                    header.append("HTTP/1.1 200 OK\r\n")
                    header.append("Content-Type: $contentType\r\n")
                    header.append("Accept-Ranges: bytes\r\n")
                    header.append("Content-Length: $totalLength\r\n")
                    header.append("Access-Control-Allow-Origin: *\r\n")
                    header.append("Access-Control-Allow-Methods: GET, HEAD, OPTIONS\r\n")
                    header.append("Access-Control-Allow-Headers: Range, Content-Type, Accept, Origin\r\n")
                    header.append("Access-Control-Expose-Headers: Content-Length, Content-Range, Accept-Ranges\r\n")
                    header.append("Connection: close\r\n\r\n")

                    output.write(header.toString().toByteArray(Charsets.US_ASCII))
                    output.flush()
                }

                "GET" -> {
                    if (rangeHeader != null && totalLength > 0) {
                        handleRangeRequest(rangeHeader, data, totalLength, output)
                    } else {
                        handleFullRequest(data, totalLength, output)
                    }
                }

                else -> {
                    val notAllowed = "HTTP/1.1 405 Method Not Allowed\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                    output.write(notAllowed.toByteArray(Charsets.US_ASCII))
                    output.flush()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Client socket handler completed: ${e.message}")
        } finally {
            try {
                client.close()
            } catch (_: Exception) {}
        }
    }

    private fun handleFullRequest(data: ByteArray, totalLength: Long, output: OutputStream) {
        val header = StringBuilder()
        header.append("HTTP/1.1 200 OK\r\n")
        header.append("Content-Type: $contentType\r\n")
        header.append("Accept-Ranges: bytes\r\n")
        header.append("Content-Length: $totalLength\r\n")
        header.append("Access-Control-Allow-Origin: *\r\n")
        header.append("Access-Control-Allow-Methods: GET, HEAD, OPTIONS\r\n")
        header.append("Access-Control-Allow-Headers: Range, Content-Type, Accept, Origin\r\n")
        header.append("Access-Control-Expose-Headers: Content-Length, Content-Range, Accept-Ranges\r\n")
        header.append("Connection: close\r\n\r\n")

        output.write(header.toString().toByteArray(Charsets.US_ASCII))
        if (data.isNotEmpty()) {
            output.write(data)
        }
        output.flush()
    }

    private fun handleRangeRequest(rangeHeader: String, data: ByteArray, totalLength: Long, output: OutputStream) {
        val rangeStr = rangeHeader.substringAfter("bytes=").trim()
        val rangeParts = rangeStr.split("-")
        val (start, end) = when {
            rangeParts[0].isEmpty() && rangeParts.size > 1 -> {
                // Suffix range: bytes=-500
                val suffix = rangeParts[1].toLongOrNull() ?: 0L
                val s = (totalLength - suffix).coerceAtLeast(0L)
                Pair(s, totalLength - 1)
            }
            rangeParts.size > 1 && rangeParts[1].isNotEmpty() -> {
                val s = rangeParts[0].toLongOrNull() ?: 0L
                val e = (rangeParts[1].toLongOrNull() ?: (totalLength - 1)).coerceAtMost(totalLength - 1)
                Pair(s, e)
            }
            else -> {
                val s = rangeParts[0].toLongOrNull() ?: 0L
                Pair(s, totalLength - 1)
            }
        }

        val rangeLength = (end - start + 1).coerceAtLeast(0)

        val header = StringBuilder()
        header.append("HTTP/1.1 206 Partial Content\r\n")
        header.append("Content-Type: $contentType\r\n")
        header.append("Content-Range: bytes $start-$end/$totalLength\r\n")
        header.append("Content-Length: $rangeLength\r\n")
        header.append("Accept-Ranges: bytes\r\n")
        header.append("Access-Control-Allow-Origin: *\r\n")
        header.append("Access-Control-Allow-Methods: GET, HEAD, OPTIONS\r\n")
        header.append("Access-Control-Allow-Headers: Range, Content-Type, Accept, Origin\r\n")
        header.append("Access-Control-Expose-Headers: Content-Length, Content-Range, Accept-Ranges\r\n")
        header.append("Connection: close\r\n\r\n")

        output.write(header.toString().toByteArray(Charsets.US_ASCII))

        if (rangeLength > 0 && start < data.size) {
            val safeStart = start.toInt().coerceIn(0, data.size)
            val safeLength = rangeLength.toInt().coerceAtMost(data.size - safeStart)
            output.write(data, safeStart, safeLength)
        }
        output.flush()
    }
}
