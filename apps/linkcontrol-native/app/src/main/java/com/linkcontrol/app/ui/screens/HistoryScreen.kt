package com.linkcontrol.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linkcontrol.app.data.Role
import com.linkcontrol.app.ui.components.GlowCard
import com.linkcontrol.app.ui.rememberController
import com.linkcontrol.app.ui.theme.Cyan
import com.linkcontrol.app.ui.theme.TextLo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@Composable
fun HistoryScreen() {
    val c = rememberController()
    val history by c.history.collectAsState()
    val fmt = remember { SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text("Connection history", color = MaterialTheme.colorScheme.onBackground,
                fontSize = 24.sp, fontWeight = FontWeight.Bold)
            if (history.isNotEmpty()) TextButton(onClick = { c.clearHistory() }) {
                Text("Clear", color = Cyan)
            }
        }
        Spacer(Modifier.height(12.dp))

        if (history.isEmpty()) {
            Text("No sessions recorded yet.", color = TextLo)
            return
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(history, key = { it.id }) { h ->
                GlowCard {
                    Column {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(h.peerName, color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.SemiBold)
                            Text(if (h.role == Role.CONTROLLER) "Controlled" else "Shared",
                                color = Cyan, fontSize = 12.sp)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(fmt.format(Date(h.startedAt)) + "  •  " + duration(h.startedAt, h.endedAt),
                            color = TextLo, fontSize = 12.sp)
                        Text("Ended: ${h.endReason}", color = TextLo, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

private fun duration(start: Long, end: Long?): String {
    if (end == null) return "—"
    val sec = TimeUnit.MILLISECONDS.toSeconds(end - start).coerceAtLeast(0)
    return if (sec < 60) "${sec}s" else "${sec / 60}m ${sec % 60}s"
}
