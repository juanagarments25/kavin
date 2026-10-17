package com.linkcontrol.app.control

import android.content.Context
import android.content.Intent
import android.util.Log
import com.linkcontrol.app.data.AppInfo

/**
 * Host-side helper: lists the apps this phone makes available to a controller,
 * and launches one on request.
 *
 * Enumeration uses PackageManager over the LAUNCHER intent. On Android 11+ this
 * needs the <queries> element declared in the manifest (it is) — we do NOT use
 * the Play-restricted QUERY_ALL_PACKAGES permission.
 *
 * Launching is an ordinary `startActivity` of the app's own launcher intent — it
 * never bypasses any Android restriction. See the background-activity-start note
 * in README; on newer Android this works reliably when LinkControl holds the
 * "Display over other apps" permission.
 */
object AppCatalog {

    fun list(context: Context): List<AppInfo> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return runCatching {
            pm.queryIntentActivities(intent, 0)
                .mapNotNull { ri ->
                    val pkg = ri.activityInfo?.packageName ?: return@mapNotNull null
                    if (pkg == context.packageName) return@mapNotNull null // hide ourselves
                    AppInfo(ri.loadLabel(pm).toString(), pkg)
                }
                .distinctBy { it.packageName }
                .sortedBy { it.label.lowercase() }
        }.getOrElse {
            Log.w(TAG, "app enumeration failed", it); emptyList()
        }
    }

    /** @return true if an activity was started. */
    fun launch(context: Context, packageName: String): Boolean {
        val launch = context.packageManager.getLaunchIntentForPackage(packageName)
            ?: return false.also { Log.w(TAG, "no launch intent for $packageName") }
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(launch); true }
            .getOrElse { Log.w(TAG, "launch failed for $packageName", it); false }
    }

    private const val TAG = "AppCatalog"
}
