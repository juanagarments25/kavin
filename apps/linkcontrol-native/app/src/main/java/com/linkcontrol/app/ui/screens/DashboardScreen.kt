package com.linkcontrol.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linkcontrol.app.data.ConnState
import com.linkcontrol.app.data.Peer
import com.linkcontrol.app.ui.components.GlowCard
import com.linkcontrol.app.ui.components.SectionLabel
import com.linkcontrol.app.ui.components.StatusDot
import com.linkcontrol.app.ui.rememberController
import com.linkcontrol.app.ui.theme.Cyan
import com.linkcontrol.app.ui.theme.TextLo

@Composable
fun DashboardScreen(onOpenPeer: (String) -> Unit, onPair: () -> Unit) {
    val c = rememberController()
    val peers by c.peers.collectAsState()
    val online by c.signalingOnline.collectAsState()
    val state by c.connState.collectAsState()
    val quality by c.quality.collectAsState()
    val history by c.history.collectAsState()

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Column(Modifier.padding(top = 20.dp, bottom = 4.dp)) {
                Text("WELCOME, SORCERER", color = Cyan, fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp)
                Text("LINKCONTROL", color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Text("CURSED ENERGY DEVICE NETWORK", color = TextLo, fontSize = 10.sp,
                    letterSpacing = 2.sp)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(online)
                    Spacer(Modifier.width(6.dp))
                    Text(if (online) "Signaling connected" else "Connecting to signaling…",
                        color = TextLo, fontSize = 13.sp)
                }
            }
        }

        // live session banner
        if (state == ConnState.CONNECTED || state == ConnState.CONNECTING) {
            item {
                GlowCard {
                    Column {
                        Text(if (state == ConnState.CONNECTED) "Active session" else "Connecting…",
                            color = Cyan, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                        Text("Link quality: ${quality.label}  •  ${quality.fps} fps",
                            color = TextLo, fontSize = 13.sp)
                    }
                }
            }
        }

        item { SectionLabel("Paired devices") }
        if (peers.isEmpty()) {
            item {
                GlowCard {
                    Column {
                        Text("No paired devices yet", color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(4.dp))
                        Text("Tap “Pair a device” to link your two phones with a QR or code.",
                            color = TextLo, fontSize = 13.sp)
                    }
                }
            }
        } else {
            items(peers, key = { it.deviceId }) { p -> PeerRow(p) { onOpenPeer(p.deviceId) } }
        }

        item { Spacer(Modifier.height(4.dp)) }
        item {
            GlowCard(Modifier.clickable { onPair() }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Add, null, tint = Cyan)
                    Spacer(Modifier.width(10.dp))
                    Text("Pair a device", color = Cyan, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        item { SectionLabel("Recent sessions") }
        if (history.isEmpty()) {
            item { Text("No sessions yet.", color = TextLo, fontSize = 13.sp,
                modifier = Modifier.padding(start = 4.dp, bottom = 24.dp)) }
        } else {
            items(history.take(3), key = { it.id }) { h ->
                GlowCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${h.peerName} • ${h.role.name.lowercase()}",
                            color = MaterialTheme.colorScheme.onSurface)
                        Text(h.endReason, color = TextLo, fontSize = 12.sp)
                    }
                }
            }
            item { Spacer(Modifier.height(20.dp)) }
        }
    }
}

@Composable
private fun PeerRow(peer: Peer, onClick: () -> Unit) {
    GlowCard(Modifier.clickable { onClick() }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(42.dp),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.PhoneAndroid, null, tint = Cyan) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(peer.name, color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(peer.online)
                    Spacer(Modifier.width(6.dp))
                    Text(if (peer.online) "Online" else "Offline", color = TextLo, fontSize = 12.sp)
                }
            }
        }
    }
}
