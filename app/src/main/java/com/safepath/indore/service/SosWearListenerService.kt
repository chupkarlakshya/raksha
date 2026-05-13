package com.safepath.indore.service

import android.content.Intent
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.safepath.indore.ui.SosActivity

/**
 * Background service that listens for messages from the paired Wear OS watch.
 * When the watch sends an SOS message on path "/sos", this service launches
 * SosActivity on the phone — even if the app is not currently open.
 */
class SosWearListenerService : WearableListenerService() {

    companion object {
        private const val SOS_PATH = "/sos"
        // Default Indore coordinates used when GPS is not available
        private const val DEFAULT_LAT = 22.7196
        private const val DEFAULT_LNG = 75.8577
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        super.onMessageReceived(messageEvent)

        if (messageEvent.path == SOS_PATH) {
            val message = String(messageEvent.data)
            if (message == "SOS_TRIGGERED") {
                launchSosActivity()
            }
        }
    }

    private fun launchSosActivity() {
        val intent = Intent(this, SosActivity::class.java).apply {
            // Flags needed to start an Activity from a non-Activity context (a Service)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(SosActivity.EXTRA_LAT, DEFAULT_LAT)
            putExtra(SosActivity.EXTRA_LNG, DEFAULT_LNG)
        }
        startActivity(intent)
    }
}
