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
 * UDP client for sending location data.
 * This class provides functionality for sending data over UDP, including handling packet size limits,
 * timeouts, error conditions, retries, and optional response handling.
 */
class UdpClient {

    companion object {
        // Tag for logging messages related to UDP operations.
        private const val TAG = Constants.Logs.TAG_NETWORK
        // Maximum size for a UDP packet. This is a common practical limit, though theoretical limits are higher.
        private const val MAX_UDP_PACKET_SIZE = 1024
    }

    /**
     * Sealed class representing the possible outcomes of a UDP send operation.
     * This provides a clear and exhaustive way to handle different results.
     */
    sealed class UdpResult {
        object Success : UdpResult() // Indicates that data was sent successfully.
        data class Error(val message: String, val exception: Throwable? = null) : UdpResult() // Indicates an error occurred.
        object Timeout : UdpResult() // Indicates that the operation timed out.
    }

    /**
     * Sends data via UDP to a specific server.
     * This method wraps the actual send operation with a timeout and handles data size validation.
     *
     * @param serverIP The IP address of the destination server.
     * @param port The UDP port (defaults to [Constants.UDP_PORT], typically 6001).
     * @param data The string data to be sent.
     * @return A [UdpResult] indicating the outcome of the send operation.
     */
    suspend fun sendData(
        serverIP: String,
        port: Int = Constants.UDP_PORT,
        data: String
    ): UdpResult = withContext(Dispatchers.IO) { // Ensure this runs on an IO dispatcher.

        Log.d(TAG, "UDP: Attempting to send to $serverIP:$port - Data: $data")

        try {
            val dataBytes = data.toByteArray(Charsets.UTF_8)
            // Check if the data size exceeds the maximum UDP packet size.
            if (dataBytes.size > MAX_UDP_PACKET_SIZE) {
                Log.w(TAG, "UDP: Data size (${dataBytes.size}) exceeds max packet size ($MAX_UDP_PACKET_SIZE)")
                return@withContext UdpResult.Error("Data too large for UDP packet")
            }

            // Use `withTimeoutOrNull` to impose a strict timeout on the entire send operation.
            // If the operation exceeds `Constants.NETWORK_TIMEOUT`, `result` will be null.
            val result = withTimeoutOrNull(Constants.NETWORK_TIMEOUT.toLong()) {
                performUdpSend(serverIP, port, dataBytes) // Delegate to the actual send logic.
            }

            // If `result` is null, it means a timeout occurred. Log and return Timeout.
            return@withContext result ?: UdpResult.Timeout.also {
                Log.w(TAG, "UDP: Timeout sending to $serverIP:$port")
            }

        } catch (e: Exception) {
            // Catch any unexpected exceptions that might occur outside of `performUdpSend` or `withTimeoutOrNull`.
            Log.e(TAG, "UDP: Error sending to $serverIP:$port", e)
            return@withContext UdpResult.Error(
                message = "UDP Error: ${e.message}",
                exception = e
            )
        }
    }

    /**
     * Performs the actual UDP data transmission.
     *
     * @param serverIP The IP address of the destination server.
     * @param port The UDP port.
     * @param dataBytes The data as a byte array to be sent.
     * @return A [UdpResult] indicating the outcome.
     */
    private suspend fun performUdpSend(
        serverIP: String,
        port: Int,
        dataBytes: ByteArray
    ): UdpResult = withContext(Dispatchers.IO) {

        var socket: DatagramSocket? = null

        try {
            // Create a new DatagramSocket.
            socket = DatagramSocket().apply {
                // Set a timeout for socket operations (e.g., receive).
                soTimeout = Constants.NETWORK_TIMEOUT
                // UDP is a connectionless protocol, so no `connect` call is explicitly needed here for sending.
            }

            // Resolve the server IP address to an InetAddress object.
            val address = InetAddress.getByName(serverIP)

            // Create a DatagramPacket containing the data, destination address, and port.
            val packet = DatagramPacket(
                dataBytes, // The actual data bytes.
                dataBytes.size, // The length of the data.
                address, // The destination IP address.
                port // The destination port.
            )

            // Send the packet over the UDP socket.
            socket.send(packet)

            Log.d(TAG, "UDP: Successfully sent to $serverIP:$port (${dataBytes.size} bytes)")
            return@withContext UdpResult.Success

        } catch (e: SocketTimeoutException) {
            // Handle specific SocketTimeoutException for operations like receive (if implemented).
            Log.w(TAG, "UDP: Socket timeout for $serverIP:$port", e)
            return@withContext UdpResult.Timeout

        } catch (e: IOException) {
            // Handle general IO exceptions (e.g., network issues).
            Log.e(TAG, "UDP: IO error for $serverIP:$port", e)
            return@withContext UdpResult.Error(
                message = "UDP IO Error: ${e.message}",
                exception = e
            )

        } catch (e: Exception) {
            // Catch any other unexpected exceptions.
            Log.e(TAG, "UDP: Unexpected error for $serverIP:$port", e)
            return@withContext UdpResult.Error(
                message = "UDP Unexpected Error: ${e.message}",
                exception = e
            )

        } finally {
            // Ensure resources are closed in all cases to prevent leaks.
            try {
                socket?.close() // Close the DatagramSocket.
            } catch (e: Exception) {
                Log.w(TAG, "UDP: Error closing socket", e) // Log any errors during cleanup.
            }
        }
    }

    /**
     * Tests UDP connectivity to a server.
     * Note: UDP is a connectionless protocol. This "test" primarily verifies that
     * an `InetAddress` can be resolved and a `DatagramSocket` can be created,
     * it does NOT guarantee that a packet will be received by the server.
     *
     * @param serverIP The IP address of the server (used for format validation).
     * @param port The UDP port.
     * @return `true` if a socket can be created and the IP is valid, `false` otherwise.
     */
    suspend fun testConnection(
        serverIP: String,
        port: Int = Constants.UDP_PORT
    ): Boolean = withContext(Dispatchers.IO) {

        try {
            // Validate the IP address by attempting to resolve it.
            InetAddress.getByName(serverIP)

            // Try to create and immediately close a DatagramSocket.
            // `use` ensures the socket is closed automatically.
            DatagramSocket().use { socket ->
                socket.soTimeout = 1000 // Set a very short timeout for the test socket.
                true // If we reach here, it means we could resolve the IP and open a socket.
            }

        } catch (e: Exception) {
            // Log a debug message for failed connection tests.
            Log.d(TAG, "UDP: Test connection failed for $serverIP:$port - ${e.message}")
            false // Return false if any exception occurs during the test.
        }
    }

    /**
     * Sends data via UDP to a server with automatic retries in case of failure (e.g., timeout).
     *
     * @param serverIP The IP address of the server.
     * @param port The UDP port.
     * @param data The data string to send.
     * @param maxRetries The maximum number of retry attempts (defaults to [Constants.MAX_RETRY_ATTEMPTS]).
     * @return The final [UdpResult] after all attempts have been made.
     */
    suspend fun sendDataWithRetry(
        serverIP: String,
        port: Int = Constants.UDP_PORT,
        data: String,
        maxRetries: Int = Constants.MAX_RETRY_ATTEMPTS
    ): UdpResult {

        repeat(maxRetries) { attempt -> // Loop for the specified number of retries.
            val result = sendData(serverIP, port, data) // Attempt to send data.

            if (result is UdpResult.Success) {
                // If successful, log if it was a retry and then return success.
                if (attempt > 0) {
                    Log.d(TAG, "UDP: Success on retry $attempt for $serverIP:$port")
                }
                return result
            }

            // If it's not the last attempt, log and introduce a delay before retrying.
            if (attempt < maxRetries - 1) {
                Log.d(TAG, "UDP: Retry $attempt failed for $serverIP:$port, retrying in ${Constants.RETRY_DELAY}ms...")
                kotlinx.coroutines.delay(Constants.RETRY_DELAY) // Pause for the defined retry delay.
            }
        }

        // If all attempts fail, log a warning and return an error result.
        Log.w(TAG, "UDP: All $maxRetries attempts failed for $serverIP:$port")
        return UdpResult.Error("Failed after $maxRetries attempts")
    }

    /**
     * Sends a list of data strings as individual UDP packets.
     * This is useful when the total data size exceeds the maximum single UDP packet size,
     * requiring the data to be fragmented and sent as multiple packets.
     *
     * @param serverIP The IP address of the server.
     * @param port The UDP port.
     * @param dataList A list of strings, where each string will be sent as a separate UDP packet.
     * @return A list of [UdpResult] indicating the outcome for each packet sent.
     */
    suspend fun sendMultiplePackets(
        serverIP: String,
        port: Int = Constants.UDP_PORT,
        dataList: List<String>
    ): List<UdpResult> = withContext(Dispatchers.IO) {

        // Use `map` to send each data string in the list and collect their results.
        dataList.map { data ->
            sendData(serverIP, port, data)
        }
    }

    /**
     * Sends data via UDP and optionally waits for a confirmation response from the server.
     * Note: This method implements a basic request-response pattern over UDP,
     * effectively making it behave more like a reliable protocol, but it relies
     * on the server being configured to send a response.
     *
     * @param serverIP The IP address of the server.
     * @param port The UDP port.
     * @param data The data string to send.
     * @param expectResponse If `true`, the client will wait for a response after sending. Defaults to `false`.
     * @return A [Pair] where the first element is the [UdpResult] of the send operation,
     * and the second element is the received response string, or `null` if no response was expected or received.
     */
    suspend fun sendDataWithConfirmation(
        serverIP: String,
        port: Int = Constants.UDP_PORT,
        data: String,
        expectResponse: Boolean = false
    ): Pair<UdpResult, String?> = withContext(Dispatchers.IO) {

        // First, attempt to send the data.
        val sendResult = sendData(serverIP, port, data)

        // If no response is expected or the send itself failed, return immediately.
        if (!expectResponse || sendResult !is UdpResult.Success) {
            return@withContext Pair(sendResult, null)
        }

        // If a response is expected and data was sent successfully, attempt to receive it.
        try {
            DatagramSocket().use { socket ->
                // Set a timeout for receiving the response.
                socket.soTimeout = 2000 // 2 seconds to wait for a response.

                val buffer = ByteArray(1024) // Buffer for incoming data.
                val packet = DatagramPacket(buffer, buffer.size) // Packet to hold the received data.

                socket.receive(packet) // Block until a packet is received or timeout occurs.
                val response = String(packet.data, 0, packet.length, Charsets.UTF_8) // Convert received bytes to string.

                Log.d(TAG, "UDP: Received response from $serverIP:$port - $response")
                return@withContext Pair(sendResult, response) // Return success with the response.
            }

        } catch (e: SocketTimeoutException) {
            // Log if no response is received within the timeout.
            Log.d(TAG, "UDP: No response received from $serverIP:$port")
            return@withContext Pair(sendResult, null) // Return success of send, but no response.

        } catch (e: Exception) {
            // Log any other errors during the response reception phase.
            Log.w(TAG, "UDP: Error receiving response from $serverIP:$port", e)
            return@withContext Pair(sendResult, null) // Return success of send, but no response due to error.
        }
    }
}