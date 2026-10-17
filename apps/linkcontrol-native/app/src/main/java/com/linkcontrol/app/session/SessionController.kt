package com.linkcontrol.app.session

import android.content.Context
import android.content.Intent
import android.util.Log
import com.linkcontrol.app.control.AppCatalog
import com.linkcontrol.app.control.ControlCommand
import com.linkcontrol.app.control.RemoteControlService
import com.linkcontrol.app.data.AppInfo
import com.linkcontrol.app.data.ConnState
import com.linkcontrol.app.data.ControlGrant
import com.linkcontrol.app.data.DeviceStore
import com.linkcontrol.app.data.Identity
import com.linkcontrol.app.data.LinkQuality
import com.linkcontrol.app.data.Peer
import com.linkcontrol.app.data.Role
import com.linkcontrol.app.data.SessionRecord
import com.linkcontrol.app.signaling.SignalingClient
import com.linkcontrol.app.webrtc.ScreenCaptureService
import com.linkcontrol.app.webrtc.WebRtcManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import org.webrtc.IceCandidate
import org.webrtc.PeerConnection
import org.webrtc.SessionDescription
import org.webrtc.VideoTrack
import java.util.UUID

/**
 * Single source of truth for pairing + sessions. The UI only reads its flows and
 * calls its intent methods; all signaling, WebRTC and control wiring lives here.
 */
class SessionController private constructor(private val appContext: Context) :
    SignalingClient.Events, WebRtcManager.Callbacks {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val store = DeviceStore(appContext)

    // ---- observable state ----
    val connState = MutableStateFlow(ConnState.IDLE)
    val signalingOnline = MutableStateFlow(false)
    val peers = MutableStateFlow<List<Peer>>(emptyList())
    val history = MutableStateFlow<List<SessionRecord>>(emptyList())
    val role = MutableStateFlow(Role.NONE)
    val activePeer = MutableStateFlow<Peer?>(null)
    val quality = MutableStateFlow(LinkQuality())
    val controlGrant = MutableStateFlow(ControlGrant.NONE)
    val pairingCode = MutableStateFlow<String?>(null)     // host: code to show
    val incomingRequest = MutableStateFlow<IncomingRequest?>(null) // host: consent prompt
    val remoteTrack = MutableStateFlow<VideoTrack?>(null) // controller: video to render
    val availableApps = MutableStateFlow<List<AppInfo>>(emptyList()) // controller: host's app list
    val events = MutableSharedFlow<String>(extraBufferCapacity = 8) // transient toasts/errors

    data class IncomingRequest(val fromPeerId: String, val fromName: String, val wantsControl: Boolean)

    private var identity: Identity? = null
    private var signaling: SignalingClient? = null
    private var rtc: WebRtcManager? = null
    private var currentPeerId: String? = null
    private var currentSessionToken: String? = null
    private var sessionRecordId: String? = null
    private var sessionStart: Long = 0

    // host: capture params captured while awaiting the FGS to go foreground
    private var pendingGrant: ControlGrant = ControlGrant.NONE

    val webRtcEglContext get() = rtc?.eglBase?.eglBaseContext

    // ---------------- lifecycle ----------------

    fun bootstrap() {
        scope.launch {
            peers.value = store.loadPeers()
            history.value = store.loadHistory()
            identity = store.loadIdentity()
            connectSignaling()
        }
    }

    private suspend fun connectSignaling() {
        val url = store.signalingUrl()
        signaling?.close()
        signaling = SignalingClient(url, this).also { it.connect() }
    }

    fun reconnectSignaling() { scope.launch { connectSignaling() } }

    // ---------------- pairing ----------------

    /** HOST: ask the server for a fresh pairing code to display as text + QR. */
    fun createPairingCode() {
        ensureRegisteredThen { signaling?.send("create_pairing") }
    }

    /** CONTROLLER: redeem a code typed or scanned from the host.
     *  A scanned QR also carries the host's signaling URL; adopt it first so both
     *  phones are on the same server, otherwise the code can't resolve. */
    fun redeemPairingCode(code: String, url: String? = null) {
        scope.launch {
            if (!url.isNullOrBlank() && url != store.signalingUrl()) {
                store.setSignalingUrl(url); connectSignaling()
            }
            ensureRegisteredThen { signaling?.send("redeem_pairing") { put("code", code.trim()) } }
        }
    }

    fun unpair(peerId: String) {
        signaling?.send("unpair") { put("peerId", peerId) }
        scope.launch { store.removePeer(peerId); peers.value = store.loadPeers() }
    }

    // ---------------- connect (controller) ----------------

    fun requestSession(peer: Peer, wantControl: Boolean) {
        if (connState.value == ConnState.CONNECTED || connState.value == ConnState.CONNECTING) {
            emit("A session is already active"); return
        }
        currentPeerId = peer.deviceId
        activePeer.value = peer
        role.value = Role.CONTROLLER
        connState.value = ConnState.CONNECTING
        signaling?.send("control_request") {
            put("to", peer.deviceId); put("wantControl", wantControl)
        }
        emit("Requesting ${peer.name}…")
    }

    // ---------------- consent (host) ----------------

    fun denyIncoming() {
        val req = incomingRequest.value ?: return
        signaling?.send("control_response") { put("to", req.fromPeerId); put("accepted", false) }
        incomingRequest.value = null
        connState.value = ConnState.IDLE
    }

    /**
     * HOST approved. The Activity has already obtained the MediaProjection consent
     * and passes the result here along with the host's real screen size.
     */
    fun hostApproveAndCapture(grant: ControlGrant, projectionData: Intent, widthPx: Int, heightPx: Int) {
        val req = incomingRequest.value ?: return
        incomingRequest.value = null
        currentPeerId = req.fromPeerId
        activePeer.value = peers.value.firstOrNull { it.deviceId == req.fromPeerId }
            ?: Peer(req.fromPeerId, req.fromName)
        role.value = Role.HOST
        pendingGrant = grant
        controlGrant.value = grant
        connState.value = ConnState.CONNECTING

        signaling?.send("control_response") {
            put("to", req.fromPeerId); put("accepted", true); put("control", grant.name)
        }
        // Spin up the foreground service; it calls onCaptureServiceReady once foreground.
        ScreenCaptureService.start(appContext, /*resultCode*/ -1, projectionData, widthPx, heightPx)
    }

    /** Called by ScreenCaptureService after it is running in the foreground. */
    fun onCaptureServiceReady(projectionData: Intent, widthPx: Int, heightPx: Int) {
        val mgr = WebRtcManager(appContext, isOfferer = true, iceServers = iceServers(), callbacks = this)
        rtc = mgr
        sessionStart = System.currentTimeMillis()
        sessionRecordId = UUID.randomUUID().toString()
        mgr.init()
        mgr.startScreenCapture(projectionData, widthPx, heightPx)
        startQualityLoop()
    }

    // ---------------- controller remote-control ----------------

    fun sendTap(nx: Float, ny: Float) = send(ControlCommand.Tap(nx, ny))
    fun sendSwipe(x1: Float, y1: Float, x2: Float, y2: Float, dur: Long) =
        send(ControlCommand.Swipe(x1, y1, x2, y2, dur))
    fun sendNav(name: String) = send(ControlCommand.Nav(name))

    /** Ask the host for its list of launchable apps (does not require control grant). */
    fun requestApps() { rtc?.sendData(ControlCommand.ListApps.toJson()) }

    /** Ask the host to open an app. Requires full-control grant. */
    fun launchApp(pkg: String) = send(ControlCommand.LaunchApp(pkg))

    private fun send(cmd: ControlCommand) {
        if (controlGrant.value != ControlGrant.FULL_CONTROL) { emit("Control not granted"); return }
        if (rtc?.sendData(cmd.toJson()) != true) emit("Control channel not ready")
    }

    // ---------------- end ----------------

    fun endSession(reason: String) {
        if (connState.value == ConnState.IDLE) return
        currentPeerId?.let { signaling?.send("session_end") { put("to", it) } }
        teardown(reason)
    }

    private fun teardown(reason: String) {
        if (role.value == Role.HOST) ScreenCaptureService.stop(appContext)
        rtc?.close(reason); rtc = null
        remoteTrack.value = null
        availableApps.value = emptyList()
        controlGrant.value = ControlGrant.NONE
        connState.value = if (reason.startsWith("fail")) ConnState.FAILED else ConnState.DISCONNECTED
        // record history
        val peer = activePeer.value
        val id = sessionRecordId
        if (peer != null && id != null) {
            val rec = SessionRecord(id, peer.deviceId, peer.name, role.value,
                sessionStart.takeIf { it > 0 } ?: System.currentTimeMillis(),
                System.currentTimeMillis(), reason)
            scope.launch { store.addHistory(rec); history.value = store.loadHistory() }
        }
        currentPeerId = null; currentSessionToken = null; sessionRecordId = null; sessionStart = 0
        role.value = Role.NONE
    }

    // ---------------- signaling events ----------------

    override fun onOpen() {
        signalingOnline.value = true
        val id = identity
        if (id == null) signaling?.send("register") { put("name", android.os.Build.MODEL) }
        else signaling?.send("authenticate") { put("deviceId", id.deviceId); put("token", id.token); put("name", android.os.Build.MODEL) }
    }

    // SignalingClient.Events: socket closed / failed (distinct from the WebRTC onClosed()).
    override fun onClosed(reason: String) { signalingOnline.value = false }
    override fun onFailure(t: Throwable) { signalingOnline.value = false }

    override fun onMessage(obj: JSONObject) {
        when (obj.optString("type")) {
            "registered" -> {
                val id = Identity(obj.getString("deviceId"), obj.getString("token"))
                identity = id; scope.launch { store.saveIdentity(id) }
                runPending()
            }
            "authenticated" -> { runPending(); signaling?.send("list_peers") }
            "pairing_created" -> pairingCode.value = obj.getString("code")
            "paired" -> onPaired(obj)
            "unpaired" -> scope.launch {
                store.removePeer(obj.getString("peerId")); peers.value = store.loadPeers()
            }
            "peers" -> onPeers(obj)
            "presence" -> onPresence(obj)
            "control_request" -> onControlRequest(obj)
            "control_response" -> onControlResponse(obj)
            "offer" -> onOffer(obj)
            "answer" -> rtc?.onRemoteAnswer(SessionDescription(SessionDescription.Type.ANSWER, obj.getJSONObject("payload").getString("sdp")))
            "ice" -> onIce(obj)
            "session_end" -> teardown("ended_by_peer")
            "error" -> onError(obj.optString("error"))
        }
    }

    private fun onPaired(obj: JSONObject) {
        val peer = Peer(obj.getString("peerId"), obj.optString("peerName", "Device"), online = true)
        pairingCode.value = null
        scope.launch { store.upsertPeer(peer); peers.value = store.loadPeers(); signaling?.send("list_peers") }
        emit("Paired with ${peer.name}")
    }

    private fun onPeers(obj: JSONObject) {
        val arr = obj.getJSONArray("peers")
        val list = (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Peer(o.getString("deviceId"), o.optString("name", "Device"), o.optBoolean("online"))
        }
        // merge online flags onto stored peers
        scope.launch {
            val stored = store.loadPeers()
            peers.value = stored.map { s -> list.firstOrNull { it.deviceId == s.deviceId }?.let { s.copy(online = it.online) } ?: s }
        }
    }

    private fun onPresence(obj: JSONObject) {
        val id = obj.getString("deviceId"); val online = obj.getBoolean("online")
        peers.value = peers.value.map { if (it.deviceId == id) it.copy(online = online) else it }
    }

    private fun onControlRequest(obj: JSONObject) {
        val from = obj.getString("from")
        val name = peers.value.firstOrNull { it.deviceId == from }?.name ?: "A paired device"
        incomingRequest.value = IncomingRequest(from, name, obj.optBoolean("wantControl"))
        connState.value = ConnState.WAITING_CONSENT
        emit("$name wants to connect")
    }

    private fun onControlResponse(obj: JSONObject) {
        // controller side
        if (!obj.optBoolean("accepted")) { emit("Request declined"); teardown("declined"); return }
        controlGrant.value = runCatching { ControlGrant.valueOf(obj.optString("control", "VIEW_ONLY")) }
            .getOrDefault(ControlGrant.VIEW_ONLY)
        // prepare to answer the upcoming offer
        val mgr = WebRtcManager(appContext, isOfferer = false, iceServers = iceServers(), callbacks = this)
        rtc = mgr; mgr.init()
        sessionStart = System.currentTimeMillis()
        sessionRecordId = UUID.randomUUID().toString()
        connState.value = ConnState.CONNECTING
        startQualityLoop()
    }

    private fun onOffer(obj: JSONObject) {
        currentSessionToken = obj.optString("sessionToken", null)
        currentPeerId = obj.getString("from")
        val sdp = obj.getJSONObject("payload").getString("sdp")
        rtc?.onRemoteOffer(SessionDescription(SessionDescription.Type.OFFER, sdp))
    }

    private fun onIce(obj: JSONObject) {
        val p = obj.getJSONObject("payload")
        rtc?.onRemoteIce(IceCandidate(p.getString("sdpMid"), p.getInt("sdpMLineIndex"), p.getString("candidate")))
    }

    private fun onError(err: String) {
        Log.w(TAG, "signaling error: $err")
        when (err) {
            "pairing_invalid_or_expired" -> emit("Pairing code invalid or expired")
            "peer_offline" -> { emit("Device is offline"); teardown("peer_offline") }
            "peer_not_paired" -> { emit("Device not paired"); teardown("not_paired") }
            "auth_failed" -> { identity = null; signaling?.send("register") { put("name", android.os.Build.MODEL) } }
            else -> emit("Error: $err")
        }
    }

    // ---------------- WebRtcManager callbacks ----------------

    override fun onLocalDescription(sdp: SessionDescription) {
        val type = if (sdp.type == SessionDescription.Type.OFFER) "offer" else "answer"
        signaling?.send(type) {
            put("to", currentPeerId)
            currentSessionToken?.let { put("sessionToken", it) }
            put("payload", JSONObject().put("sdp", sdp.description))
        }
    }

    override fun onLocalIce(candidate: IceCandidate) {
        signaling?.send("ice") {
            put("to", currentPeerId)
            put("payload", JSONObject()
                .put("sdpMid", candidate.sdpMid)
                .put("sdpMLineIndex", candidate.sdpMLineIndex)
                .put("candidate", candidate.sdp))
        }
    }

    override fun onRemoteVideoTrack(track: VideoTrack) { remoteTrack.value = track }
    override fun onConnected() { connState.value = ConnState.CONNECTED; emit("Connected") }
    override fun onFailed(reason: String) { emit("Connection $reason"); teardown("fail_$reason") }
    override fun onClosed() { /* teardown already handles state */ }

    override fun onDataChannelReady() {
        Log.i(TAG, "data channel open")
        // controller: fetch the host's app list as soon as the channel is ready
        if (role.value == Role.CONTROLLER) requestApps()
    }

    override fun onDataMessage(json: String) {
        val type = runCatching { JSONObject(json).optString("t") }.getOrDefault("")
        when (role.value) {
            Role.HOST -> onHostData(type, json)
            Role.CONTROLLER -> onControllerData(type, json)
            else -> {}
        }
    }

    private fun onHostData(type: String, json: String) {
        when (type) {
            "list_apps" -> {
                val apps = AppCatalog.list(appContext)
                val arr = JSONArray()
                apps.forEach { arr.put(JSONObject().put("label", it.label).put("pkg", it.packageName)) }
                rtc?.sendData(JSONObject().put("t", "app_list").put("apps", arr).toString())
            }
            "launch_app" -> {
                // guarded: only with full control, and report the outcome honestly
                if (controlGrant.value != ControlGrant.FULL_CONTROL) return
                val pkg = JSONObject(json).optString("pkg")
                val ok = AppCatalog.launch(appContext, pkg)
                rtc?.sendData(JSONObject().put("t", "launch_result").put("pkg", pkg).put("ok", ok).toString())
            }
            "tap", "swipe", "nav" -> {
                // execute only with full-control grant AND the a11y service enabled
                if (controlGrant.value != ControlGrant.FULL_CONTROL) return
                val svc = RemoteControlService.instance ?: run { emit("Enable remote control on host"); return }
                ControlCommand.parse(json)?.let { svc.execute(it) }
            }
        }
    }

    private fun onControllerData(type: String, json: String) {
        when (type) {
            "app_list" -> {
                val arr = JSONObject(json).optJSONArray("apps") ?: JSONArray()
                availableApps.value = (0 until arr.length()).map {
                    val o = arr.getJSONObject(it); AppInfo(o.optString("label"), o.optString("pkg"))
                }
            }
            "launch_result" -> {
                val o = JSONObject(json)
                emit(if (o.optBoolean("ok")) "Opened on remote device" else "Couldn't open that app")
            }
        }
    }

    // ---------------- helpers ----------------

    private var pending: (() -> Unit)? = null
    private fun ensureRegisteredThen(action: () -> Unit) {
        if (identity != null && signaling?.isOpen == true) action() else { pending = action; reconnectSignaling() }
    }
    private fun runPending() { pending?.invoke(); pending = null }

    private fun startQualityLoop() = scope.launch {
        while (isActive && connState.value != ConnState.IDLE && rtc != null) {
            rtc?.pollQuality { quality.value = it }
            delay(2000)
        }
    }

    private fun emit(msg: String) { scope.launch { events.emit(msg) } }

    /** STUN is free; add your TURN server (see Security settings / DEPLOYMENT.md) for
     *  reliable connections across mobile networks and symmetric NAT. */
    private fun iceServers(): List<PeerConnection.IceServer> = buildList {
        add(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer())
        // Example TURN (replace with your own credentials):
        // add(PeerConnection.IceServer.builder("turn:your.turn.host:3478")
        //     .setUsername("user").setPassword("pass").createIceServer())
    }

    fun clearHistory() { scope.launch { store.clearHistory(); history.value = emptyList() } }
    suspend fun currentSignalingUrl() = store.signalingUrl()
    fun setSignalingUrl(url: String) { scope.launch { store.setSignalingUrl(url); connectSignaling() } }
    fun myDeviceId() = identity?.deviceId

    companion object {
        private const val TAG = "SessionController"
        @Volatile private var INSTANCE: SessionController? = null
        fun get(context: Context): SessionController =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: SessionController(context.applicationContext).also { INSTANCE = it }
            }
    }
}
