package com.linkcontrol.app.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linkcontrol.app.control.RemoteControlService
import com.linkcontrol.app.ui.components.GlowCard
import com.linkcontrol.app.ui.components.SectionLabel
import com.linkcontrol.app.ui.components.StatusDot
import com.linkcontrol.app.ui.rememberController
import com.linkcontrol.app.ui.theme.Cyan
import com.linkcontrol.app.ui.theme.Danger
import com.linkcontrol.app.ui.theme.NavyDeep
import com.linkcontrol.app.ui.theme.TextLo

@Composable
fun SecurityScreen() {
    val c = rememberController()
    val context = LocalContext.current
    val peers by c.peers.collectAsState()
    val scroll = rememberScrollState()

    var url by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { url = c.currentSignalingUrl() }
    val a11yEnabled = RemoteControlService.isEnabled

    Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(16.dp)) {
        Text("Security", color = MaterialTheme.colorScheme.onBackground,
            fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))

        SectionLabel("Remote-control permission")
        GlowCard {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(a11yEnabled)
                    Spacer(Modifier.height(0.dp))
                    Text("  ${if (a11yEnabled) "Enabled" else "Not enabled"}",
                        color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(8.dp))
                Text("Remote taps and swipes require LinkControl's accessibility service. " +
                        "Enable it only if you want this phone to be controllable. It never reads " +
                        "screen content or passwords, and you can turn it off any time.",
                    color = TextLo, fontSize = 13.sp)
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = {
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (a11yEnabled) "Open accessibility settings" else "Enable in settings", color = Cyan)
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        SectionLabel("Signaling server")
        GlowCard {
            Column {
                OutlinedTextField(value = url, onValueChange = { url = it },
                    label = { Text("wss://host or ws://host:port") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                Button(onClick = { c.setSignalingUrl(url) }, modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Cyan)) {
                    Text("Save & reconnect", color = NavyDeep, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(8.dp))
                Text("Use wss:// (TLS) in production. For reliable connections across mobile " +
                        "networks, also configure a TURN server — see DEPLOYMENT.md.",
                    color = TextLo, fontSize = 12.sp)
            }
        }

        Spacer(Modifier.height(20.dp))
        SectionLabel("This device")
        GlowCard {
            Text("ID: ${c.myDeviceId()?.take(12) ?: "registering…"}", color = TextLo, fontSize = 13.sp)
        }

        Spacer(Modifier.height(20.dp))
        SectionLabel("Trusted devices")
        if (peers.isEmpty()) {
            Text("None yet.", color = TextLo, fontSize = 13.sp)
        } else {
            peers.forEach { p ->
                GlowCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(p.name, color = MaterialTheme.colorScheme.onSurface)
                        OutlinedButton(onClick = { c.unpair(p.deviceId) },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Danger)) {
                            Text("Remove")
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
