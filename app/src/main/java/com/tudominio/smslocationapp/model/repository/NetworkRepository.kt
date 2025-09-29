package com.tudominio.smslocation.model.repository

import android.content.Context
import android.util.Log
import com.tudominio.smslocation.model.data.LocationData
import com.tudominio.smslocation.util.Constants
import kotlinx.coroutines.*
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * Repository ultra-simple - ENVÍA UDP a los 4 SERVIDORES, sin colas ni memoria
 */
class SimpleNetworkRepository(private val context: Context) {

    companion object {
        private const val TAG = Constants.Logs.TAG_NETWORK
        private const val UDP_TIMEOUT = 2000 // 2 segundos máximo
    }

    // Métodos para envío a cada servidor individualmente
    suspend fun sendToServer1(locationData: LocationData) {
        sendUDP(Constants.SERVER_IP_1, locationData, "Server1")
    }

    suspend fun sendToServer2(locationData: LocationData) {
        sendUDP(Constants.SERVER_IP_2, locationData, "Server2")
    }

    suspend fun sendToServer3(locationData: LocationData) {
        sendUDP(Constants.SERVER_IP_3, locationData, "Server3")
    }

    suspend fun sendToServer4(locationData: LocationData) {
        sendUDP(Constants.SERVER_IP_4, locationData, "Server4")
    }

    private suspend fun sendUDP(serverIP: String, locationData: LocationData, serverName: String) = withContext(Dispatchers.IO) {
        // Verificar que la IP no esté vacía
        if (serverIP.isBlank()) {
            Log.w(TAG, "$serverName: IP address is empty, skipping")
            return@withContext
        }

        var socket: DatagramSocket? = null

        try {
            withTimeoutOrNull(UDP_TIMEOUT.toLong()) {
                socket = DatagramSocket()
                socket!!.soTimeout = UDP_TIMEOUT

                val jsonData = locationData.toJsonFormat()
                val dataBytes = jsonData.toByteArray()
                val address = InetAddress.getByName(serverIP)
                val packet = DatagramPacket(dataBytes, dataBytes.size, address, Constants.UDP_PORT)

                socket!!.send(packet)
                Log.d(TAG, "$serverName UDP SUCCESS to $serverIP: ${locationData.getFormattedCoordinates()}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "$serverName UDP FAILED to $serverIP: ${e.message}")
        } finally {
            try {
                socket?.close()
            } catch (e: Exception) {
                // Ignorar errores de cierre
            }
        }
    }

    // Métodos de test para cada servidor
    suspend fun testServer1(): Boolean = testUDP(Constants.SERVER_IP_1, "Server1")
    suspend fun testServer2(): Boolean = testUDP(Constants.SERVER_IP_2, "Server2")
    suspend fun testServer3(): Boolean = testUDP(Constants.SERVER_IP_3, "Server3")
    suspend fun testServer4(): Boolean = testUDP(Constants.SERVER_IP_4, "Server4")

    private suspend fun testUDP(serverIP: String, serverName: String): Boolean = withContext(Dispatchers.IO) {
        if (serverIP.isBlank()) {
            Log.d(TAG, "$serverName: IP address is empty, test failed")
            return@withContext false
        }

        try {
            withTimeoutOrNull(2000L) {
                InetAddress.getByName(serverIP)
                Log.d(TAG, "$serverName connection test PASSED for $serverIP")
                true
            } ?: false
        } catch (e: Exception) {
            Log.d(TAG, "$serverName connection test FAILED for $serverIP: ${e.message}")
            false
        }
    }

    // Método para test de conectividad completo
    suspend fun testAllServers(): Map<String, Boolean> {
        return mapOf(
            "server1" to testServer1(),
            "server2" to testServer2(),
            "server3" to testServer3(),
            "server4" to testServer4()
        )
    }

    fun cleanup() {
        // Nada que limpiar - sin estado persistente
        Log.d(TAG, "SimpleNetworkRepository cleaned up")
    }
}