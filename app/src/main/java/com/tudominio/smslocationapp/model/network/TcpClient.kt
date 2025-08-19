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
 * TCP client for sending location data.
 * This class handles the low-level TCP communication, including connection, data writing,
 * timeouts, and error handling. It also supports sending data with HTTP headers for compatibility.
 */
class TcpClient {

    companion object {
        // Tag for logging messages related to TCP operations.
        private const val TAG = Constants.Logs.TAG_NETWORK
    }

    /**
     * Sealed class representing the possible outcomes of a TCP send operation.
     * This provides a clear and exhaustive way to handle different results.
     */
    sealed class TcpResult {
        object Success : TcpResult() // Indicates that data was sent successfully.
        data class Error(val message: String, val exception: Throwable? = null) : TcpResult() // Indicates an error occurred.
        object Timeout : TcpResult() // Indicates that the operation timed out.
    }

    /**
     * Sends data via TCP to a specific server.
     * This method wraps the actual send operation with a timeout.
     *
     * @param serverIP The IP address of the destination server.
     * @param port The TCP port (defaults to [Constants.TCP_PORT], typically 6000).
     * @param data The string data to be sent.
     * @return A [TcpResult] indicating the outcome of the send operation.
     */
    suspend fun sendData(
        serverIP: String,
        port: Int = Constants.TCP_PORT,
        data: String
    ): TcpResult = withContext(Dispatchers.IO) { // Ensure this runs on an IO dispatcher.

        Log.d(TAG, "TCP: Attempting to send to $serverIP:$port - Data: $data")

        try {
            // Use `withTimeoutOrNull` to impose a strict timeout on the entire send operation.
            // If the operation exceeds `Constants.NETWORK_TIMEOUT`, `result` will be null.
            val result = withTimeoutOrNull(Constants.NETWORK_TIMEOUT.toLong()) {
                performTcpSend(serverIP, port, data) // Delegate to the actual send logic.
            }

            // If `result` is null, it means a timeout occurred. Log and return Timeout.
            return@withContext result ?: TcpResult.Timeout.also {
                Log.w(TAG, "TCP: Timeout sending to $serverIP:$port")
            }

        } catch (e: Exception) {
            // Catch any unexpected exceptions that might occur outside of `performTcpSend` or `withTimeoutOrNull`.
            Log.e(TAG, "TCP: Error sending to $serverIP:$port", e)
            return@withContext TcpResult.Error(
                message = "TCP Error: ${e.message}",
                exception = e
            )
        }
    }

    /**
     * Performs the actual TCP data transmission, including socket creation, connection,
     * and writing data with optional JSON HTTP headers.
     *
     * @param serverIP The IP address of the destination server.
     * @param port The TCP port.
     * @param data The string data (expected to be JSON) to be sent.
     * @return A [TcpResult] indicating the outcome.
     */
    private suspend fun performTcpSend(
        serverIP: String,
        port: Int,
        data: String
    ): TcpResult = withContext(Dispatchers.IO) {

        var socket: Socket? = null
        var writer: PrintWriter? = null

        try {
            // Create a new socket.
            socket = Socket().apply {
                // Set the socket read timeout to prevent indefinite blocking on input stream operations.
                soTimeout = Constants.NETWORK_TIMEOUT
                // Enable TCP_NODELAY to disable the Nagle algorithm, ensuring data is sent immediately.
                tcpNoDelay = true
            }

            // Connect to the server with a specific connection timeout.
            socket.connect(
                java.net.InetSocketAddress(serverIP, port),
                Constants.NETWORK_TIMEOUT
            )

            // Verify if the socket is successfully connected.
            if (!socket.isConnected) {
                return@withContext TcpResult.Error("Failed to connect to $serverIP:$port")
            }

            // Construct HTTP POST request headers for JSON content.
            // This makes the data compatible with web servers or applications expecting HTTP.
            val httpRequest = buildString {
                appendLine("POST / HTTP/1.1") // Standard HTTP POST method and protocol version.
                appendLine("Host: $serverIP:$port") // Specifies the host and port.
                appendLine("Content-Type: ${Constants.DataFormat.CONTENT_TYPE}") // Declare content type as JSON.
                // Calculate content length based on UTF-8 byte size of the data.
                appendLine("Content-Length: ${data.toByteArray(Charsets.UTF_8).size}")
                appendLine("Connection: close") // Request to close the connection after the response.
                appendLine() // An empty line is required between headers and body in HTTP.
                append(data) // Append the actual JSON data (body of the request).
            }

            // Create a PrintWriter to write data to the socket's output stream.
            // `true` in PrintWriter constructor enables auto-flushing.
            writer = PrintWriter(socket.getOutputStream(), true)
            writer.print(httpRequest) // Write the complete HTTP request.
            writer.flush() // Ensure all buffered data is sent immediately.

            // Check for errors that might have occurred during the write operation.
            if (writer.checkError()) {
                return@withContext TcpResult.Error("Error writing JSON data to TCP stream")
            }

            Log.d(TAG, "TCP: Successfully sent JSON to $serverIP:$port")
            return@withContext TcpResult.Success

        } catch (e: SocketTimeoutException) {
            // Handle specific SocketTimeoutException for connection or read/write timeouts.
            Log.w(TAG, "TCP: Socket timeout for $serverIP:$port", e)
            return@withContext TcpResult.Timeout

        } catch (e: IOException) {
            // Handle general IO exceptions (e.g., network issues, broken pipe).
            Log.e(TAG, "TCP: IO error for $serverIP:$port", e)
            return@withContext TcpResult.Error(
                message = "TCP IO Error: ${e.message}",
                exception = e
            )

        } catch (e: Exception) {
            // Catch any other unexpected exceptions.
            Log.e(TAG, "TCP: Unexpected error for $serverIP:$port", e)
            return@withContext TcpResult.Error(
                message = "TCP Unexpected Error: ${e.message}",
                exception = e
            )

        } finally {
            // Ensure resources are closed in all cases to prevent leaks.
            try {
                writer?.close() // Close the PrintWriter.
                socket?.close() // Close the socket.
            } catch (e: Exception) {
                Log.w(TAG, "TCP: Error closing resources", e) // Log any errors during cleanup.
            }
        }
    }

    /**
     * Tests TCP connectivity to a specific server by attempting to establish a connection.
     * This is a quick check to see if the server is reachable on the given port.
     *
     * @param serverIP The IP address of the server.
     * @param port The TCP port.
     * @return `true` if a connection can be established within the timeout, `false` otherwise.
     */
    suspend fun testConnection(
        serverIP: String,
        port: Int = Constants.TCP_PORT
    ): Boolean = withContext(Dispatchers.IO) {

        try {
            // Use `withTimeoutOrNull` for a shorter timeout specifically for connection tests.
            withTimeoutOrNull(3000L) { // Timeout set to 3 seconds.
                Socket().use { socket -> // `use` ensures the socket is closed automatically.
                    socket.soTimeout = 3000 // Set read timeout for the socket.
                    socket.connect(
                        java.net.InetSocketAddress(serverIP, port),
                        3000 // Connection timeout.
                    )
                    socket.isConnected // Return true if connected.
                }
            } ?: false // If timeout occurs, `withTimeoutOrNull` returns null, so return false.

        } catch (e: Exception) {
            // Log a debug message for failed connection tests (less severe than send errors).
            Log.d(TAG, "TCP: Test connection failed for $serverIP:$port - ${e.message}")
            false // Return false if any exception occurs during the test.
        }
    }

    /**
     * Sends data via TCP to a server with automatic retries in case of failure.
     *
     * @param serverIP The IP address of the server.
     * @param port The TCP port.
     * @param data The data string to send.
     * @param maxRetries The maximum number of retry attempts (defaults to [Constants.MAX_RETRY_ATTEMPTS]).
     * @return The final [TcpResult] after all attempts have been made.
     */
    suspend fun sendDataWithRetry(
        serverIP: String,
        port: Int = Constants.TCP_PORT,
        data: String,
        maxRetries: Int = Constants.MAX_RETRY_ATTEMPTS
    ): TcpResult {

        repeat(maxRetries) { attempt -> // Loop for the specified number of retries.
            val result = sendData(serverIP, port, data) // Attempt to send data.

            if (result is TcpResult.Success) {
                // If successful, log if it was a retry and then return success.
                if (attempt > 0) {
                    Log.d(TAG, "TCP: Success on retry $attempt for $serverIP:$port")
                }
                return result
            }

            // If it's not the last attempt, log and introduce a delay before retrying.
            if (attempt < maxRetries - 1) {
                Log.d(TAG, "TCP: Retry $attempt failed for $serverIP:$port, retrying in ${Constants.RETRY_DELAY}ms...")
                kotlinx.coroutines.delay(Constants.RETRY_DELAY) // Pause for the defined retry delay.
            }
        }

        // If all attempts fail, log a warning and return an error result.
        Log.w(TAG, "TCP: All $maxRetries attempts failed for $serverIP:$port")
        return TcpResult.Error("Failed after $maxRetries attempts")
    }
}