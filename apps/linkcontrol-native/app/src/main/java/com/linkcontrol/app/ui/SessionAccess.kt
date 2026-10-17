package com.linkcontrol.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.linkcontrol.app.session.SessionController

@Composable
fun rememberController(): SessionController {
    val ctx = LocalContext.current.applicationContext
    return remember { SessionController.get(ctx) }
}
