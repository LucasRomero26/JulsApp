package com.tudominio.smslocation.model.network

import android.util.Log
import com.tudominio.smslocation.util.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.io.IOException

/**
 * UDP client optimizado para máxima velocidad.
 * Configurado con timeouts reducidos y optimizaciones de buffer.
 */
class UdpClient {

    companion object {
        private const val TAG = Constants.Logs.TAG_NETWORK
        private const val MAX_UDP_PACKET_SIZE = 1024
        // Timeout optimizado para UDP ultra-rápido
        private const val FAST_UDP_TIMEOUT = 1000L // 1 segundo para máxima velocidad
        private const val SOCKET_TIMEOUT = 800 // 0.8 segundos para socket
    }

    sealed class UdpResult {
        object Success : UdpResult()
        data class Error(val message: String, val exception: Throwable? = null) : UdpResult()
        object Timeout : UdpResult()
    }

    /**
     * Envío UDP ultra-rápido con timeouts optimizados
     */
    suspend fun sendData(
        serverIP: String,
        port: Int = Constants.UDP_PORT,
        data: String
    ): UdpResult = withContext(Dispatchers.IO) {

        Log.d(TAG, "Fast UDP: Sending to $serverIP:$port - Size: ${data.length} chars")

        try {
            val dataBytes = data.toByteArray(Charsets.UTF_8)
            if (dataBytes.size > MAX_UDP_PACKET_SIZE) {
                Log.w(TAG, "UDP: Data size (${dataBytes.size}) exceeds max packet size ($MAX_UDP_PACKET_SIZE)")
                return@withContext UdpResult.Error("Data too large for UDP packet")
            }

            // Timeout ultra-reducido para máxima velocidad
            val result = withTimeoutOrNull(FAST_UDP_TIMEOUT) {
                performFastUdpSend(serverIP, port, dataBytes)
            }

            return@withContext result ?: UdpResult.Timeout.also {
                Log.w(TAG, "Fast UDP: Timeout sending to $serverIP:$port after ${FAST_UDP_TIMEOUT}ms")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Fast UDP: Error sending to $serverIP:$port", e)
            return@withContext UdpResult.Error(
                message = "UDP Error: ${e.message}",
                exception = e
            )
        }
    }

    /**
     * Implementación UDP optimizada para velocidad máxima
     */
    private suspend fun performFastUdpSend(
        serverIP: String,
        port: Int,
        dataBytes: ByteArray
    ): UdpResult = withContext(Dispatchers.IO) {

        var socket: DatagramSocket? = null

        try {
            socket = DatagramSocket().apply {
                // Configuración optimizada para velocidad
                soTimeout = SOCKET_TIMEOUT
                sendBufferSize = 16384 // Buffer más grande para mejor rendimiento
                receiveBufferSize = 8192
                reuseAddress = true // Reusar dirección para velocidad
                trafficClass = 0x10 // IPTOS_LOWDELAY para baja latencia
            }

            val address = InetAddress.getByName(serverIP)
            val packet = DatagramPacket(dataBytes, dataBytes.size, address, port)

            // Envío directo sin verificaciones adicionales para máxima velocidad
            socket.send(packet)

            Log.d(TAG, "Fast UDP: SUCCESS to $serverIP:$port (${dataBytes.size} bytes)")
            return@withContext UdpResult.Success

        } catch (e: SocketTimeoutException) {
            Log.w(TAG, "Fast UDP: Timeout for $serverIP:$port", e)
            return@withContext UdpResult.Timeout

        } catch (e: IOException) {
            Log.e(TAG, "Fast UDP: IO error for $serverIP:$port", e)
            return@withContext UdpResult.Error("UDP IO Error: ${e.message}", e)

        } catch (e: Exception) {
            Log.e(TAG, "Fast UDP: Unexpected error for $serverIP:$port", e)
            return@withContext UdpResult.Error("UDP Error: ${e.message}", e)

        } finally {
            try {
                socket?.close()
            } catch (e: Exception) {
                Log.w(TAG, "Fast UDP: Error closing socket", e)
            }
        }
    }

    /**
     * Test de conexión UDP rápido
     */
    suspend fun testConnection(
        serverIP: String,
        port: Int = Constants.UDP_PORT
    ): Boolean = withContext(Dispatchers.IO) {

        try {
            withTimeoutOrNull(800L) { // Test ultra-rápido
                InetAddress.getByName(serverIP)
                DatagramSocket().use { socket ->
                    socket.soTimeout = 500
                    true
                }
            } ?: false

        } catch (e: Exception) {
            Log.d(TAG, "Fast UDP: Test connection failed for $serverIP:$port - ${e.message}")
            false
        }
    }

    /**
     * Envío con reintentos optimizado para velocidad
     */
    suspend fun sendDataWithRetry(
        serverIP: String,
        port: Int = Constants.UDP_PORT,
        data: String,
        maxRetries: Int = Constants.MAX_RETRY_ATTEMPTS
    ): UdpResult {

        repeat(maxRetries) { attempt ->
            val result = sendData(serverIP, port, data)

            if (result is UdpResult.Success) {
                if (attempt > 0) {
                    Log.d(TAG, "Fast UDP: Success on retry $attempt for $serverIP:$port")
                }
                return result
            }

            // Delay ultra-corto entre reintentos para velocidad máxima
            if (attempt < maxRetries - 1) {
                Log.d(TAG, "Fast UDP: Retry $attempt failed for $serverIP:$port, retrying in ${Constants.RETRY_DELAY}ms...")
                kotlinx.coroutines.delay(Constants.RETRY_DELAY)
            }
        }

        Log.w(TAG, "Fast UDP: All $maxRetries attempts failed for $serverIP:$port")
        return UdpResult.Error("Failed after $maxRetries fast attempts")
    }

    /**
     * Envío múltiple optimizado
     */
    suspend fun sendMultiplePackets(
        serverIP: String,
        port: Int = Constants.UDP_PORT,
        dataList: List<String>
    ): List<UdpResult> = withContext(Dispatchers.IO) {
        dataList.map { data ->
            sendData(serverIP, port, data)
        }
    }

    /**
     * Envío con confirmación (opcional, pero rápido)
     */
    suspend fun sendDataWithConfirmation(
        serverIP: String,
        port: Int = Constants.UDP_PORT,
        data: String,
        expectResponse: Boolean = false
    ): Pair<UdpResult, String?> = withContext(Dispatchers.IO) {

        val sendResult = sendData(serverIP, port, data)

        if (!expectResponse || sendResult !is UdpResult.Success) {
            return@withContext Pair(sendResult, null)
        }

        try {
            DatagramSocket().use { socket ->
                socket.soTimeout = 800 // Timeout corto para confirmación rápida
                val buffer = ByteArray(256) // Buffer pequeño para respuesta rápida
                val packet = DatagramPacket(buffer, buffer.size)

                socket.receive(packet)
                val response = String(packet.data, 0, packet.length, Charsets.UTF_8)

                Log.d(TAG, "Fast UDP: Received confirmation from $serverIP:$port - $response")
                return@withContext Pair(sendResult, response)
            }

        } catch (e: SocketTimeoutException) {
            Log.d(TAG, "Fast UDP: No confirmation received from $serverIP:$port (timeout)")
            return@withContext Pair(sendResult, null)

        } catch (e: Exception) {
            Log.w(TAG, "Fast UDP: Error receiving confirmation from $serverIP:$port", e)
            return@withContext Pair(sendResult, null)
        }
    }
}