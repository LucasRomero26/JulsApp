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
import java.util.concurrent.atomic.AtomicInteger

/**
 * UDP client mejorado con gestión de recursos y monitoreo de salud
 */
class UdpClient {

    companion object {
        private const val TAG = Constants.Logs.TAG_NETWORK
        private const val MAX_UDP_PACKET_SIZE = 1024
        private const val FAST_UDP_TIMEOUT = 2000L // Incrementado a 2 segundos
        private const val SOCKET_TIMEOUT = 1500 // 1.5 segundos
    }

    // Contadores para monitoreo
    private val successCount = AtomicInteger(0)
    private val failureCount = AtomicInteger(0)
    private val timeoutCount = AtomicInteger(0)

    sealed class UdpResult {
        object Success : UdpResult()
        data class Error(val message: String, val exception: Throwable? = null) : UdpResult()
        object Timeout : UdpResult()
    }

    suspend fun sendData(
        serverIP: String,
        port: Int = Constants.UDP_PORT,
        data: String
    ): UdpResult = withContext(Dispatchers.IO) {

        Log.d(TAG, "UDP Send to $serverIP:$port - Size: ${data.length} chars")

        try {
            val dataBytes = data.toByteArray(Charsets.UTF_8)
            if (dataBytes.size > MAX_UDP_PACKET_SIZE) {
                Log.w(TAG, "Data size (${dataBytes.size}) exceeds max packet size")
                failureCount.incrementAndGet()
                return@withContext UdpResult.Error("Data too large for UDP packet")
            }

            // Validar IP antes de enviar
            if (!isValidIPAddress(serverIP)) {
                Log.w(TAG, "Invalid IP address: $serverIP")
                failureCount.incrementAndGet()
                return@withContext UdpResult.Error("Invalid IP address")
            }

            val result = withTimeoutOrNull(FAST_UDP_TIMEOUT) {
                performUdpSend(serverIP, port, dataBytes)
            }

            return@withContext result ?: UdpResult.Timeout.also {
                timeoutCount.incrementAndGet()
                Log.w(TAG, "UDP Timeout to $serverIP:$port after ${FAST_UDP_TIMEOUT}ms")
            }

        } catch (e: Exception) {
            failureCount.incrementAndGet()
            Log.e(TAG, "UDP Error to $serverIP:$port", e)
            return@withContext UdpResult.Error("UDP Error: ${e.message}", e)
        }
    }

    private suspend fun performUdpSend(
        serverIP: String,
        port: Int,
        dataBytes: ByteArray
    ): UdpResult = withContext(Dispatchers.IO) {

        var socket: DatagramSocket? = null

        try {
            // Crear socket con configuración optimizada
            socket = DatagramSocket().apply {
                soTimeout = SOCKET_TIMEOUT
                sendBufferSize = 16384
                receiveBufferSize = 8192
                reuseAddress = true
                trafficClass = 0x10 // IPTOS_LOWDELAY

                // Configuraciones adicionales para estabilidad
                try {
                    // DatagramSocket no soporta keepAlive como TCP
                    // Configurar otros parámetros UDP específicos si es necesario
                } catch (e: Exception) {
                    Log.d(TAG, "Error setting socket options: ${e.message}")
                }
            }

            val address = InetAddress.getByName(serverIP)
            val packet = DatagramPacket(dataBytes, dataBytes.size, address, port)

            // Envío con monitoreo de tiempo
            val startTime = System.currentTimeMillis()
            socket.send(packet)
            val endTime = System.currentTimeMillis()

            successCount.incrementAndGet()
            Log.d(TAG, "UDP SUCCESS to $serverIP:$port (${dataBytes.size} bytes, ${endTime - startTime}ms)")

            return@withContext UdpResult.Success

        } catch (e: SocketTimeoutException) {
            timeoutCount.incrementAndGet()
            Log.w(TAG, "UDP Socket timeout for $serverIP:$port")
            return@withContext UdpResult.Timeout

        } catch (e: IOException) {
            failureCount.incrementAndGet()
            Log.e(TAG, "UDP IO error for $serverIP:$port", e)
            return@withContext UdpResult.Error("UDP IO Error: ${e.message}", e)

        } catch (e: Exception) {
            failureCount.incrementAndGet()
            Log.e(TAG, "UDP Unexpected error for $serverIP:$port", e)
            return@withContext UdpResult.Error("UDP Error: ${e.message}", e)

        } finally {
            // Cerrar socket de manera segura
            try {
                socket?.close()
            } catch (e: Exception) {
                Log.w(TAG, "Error closing UDP socket", e)
            }
        }
    }

    suspend fun testConnection(
        serverIP: String,
        port: Int = Constants.UDP_PORT
    ): Boolean = withContext(Dispatchers.IO) {

        try {
            if (!isValidIPAddress(serverIP)) {
                Log.w(TAG, "Invalid IP for connection test: $serverIP")
                return@withContext false
            }

            withTimeoutOrNull(1500L) { // Test rápido
                var socket: DatagramSocket? = null
                try {
                    socket = DatagramSocket()
                    socket.soTimeout = 1000

                    // Verificar que podemos resolver la dirección
                    InetAddress.getByName(serverIP)

                    Log.d(TAG, "UDP connection test passed for $serverIP:$port")
                    true
                } finally {
                    socket?.close()
                }
            } ?: false

        } catch (e: Exception) {
            Log.d(TAG, "UDP connection test failed for $serverIP:$port - ${e.message}")
            false
        }
    }

    suspend fun sendDataWithRetry(
        serverIP: String,
        port: Int = Constants.UDP_PORT,
        data: String,
        maxRetries: Int = Constants.MAX_RETRY_ATTEMPTS
    ): UdpResult {

        var lastResult: UdpResult? = null

        repeat(maxRetries) { attempt ->
            val result = sendData(serverIP, port, data)

            if (result is UdpResult.Success) {
                if (attempt > 0) {
                    Log.d(TAG, "UDP Success on retry $attempt for $serverIP:$port")
                }
                return result
            }

            lastResult = result

            // Delay progresivo entre reintentos
            if (attempt < maxRetries - 1) {
                val delay = Constants.RETRY_DELAY * (attempt + 1)
                Log.d(TAG, "UDP Retry $attempt failed for $serverIP:$port, retrying in ${delay}ms")
                kotlinx.coroutines.delay(delay)
            }
        }

        Log.w(TAG, "UDP All $maxRetries attempts failed for $serverIP:$port")
        return lastResult ?: UdpResult.Error("All retry attempts failed")
    }

    suspend fun sendMultiplePackets(
        serverIP: String,
        port: Int = Constants.UDP_PORT,
        dataList: List<String>
    ): List<UdpResult> = withContext(Dispatchers.IO) {

        if (dataList.isEmpty()) {
            return@withContext emptyList()
        }

        Log.d(TAG, "Sending ${dataList.size} UDP packets to $serverIP:$port")

        dataList.mapIndexed { index, data ->
            val result = sendData(serverIP, port, data)

            // Pequeño delay entre paquetes para evitar saturación
            if (index < dataList.size - 1) {
                kotlinx.coroutines.delay(50)
            }

            result
        }
    }

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

        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket().apply {
                soTimeout = 1000 // Timeout corto para confirmación
            }

            val buffer = ByteArray(256)
            val packet = DatagramPacket(buffer, buffer.size)

            socket.receive(packet)
            val response = String(packet.data, 0, packet.length, Charsets.UTF_8)

            Log.d(TAG, "UDP Confirmation received from $serverIP:$port - $response")
            return@withContext Pair(sendResult, response)

        } catch (e: SocketTimeoutException) {
            Log.d(TAG, "UDP No confirmation from $serverIP:$port (timeout)")
            return@withContext Pair(sendResult, null)

        } catch (e: Exception) {
            Log.w(TAG, "UDP Error receiving confirmation from $serverIP:$port", e)
            return@withContext Pair(sendResult, null)

        } finally {
            socket?.close()
        }
    }

    private fun isValidIPAddress(ip: String): Boolean {
        return try {
            val parts = ip.split(".")
            if (parts.size != 4) return false

            parts.all { part ->
                val num = part.toIntOrNull()
                num != null && num in 0..255
            }
        } catch (e: Exception) {
            false
        }
    }

    fun getStatistics(): Map<String, Int> {
        return mapOf(
            "success_count" to successCount.get(),
            "failure_count" to failureCount.get(),
            "timeout_count" to timeoutCount.get(),
            "total_attempts" to (successCount.get() + failureCount.get() + timeoutCount.get())
        )
    }

    fun resetStatistics() {
        successCount.set(0)
        failureCount.set(0)
        timeoutCount.set(0)
        Log.d(TAG, "UDP statistics reset")
    }

    fun getSuccessRate(): Float {
        val total = successCount.get() + failureCount.get() + timeoutCount.get()
        return if (total > 0) {
            (successCount.get().toFloat() / total.toFloat()) * 100f
        } else {
            0f
        }
    }
}