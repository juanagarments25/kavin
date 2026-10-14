package com.kagemonark.system;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Puts the alarms back after the phone restarts, the time changes, or the app updates. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        try { AlarmScheduler.rescheduleFromStore(context); } catch (Exception ignored) {}
    }
}
