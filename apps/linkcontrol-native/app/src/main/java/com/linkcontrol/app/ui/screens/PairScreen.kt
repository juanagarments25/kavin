package com.linkcontrol.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.linkcontrol.app.ui.components.GlowCard
import com.linkcontrol.app.ui.components.QrScanner
import com.linkcontrol.app.ui.components.qrImage
import com.linkcontrol.app.ui.rememberController
import com.linkcontrol.app.ui.theme.Cyan
import com.linkcontrol.app.ui.theme.NavyDeep
import com.linkcontrol.app.ui.theme.TextLo
import org.json.JSONObject

private enum class Mode { RECEIVE, GIVE }

@Composable
fun PairScreen(onDone: () -> Unit) {
    val c = rememberController()
    var mode by remember { mutableStateOf(Mode.RECEIVE) }
    val pairingCode by c.pairingCode.collectAsState()

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Pair a device", color = MaterialTheme.colorScheme.onBackground,
            fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))

        // segmented toggle
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)) {
            SegTab("This phone shares", mode == Mode.RECEIVE, Modifier.weight(1f)) { mode = Mode.RECEIVE }
            SegTab("This phone controls", mode == Mode.GIVE, Modifier.weight(1f)) { mode = Mode.GIVE }
        }
        Spacer(Modifier.height(20.dp))

        when (mode) {
            Mode.RECEIVE -> HostPairing(pairingCode, onGenerate = { c.createPairingCode() })
            Mode.GIVE -> ControllerPairing(onRedeem = { code, url -> c.redeemPairingCode(code, url); onDone() })
        }
    }
}

@Composable
private fun SegTab(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) Cyan else Color.Transparent)
            .clickable { onClick() }
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = if (selected) NavyDeep else TextLo,
            fontWeight = FontWeight.SemiBold, fontSize = 13.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun HostPairing(code: String?, onGenerate: () -> Unit) {
    val c = rememberController()
    // Need the current signaling URL embedded in the QR so the controller can match it.
    var url by remember { mutableStateOf("") }
    androidx.compose.runtime.LaunchedEffect(Unit) { url = c.currentSignalingUrl() }

    GlowCard {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Show this to the controlling phone", color = TextLo, fontSize = 13.sp)
            Spacer(Modifier.height(16.dp))
            if (code == null) {
                Button(onClick = onGenerate, colors = ButtonDefaults.buttonColors(containerColor = Cyan)) {
                    Text("Generate pairing code", color = NavyDeep, fontWeight = FontWeight.SemiBold)
                }
            } else {
                val payload = remember(code, url) {
                    JSONObject().put("c", code).put("u", url).toString()
                }
                Image(qrImage(payload), contentDescription = "Pairing QR",
                    modifier = Modifier.fillMaxWidth(0.72f).aspectRatio(1f)
                        .clip(RoundedCornerShape(12.dp)))
                Spacer(Modifier.height(16.dp))
                Text(code, color = Cyan, fontSize = 34.sp, fontWeight = FontWeight.Bold,
                    letterSpacing = 6.sp)
                Spacer(Modifier.height(4.dp))
                Text("Code expires in 5 minutes · one-time use", color = TextLo, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun ControllerPairing(onRedeem: (String, String?) -> Unit) {
    val context = LocalContext.current
    var hasCam by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasCam = it
    }
    var manual by remember { mutableStateOf("") }

    Column {
        GlowCard {
            Column {
                Text("Scan the host's QR code", color = TextLo, fontSize = 13.sp)
                Spacer(Modifier.height(12.dp))
                Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp))
                    .background(NavyDeep), contentAlignment = Alignment.Center) {
                    if (hasCam) {
                        QrScanner { raw -> onRedeem(extractCode(raw), extractUrl(raw)) }
                        ScannerOverlay()
                    } else {
                        Button(onClick = { launcher.launch(Manifest.permission.CAMERA) },
                            colors = ButtonDefaults.buttonColors(containerColor = Cyan)) {
                            Text("Enable camera", color = NavyDeep, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        GlowCard {
            Column {
                Text("…or enter the 6-digit code", color = TextLo, fontSize = 13.sp)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(value = manual, onValueChange = { manual = it.filter(Char::isDigit).take(6) },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                Button(onClick = { if (manual.length == 6) onRedeem(manual, null) },
                    enabled = manual.length == 6, modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Cyan)) {
                    Text("Pair", color = NavyDeep, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/** Glowing holographic scanner frame: corner brackets + a sweeping scanline. */
@Composable
private fun ScannerOverlay() {
    val t = rememberInfiniteTransition(label = "scan")
    val y by t.animateFloat(0f, 1f,
        infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Reverse), label = "y")
    val glow by t.animateFloat(0.4f, 1f,
        infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "glow")

    androidx.compose.foundation.Canvas(Modifier.fillMaxSize().padding(14.dp)) {
        val w = size.width; val h = size.height
        val len = minOf(w, h) * 0.16f
        val stroke = 4f
        val cyan = Cyan.copy(alpha = glow)
        // four corner brackets
        fun corner(cx: Float, cy: Float, dx: Int, dy: Int) {
            drawLine(cyan, Offset(cx, cy), Offset(cx + dx * len, cy), stroke, StrokeCap.Round)
            drawLine(cyan, Offset(cx, cy), Offset(cx, cy + dy * len), stroke, StrokeCap.Round)
        }
        corner(0f, 0f, 1, 1)
        corner(w, 0f, -1, 1)
        corner(0f, h, 1, -1)
        corner(w, h, -1, -1)
        // sweeping scanline with a soft vertical falloff
        val sy = y * h
        drawRect(
            brush = Brush.verticalGradient(
                listOf(Cyan.copy(alpha = 0f), Cyan.copy(alpha = 0.55f), Cyan.copy(alpha = 0f)),
                startY = sy - 26f, endY = sy + 26f),
            topLeft = Offset(0f, sy - 26f), size = Size(w, 52f))
        drawLine(Cyan.copy(alpha = 0.9f), Offset(0f, sy), Offset(w, sy), 2f)
    }
}

/** QR payload may be the JSON {c,u} or just a bare code. */
private fun extractCode(raw: String): String = runCatching {
    JSONObject(raw).getString("c")
}.getOrDefault(raw.filter(Char::isDigit).take(6))

private fun extractUrl(raw: String): String? = runCatching {
    JSONObject(raw).optString("u").ifBlank { null }
}.getOrNull()
