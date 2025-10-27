package com.tudominio.smslocation.streaming

import android.content.Context
import android.util.Log
import com.tudominio.smslocation.util.Constants
import com.tudominio.smslocation.util.DeviceUtils
import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONObject
import org.webrtc.*
import java.net.URISyntaxException

class WebRTCManager(private val context: Context) {

    companion object {
        private const val TAG = "WebRTCManager"

        private val STUN_SERVERS = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun2.l.google.com:19302").createIceServer()
        )
    }

    private var socket: Socket? = null
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var localVideoSource: VideoSource? = null
    private var videoCapturer: CameraVideoCapturer? = null
    private val peerConnections = mutableMapOf<String, PeerConnection>()

    // ✅ CORREGIDO: Pasar context a getDeviceName()
    private val deviceId = DeviceUtils.getDeviceId(context)
    private val deviceName = DeviceUtils.getDeviceName(context)

    private var isInitialized = false

    // ✨ EglBase para contexto de renderizado
    private var eglBase: EglBase? = null

    /**
     * Inicializar WebRTC y conectar al servidor de señalización
     */
    fun initialize() {
        if (isInitialized) {
            Log.w(TAG, "WebRTC already initialized")
            return
        }

        try {
            Log.d(TAG, "Initializing WebRTC...")

            // 1. Crear EglBase
            eglBase = EglBase.create()

            // 2. Inicializar PeerConnectionFactory
            initializePeerConnectionFactory()

            // 3. Configurar captura de cámara
            setupVideoCapture()

            // 4. Conectar al servidor de señalización
            connectToSignalingServer()

            isInitialized = true
            Log.d(TAG, "✅ WebRTC initialized successfully")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error initializing WebRTC", e)
        }
    }

    private fun initializePeerConnectionFactory() {
        val options = PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(true)
            .setFieldTrials("WebRTC-H264HighProfile/Enabled/")
            .createInitializationOptions()

        PeerConnectionFactory.initialize(options)

        val encoderFactory = DefaultVideoEncoderFactory(
            eglBase?.eglBaseContext,
            true, // enableIntelVp8Encoder
            true  // enableH264HighProfile
        )

        val decoderFactory = DefaultVideoDecoderFactory(eglBase?.eglBaseContext)

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .createPeerConnectionFactory()

        Log.d(TAG, "PeerConnectionFactory created")
    }

    private fun setupVideoCapture() {
        val enumerator = Camera2Enumerator(context)

        // Buscar cámara trasera
        val cameraName = enumerator.deviceNames.firstOrNull { enumerator.isBackFacing(it) }
            ?: enumerator.deviceNames.firstOrNull()

        if (cameraName == null) {
            Log.e(TAG, "No camera found on device")
            return
        }

        videoCapturer = enumerator.createCapturer(cameraName, null)
        localVideoSource = peerConnectionFactory?.createVideoSource(videoCapturer?.isScreencast ?: false)

        val surfaceTextureHelper = SurfaceTextureHelper.create(
            "CaptureThread",
            eglBase?.eglBaseContext
        )

        videoCapturer?.initialize(
            surfaceTextureHelper,
            context,
            localVideoSource?.capturerObserver
        )

        // Iniciar captura: 640x480 @ 15fps
        videoCapturer?.startCapture(
            Constants.VIDEO_WIDTH,
            Constants.VIDEO_HEIGHT,
            Constants.VIDEO_FPS
        )

        Log.d(TAG, "Video capture started: ${Constants.VIDEO_WIDTH}x${Constants.VIDEO_HEIGHT} @ ${Constants.VIDEO_FPS}fps")
    }

    private fun connectToSignalingServer() {
        try {
            val serverUrl = "http://${Constants.SERVER_IP_1}:3001"
            Log.d(TAG, "Connecting to signaling server: $serverUrl")

            socket = IO.socket(serverUrl)

            setupSocketListeners()

            socket?.connect()

        } catch (e: URISyntaxException) {
            Log.e(TAG, "Invalid server URL", e)
        }
    }

    private fun setupSocketListeners() {
        socket?.on(Socket.EVENT_CONNECT) {
            Log.d(TAG, "✅ Connected to signaling server")

            // Registrarse como broadcaster
            val registerData = JSONObject().apply {
                put("deviceId", deviceId)
                put("deviceName", deviceName)
            }
            socket?.emit("register-broadcaster", registerData)
        }

        socket?.on(Socket.EVENT_DISCONNECT) {
            Log.w(TAG, "Disconnected from signaling server")
        }

        socket?.on(Socket.EVENT_CONNECT_ERROR) { args ->
            Log.e(TAG, "Connection error: ${args.getOrNull(0)}")
        }

        socket?.on("viewer-ready") { args ->
            val data = args[0] as JSONObject
            val viewerId = data.getString("viewerId")
            Log.d(TAG, "👀 Viewer ready: $viewerId")

            createPeerConnection(viewerId)
        }

        socket?.on("answer") { args ->
            val data = args[0] as JSONObject
            handleAnswer(data)
        }

        socket?.on("ice-candidate") { args ->
            val data = args[0] as JSONObject
            handleIceCandidate(data)
        }
    }

    private fun createPeerConnection(peerId: String) {
        Log.d(TAG, "Creating peer connection for: $peerId")

        val rtcConfig = PeerConnection.RTCConfiguration(STUN_SERVERS).apply {
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
            tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }

        val pc = peerConnectionFactory?.createPeerConnection(
            rtcConfig,
            PeerConnectionObserver(peerId)
        )

        if (pc == null) {
            Log.e(TAG, "Failed to create peer connection")
            return
        }

        // Agregar track de video
        val videoTrack = peerConnectionFactory?.createVideoTrack("video_track", localVideoSource)
        val streamId = "stream_$deviceId"
        pc.addTrack(videoTrack, listOf(streamId))

        peerConnections[peerId] = pc

        // Crear offer
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "false"))
        }

        pc.createOffer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription) {
                pc.setLocalDescription(object : SdpObserver {
                    override fun onSetSuccess() {
                        Log.d(TAG, "Local description set")

                        // Enviar offer al peer
                        val offerData = JSONObject().apply {
                            put("target", peerId)
                            put("sdp", JSONObject().apply {
                                put("type", sdp.type.canonicalForm())
                                put("sdp", sdp.description)
                            })
                        }
                        socket?.emit("offer", offerData)
                    }

                    override fun onSetFailure(error: String?) {
                        Log.e(TAG, "Failed to set local description: $error")
                    }

                    override fun onCreateSuccess(p0: SessionDescription?) {}
                    override fun onCreateFailure(p0: String?) {}
                }, sdp)
            }

            override fun onCreateFailure(error: String?) {
                Log.e(TAG, "Failed to create offer: $error")
            }

            override fun onSetSuccess() {}
            override fun onSetFailure(p0: String?) {}
        }, constraints)
    }

    private fun handleAnswer(data: JSONObject) {
        val sender = data.getString("sender")
        val sdpData = data.getJSONObject("sdp")

        val answer = SessionDescription(
            SessionDescription.Type.ANSWER,
            sdpData.getString("sdp")
        )

        peerConnections[sender]?.setRemoteDescription(object : SdpObserver {
            override fun onSetSuccess() {
                Log.d(TAG, "✅ Remote description set for $sender")
            }

            override fun onSetFailure(error: String?) {
                Log.e(TAG, "Failed to set remote description: $error")
            }

            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onCreateFailure(p0: String?) {}
        }, answer)
    }

    private fun handleIceCandidate(data: JSONObject) {
        val sender = data.getString("sender")
        val candidateData = data.getJSONObject("candidate")

        val candidate = IceCandidate(
            candidateData.getString("sdpMid"),
            candidateData.getInt("sdpMLineIndex"),
            candidateData.getString("candidate")
        )

        peerConnections[sender]?.addIceCandidate(candidate)
        Log.d(TAG, "ICE candidate added for $sender")
    }

    /**
     * Detener y limpiar recursos
     */
    fun cleanup() {
        Log.d(TAG, "Cleaning up WebRTC resources...")

        videoCapturer?.stopCapture()
        videoCapturer?.dispose()

        peerConnections.values.forEach { it.close() }
        peerConnections.clear()

        localVideoSource?.dispose()

        socket?.disconnect()
        socket = null

        peerConnectionFactory?.dispose()
        peerConnectionFactory = null

        eglBase?.release()
        eglBase = null

        isInitialized = false
        Log.d(TAG, "WebRTC cleanup completed")
    }

    /**
     * Observer para eventos de PeerConnection
     */
    inner class PeerConnectionObserver(private val peerId: String) : PeerConnection.Observer {

        override fun onIceCandidate(candidate: IceCandidate) {
            Log.d(TAG, "ICE candidate generated for $peerId")

            val candidateData = JSONObject().apply {
                put("target", peerId)
                put("candidate", JSONObject().apply {
                    put("sdpMLineIndex", candidate.sdpMLineIndex)
                    put("sdpMid", candidate.sdpMid)
                    put("candidate", candidate.sdp)
                })
            }

            socket?.emit("ice-candidate", candidateData)
        }

        override fun onConnectionChange(newState: PeerConnection.PeerConnectionState) {
            Log.d(TAG, "Connection state changed to $newState for $peerId")

            when (newState) {
                PeerConnection.PeerConnectionState.CONNECTED -> {
                    Log.d(TAG, "✅ Peer $peerId connected successfully")
                }
                PeerConnection.PeerConnectionState.FAILED,
                PeerConnection.PeerConnectionState.DISCONNECTED -> {
                    Log.w(TAG, "⚠️ Peer $peerId connection lost")
                    peerConnections.remove(peerId)?.close()
                }
                else -> {}
            }
        }

        override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState) {
            Log.d(TAG, "ICE connection state: $newState for $peerId")
        }

        override fun onIceConnectionReceivingChange(receiving: Boolean) {
            Log.d(TAG, "ICE connection receiving change: $receiving for $peerId")
        }

        override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {
            Log.d(TAG, "ICE candidates removed: ${candidates?.size ?: 0} for $peerId")
        }

        override fun onSignalingChange(newState: PeerConnection.SignalingState) {
            Log.d(TAG, "Signaling state: $newState for $peerId")
        }

        override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState) {
            Log.d(TAG, "ICE gathering state: $newState for $peerId")
        }

        override fun onAddStream(stream: MediaStream) {
            Log.d(TAG, "Stream added for $peerId")
        }

        override fun onRemoveStream(stream: MediaStream) {
            Log.d(TAG, "Stream removed for $peerId")
        }

        override fun onDataChannel(dataChannel: DataChannel) {
            Log.d(TAG, "Data channel created for $peerId")
        }

        override fun onRenegotiationNeeded() {
            Log.d(TAG, "Renegotiation needed for $peerId")
        }

        override fun onAddTrack(receiver: RtpReceiver, streams: Array<out MediaStream>) {
            Log.d(TAG, "Track added for $peerId")
        }
    }
}