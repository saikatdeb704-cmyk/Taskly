package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

data class SubTask(
    val title: String,
    val isCompleted: Boolean = false
) {
    override fun toString(): String {
        return "$title||$isCompleted"
    }

    companion object {
        fun fromString(str: String): SubTask? {
            val parts = str.split("||")
            if (parts.size >= 2) {
                return SubTask(parts[0], parts[1].toBoolean())
            }
            return null
        }
    }
}

@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val description: String = "",
    val category: String = "Personal",
    val priority: String = "Medium", // "High", "Medium", "Low"
    val dueDate: Long? = null,
    val isCompleted: Boolean = false,
    val reminderMinutesBefore: Int = 0, // minutes before due date to trigger the alarm/reminder. 0 means at time of event.
    val createdAt: Long = System.currentTimeMillis(),
    val subtasksRaw: String = "" // serialized subtasks delimited by newline
) {
    fun getSubtasks(): List<SubTask> {
        if (subtasksRaw.isEmpty()) return emptyList()
        return subtasksRaw.split("\n").mapNotNull { SubTask.fromString(it) }
    }

    fun withSubtasks(list: List<SubTask>): Task {
        return copy(subtasksRaw = list.joinToString("\n") { it.toString() })
    }
}
