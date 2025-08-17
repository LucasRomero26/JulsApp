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
 * Cliente UDP para envío de datos de ubicación
 */
class UdpClient {

    companion object {
        private const val TAG = Constants.Logs.TAG_NETWORK
        private const val MAX_UDP_PACKET_SIZE = 1024 // Tamaño máximo del paquete UDP
    }

    /**
     * Resultado del envío UDP
     */
    sealed class UdpResult {
        object Success : UdpResult()
        data class Error(val message: String, val exception: Throwable? = null) : UdpResult()
        object Timeout : UdpResult()
    }

    /**
     * Enviar datos via UDP a un servidor específico
     *
     * @param serverIP IP del servidor destino
     * @param port Puerto UDP (normalmente 6001)
     * @param data Datos a enviar
     * @return Resultado del envío
     */
    suspend fun sendData(
        serverIP: String,
        port: Int = Constants.UDP_PORT,
        data: String
    ): UdpResult = withContext(Dispatchers.IO) {

        Log.d(TAG, "UDP: Attempting to send to $serverIP:$port - Data: $data")

        try {
            // Verificar tamaño de datos
            val dataBytes = data.toByteArray(Charsets.UTF_8)
            if (dataBytes.size > MAX_UDP_PACKET_SIZE) {
                Log.w(TAG, "UDP: Data size (${dataBytes.size}) exceeds max packet size ($MAX_UDP_PACKET_SIZE)")
                return@withContext UdpResult.Error("Data too large for UDP packet")
            }

            // Usar timeout para evitar bloqueos indefinidos
            val result = withTimeoutOrNull(Constants.NETWORK_TIMEOUT.toLong()) {
                performUdpSend(serverIP, port, dataBytes)
            }

            return@withContext result ?: UdpResult.Timeout.also {
                Log.w(TAG, "UDP: Timeout sending to $serverIP:$port")
            }

        } catch (e: Exception) {
            Log.e(TAG, "UDP: Error sending to $serverIP:$port", e)
            return@withContext UdpResult.Error(
                message = "UDP Error: ${e.message}",
                exception = e
            )
        }
    }

    /**
     * Realizar el envío UDP real
     */
    private suspend fun performUdpSend(
        serverIP: String,
        port: Int,
        dataBytes: ByteArray
    ): UdpResult = withContext(Dispatchers.IO) {

        var socket: DatagramSocket? = null

        try {
            // Crear socket UDP
            socket = DatagramSocket().apply {
                soTimeout = Constants.NETWORK_TIMEOUT
                // UDP no necesita conexión, es connectionless
            }

            // Resolver dirección del servidor
            val address = InetAddress.getByName(serverIP)

            // Crear paquete UDP
            val packet = DatagramPacket(
                dataBytes,
                dataBytes.size,
                address,
                port
            )

            // Enviar paquete
            socket.send(packet)

            Log.d(TAG, "UDP: Successfully sent to $serverIP:$port (${dataBytes.size} bytes)")
            return@withContext UdpResult.Success

        } catch (e: SocketTimeoutException) {
            Log.w(TAG, "UDP: Socket timeout for $serverIP:$port", e)
            return@withContext UdpResult.Timeout

        } catch (e: IOException) {
            Log.e(TAG, "UDP: IO error for $serverIP:$port", e)
            return@withContext UdpResult.Error(
                message = "UDP IO Error: ${e.message}",
                exception = e
            )

        } catch (e: Exception) {
            Log.e(TAG, "UDP: Unexpected error for $serverIP:$port", e)
            return@withContext UdpResult.Error(
                message = "UDP Unexpected Error: ${e.message}",
                exception = e
            )

        } finally {
            // Cleanup resources
            try {
                socket?.close()
            } catch (e: Exception) {
                Log.w(TAG, "UDP: Error closing socket", e)
            }
        }
    }

    /**
     * Verificar que se pueda crear un socket UDP (no garantiza conectividad)
     * UDP es connectionless, por lo que no podemos "testear" la conexión como TCP
     *
     * @param serverIP IP del servidor (para validar formato)
     * @param port Puerto UDP
     * @return true si se puede crear socket y la IP es válida
     */
    suspend fun testConnection(
        serverIP: String,
        port: Int = Constants.UDP_PORT
    ): Boolean = withContext(Dispatchers.IO) {

        try {
            // Validar IP y crear socket de prueba
            InetAddress.getByName(serverIP)

            DatagramSocket().use { socket ->
                socket.soTimeout = 1000 // Timeout muy corto para test
                true
            }

        } catch (e: Exception) {
            Log.d(TAG, "UDP: Test connection failed for $serverIP:$port - ${e.message}")
            false
        }
    }

    /**
     * Enviar datos con reintentos automáticos
     *
     * @param serverIP IP del servidor
     * @param port Puerto UDP
     * @param data Datos a enviar
     * @param maxRetries Número máximo de reintentos
     * @return Resultado final después de todos los intentos
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
                    Log.d(TAG, "UDP: Success on retry $attempt for $serverIP:$port")
                }
                return result
            }

            // Si no es el último intento, esperar antes de reintentar
            if (attempt < maxRetries - 1) {
                Log.d(TAG, "UDP: Retry $attempt failed for $serverIP:$port, retrying...")
                kotlinx.coroutines.delay(Constants.RETRY_DELAY)
            }
        }

        Log.w(TAG, "UDP: All $maxRetries attempts failed for $serverIP:$port")
        return UdpResult.Error("Failed after $maxRetries attempts")
    }

    /**
     * Enviar múltiples paquetes UDP (útil para datos grandes)
     *
     * @param serverIP IP del servidor
     * @param port Puerto UDP
     * @param dataList Lista de strings a enviar como paquetes separados
     * @return Lista de resultados para cada paquete
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
     * Enviar datos con confirmación de recepción (requiere servidor que responda)
     * Nota: Esto convierte UDP en un protocolo más parecido a TCP
     *
     * @param serverIP IP del servidor
     * @param port Puerto UDP
     * @param data Datos a enviar
     * @param expectResponse Si esperar respuesta del servidor
     * @return Resultado del envío y respuesta si la hay
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

        // Si esperamos respuesta, intentar recibirla
        try {
            DatagramSocket().use { socket ->
                socket.soTimeout = 2000 // 2 segundos para recibir respuesta

                val buffer = ByteArray(1024)
                val packet = DatagramPacket(buffer, buffer.size)

                socket.receive(packet)
                val response = String(packet.data, 0, packet.length, Charsets.UTF_8)

                Log.d(TAG, "UDP: Received response from $serverIP:$port - $response")
                return@withContext Pair(sendResult, response)
            }

        } catch (e: SocketTimeoutException) {
            Log.d(TAG, "UDP: No response received from $serverIP:$port")
            return@withContext Pair(sendResult, null)

        } catch (e: Exception) {
            Log.w(TAG, "UDP: Error receiving response from $serverIP:$port", e)
            return@withContext Pair(sendResult, null)
        }
    }
}