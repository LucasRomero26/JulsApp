package com.tudominio.smslocation.model.network

import android.content.Context
import android.util.Log
import com.tudominio.smslocation.util.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL

/**
 * Utility to automatically install and run the Cloudflare `cloudflared` binary.
 * This allows the application to establish a local TCP tunnel without manual setup.
 */
class CloudflareTunnel(private val context: Context) {

    companion object {
        private const val TAG = Constants.Logs.TAG_NETWORK
    }

    private val binaryFile: File = File(context.filesDir, "cloudflared")

    /**
     * Ensures that the Cloudflare tunnel is installed and running. If the binary
     * is missing, it will be downloaded from the official release URL.
     */
    suspend fun ensureTunnel() = withContext(Dispatchers.IO) {
        try {
            if (!binaryFile.exists()) {
                downloadBinary()
                binaryFile.setExecutable(true)
            }
            startTunnel()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start Cloudflare tunnel", e)
        }
    }

    // Downloads the `cloudflared` binary into the app's internal storage.
    private fun downloadBinary() {
        URL(Constants.CLOUDFLARE_BINARY_URL).openStream().use { input ->
            FileOutputStream(binaryFile).use { output ->
                input.copyTo(output)
            }
        }
    }

    // Starts the Cloudflare tunnel process with the configured hostname and local port.
    private fun startTunnel() {
        ProcessBuilder(
            binaryFile.absolutePath,
            "access",
            "tcp",
            "--hostname",
            Constants.CLOUDFLARE_TUNNEL_HOSTNAME,
            "--url",
            "localhost:${Constants.CLOUDFLARE_LOCAL_PORT}"
        ).redirectErrorStream(true).start()
    }
}
