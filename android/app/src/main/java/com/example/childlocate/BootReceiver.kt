package com.example.childlocate

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Restarts location sharing after the phone reboots, if it was turned on. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED && Prefs.isEnabled(context)) {
            LocationService.start(context)
        }
    }
}
