package com.linkcontrol.app.webrtc

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.linkcontrol.app.MainActivity
import com.linkcontrol.app.R
import com.linkcontrol.app.session.SessionController

/**
 * Foreground service that legally hosts the MediaProjection capture on Android
 * 10+ (type mediaProjection). Its notification is ongoing and cannot be swiped
 * away, and carries a Stop action — so a shared screen is always visible to the
 * host and always one tap from ending. This directly satisfies the
 * "never hide the session" and "stop sharing immediately" requirements.
 */
class ScreenCaptureService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                SessionController.get(applicationContext).endSession("stopped_by_host")
                stopSelf()
                return START_NOT_STICKY
            }
            else -> startCapture(intent)
        }
        return START_NOT_STICKY
    }

    private fun startCapture(intent: Intent?) {
        createChannel()
        startForegroundCompat()

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
        val data = intent?.let { IntentCompat.projectionData(it) }
        val w = intent?.getIntExtra(EXTRA_WIDTH, 0) ?: 0
        val h = intent?.getIntExtra(EXTRA_HEIGHT, 0) ?: 0
        if (data == null || resultCode == 0 || w == 0 || h == 0) {
            stopSelf(); return
        }
        // Capture must begin only now that we are a running mediaProjection FGS.
        SessionController.get(applicationContext).onCaptureServiceReady(data, w, h)
    }

    private fun startForegroundCompat() {
        val notif = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(
            this, 1, Intent(this, ScreenCaptureService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.capture_notif_title))
            .setContentText(getString(R.string.capture_notif_text))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(open)
            .addAction(0, "Stop sharing", stop)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            val ch = NotificationChannel(CHANNEL_ID, getString(R.string.capture_channel),
                NotificationManager.IMPORTANCE_HIGH).apply {
                description = getString(R.string.capture_notif_text)
                setShowBadge(true)
            }
            nm.createNotificationChannel(ch)
        }
    }

    companion object {
        private const val CHANNEL_ID = "remote_session"
        private const val NOTIF_ID = 42
        const val ACTION_STOP = "com.linkcontrol.app.STOP_CAPTURE"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_DATA = "result_data"
        const val EXTRA_WIDTH = "width"
        const val EXTRA_HEIGHT = "height"

        fun start(context: Context, resultCode: Int, data: Intent, width: Int, height: Int) {
            val i = Intent(context, ScreenCaptureService::class.java).apply {
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_DATA, data)
                putExtra(EXTRA_WIDTH, width)
                putExtra(EXTRA_HEIGHT, height)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(i)
            else context.startService(i)
        }

        fun stop(context: Context) {
            context.startService(Intent(context, ScreenCaptureService::class.java).setAction(ACTION_STOP))
        }
    }
}

/** Backport-safe extraction of the parcelled projection Intent. */
private object IntentCompat {
    fun projectionData(i: Intent): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            i.getParcelableExtra(ScreenCaptureService.EXTRA_DATA, Intent::class.java)
        else @Suppress("DEPRECATION") i.getParcelableExtra(ScreenCaptureService.EXTRA_DATA)
}
