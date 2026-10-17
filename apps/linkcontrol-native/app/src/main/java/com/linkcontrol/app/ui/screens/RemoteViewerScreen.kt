package com.linkcontrol.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.SquareFoot
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.linkcontrol.app.data.ConnState
import com.linkcontrol.app.data.ControlGrant
import com.linkcontrol.app.data.Role
import com.linkcontrol.app.ui.rememberController
import com.linkcontrol.app.ui.theme.Cyan
import com.linkcontrol.app.ui.theme.Danger
import com.linkcontrol.app.ui.theme.NavyDeep
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

@Composable
fun RemoteViewerScreen(onExit: () -> Unit) {
    val c = rememberController()
    val state by c.connState.collectAsState()
    val role by c.role.collectAsState()
    val grant by c.controlGrant.collectAsState()
    val quality by c.quality.collectAsState()
    val track by c.remoteTrack.collectAsState()
    val peer by c.activePeer.collectAsState()
    val apps by c.availableApps.collectAsState()

    var fullscreen by remember { mutableStateOf(false) }
    var showApps by remember { mutableStateOf(false) }

    // Leave the screen when the session ends.
    LaunchedEffect(state) {
        if (state == ConnState.IDLE || state == ConnState.DISCONNECTED || state == ConnState.FAILED) onExit()
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {

        if (role == Role.HOST) {
            // On the host, we don't show the remote; we show the sharing status.
            HostSharingPanel(onStop = { c.endSession("stopped_by_host") })
        } else {
            RemoteSurface(track = track, eglContextProvider = { c.webRtcEglContext },
                controlEnabled = grant == ControlGrant.FULL_CONTROL,
                onTap = c::sendTap, onSwipe = c::sendSwipe)
        }

        // top status bar
        if (!fullscreen) {
            Row(
                Modifier.fillMaxWidth().background(Color(0xCC070B18)).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Circle, null, tint = if (state == ConnState.CONNECTED) Cyan else Danger,
                    modifier = Modifier.height(10.dp))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(peer?.name ?: "Session", color = Color.White, fontSize = 14.sp)
                    Text(statusText(state, grant) + " • ${quality.label}",
                        color = Color(0xFF8A97C2), fontSize = 11.sp)
                }
                if (role == Role.CONTROLLER && grant == ControlGrant.FULL_CONTROL) {
                    IconButton(onClick = { showApps = true; c.requestApps() }) {
                        Icon(Icons.Default.Apps, "Apps", tint = Cyan)
                    }
                }
                IconButton(onClick = { fullscreen = true }) {
                    Icon(Icons.Default.Crop, "Fullscreen", tint = Color.White)
                }
            }
        } else {
            IconButton(onClick = { fullscreen = false }, modifier = Modifier.align(Alignment.TopEnd)) {
                Icon(Icons.Default.Crop, "Exit fullscreen", tint = Color.White)
            }
        }

        // bottom control bar (controller + full control only)
        if (!fullscreen && role == Role.CONTROLLER && grant == ControlGrant.FULL_CONTROL) {
            Row(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .background(Color(0xCC070B18)).padding(8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                NavBtn(Icons.AutoMirrored.Filled.ArrowBack, "Back") { c.sendNav("back") }
                NavBtn(Icons.Default.Home, "Home") { c.sendNav("home") }
                NavBtn(Icons.Default.SquareFoot, "Recents") { c.sendNav("recents") }
                IconButton(onClick = { c.endSession("ended_by_controller") }) {
                    Icon(Icons.Default.Circle, "Disconnect", tint = Danger)
                }
            }
        }

        // disconnect FAB for view-only
        if (!fullscreen && role == Role.CONTROLLER && grant != ControlGrant.FULL_CONTROL) {
            IconButton(onClick = { c.endSession("ended_by_controller") },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
                Icon(Icons.Default.Circle, "Disconnect", tint = Danger)
            }
        }

        // remote app launcher panel
        if (showApps) {
            AppsPanel(apps = apps,
                onLaunch = { pkg -> c.launchApp(pkg); showApps = false },
                onRefresh = { c.requestApps() },
                onClose = { showApps = false })
        }
    }
}

@Composable
private fun AppsPanel(
    apps: List<com.linkcontrol.app.data.AppInfo>,
    onLaunch: (String) -> Unit,
    onRefresh: () -> Unit,
    onClose: () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(Color(0xCC03060F)).pointerInput(Unit) {
        detectTapGestures { onClose() }
    }) {
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(0.6f)
                .background(Color(0xFF0E1630))
                .padding(16.dp)
                .pointerInput(Unit) { detectTapGestures { } } // swallow taps inside
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text("Apps on remote device", color = Color.White, fontSize = 16.sp)
                Row {
                    androidx.compose.material3.TextButton(onClick = onRefresh) { Text("Refresh", color = Cyan) }
                    androidx.compose.material3.TextButton(onClick = onClose) { Text("Close", color = Color(0xFF8A97C2)) }
                }
            }
            Spacer(Modifier.height(8.dp))
            if (apps.isEmpty()) {
                Text("Fetching the host's app list…", color = Color(0xFF8A97C2), fontSize = 13.sp)
            } else {
                androidx.compose.foundation.lazy.LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(apps, key = { it.packageName }) { app ->
                        Column(Modifier.fillMaxWidth()
                            .pointerInput(app.packageName) { detectTapGestures { onLaunch(app.packageName) } }
                            .padding(vertical = 12.dp)) {
                            Text(app.label, color = Color.White, fontSize = 15.sp)
                            Text(app.packageName, color = Color(0xFF8A97C2), fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NavBtn(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onClick) { Icon(icon, label, tint = Color.White) }
        Text(label, color = Color(0xFF8A97C2), fontSize = 10.sp)
    }
}

private fun statusText(s: ConnState, g: ControlGrant) = when (s) {
    ConnState.CONNECTED -> if (g == ControlGrant.FULL_CONTROL) "Controlling" else "Viewing"
    ConnState.CONNECTING -> "Connecting…"
    ConnState.WAITING_CONSENT -> "Waiting for approval…"
    else -> s.name
}

/** Host just sees that its screen is being shared, with a prominent Stop. */
@Composable
private fun HostSharingPanel(onStop: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.Circle, null, tint = Cyan, modifier = Modifier.height(48.dp))
        Spacer(Modifier.height(16.dp))
        Text("Your screen is being shared", color = Color.White, fontSize = 18.sp)
        Spacer(Modifier.height(8.dp))
        Text("The paired device can see this screen. You can stop any time — here or from the notification.",
            color = Color(0xFF8A97C2), fontSize = 13.sp)
        Spacer(Modifier.height(24.dp))
        androidx.compose.material3.Button(onClick = onStop,
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Danger)) {
            Text("Stop sharing", color = Color.White)
        }
    }
}

/**
 * Renders the remote video and translates touches into normalised control
 * commands, accounting for the letterboxing produced by aspect-fit scaling.
 */
@Composable
private fun RemoteSurface(
    track: VideoTrack?,
    eglContextProvider: () -> org.webrtc.EglBase.Context?,
    controlEnabled: Boolean,
    onTap: (Float, Float) -> Unit,
    onSwipe: (Float, Float, Float, Float, Long) -> Unit,
) {
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    // frame dimensions reported by the renderer (post-rotation handled below)
    var frameW by remember { mutableStateOf(0) }
    var frameH by remember { mutableStateOf(0) }

    val renderer = remember { mutableStateOf<SurfaceViewRenderer?>(null) }

    Box(Modifier.fillMaxSize().onSizeChanged { viewSize = it }) {
        androidx.compose.ui.viewinterop.AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                SurfaceViewRenderer(ctx).apply {
                    val egl = eglContextProvider()
                    init(egl, object : RendererCommon.RendererEvents {
                        override fun onFirstFrameRendered() {}
                        override fun onFrameResolutionChanged(w: Int, h: Int, rotation: Int) {
                            if (rotation == 90 || rotation == 270) { frameW = h; frameH = w }
                            else { frameW = w; frameH = h }
                        }
                    })
                    setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)
                    setEnableHardwareScaler(true)
                    renderer.value = this
                }
            }
        )

        if (controlEnabled) {
            Box(
                Modifier.fillMaxSize().pointerInput(viewSize, frameW, frameH) {
                    detectTapGestures { pos ->
                        norm(pos, viewSize, frameW, frameH)?.let { (nx, ny) -> onTap(nx, ny) }
                    }
                }.pointerInput(viewSize, frameW, frameH) {
                    var start: Offset? = null
                    detectDragGestures(
                        onDragStart = { start = it },
                        onDragEnd = { }
                    ) { change, _ ->
                        val s = start ?: return@detectDragGestures
                        val a = norm(s, viewSize, frameW, frameH)
                        val b = norm(change.position, viewSize, frameW, frameH)
                        if (a != null && b != null) {
                            onSwipe(a.first, a.second, b.first, b.second, 180)
                            start = change.position // continuous drag → chained swipes
                        }
                    }
                }
            )
        }
    }

    val activeRenderer = renderer.value
    // Attach/detach the sink when the track or renderer changes (not on every recomposition).
    DisposableEffect(activeRenderer, track) {
        if (activeRenderer != null && track != null) track.addSink(activeRenderer)
        onDispose { if (activeRenderer != null && track != null) track.removeSink(activeRenderer) }
    }
    // Release the renderer only when we leave the screen.
    DisposableEffect(Unit) {
        onDispose { renderer.value?.release() }
    }
}

/** Map a touch point to normalised [0,1] host-screen coords, or null if in the letterbox. */
private fun norm(p: Offset, view: IntSize, frameW: Int, frameH: Int): Pair<Float, Float>? {
    if (view.width == 0 || view.height == 0 || frameW == 0 || frameH == 0) return null
    val viewAspect = view.width.toFloat() / view.height
    val frameAspect = frameW.toFloat() / frameH
    val dispW: Float; val dispH: Float; val offX: Float; val offY: Float
    if (frameAspect > viewAspect) {
        dispW = view.width.toFloat(); dispH = view.width / frameAspect
        offX = 0f; offY = (view.height - dispH) / 2f
    } else {
        dispH = view.height.toFloat(); dispW = view.height * frameAspect
        offY = 0f; offX = (view.width - dispW) / 2f
    }
    val nx = (p.x - offX) / dispW
    val ny = (p.y - offY) / dispH
    if (nx < 0f || nx > 1f || ny < 0f || ny > 1f) return null
    return nx to ny
}
