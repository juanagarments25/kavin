package com.linkcontrol.app

import android.app.Application
import com.linkcontrol.app.session.SessionController

class LinkControlApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Create the controller and connect to signaling as soon as the app starts.
        SessionController.get(this).bootstrap()
    }
}
