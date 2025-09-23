package com.tudominio.smslocation.model.repository

import android.content.Context
import android.util.Log
import com.tudominio.smslocation.model.data.LocationData
import com.tudominio.smslocation.util.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * Repository ultra-simple - SOLO envía UDP, sin colas ni memoria
 */
class SimpleNetworkRepository(private val context: Context) {

    companion object {
        private const val TAG = Constants.Logs.TAG_NETWORK
        private const val UDP_TIMEOUT = 1000 // 1 segundo máximo
    }

    suspend fun sendToServer1(locationData: LocationData) {
        sendUDP(Constants.SERVER_IP_1, locationData)
    }

    suspend fun sendToServer2(locationData: LocationData) {
        sendUDP(Constants.SERVER_IP_2, locationData)
    }

    private suspend fun sendUDP(serverIP: String, locationData: LocationData) = withContext(Dispatchers.IO) {
        var socket: DatagramSocket? = null

        try {
            withTimeoutOrNull(UDP_TIMEOUT.toLong()) {
                socket = DatagramSocket()

                val jsonData = locationData.toJsonFormat()
                val dataBytes = jsonData.toByteArray()
                val address = InetAddress.getByName(serverIP)
                val packet = DatagramPacket(dataBytes, dataBytes.size, address, Constants.UDP_PORT)

                socket!!.send(packet)
                Log.d(TAG, "UDP sent to $serverIP: ${locationData.getFormattedCoordinates()}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "UDP failed to $serverIP: ${e.message}")
        } finally {
            try {
                socket?.close()
            } catch (e: Exception) {
                // Ignorar errores de cierre
            }
        }
    }

    suspend fun testServer1(): Boolean = testUDP(Constants.SERVER_IP_1)
    suspend fun testServer2(): Boolean = testUDP(Constants.SERVER_IP_2)

    private suspend fun testUDP(serverIP: String): Boolean = withContext(Dispatchers.IO) {
        try {
            withTimeoutOrNull(2000L) {
                InetAddress.getByName(serverIP)
                true
            } ?: false
        } catch (e: Exception) {
            Log.d(TAG, "Test failed for $serverIP")
            false
        }
    }

    fun cleanup() {
        // Nada que limpiar - sin estado persistente
    }
}