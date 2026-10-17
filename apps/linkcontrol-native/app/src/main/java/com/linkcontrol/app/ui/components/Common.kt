package com.linkcontrol.app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linkcontrol.app.ui.theme.Cyan
import com.linkcontrol.app.ui.theme.NavyBorder
import com.linkcontrol.app.ui.theme.NavyCard
import com.linkcontrol.app.ui.theme.Offline
import com.linkcontrol.app.ui.theme.Online
import com.linkcontrol.app.ui.theme.TextLo

@Composable
fun GlowCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    // Holographic hairline: a faint cyan-biased gradient edge instead of a flat border.
    val edge = Brush.linearGradient(
        listOf(Cyan.copy(alpha = 0.55f), NavyBorder, Cyan.copy(alpha = 0.25f))
    )
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = NavyCard),
        border = BorderStroke(1.dp, edge),
    ) { Box(Modifier.padding(16.dp)) { content() } }
}

@Composable
fun StatusDot(online: Boolean, modifier: Modifier = Modifier) {
    if (!online) {
        Box(modifier.size(10.dp).clip(CircleShape).background(Offline))
        return
    }
    // Online: a soft pulsing halo around the dot (anime/holo cue).
    val t = rememberInfiniteTransition(label = "pulse")
    val a by t.animateFloat(
        initialValue = 0.15f, targetValue = 0.5f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "halo"
    )
    Box(modifier.size(18.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(18.dp).clip(CircleShape).background(Online.copy(alpha = a)))
        Box(Modifier.size(9.dp).clip(CircleShape).background(Online))
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        color = TextLo,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.5.sp,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
    )
}

/** Big gradient primary button (Connect / Pair). */
val CyanGradient = Brush.horizontalGradient(listOf(Cyan, Color(0xFF4AF0C8)))

@Composable
fun KeyValueRow(k: String, v: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(k, color = TextLo)
        Text(v, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
    }
}
