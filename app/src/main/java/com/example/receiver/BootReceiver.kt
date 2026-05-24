package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.AppDatabase
import com.example.util.AlarmScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val pendingResult = goAsync()
            val database = AppDatabase.getDatabase(context)
            
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val allTasks = database.taskDao().getAllTasks().firstOrNull() ?: emptyList()
                    val now = System.currentTimeMillis()
                    for (task in allTasks) {
                        if (!task.isCompleted && task.dueDate != null && task.dueDate > now) {
                            AlarmScheduler.scheduleAlarm(context, task)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
