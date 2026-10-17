package com.linkcontrol.app.nav

sealed class Dest(val route: String) {
    data object Dashboard : Dest("dashboard")
    data object Pair : Dest("pair")
    data object Details : Dest("details/{peerId}") { fun of(id: String) = "details/$id" }
    data object Viewer : Dest("viewer")
    data object History : Dest("history")
    data object Security : Dest("security")
}
