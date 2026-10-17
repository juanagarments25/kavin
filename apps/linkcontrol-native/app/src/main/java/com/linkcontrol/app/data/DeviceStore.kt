package com.linkcontrol.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.linkcontrol.app.BuildConfig
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

private val Context.dataStore by preferencesDataStore(name = "linkcontrol")

/**
 * Minimal, local persistence: our identity, the chosen signaling URL,
 * the set of trusted (paired) peers, and recent session history.
 * Pairing codes and session tokens are deliberately NOT persisted.
 */
class DeviceStore(private val context: Context) {

    private val kDeviceId = stringPreferencesKey("device_id")
    private val kToken = stringPreferencesKey("token")
    private val kSignalingUrl = stringPreferencesKey("signaling_url")
    private val kPeers = stringPreferencesKey("peers_json")
    private val kHistory = stringPreferencesKey("history_json")

    suspend fun loadIdentity(): Identity? {
        val p = context.dataStore.data.first()
        val id = p[kDeviceId]; val t = p[kToken]
        return if (id != null && t != null) Identity(id, t) else null
    }

    suspend fun saveIdentity(identity: Identity) {
        context.dataStore.edit { it[kDeviceId] = identity.deviceId; it[kToken] = identity.token }
    }

    suspend fun signalingUrl(): String =
        context.dataStore.data.first()[kSignalingUrl] ?: BuildConfig.DEFAULT_SIGNALING_URL

    suspend fun setSignalingUrl(url: String) {
        context.dataStore.edit { it[kSignalingUrl] = url.trim() }
    }

    suspend fun loadPeers(): List<Peer> =
        parsePeers(context.dataStore.data.first()[kPeers])

    suspend fun upsertPeer(peer: Peer) {
        val current = loadPeers().filter { it.deviceId != peer.deviceId } + peer
        savePeers(current)
    }

    suspend fun removePeer(deviceId: String) {
        savePeers(loadPeers().filter { it.deviceId != deviceId })
    }

    private suspend fun savePeers(peers: List<Peer>) {
        val arr = JSONArray()
        peers.forEach {
            arr.put(JSONObject().apply {
                put("deviceId", it.deviceId); put("name", it.name); put("pairedAt", it.pairedAt)
            })
        }
        context.dataStore.edit { p -> p[kPeers] = arr.toString() }
    }

    private fun parsePeers(json: String?): List<Peer> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Peer(o.getString("deviceId"), o.optString("name", "Device"),
                    online = false, pairedAt = o.optLong("pairedAt"))
            }
        }.getOrDefault(emptyList())
    }

    suspend fun loadHistory(): List<SessionRecord> {
        val json = context.dataStore.data.first()[kHistory] ?: return emptyList()
        return runCatching {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                SessionRecord(
                    id = o.getString("id"),
                    peerId = o.getString("peerId"),
                    peerName = o.optString("peerName", "Device"),
                    role = Role.valueOf(o.optString("role", "NONE")),
                    startedAt = o.getLong("startedAt"),
                    endedAt = if (o.isNull("endedAt")) null else o.getLong("endedAt"),
                    endReason = o.optString("endReason", ""),
                )
            }
        }.getOrDefault(emptyList())
    }

    suspend fun addHistory(record: SessionRecord) {
        val current = (listOf(record) + loadHistory()).take(50) // bounded retention
        val arr = JSONArray()
        current.forEach {
            arr.put(JSONObject().apply {
                put("id", it.id); put("peerId", it.peerId); put("peerName", it.peerName)
                put("role", it.role.name); put("startedAt", it.startedAt)
                if (it.endedAt == null) put("endedAt", JSONObject.NULL) else put("endedAt", it.endedAt)
                put("endReason", it.endReason)
            })
        }
        context.dataStore.edit { it[kHistory] = arr.toString() }
    }

    suspend fun clearHistory() { context.dataStore.edit { it[kHistory] = "[]" } }
}
