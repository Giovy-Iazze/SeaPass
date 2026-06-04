package com.example.notification

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.data.Certificate
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NotificationHelper(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

    companion object {
        const val CHANNEL_ID = "mariners_wallet_expiry_alerts"
        const val CHANNEL_NAME = "Certificate Expiry Reminders"
        const val CHANNEL_DESC = "Notifications about expiring maritime certificates"
    }

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = CHANNEL_DESC
            }
            notificationManager?.createNotificationChannel(channel)
        }
    }

    fun scheduleExpiries(certificate: Certificate) {
        val expiryMillis = certificate.expiryDate
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val formattedDate = format.format(Date(expiryMillis))

        // Schedule reminders exactly 90, 60, and 30 days before expiration
        val intervals = listOf(90, 60, 30)
        for (days in intervals) {
            val triggerTime = expiryMillis - (days * 24L * 60 * 60 * 1000)
            
            // Only schedule if the alarm represents a future moment
            if (triggerTime > System.currentTimeMillis()) {
                val requestCode = calculateRequestCode(certificate.id, days)
                
                val intent = Intent(context, ExpiryNotificationReceiver::class.java).apply {
                    putExtra("id", certificate.id)
                    putExtra("title", certificate.title)
                    putExtra("expiryDate", formattedDate)
                    putExtra("daysLeft", days)
                }

                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    requestCode,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                if (alarmManager != null) {
                    try {
                        // setAndAllowWhileIdle ensures it fires accurately even during Android Doze mode
                        alarmManager.setAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            triggerTime,
                            pendingIntent
                        )
                        Log.d("NotificationHelper", "Scheduled reminder: ${certificate.title} at $days days before expiry (Trigger time: ${Date(triggerTime)})")
                    } catch (e: Exception) {
                        Log.e("NotificationHelper", "Failed to schedule alarm for days $days: ${e.message}", e)
                    }
                }
            } else {
                Log.d("NotificationHelper", "Skip scheduling $days days reminder for ${certificate.title}: target time is already in the past.")
            }
        }
    }

    fun cancelReminders(certificateId: Int) {
        val intervals = listOf(90, 60, 30)
        for (days in intervals) {
            val requestCode = calculateRequestCode(certificateId, days)
            val intent = Intent(context, ExpiryNotificationReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pendingIntent != null && alarmManager != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
            }
        }
    }

    private fun calculateRequestCode(certificateId: Int, days: Int): Int {
        return certificateId * 1000 + days
    }
}
