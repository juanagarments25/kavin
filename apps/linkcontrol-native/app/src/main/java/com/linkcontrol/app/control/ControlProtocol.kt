package com.linkcontrol.app.control

import org.json.JSONObject

/**
 * Control commands sent by the controller over the WebRTC data channel and
 * executed on the host by RemoteControlService.
 *
 * Coordinates are NORMALISED (0f..1f) relative to the host screen, so they are
 * independent of the controller's display size and the stream resolution. The
 * host multiplies them back up by its real screen dimensions.
 */
sealed interface ControlCommand {
    fun toJson(): String

    data class Tap(val x: Float, val y: Float) : ControlCommand {
        override fun toJson() = obj("tap") { put("x", x); put("y", y) }
    }
    data class Swipe(
        val x1: Float, val y1: Float, val x2: Float, val y2: Float, val durationMs: Long,
    ) : ControlCommand {
        override fun toJson() = obj("swipe") {
            put("x1", x1); put("y1", y1); put("x2", x2); put("y2", y2); put("d", durationMs)
        }
    }
    /** name: "back" | "home" | "recents" */
    data class Nav(val name: String) : ControlCommand {
        override fun toJson() = obj("nav") { put("name", name) }
    }

    /** Controller asks the host for its list of available apps. */
    data object ListApps : ControlCommand {
        override fun toJson() = obj("list_apps") {}
    }

    /** Controller asks the host to open an app by package name. */
    data class LaunchApp(val packageName: String) : ControlCommand {
        override fun toJson() = obj("launch_app") { put("pkg", packageName) }
    }

    companion object {
        private inline fun obj(type: String, build: JSONObject.() -> Unit): String =
            JSONObject().apply { put("t", type); build() }.toString()

        fun parse(json: String): ControlCommand? = runCatching {
            val o = JSONObject(json)
            when (o.getString("t")) {
                "tap" -> Tap(o.getDouble("x").toFloat(), o.getDouble("y").toFloat())
                "swipe" -> Swipe(
                    o.getDouble("x1").toFloat(), o.getDouble("y1").toFloat(),
                    o.getDouble("x2").toFloat(), o.getDouble("y2").toFloat(), o.getLong("d"))
                "nav" -> Nav(o.getString("name"))
                "list_apps" -> ListApps
                "launch_app" -> LaunchApp(o.getString("pkg"))
                else -> null
            }
        }.getOrNull()
    }
}
