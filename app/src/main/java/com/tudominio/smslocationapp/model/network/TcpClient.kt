package com.tudominio.smslocation.model.network

import android.util.Log
import com.tudominio.smslocation.util.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.PrintWriter
import java.net.Socket
import java.net.SocketTimeoutException
import java.io.IOException

/**
 * Cliente TCP para envío de datos de ubicación
 */
class TcpClient {

    companion object {
        private const val TAG = Constants.Logs.TAG_NETWORK
    }

    /**
     * Resultado del envío TCP
     */
    sealed class TcpResult {
        object Success : TcpResult()
        data class Error(val message: String, val exception: Throwable? = null) : TcpResult()
        object Timeout : TcpResult()
    }

    /**
     * Enviar datos via TCP a un servidor específico
     *
     * @param serverIP IP del servidor destino
     * @param port Puerto TCP (normalmente 6000)
     * @param data Datos a enviar
     * @return Resultado del envío
     */
    suspend fun sendData(
        serverIP: String,
        port: Int = Constants.TCP_PORT,
        data: String
    ): TcpResult = withContext(Dispatchers.IO) {

        Log.d(TAG, "TCP: Attempting to send to $serverIP:$port - Data: $data")

        try {
            // Usar timeout para evitar bloqueos indefinidos
            val result = withTimeoutOrNull(Constants.NETWORK_TIMEOUT.toLong()) {
                performTcpSend(serverIP, port, data)
            }

            return@withContext result ?: TcpResult.Timeout.also {
                Log.w(TAG, "TCP: Timeout sending to $serverIP:$port")
            }

        } catch (e: Exception) {
            Log.e(TAG, "TCP: Error sending to $serverIP:$port", e)
            return@withContext TcpResult.Error(
                message = "TCP Error: ${e.message}",
                exception = e
            )
        }
    }

    /**
     * Realizar el envío TCP real con headers JSON
     */
    private suspend fun performTcpSend(
        serverIP: String,
        port: Int,
        data: String
    ): TcpResult = withContext(Dispatchers.IO) {

        var socket: Socket? = null
        var writer: PrintWriter? = null

        try {
            // Crear socket con timeout
            socket = Socket().apply {
                soTimeout = Constants.NETWORK_TIMEOUT
                tcpNoDelay = true // Enviar inmediatamente sin buffer
            }

            // Conectar al servidor
            socket.connect(
                java.net.InetSocketAddress(serverIP, port),
                Constants.NETWORK_TIMEOUT
            )

            if (!socket.isConnected) {
                return@withContext TcpResult.Error("Failed to connect to $serverIP:$port")
            }

            // Crear headers HTTP para JSON (opcional, mejora compatibilidad con servidores web)
            val httpRequest = buildString {
                appendLine("POST / HTTP/1.1")
                appendLine("Host: $serverIP:$port")
                appendLine("Content-Type: ${Constants.DataFormat.CONTENT_TYPE}")
                appendLine("Content-Length: ${data.toByteArray(Charsets.UTF_8).size}")
                appendLine("Connection: close")
                appendLine() // Línea vacía antes del body
                append(data)
            }

            // Enviar datos con headers HTTP
            writer = PrintWriter(socket.getOutputStream(), true)
            writer.print(httpRequest)
            writer.flush()

            // Verificar que el writer no tenga errores
            if (writer.checkError()) {
                return@withContext TcpResult.Error("Error writing JSON data to TCP stream")
            }

            Log.d(TAG, "TCP: Successfully sent JSON to $serverIP:$port")
            return@withContext TcpResult.Success

        } catch (e: SocketTimeoutException) {
            Log.w(TAG, "TCP: Socket timeout for $serverIP:$port", e)
            return@withContext TcpResult.Timeout

        } catch (e: IOException) {
            Log.e(TAG, "TCP: IO error for $serverIP:$port", e)
            return@withContext TcpResult.Error(
                message = "TCP IO Error: ${e.message}",
                exception = e
            )

        } catch (e: Exception) {
            Log.e(TAG, "TCP: Unexpected error for $serverIP:$port", e)
            return@withContext TcpResult.Error(
                message = "TCP Unexpected Error: ${e.message}",
                exception = e
            )

        } finally {
            // Cleanup resources
            try {
                writer?.close()
                socket?.close()
            } catch (e: Exception) {
                Log.w(TAG, "TCP: Error closing resources", e)
            }
        }
    }

    /**
     * Verificar conectividad TCP a un servidor
     *
     * @param serverIP IP del servidor
     * @param port Puerto TCP
     * @return true si se puede conectar, false en caso contrario
     */
    suspend fun testConnection(
        serverIP: String,
        port: Int = Constants.TCP_PORT
    ): Boolean = withContext(Dispatchers.IO) {

        try {
            withTimeoutOrNull(3000L) { // Timeout más corto para test
                Socket().use { socket ->
                    socket.soTimeout = 3000
                    socket.connect(
                        java.net.InetSocketAddress(serverIP, port),
                        3000
                    )
                    socket.isConnected
                }
            } ?: false

        } catch (e: Exception) {
            Log.d(TAG, "TCP: Test connection failed for $serverIP:$port - ${e.message}")
            false
        }
    }

    /**
     * Enviar datos con reintentos automáticos
     *
     * @param serverIP IP del servidor
     * @param port Puerto TCP
     * @param data Datos a enviar
     * @param maxRetries Número máximo de reintentos
     * @return Resultado final después de todos los intentos
     */
    suspend fun sendDataWithRetry(
        serverIP: String,
        port: Int = Constants.TCP_PORT,
        data: String,
        maxRetries: Int = Constants.MAX_RETRY_ATTEMPTS
    ): TcpResult {

        repeat(maxRetries) { attempt ->
            val result = sendData(serverIP, port, data)

            if (result is TcpResult.Success) {
                if (attempt > 0) {
                    Log.d(TAG, "TCP: Success on retry $attempt for $serverIP:$port")
                }
                return result
            }

            // Si no es el último intento, esperar antes de reintentar
            if (attempt < maxRetries - 1) {
                Log.d(TAG, "TCP: Retry $attempt failed for $serverIP:$port, retrying...")
                kotlinx.coroutines.delay(Constants.RETRY_DELAY)
            }
        }

        Log.w(TAG, "TCP: All $maxRetries attempts failed for $serverIP:$port")
        return TcpResult.Error("Failed after $maxRetries attempts")
    }
}