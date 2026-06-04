package com.example.notification

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity

class ExpiryNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        val certId = intent.getIntExtra("id", 0)
        val certTitle = intent.getStringExtra("title") ?: "Certificate"
        val expiryDate = intent.getStringExtra("expiryDate") ?: ""
        val daysLeft = intent.getIntExtra("daysLeft", 30)

        Log.d("ExpiryReceiver", "Alarms triggered for certificate $certTitle (ID $certId) at $daysLeft days.")

        // Open the MainActivity when clicking the notification
        val activityIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            certId,
            activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = "Certificate Expiring Soon"
        val message = "Reminder: Your $certTitle is expiring on $expiryDate. Please plan renewal."

        val notification = NotificationCompat.Builder(context, NotificationHelper.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info) // Stable system status icon
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        val notificationId = certId * 1000 + daysLeft
        notificationManager?.notify(notificationId, notification)
    }
}
