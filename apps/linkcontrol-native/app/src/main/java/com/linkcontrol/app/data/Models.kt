package com.linkcontrol.app.data

/** This device's identity, minted by the signaling server on first registration. */
data class Identity(val deviceId: String, val token: String)

/** A device we have paired with (a "trusted device"). */
data class Peer(
    val deviceId: String,
    val name: String,
    val online: Boolean = false,
    val pairedAt: Long = System.currentTimeMillis(),
)

/** One past session, for Connection history. */
data class SessionRecord(
    val id: String,
    val peerId: String,
    val peerName: String,
    val role: Role,            // were we controller or host
    val startedAt: Long,
    val endedAt: Long?,
    val endReason: String,
)

enum class Role { CONTROLLER, HOST, NONE }

/** High-level connection lifecycle the whole UI reacts to. */
enum class ConnState { IDLE, CONNECTING, WAITING_CONSENT, CONNECTED, FAILED, DISCONNECTED }

/** Link quality derived from WebRTC stats. */
data class LinkQuality(
    val rttMs: Long = -1,
    val kbps: Long = 0,
    val fps: Int = 0,
) {
    val label: String get() = when {
        rttMs < 0 -> "—"
        rttMs < 80 -> "Excellent"
        rttMs < 160 -> "Good"
        rttMs < 300 -> "Fair"
        else -> "Poor"
    }
}

/** Whether the host has granted remote *control* (vs. screen-view only). */
enum class ControlGrant { NONE, VIEW_ONLY, FULL_CONTROL }

/** A launchable app the host makes available to the controller. */
data class AppInfo(val label: String, val packageName: String)
