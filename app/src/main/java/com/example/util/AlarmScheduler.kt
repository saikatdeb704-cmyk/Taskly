package com.example.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.data.Task
import com.example.receiver.AlarmReceiver

object AlarmScheduler {

    private const val TAG = "AlarmScheduler"

    fun scheduleAlarm(context: Context, task: Task) {
        val dueDate = task.dueDate ?: return
        if (task.isCompleted) {
            cancelAlarm(context, task.id)
            return
        }

        val offsetMs = task.reminderMinutesBefore * 60 * 1000L
        var alarmTime = dueDate - offsetMs
        val now = System.currentTimeMillis()
        var actualReminderMinutes = task.reminderMinutesBefore

        if (alarmTime < now) {
            if (dueDate > now) {
                // If the offset alarm time has already passed but the due date is in the future,
                // trigger exactly on the due date.
                alarmTime = dueDate
                actualReminderMinutes = 0
            } else {
                // Both times have passed
                return
            }
        }

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("task_id", task.id)
            putExtra("task_title", task.title)
            putExtra("task_desc", task.description)
            putExtra("task_category", task.category)
            putExtra("task_priority", task.priority)
            putExtra("reminder_offset_minutes", actualReminderMinutes)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            task.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        alarmTime,
                        pendingIntent
                    )
                    Log.d(TAG, "Scheduled exact alarm for task ${task.id} at $alarmTime")
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        alarmTime,
                        pendingIntent
                    )
                    Log.d(TAG, "Scheduled fallback exact alarm for task ${task.id} at $alarmTime (no exact privilege)")
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    alarmTime,
                    pendingIntent
                )
                Log.d(TAG, "Scheduled exact alarm for task ${task.id} on API M+")
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    alarmTime,
                    pendingIntent
                )
                Log.d(TAG, "Scheduled exact alarm on older API task ${task.id}")
            }
        } catch (e: SecurityException) {
            // Fallback for security exceptions on restricted Android versions
            alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                alarmTime,
                pendingIntent
            )
            Log.e(TAG, "SecurityException: Scheduled fallback alarm for task ${task.id}", e)
        }
    }

    fun cancelAlarm(context: Context, taskId: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            taskId,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )

        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
            Log.d(TAG, "Canceled alarm for task $taskId")
        }
    }
}
