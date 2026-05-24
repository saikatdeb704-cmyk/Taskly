package com.example.receiver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.MainActivity

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getIntExtra("task_id", 0)
        val taskTitle = intent.getStringExtra("task_title") ?: "Task Reminder"
        val taskDesc = intent.getStringExtra("task_desc") ?: ""
        val taskCategory = intent.getStringExtra("task_category") ?: "General"
        val taskPriority = intent.getStringExtra("task_priority") ?: "Medium"
        val reminderMinutes = intent.getIntExtra("reminder_offset_minutes", 0)

        showNotification(context, taskId, taskTitle, taskDesc, taskCategory, taskPriority, reminderMinutes)
    }

    private fun showNotification(
        context: Context,
        taskId: Int,
        title: String,
        description: String,
        category: String,
        priority: String,
        reminderMinutes: Int
    ) {
        val channelId = "task_alarm_channel"
        val channelName = "Task Alarms & Reminders"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create Channel for Android O and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(channelId, channelName, importance).apply {
                this.description = "Urgent notifications for scheduled tasks and list alarms"
                enableVibration(true)
                setBypassDnd(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val activityIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            taskId,
            activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        // Set priority badge color/accent based on Task Priority
        val color = when (priority) {
            "High" -> 0xFFD32F2F.toInt()    // Red
            "Medium" -> 0xFFF57C00.toInt()  // Orange
            else -> 0xFF388E3C.toInt()      // Green
        }

        val displayTitle = if (reminderMinutes > 0) "⏰ Reminder: $title" else "🚨 Due Now: $title"

        val textContent = when {
            reminderMinutes > 0 -> {
                val timeStr = if (reminderMinutes >= 60) {
                    val hours = reminderMinutes / 60
                    if (hours == 1) "1 hour" else "$hours hours"
                } else {
                    "$reminderMinutes minutes"
                }
                "This task starts in $timeStr. [$category]"
            }
            description.isNotEmpty() -> {
                "[$category] $description"
            }
            else -> {
                "Task is due now! ($category)"
            }
        }

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info) // System standard fallback icon. We can also use app-specific drawables.
            .setContentTitle(displayTitle)
            .setContentText(textContent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setColor(color)
            .setColorized(true)
            .setSound(soundUri)
            .setVibrate(longArrayOf(0, 500, 200, 500))
            .setContentIntent(pendingIntent)

        notificationManager.notify(taskId, builder.build())
    }
}
