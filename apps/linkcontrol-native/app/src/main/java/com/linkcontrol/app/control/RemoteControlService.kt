package com.linkcontrol.app.control

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent

/**
 * Executes approved remote-control commands as real gestures.
 *
 * This is the ONLY non-root way to drive taps/swipes/navigation in other apps
 * on stock Android, and it is gated three ways:
 *   1. The user must manually enable it in Settings ▸ Accessibility.
 *   2. Android shows its own always-on indicator while an a11y service runs.
 *   3. LinkControl only forwards commands after the host taps "Allow control"
 *      for the session (see SessionController / control_response).
 *
 * It declares canRetrieveWindowContent="false", so it cannot read on-screen
 * text, fields, or passwords — only inject the gestures it is handed.
 */
class RemoteControlService : AccessibilityService() {

    override fun onServiceConnected() {
        instance = this
        Log.i(TAG, "Remote control service connected")
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) { /* not used */ }
    override fun onInterrupt() { /* no-op */ }

    private fun screenSize(): Pair<Int, Int> {
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            val b = wm.currentWindowMetrics.bounds
            b.width() to b.height()
        } else {
            val dm = DisplayMetrics()
            @Suppress("DEPRECATION") wm.defaultDisplay.getRealMetrics(dm)
            dm.widthPixels to dm.heightPixels
        }
    }

    fun execute(cmd: ControlCommand) {
        when (cmd) {
            is ControlCommand.Nav -> when (cmd.name) {
                "back" -> performGlobalAction(GLOBAL_ACTION_BACK)
                "home" -> performGlobalAction(GLOBAL_ACTION_HOME)
                "recents" -> performGlobalAction(GLOBAL_ACTION_RECENTS)
                else -> Log.w(TAG, "unknown nav ${cmd.name}")
            }
            is ControlCommand.Tap -> {
                val (w, h) = screenSize()
                dispatch(buildTap(cmd.x * w, cmd.y * h))
            }
            is ControlCommand.Swipe -> {
                val (w, h) = screenSize()
                dispatch(buildSwipe(cmd.x1 * w, cmd.y1 * h, cmd.x2 * w, cmd.y2 * h, cmd.durationMs))
            }
        }
    }

    private fun buildTap(px: Float, py: Float): GestureDescription {
        val path = Path().apply { moveTo(px, py); lineTo(px, py) }
        return GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 60))
            .build()
    }

    private fun buildSwipe(x1: Float, y1: Float, x2: Float, y2: Float, dur: Long): GestureDescription {
        val path = Path().apply { moveTo(x1, y1); lineTo(x2, y2) }
        val duration = dur.coerceIn(20, 3000)
        return GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
            .build()
    }

    private fun dispatch(g: GestureDescription) {
        val ok = dispatchGesture(g, null, null)
        if (!ok) Log.w(TAG, "dispatchGesture rejected")
    }

    companion object {
        private const val TAG = "RemoteControlSvc"
        @Volatile var instance: RemoteControlService? = null
            private set

        val isEnabled: Boolean get() = instance != null
    }
}
