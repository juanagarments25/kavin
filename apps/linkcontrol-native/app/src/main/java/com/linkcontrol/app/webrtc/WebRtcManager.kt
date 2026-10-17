package com.linkcontrol.app.webrtc

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.util.Log
import com.linkcontrol.app.data.LinkQuality
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.ScreenCapturerAndroid
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

/**
 * Owns one WebRTC peer connection for a single session.
 *
 * HOST (Phone 2): captures the screen via MediaProjection, adds the video track,
 * creates the control data channel, and is the OFFERER.
 * CONTROLLER (Phone 1): receives the remote video track, renders it, and sends
 * control commands over the data channel.
 *
 * Transport is DTLS-SRTP (media) + SCTP-over-DTLS (data) — encrypted end to end.
 */
class WebRtcManager(
    private val appContext: Context,
    private val isOfferer: Boolean,        // true on host
    private val iceServers: List<PeerConnection.IceServer>,
    private val callbacks: Callbacks,
) {
    interface Callbacks {
        fun onLocalDescription(sdp: SessionDescription)
        fun onLocalIce(candidate: IceCandidate)
        fun onRemoteVideoTrack(track: VideoTrack)          // controller side
        fun onConnected()
        fun onFailed(reason: String)
        fun onClosed()
        fun onDataMessage(json: String)                    // any typed message from the peer
        fun onDataChannelReady()                           // data channel open (either side)
    }

    val eglBase: EglBase = EglBase.create()

    private lateinit var factory: PeerConnectionFactory
    private var pc: PeerConnection? = null
    private var dataChannel: DataChannel? = null

    // host capture resources
    private var screenCapturer: ScreenCapturerAndroid? = null
    private var videoSource: VideoSource? = null
    private var videoTrack: VideoTrack? = null
    private var surfaceHelper: SurfaceTextureHelper? = null

    @Volatile private var closed = false

    fun init() {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(appContext)
                .createInitializationOptions()
        )
        val encoder = DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true)
        val decoder = DefaultVideoDecoderFactory(eglBase.eglBaseContext)
        factory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(encoder)
            .setVideoDecoderFactory(decoder)
            .createPeerConnectionFactory()

        val rtc = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
            // Prefer TURN relay only when direct paths fail (default behaviour).
            keyType = PeerConnection.KeyType.ECDSA
        }
        pc = factory.createPeerConnection(rtc, pcObserver) ?: run {
            callbacks.onFailed("peer_connection_create_failed"); return
        }

        if (isOfferer) {
            // host owns the control data channel; it is bidirectional
            val init = DataChannel.Init().apply { ordered = true }
            dataChannel = pc!!.createDataChannel("control", init)?.also { registerDataChannel(it) }
        }
    }

    // ---------------- HOST: screen capture ----------------

    /**
     * @param projectionData the Intent returned from the MediaProjection consent dialog
     * @param widthPx/heightPx the host's real screen size (streamed at a capped scale)
     */
    fun startScreenCapture(projectionData: Intent, widthPx: Int, heightPx: Int, fps: Int = 30) {
        val callback = object : MediaProjection.Callback() {
            override fun onStop() { Log.i(TAG, "MediaProjection stopped by system/user") ; close("projection_stopped") }
        }
        val capturer = ScreenCapturerAndroid(projectionData, callback)
        screenCapturer = capturer

        val source = factory.createVideoSource(/* isScreencast = */ true)
        videoSource = source
        val helper = SurfaceTextureHelper.create("CaptureThread", eglBase.eglBaseContext)
        surfaceHelper = helper
        capturer.initialize(helper, appContext, source.capturerObserver)

        // Cap the long edge to keep bandwidth sane; preserve aspect ratio.
        val maxEdge = 1280
        val scale = if (maxOf(widthPx, heightPx) > maxEdge) maxEdge.toFloat() / maxOf(widthPx, heightPx) else 1f
        val w = (widthPx * scale).toInt().coerceAtLeast(2) and 1.inv()
        val h = (heightPx * scale).toInt().coerceAtLeast(2) and 1.inv()
        capturer.startCapture(w, h, fps)

        val track = factory.createVideoTrack("screen", source).apply { setEnabled(true) }
        videoTrack = track
        pc?.addTrack(track, listOf("linkcontrol-stream"))

        createOffer()
    }

    private fun createOffer() {
        val c = MediaConstraints()
        pc?.createOffer(object : SimpleSdp() {
            override fun onCreateSuccess(sdp: SessionDescription) {
                pc?.setLocalDescription(SimpleSdp(), sdp)
                callbacks.onLocalDescription(sdp)
            }
        }, c)
    }

    // ---------------- CONTROLLER: answer ----------------

    fun onRemoteOffer(sdp: SessionDescription) {
        pc?.setRemoteDescription(SimpleSdp(), sdp)
        val c = MediaConstraints()
        pc?.createAnswer(object : SimpleSdp() {
            override fun onCreateSuccess(answer: SessionDescription) {
                pc?.setLocalDescription(SimpleSdp(), answer)
                callbacks.onLocalDescription(answer)
            }
        }, c)
    }

    fun onRemoteAnswer(sdp: SessionDescription) {
        pc?.setRemoteDescription(SimpleSdp(), sdp)
    }

    fun onRemoteIce(candidate: IceCandidate) {
        pc?.addIceCandidate(candidate)
    }

    // ---------------- control data channel ----------------

    private fun registerDataChannel(dc: DataChannel) {
        dataChannel = dc
        dc.registerObserver(object : DataChannel.Observer {
            override fun onBufferedAmountChange(previousAmount: Long) {}
            override fun onStateChange() {
                Log.i(TAG, "dataChannel state ${dc.state()}")
                if (dc.state() == DataChannel.State.OPEN) callbacks.onDataChannelReady()
            }
            override fun onMessage(buffer: DataChannel.Buffer) {
                val bytes = ByteArray(buffer.data.remaining()).also { buffer.data.get(it) }
                callbacks.onDataMessage(String(bytes, StandardCharsets.UTF_8))
            }
        })
    }

    /** Send a typed message to the peer. Returns false if the channel isn't open. */
    fun sendData(json: String): Boolean {
        val dc = dataChannel ?: return false
        if (dc.state() != DataChannel.State.OPEN) return false
        val buf = ByteBuffer.wrap(json.toByteArray(StandardCharsets.UTF_8))
        return dc.send(DataChannel.Buffer(buf, false))
    }

    // ---------------- rendering (controller) ----------------

    fun attachRenderer(renderer: SurfaceViewRenderer, track: VideoTrack) {
        runCatching { track.addSink(renderer) }
    }

    // ---------------- stats ----------------

    fun pollQuality(onResult: (LinkQuality) -> Unit) {
        val conn = pc ?: return
        conn.getStats { report ->
            var rtt = -1L; var kbps = 0L; var fps = 0
            report.statsMap.values.forEach { s ->
                when (s.type) {
                    "candidate-pair" -> (s.members["currentRoundTripTime"] as? Double)?.let { rtt = (it * 1000).toLong() }
                    "inbound-rtp", "outbound-rtp" -> {
                        (s.members["framesPerSecond"] as? Double)?.let { fps = it.toInt() }
                    }
                }
            }
            onResult(LinkQuality(rttMs = rtt, kbps = kbps, fps = fps))
        }
    }

    // ---------------- teardown ----------------

    fun close(reason: String = "closed") {
        if (closed) return
        closed = true
        runCatching { screenCapturer?.stopCapture() }
        runCatching { dataChannel?.close() }
        runCatching { videoTrack?.dispose() }
        runCatching { videoSource?.dispose() }
        runCatching { surfaceHelper?.dispose() }
        runCatching { pc?.close(); pc?.dispose() }
        runCatching { factory.dispose() }
        runCatching { eglBase.release() }
        callbacks.onClosed()
    }

    private val pcObserver = object : PeerConnection.Observer {
        override fun onIceCandidate(candidate: IceCandidate) { callbacks.onLocalIce(candidate) }
        override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
            Log.i(TAG, "ice $state")
            when (state) {
                PeerConnection.IceConnectionState.CONNECTED,
                PeerConnection.IceConnectionState.COMPLETED -> callbacks.onConnected()
                PeerConnection.IceConnectionState.FAILED -> callbacks.onFailed("ice_failed")
                PeerConnection.IceConnectionState.DISCONNECTED -> callbacks.onFailed("ice_disconnected")
                PeerConnection.IceConnectionState.CLOSED -> callbacks.onClosed()
                else -> {}
            }
        }
        override fun onTrack(transceiver: org.webrtc.RtpTransceiver) {
            (transceiver.receiver.track() as? VideoTrack)?.let { callbacks.onRemoteVideoTrack(it) }
        }
        override fun onAddTrack(receiver: RtpReceiver, streams: Array<out org.webrtc.MediaStream>) {
            (receiver.track() as? VideoTrack)?.let { callbacks.onRemoteVideoTrack(it) }
        }
        override fun onDataChannel(dc: DataChannel) { registerDataChannel(dc) }
        override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}
        override fun onIceConnectionReceivingChange(p0: Boolean) {}
        override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}
        override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
        override fun onAddStream(p0: org.webrtc.MediaStream?) {}
        override fun onRemoveStream(p0: org.webrtc.MediaStream?) {}
        override fun onRenegotiationNeeded() {}
        override fun onConnectionChange(newState: PeerConnection.PeerConnectionState) {
            if (newState == PeerConnection.PeerConnectionState.FAILED) callbacks.onFailed("pc_failed")
        }
    }

    companion object { private const val TAG = "WebRtcManager" }
}

/** SdpObserver with empty defaults so call sites override only what they need. */
open class SimpleSdp : SdpObserver {
    override fun onCreateSuccess(sdp: SessionDescription) {}
    override fun onSetSuccess() {}
    override fun onCreateFailure(error: String?) { Log.w("SimpleSdp", "create fail $error") }
    override fun onSetFailure(error: String?) { Log.w("SimpleSdp", "set fail $error") }
}
