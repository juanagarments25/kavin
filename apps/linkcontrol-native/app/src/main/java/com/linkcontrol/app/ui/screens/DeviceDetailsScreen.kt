package com.linkcontrol.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linkcontrol.app.ui.components.GlowCard
import com.linkcontrol.app.ui.components.KeyValueRow
import com.linkcontrol.app.ui.components.SectionLabel
import com.linkcontrol.app.ui.rememberController
import com.linkcontrol.app.ui.theme.Cyan
import com.linkcontrol.app.ui.theme.Danger
import com.linkcontrol.app.ui.theme.NavyDeep
import com.linkcontrol.app.ui.theme.TextLo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DeviceDetailsScreen(peerId: String, onConnect: () -> Unit, onUnpaired: () -> Unit) {
    val c = rememberController()
    val peers by c.peers.collectAsState()
    val peer = peers.firstOrNull { it.deviceId == peerId }

    if (peer == null) {
        Column(Modifier.fillMaxSize().padding(16.dp)) { Text("Device not found", color = TextLo) }
        return
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(peer.name, color = MaterialTheme.colorScheme.onBackground,
            fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))

        GlowCard {
            Column {
                KeyValueRow("Status", if (peer.online) "Online" else "Offline")
                KeyValueRow("Device ID", peer.deviceId.take(8) + "…")
                KeyValueRow("Paired", SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(peer.pairedAt)))
            }
        }

        Spacer(Modifier.height(20.dp))
        SectionLabel("Start a session")
        Text("The other phone must approve before anything is shared.",
            color = TextLo, fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp, bottom = 10.dp))

        Button(
            onClick = { c.requestSession(peer, wantControl = true); onConnect() },
            enabled = peer.online,
            modifier = Modifier.fillMaxWidth().height(54.dp)
                .shadow(18.dp, RoundedCornerShape(27.dp), ambientColor = Cyan, spotColor = Cyan),
            shape = RoundedCornerShape(27.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Cyan)
        ) { Text("Connect (request control)", color = NavyDeep, fontWeight = FontWeight.Bold) }

        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = { c.requestSession(peer, wantControl = false); onConnect() },
            enabled = peer.online,
            modifier = Modifier.fillMaxWidth().height(54.dp),
        ) { Text("Connect (view only)", color = Cyan, fontWeight = FontWeight.SemiBold) }

        Spacer(Modifier.weight(1f))
        OutlinedButton(
            onClick = { c.unpair(peer.deviceId); onUnpaired() },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Danger)
        ) { Text("Remove / untrust this device") }
        Spacer(Modifier.height(8.dp))
    }
}
