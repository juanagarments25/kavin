package com.linkcontrol.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.linkcontrol.app.data.ControlGrant
import com.linkcontrol.app.nav.Dest
import com.linkcontrol.app.session.SessionController
import com.linkcontrol.app.ui.rememberController
import com.linkcontrol.app.ui.screens.DashboardScreen
import com.linkcontrol.app.ui.screens.DeviceDetailsScreen
import com.linkcontrol.app.ui.screens.HistoryScreen
import com.linkcontrol.app.ui.screens.PairScreen
import com.linkcontrol.app.ui.screens.RemoteViewerScreen
import com.linkcontrol.app.ui.screens.SecurityScreen
import com.linkcontrol.app.ui.theme.Cyan
import com.linkcontrol.app.ui.theme.LinkControlTheme

class MainActivity : ComponentActivity() {

    private lateinit var projectionManager: MediaProjectionManager
    private var pendingGrant: ControlGrant = ControlGrant.NONE

    // launcher for the official Android screen-capture consent dialog
    private val projectionLauncher = registerForActivityResult(StartActivityForResult()) { result ->
        val data = result.data
        if (result.resultCode == RESULT_OK && data != null) {
            val (w, h) = realScreenSize(this)
            SessionController.get(this).hostApproveAndCapture(pendingGrant, data, w, h)
        } else {
            Toast.makeText(this, "Screen share permission denied", Toast.LENGTH_SHORT).show()
            // No capture consent → decline the request and tell the other device.
            SessionController.get(this).denyIncoming()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        setContent {
            LinkControlTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppRoot(onRequestCapture = { grant ->
                        pendingGrant = grant
                        // launches Android's own consent dialog — we never bypass it
                        projectionLauncher.launch(projectionManager.createScreenCaptureIntent())
                    })
                }
            }
        }
    }
}

@Composable
private fun AppRoot(onRequestCapture: (ControlGrant) -> Unit) {
    val nav = rememberNavController()
    val c = rememberController()
    val context = LocalContext.current

    // ask for notification permission (needed to show the capture notification on 13+)
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // transient messages
    LaunchedEffect(Unit) {
        c.events.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }

    // incoming connection → when a session becomes active, jump to the viewer
    val connState by c.connState.collectAsState()
    LaunchedEffect(connState) {
        if (connState == com.linkcontrol.app.data.ConnState.CONNECTED) {
            nav.navigate(Dest.Viewer.route) {
                launchSingleTop = true
            }
        }
    }

    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val showBar = route in setOf(Dest.Dashboard.route, Dest.History.route, Dest.Security.route)

    Scaffold(
        bottomBar = { if (showBar) BottomBar(route) { nav.navigateTab(it) } }
    ) { pad ->
        NavHost(nav, startDestination = Dest.Dashboard.route, modifier = Modifier.padding(pad)) {
            composable(Dest.Dashboard.route) {
                DashboardScreen(
                    onOpenPeer = { nav.navigate(Dest.Details.of(it)) },
                    onPair = { nav.navigate(Dest.Pair.route) })
            }
            composable(Dest.Pair.route) { PairScreen(onDone = { nav.popBackStack() }) }
            composable(Dest.Details.route) { entry ->
                val id = entry.arguments?.getString("peerId").orEmpty()
                DeviceDetailsScreen(id,
                    onConnect = { /* viewer opens automatically when connected */ },
                    onUnpaired = { nav.popBackStack() })
            }
            composable(Dest.Viewer.route) {
                RemoteViewerScreen(onExit = {
                    nav.popBackStack(Dest.Dashboard.route, inclusive = false)
                })
            }
            composable(Dest.History.route) { HistoryScreen() }
            composable(Dest.Security.route) { SecurityScreen() }
        }
    }

    // Host consent dialog — shown over any screen when a request arrives.
    val req by c.incomingRequest.collectAsState()
    req?.let { r ->
        AlertDialog(
            onDismissRequest = { c.denyIncoming() },
            title = { Text("${r.fromName} wants to connect") },
            text = {
                Text(if (r.wantsControl)
                    "They are requesting to view AND control this screen. You choose what to allow. " +
                        "Android will then ask you to confirm screen sharing."
                else
                    "They are requesting to view this screen. Android will then ask you to confirm screen sharing.")
            },
            confirmButton = {
                TextButton(onClick = {
                    onRequestCapture(if (r.wantsControl) ControlGrant.FULL_CONTROL else ControlGrant.VIEW_ONLY)
                }) { Text(if (r.wantsControl) "Allow control" else "Allow", color = Cyan) }
            },
            dismissButton = {
                if (r.wantsControl) {
                    TextButton(onClick = {
                        onRequestCapture(ControlGrant.VIEW_ONLY)
                    }) { Text("View only", color = Cyan) }
                }
                TextButton(onClick = { c.denyIncoming() }) { Text("Deny") }
            }
        )
    }
}

@Composable
private fun BottomBar(current: String?, onSelect: (String) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        NavigationBarItem(
            selected = current == Dest.Dashboard.route,
            onClick = { onSelect(Dest.Dashboard.route) },
            icon = { Icon(Icons.Default.Home, "Home") }, label = { Text("Home") })
        NavigationBarItem(
            selected = current == Dest.History.route,
            onClick = { onSelect(Dest.History.route) },
            icon = { Icon(Icons.Default.History, "History") }, label = { Text("History") })
        NavigationBarItem(
            selected = current == Dest.Security.route,
            onClick = { onSelect(Dest.Security.route) },
            icon = { Icon(Icons.Default.Security, "Security") }, label = { Text("Security") })
    }
}

private fun androidx.navigation.NavController.navigateTab(route: String) {
    navigate(route) {
        popUpTo(Dest.Dashboard.route) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private fun realScreenSize(context: Context): Pair<Int, Int> {
    val wm = context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val b = wm.currentWindowMetrics.bounds; b.width() to b.height()
    } else {
        val dm = android.util.DisplayMetrics()
        @Suppress("DEPRECATION") wm.defaultDisplay.getRealMetrics(dm)
        dm.widthPixels to dm.heightPixels
    }
}
