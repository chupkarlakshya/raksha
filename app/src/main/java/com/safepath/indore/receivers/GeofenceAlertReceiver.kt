package com.safepath.indore.receivers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.google.android.gms.wearable.Wearable
import java.util.Locale

class GeofenceAlertReceiver : BroadcastReceiver() {

    private var tts: TextToSpeech? = null

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "com.safepath.indore.ACTION_MANUAL_GEOFENCE_TRIGGER") {
            triggerAlerts(context)
            return
        }

        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError() || event.geofenceTransition != Geofence.GEOFENCE_TRANSITION_ENTER) return

        triggerAlerts(context)
    }

    private fun triggerAlerts(context: Context) {
        // 1. Text-to-Speech voice warning
        speakWarning(context)

        // 2. High-priority notification with red banner (via colorized notification)
        showNotification(context)

        // 3. Custom vibration pattern
        vibrateWarning(context)

        // 4. Send alert to Wear OS watch
        sendWatchAlert(context)
    }

    private fun speakWarning(context: Context) {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                tts?.speak("Caution! You are entering a reported high-risk area. Stay alert.",
                    TextToSpeech.QUEUE_FLUSH, null, "unsafe_alert_tts")
            }
        }
    }

    private fun showNotification(context: Context) {
        val channelId = "unsafe_area_alert"
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Unsafe Area Alerts", NotificationManager.IMPORTANCE_HIGH)
            channel.enableVibration(true)
            channel.description = "Warns when you enter a blacklisted area"
            nm.createNotificationChannel(channel)
        }

        val clickIntent = Intent(context, com.safepath.indore.MainActivity::class.java)
        val pendingIntent = android.app.PendingIntent.getActivity(
            context, 0, clickIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("⚠️ Unsafe Zone")
            .setContentText("You entered a blacklisted high‑risk area!")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setColorized(true)
            .setColor(Color.RED)                    // red banner on lockscreen
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()

        nm.notify(1001, notification)
    }

    private fun vibrateWarning(context: Context) {
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val pattern = longArrayOf(0, 300, 100, 300, 100, 600) // long-short-long
            val vibe = VibrationEffect.createWaveform(pattern, -1) // no repeat
            vibrator.vibrate(vibe)
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(longArrayOf(0, 300, 100, 300, 100, 600), -1)
        }
    }

    private fun sendWatchAlert(context: Context) {
        Wearable.getNodeClient(context).connectedNodes.addOnSuccessListener { nodes ->
            for (node in nodes) {
                Wearable.getMessageClient(context)
                    .sendMessage(node.id, "/unsafe_area", "danger".toByteArray())
                    .addOnSuccessListener { Log.d("GeofenceReceiver", "Watch alert sent") }
                    .addOnFailureListener { Log.e("GeofenceReceiver", "Watch send failed", it) }
            }
        }
    }
}
