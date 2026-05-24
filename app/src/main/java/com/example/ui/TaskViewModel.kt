package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.Task
import com.example.data.TaskRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class SortOption(val displayName: String) {
    CREATED_DESC("Newest First"),
    CREATED_ASC("Oldest First"),
    DUE_DATE_ASC("Closest Due Date"),
    PRIORITY_HIGH_TO_LOW("High Priority First"),
    TITLE_ASC("Alphabetical (A-Z)")
}

data class FilterState(
    val query: String,
    val category: String,
    val priority: String,
    val status: String,
    val sortOpt: SortOption
)

class TaskViewModel(private val repository: TaskRepository) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _selectedCategory = MutableStateFlow("All")
    val selectedCategory = _selectedCategory.asStateFlow()

    private val _selectedPriority = MutableStateFlow("All")
    val selectedPriority = _selectedPriority.asStateFlow()

    private val _selectedStatus = MutableStateFlow("All") // "All", "Pending", "Completed"
    val selectedStatus = _selectedStatus.asStateFlow()

    private val _sortBy = MutableStateFlow(SortOption.CREATED_DESC)
    val sortBy = _sortBy.asStateFlow()

    // FilterState combined flow (exactly 5 parameters, which fits standard combine)
    private val filterState: Flow<FilterState> = combine(
        _searchQuery,
        _selectedCategory,
        _selectedPriority,
        _selectedStatus,
        _sortBy
    ) { query, category, priority, status, sortOpt ->
        FilterState(query, category, priority, status, sortOpt)
    }

    // Dynamic filtering and sorting combined flow
    val tasks: StateFlow<List<Task>> = repository.allTasks.combine(filterState) { allTasks, spec ->
        val filtered = allTasks.filter { task ->
            val matchesQuery = task.title.contains(spec.query, ignoreCase = true) ||
                    task.description.contains(spec.query, ignoreCase = true)
            
            val matchesCategory = spec.category == "All" || task.category == spec.category
            
            val matchesPriority = spec.priority == "All" || task.priority == spec.priority
            
            val matchesStatus = when (spec.status) {
                "Completed" -> task.isCompleted
                "Pending" -> !task.isCompleted
                else -> true
            }

            matchesQuery && matchesCategory && matchesPriority && matchesStatus
        }

        // Apply Sorting logic
        when (spec.sortOpt) {
            SortOption.CREATED_DESC -> filtered.sortedByDescending { it.createdAt }
            SortOption.CREATED_ASC -> filtered.sortedBy { it.createdAt }
            SortOption.DUE_DATE_ASC -> {
                // Keep those without due dates at the bottom
                filtered.sortedWith(
                    compareBy<Task> { it.dueDate == null }.thenBy { it.dueDate ?: Long.MAX_VALUE }
                )
            }
            SortOption.PRIORITY_HIGH_TO_LOW -> {
                filtered.sortedBy { task ->
                    when (task.priority) {
                        "High" -> 1
                        "Medium" -> 2
                        "Low" -> 3
                        else -> 4
                    }
                }
            }
            SortOption.TITLE_ASC -> filtered.sortedBy { it.title.lowercase() }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )


    // Dynamic Category options extracted from existing tasks combined with static defaults
    val categories: StateFlow<List<String>> = repository.allTasks
        .map { list ->
            val defaults = listOf("Work", "Personal", "Shopping", "Wellness", "Others")
            val fromTasks = list.map { it.category }.filter { it.isNotEmpty() }
            (defaults + fromTasks).distinct()
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = listOf("Work", "Personal", "Shopping", "Wellness", "Others")
        )

    val priorities = listOf("High", "Medium", "Low")

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSelectedCategory(category: String) {
        _selectedCategory.value = category
    }

    fun setSelectedPriority(priority: String) {
        _selectedPriority.value = priority
    }

    fun setSelectedStatus(status: String) {
        _selectedStatus.value = status
    }

    fun setSortBy(sortOption: SortOption) {
        _sortBy.value = sortOption
    }

    fun addTask(context: android.content.Context, title: String, description: String, category: String, priority: String, dueDate: Long?, reminderMinutesBefore: Int = 0, subtasksRaw: String = "") {
        viewModelScope.launch {
            val task = Task(
                title = title,
                description = description,
                category = category,
                priority = priority,
                dueDate = dueDate,
                reminderMinutesBefore = reminderMinutesBefore,
                subtasksRaw = subtasksRaw
            )
            val newId = repository.insert(task)
            if (dueDate != null) {
                com.example.util.AlarmScheduler.scheduleAlarm(context, task.copy(id = newId.toInt()))
            }
        }
    }

    fun updateTask(context: android.content.Context, task: Task) {
        viewModelScope.launch {
            repository.update(task)
            if (task.dueDate != null) {
                com.example.util.AlarmScheduler.scheduleAlarm(context, task)
            } else {
                com.example.util.AlarmScheduler.cancelAlarm(context, task.id)
            }
        }
    }

    fun toggleTaskCompletion(context: android.content.Context, task: Task) {
        viewModelScope.launch {
            val nextState = !task.isCompleted
            // Auto-complete or auto-reopen subtasks matching task status
            val updatedSubtasks = task.getSubtasks().map { it.copy(isCompleted = nextState) }
            val updatedTask = task.copy(isCompleted = nextState).withSubtasks(updatedSubtasks)
            repository.update(updatedTask)
            com.example.util.AlarmScheduler.scheduleAlarm(context, updatedTask)
        }
    }

    fun toggleSubtaskCompletion(context: android.content.Context, task: Task, subtaskIndex: Int) {
        viewModelScope.launch {
            val subtasks = task.getSubtasks().toMutableList()
            if (subtaskIndex in subtasks.indices) {
                val sub = subtasks[subtaskIndex]
                subtasks[subtaskIndex] = sub.copy(isCompleted = !sub.isCompleted)
                
                // If all subtasks are finished, we can optionally check the task or leave it. 
                // Let's keep it manual but update the subtask list
                val allCompleted = subtasks.isNotEmpty() && subtasks.all { it.isCompleted }
                val updatedTask = task.copy(
                    isCompleted = if (allCompleted) true else task.isCompleted
                ).withSubtasks(subtasks)
                
                repository.update(updatedTask)
                com.example.util.AlarmScheduler.scheduleAlarm(context, updatedTask)
            }
        }
    }

    fun addSubtask(context: android.content.Context, task: Task, subtaskTitle: String) {
        if (subtaskTitle.isBlank()) return
        viewModelScope.launch {
            val subtasks = task.getSubtasks().toMutableList()
            subtasks.add(com.example.data.SubTask(subtaskTitle.trim(), false))
            val updatedTask = task.copy(isCompleted = false).withSubtasks(subtasks)
            repository.update(updatedTask)
            com.example.util.AlarmScheduler.scheduleAlarm(context, updatedTask)
        }
    }

    fun removeSubtask(context: android.content.Context, task: Task, subtaskIndex: Int) {
        viewModelScope.launch {
            val subtasks = task.getSubtasks().toMutableList()
            if (subtaskIndex in subtasks.indices) {
                subtasks.removeAt(subtaskIndex)
                val updatedTask = task.withSubtasks(subtasks)
                repository.update(updatedTask)
                com.example.util.AlarmScheduler.scheduleAlarm(context, updatedTask)
            }
        }
    }

    fun deleteTask(context: android.content.Context, task: Task) {
        viewModelScope.launch {
            repository.delete(task)
            com.example.util.AlarmScheduler.cancelAlarm(context, task.id)
        }
    }

    fun clearCompletedTasks(context: android.content.Context) {
        viewModelScope.launch {
            // Retrieve all currently queried list or absolute database tasks
            repository.allTasks.firstOrNull()?.forEach { task ->
                if (task.isCompleted) {
                    repository.delete(task)
                    com.example.util.AlarmScheduler.cancelAlarm(context, task.id)
                }
            }
        }
    }

    fun toggleAllTasks(context: android.content.Context, completed: Boolean) {
        viewModelScope.launch {
            repository.allTasks.firstOrNull()?.forEach { task ->
                if (task.isCompleted != completed) {
                    val subtasks = task.getSubtasks().map { it.copy(isCompleted = completed) }
                    val updatedTask = task.copy(isCompleted = completed).withSubtasks(subtasks)
                    repository.update(updatedTask)
                    com.example.util.AlarmScheduler.scheduleAlarm(context, updatedTask)
                }
            }
        }
    }
}


class TaskViewModelFactory(private val repository: TaskRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TaskViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TaskViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
