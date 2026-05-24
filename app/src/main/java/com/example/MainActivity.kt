package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.AppDatabase
import com.example.data.Task
import com.example.data.TaskRepository
import com.example.ui.TaskViewModel
import com.example.ui.TaskViewModelFactory
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                TasklyApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasklyApp() {
    val context = LocalContext.current
    val database = remember { AppDatabase.getDatabase(context) }
    val repository = remember { TaskRepository(database.taskDao()) }
    val viewModel: TaskViewModel = viewModel(factory = TaskViewModelFactory(repository))

    // Dynamic notification permission check/request for Android 13+
    var isNotificationPermissionGranted by remember { mutableStateOf(true) }
    var isExactAlarmPermissionGranted by remember { mutableStateOf(true) }

    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        isNotificationPermissionGranted = granted
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                isNotificationPermissionGranted = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    androidx.core.content.ContextCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.POST_NOTIFICATIONS
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                } else {
                    true
                }

                isExactAlarmPermissionGranted = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    val alarmManager = context.getSystemService(android.content.Context.ALARM_SERVICE) as android.app.AlarmManager
                    alarmManager.canScheduleExactAlarms()
                } else {
                    true
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            isNotificationPermissionGranted = hasPermission
            if (!hasPermission) {
                permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(android.content.Context.ALARM_SERVICE) as android.app.AlarmManager
            isExactAlarmPermissionGranted = alarmManager.canScheduleExactAlarms()
        }
    }

    // State flows
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val selectedPriority by viewModel.selectedPriority.collectAsStateWithLifecycle()
    val selectedStatus by viewModel.selectedStatus.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val sortBy by viewModel.sortBy.collectAsStateWithLifecycle()

    // Dialog state
    var showAddEditDialog by remember { mutableStateOf(false) }
    var taskToEdit by remember { mutableStateOf<Task?>(null) }

    // Date formatting helper
    val todayDateString = remember {
        val sdf = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault())
        sdf.format(Date())
    }

    // App metrics
    val totalTasksCount = tasks.size
    val completedTasksCount = tasks.count { it.isCompleted }
    val completionRate = if (totalTasksCount > 0) completedTasksCount.toFloat() / totalTasksCount else 0f

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("app_scaffold"),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.navigationBars,
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    taskToEdit = null
                    showAddEditDialog = true
                },
                modifier = Modifier
                    .testTag("add_task_fab")
                    .padding(bottom = 16.dp, end = 8.dp),
                shape = RoundedCornerShape(16.dp),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Task",
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
        ) {
            // Header Section with bulk triggers
            HeaderSection(
                todayDateString = todayDateString,
                onToggleAll = { completed -> viewModel.toggleAllTasks(context, completed) },
                onClearCompleted = { viewModel.clearCompletedTasks(context) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Completion Progress bar & quick metrics card
            MetricsSection(
                completedCount = completedTasksCount,
                totalCount = totalTasksCount,
                progress = completionRate
            )

            Spacer(modifier = Modifier.height(16.dp))

            // System Permissions Checklist Section
            PermissionsChecklistSection(
                isNotificationGranted = isNotificationPermissionGranted,
                isAlarmGranted = isExactAlarmPermissionGranted,
                onRequestNotificationPermission = {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
                onRequestAlarmPermission = {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                        try {
                            val intent = android.content.Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                data = android.net.Uri.fromParts("package", context.packageName, null)
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            try {
                                val intent = android.content.Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                                context.startActivity(intent)
                            } catch (error: Exception) {
                                error.printStackTrace()
                            }
                        }
                    }
                }
            )

            if (!isNotificationPermissionGranted || !isExactAlarmPermissionGranted) {
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Search Bar, Category Filters & Sorters
            SearchAndFilterSection(
                searchQuery = searchQuery,
                onQueryChange = { viewModel.setSearchQuery(it) },
                selectedStatus = selectedStatus,
                onStatusChange = { viewModel.setSelectedStatus(it) },
                selectedCategory = selectedCategory,
                onCategoryChange = { viewModel.setSelectedCategory(it) },
                categories = categories,
                selectedPriority = selectedPriority,
                onPriorityChange = { viewModel.setSelectedPriority(it) },
                priorities = viewModel.priorities,
                selectedSort = sortBy,
                onSortChange = { viewModel.setSortBy(it) }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Task List Wrapper
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp)
            ) {
                if (tasks.isEmpty()) {
                    EmptyTasksState(
                        isSearchingOrFiltering = searchQuery.isNotEmpty() ||
                                selectedCategory != "All" ||
                                selectedPriority != "All" ||
                                selectedStatus != "All"
                    ) {
                        taskToEdit = null
                        showAddEditDialog = true
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("tasks_list"),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = innerPadding.calculateBottomPadding() + 80.dp)
                    ) {
                        items(tasks, key = { it.id }) { task ->
                            TaskItemCard(
                                task = task,
                                onToggleComplete = { viewModel.toggleTaskCompletion(context, task) },
                                onEdit = {
                                    taskToEdit = task
                                    showAddEditDialog = true
                                },
                                onDelete = { viewModel.deleteTask(context, task) },
                                viewModel = viewModel
                            )
                        }
                    }
                }
            }
        }
    }

    // Modal dialog to add or edit tasks
    if (showAddEditDialog) {
        val availableCategories = categories.filter { it != "All" }
        AddEditTaskDialog(
            task = taskToEdit,
            categories = availableCategories,
            priorities = viewModel.priorities,
            onDismiss = { showAddEditDialog = false },
            onSave = { title, description, category, priority, dueDate, reminderMinutesBefore ->
                if (taskToEdit == null) {
                    viewModel.addTask(context, title, description, category, priority, dueDate, reminderMinutesBefore)
                } else {
                    viewModel.updateTask(
                        context,
                        taskToEdit!!.copy(
                            title = title,
                            description = description,
                            category = category,
                            priority = priority,
                            dueDate = dueDate,
                            reminderMinutesBefore = reminderMinutesBefore
                        )
                    )
                }
                showAddEditDialog = false
            }
        )
    }
}

@Composable
fun HeaderSection(
    todayDateString: String,
    onToggleAll: (Boolean) -> Unit,
    onClearCompleted: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Geometric Balance custom checklist logo icon
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.primaryContainer)
                .testTag("app_logo_icon"),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .border(2.5.dp, MaterialTheme.colorScheme.onPrimaryContainer, RoundedCornerShape(3.dp))
            ) {
                // Checklist horizontal lines
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 3.dp, vertical = 3.dp)
                        .height(2.5.dp)
                        .background(MaterialTheme.colorScheme.onPrimaryContainer)
                        .align(Alignment.TopCenter)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 3.dp)
                        .offset(y = 10.dp)
                        .height(2.5.dp)
                        .background(MaterialTheme.colorScheme.onPrimaryContainer)
                        .align(Alignment.TopCenter)
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = "taskly",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                letterSpacing = (-0.5).sp,
                modifier = Modifier.testTag("app_title_text")
            )
            Text(
                text = todayDateString,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
        }

        // More Options Toolbar trigger
        Box {
            IconButton(
                onClick = { showMenu = true },
                modifier = Modifier.testTag("bulk_menu_button")
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Quick Actions Menu",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Mark all completed") },
                    onClick = {
                        onToggleAll(true)
                        showMenu = false
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )
                DropdownMenuItem(
                    text = { Text("Mark all pending") },
                    onClick = {
                        onToggleAll(false)
                        showMenu = false
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )
                Divider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                DropdownMenuItem(
                    text = { Text("Clear completed") },
                    onClick = {
                        onClearCompleted()
                        showMenu = false
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                )
            }
        }
    }
}

@Composable
fun PermissionsChecklistSection(
    isNotificationGranted: Boolean,
    isAlarmGranted: Boolean,
    onRequestNotificationPermission: () -> Unit,
    onRequestAlarmPermission: () -> Unit
) {
    if (isNotificationGranted && isAlarmGranted) return

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .testTag("permissions_checklist_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.18f)
        ),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Permissions Required Alert",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "System Setup Checklist",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Certain features require system access. Tap inactive items to grant authorizations.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(14.dp))

            // 1. Notification Permission Item
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { if (!isNotificationGranted) onRequestNotificationPermission() }
                    .background(
                        if (isNotificationGranted) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                        else MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                    )
                    .border(
                        width = 1.dp,
                        color = if (isNotificationGranted) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.error.copy(alpha = 0.25f),
                        shape = RoundedCornerShape(14.dp)
                    )
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isNotificationGranted) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = "Status icon",
                    tint = if (isNotificationGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "1. Enable Push Notifications",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isNotificationGranted) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
                        textDecoration = if (isNotificationGranted) TextDecoration.LineThrough else TextDecoration.None
                    )
                    Text(
                        text = if (isNotificationGranted) "Enabled & Active" else "Pending action - Tap to authorize",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isNotificationGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 2. Exact Alarm Permission Item
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { if (!isAlarmGranted) onRequestAlarmPermission() }
                    .background(
                        if (isAlarmGranted) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                        else MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                    )
                    .border(
                        width = 1.dp,
                        color = if (isAlarmGranted) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.error.copy(alpha = 0.25f),
                        shape = RoundedCornerShape(14.dp)
                    )
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isAlarmGranted) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = "Status icon",
                    tint = if (isAlarmGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "2. Allow Exact Alarms & Reminders",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isAlarmGranted) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
                        textDecoration = if (isAlarmGranted) TextDecoration.LineThrough else TextDecoration.None
                    )
                    Text(
                        text = if (isAlarmGranted) "Enabled & Active" else "Pending action - Tap to allow in Settings",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isAlarmGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun MetricsSection(completedCount: Int, totalCount: Int, progress: Float) {
    val activeCount = totalCount - completedCount
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .testTag("metrics_card"),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        ),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "DAILY FLOW",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.2.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "$completedCount/$totalCount",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "tasks loaded",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (progress >= 1f && totalCount > 0) 
                        "Perfect harmony! Exceptional effort." 
                    else if (progress >= 0.5f) 
                        "Over halfway there! Keep pushing your boundaries." 
                    else if (totalCount > 0) 
                        "Steady progress. Let's tackle them one by one!" 
                    else 
                        "All clear! Add custom tasks to begin your focus.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Premium Custom Circular Arc Canvas
            val primaryColor = MaterialTheme.colorScheme.primary
            val trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(82.dp)
            ) {
                Canvas(modifier = Modifier.size(72.dp)) {
                    // Frame trace track spacer
                    drawCircle(
                        color = trackColor,
                        style = Stroke(width = 7.dp.toPx())
                    )
                    // High-contrast primary sweep
                    drawArc(
                        color = primaryColor,
                        startAngle = -90f,
                        sweepAngle = progress * 360f,
                        useCenter = false,
                        style = Stroke(width = 8.dp.toPx())
                    )
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "${(progress * 100).toInt()}%",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "DONE",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchAndFilterSection(
    searchQuery: String,
    onQueryChange: (String) -> Unit,
    selectedStatus: String,
    onStatusChange: (String) -> Unit,
    selectedCategory: String,
    onCategoryChange: (String) -> Unit,
    categories: List<String>,
    selectedPriority: String,
    onPriorityChange: (String) -> Unit,
    priorities: List<String>,
    selectedSort: com.example.ui.SortOption,
    onSortChange: (com.example.ui.SortOption) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        // Search text field
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onQueryChange,
            placeholder = { Text("Search tasks...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search Icon") },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("search_field")
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Status Tabs (All, Pending, Completed)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                .padding(4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val statuses = listOf("All", "Pending", "Completed")
            statuses.forEach { status ->
                val isSelected = selectedStatus == status
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent)
                        .clickable { onStatusChange(status) }
                        .padding(vertical = 8.dp)
                        .testTag("status_filter_$status"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = status,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Category scroll chips row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val allCategories = listOf("All") + categories
            allCategories.forEach { category ->
                val isSelected = selectedCategory == category
                FilterChip(
                    selected = isSelected,
                    onClick = { onCategoryChange(category) },
                    label = { Text(category) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.secondary,
                        selectedLabelColor = MaterialTheme.colorScheme.onSecondary,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    shape = RoundedCornerShape(12.dp),
                    border = null,
                    modifier = Modifier.testTag("category_filter_$category")
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Priority filter row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Priority:",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 4.dp)
            )
            val allPriorities = listOf("All") + priorities
            allPriorities.forEach { priority ->
                val isSelected = selectedPriority == priority
                val badgeColor = when (priority) {
                    "High" -> HighPriorityColor
                    "Medium" -> MediumPriorityColor
                    "Low" -> LowPriorityColor
                    else -> MaterialTheme.colorScheme.primary
                }

                FilterChip(
                    selected = isSelected,
                    onClick = { onPriorityChange(priority) },
                    label = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (priority != "All") {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(badgeColor, CircleShape)
                                        .padding(end = 4.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            Text(priority)
                        }
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = badgeColor.copy(alpha = 0.15f),
                        selectedLabelColor = badgeColor,
                        containerColor = Color.Transparent,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    shape = RoundedCornerShape(12.dp),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = isSelected,
                        borderColor = if (isSelected) badgeColor else MaterialTheme.colorScheme.surfaceVariant,
                        selectedBorderColor = badgeColor
                    ),
                    modifier = Modifier.testTag("priority_filter_$priority")
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Sort filter row (NEW)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Sort By:",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 4.dp)
            )
            com.example.ui.SortOption.values().forEach { option ->
                val isSelected = selectedSort == option
                FilterChip(
                    selected = isSelected,
                    onClick = { onSortChange(option) },
                    label = { Text(option.displayName) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        selectedLabelColor = MaterialTheme.colorScheme.primary,
                        containerColor = Color.Transparent,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    shape = RoundedCornerShape(12.dp),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = isSelected,
                        borderColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        selectedBorderColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.testTag("sort_filter_${option.name}")
                )
            }
        }
    }
}

@Composable
fun TaskItemCard(
    task: Task,
    onToggleComplete: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    viewModel: com.example.ui.TaskViewModel
) {
    val context = LocalContext.current
    var isExpanded by remember { mutableStateOf(false) }
    val subtasks = remember(task.subtasksRaw) { task.getSubtasks() }
    val hasSubtasks = subtasks.isNotEmpty()
    val subtasksDone = subtasks.count { it.isCompleted }
    val subtaskRate = if (hasSubtasks) subtasksDone.toFloat() / subtasks.size else 0f

    val barrierColor = when (task.priority) {
        "High" -> HighPriorityColor
        "Medium" -> MediumPriorityColor
        else -> LowPriorityColor
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("task_card_${task.id}"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (task.isCompleted)
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            else
                MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (task.isCompleted) 0.dp else 2.dp
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (task.isCompleted)
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            else
                MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded }
        ) {
            Row(
                modifier = Modifier.fillMaxWidth()
            ) {
                // Priority Color Notch Indicator Key on the very left
                Box(
                    modifier = Modifier
                        .width(6.dp)
                        .height(if (isExpanded) 120.dp else 96.dp)
                        .background(barrierColor)
                        .align(Alignment.CenterVertically)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Geometric Check State icon on the left (square with rounded borders)
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (task.isCompleted) MaterialTheme.colorScheme.primary else Color.Transparent
                            )
                            .border(
                                width = 2.dp,
                                color = if (task.isCompleted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(
                                    alpha = 0.6f
                                ),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable { onToggleComplete() }
                            .testTag("task_checkbox_${task.id}"),
                        contentAlignment = Alignment.Center
                    ) {
                        if (task.isCompleted) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Task Completed Checkmark",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    // Task details (title, description, dynamic tag alerts)
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = task.title,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (task.isCompleted)
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            else
                                MaterialTheme.colorScheme.onBackground,
                            textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                            maxLines = if (isExpanded) 4 else 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        if (task.description.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = task.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = if (isExpanded) 10 else 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Tags row: Category, priority badge and optional due date
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.horizontalScroll(rememberScrollState())
                        ) {
                            // Category Chip
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = task.category,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Priority Badge Chip
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(barrierColor.copy(alpha = 0.15f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = task.priority + " Priority",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = barrierColor,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Optional Due date indicator
                            if (task.dueDate != null) {
                                val formattedDueDate = remember(task.dueDate) {
                                    val sdf = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
                                    sdf.format(Date(task.dueDate))
                                }
                                val isOverdue = !task.isCompleted && task.dueDate < System.currentTimeMillis()
                                val dueColor = if (isOverdue) HighPriorityColor else MaterialTheme.colorScheme.onSurfaceVariant
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isOverdue) HighPriorityColor.copy(alpha = 0.08f) else Color.Transparent)
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isOverdue) Icons.Default.Warning else Icons.Default.Notifications,
                                        contentDescription = "Notification Alarm Icon",
                                        tint = dueColor,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    val reminderSuffix = if (task.reminderMinutesBefore > 0) {
                                        val m = task.reminderMinutesBefore
                                        if (m >= 60) " (Remind ${m / 60}h early)" else " (Remind ${m}m early)"
                                    } else ""
                                    Text(
                                        text = formattedDueDate + reminderSuffix + if (isOverdue) " (Overdue)" else "",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = dueColor,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // CRUD Actions on the right (Edit and Delete)
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onEdit,
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("task_edit_${task.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit Task",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("task_delete_${task.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete Task",
                                tint = HighPriorityColor.copy(alpha = 0.8f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // Inline Subtasks Progress & Expandable Area
            if (hasSubtasks || isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, end = 24.dp, bottom = 16.dp)
                ) {
                    // Subtask Progress bar always visible if loaded
                    if (hasSubtasks) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Subtasks Checklist ($subtasksDone/${subtasks.size})",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${(subtaskRate * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        LinearProgressIndicator(
                            progress = subtaskRate,
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(CircleShape)
                                .padding(bottom = 4.dp)
                        )
                    }

                    // Expanded checklist rendering
                    if (isExpanded) {
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // List subtasks
                        subtasks.forEachIndexed { index, sub ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
                                    .padding(horizontal = 4.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (sub.isCompleted) MaterialTheme.colorScheme.primary else Color.Transparent)
                                        .border(
                                            1.5.dp,
                                            if (sub.isCompleted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                            RoundedCornerShape(6.dp)
                                        )
                                        .clickable { viewModel.toggleSubtaskCompletion(context, task, index) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (sub.isCompleted) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(10.dp))

                                Text(
                                    text = sub.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    textDecoration = if (sub.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                                    color = if (sub.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )

                                IconButton(
                                    onClick = { viewModel.removeSubtask(context, task, index) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Delete subtask",
                                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Inline Adder Text view field
                        var newSubtaskText by remember { mutableStateOf("") }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = newSubtaskText,
                                onValueChange = { newSubtaskText = it },
                                placeholder = { Text("Add checklist point...") },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f).height(48.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(
                                onClick = {
                                    if (newSubtaskText.isNotBlank()) {
                                        viewModel.addSubtask(context, task, newSubtaskText)
                                        newSubtaskText = ""
                                    }
                                },
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Add subtask icon",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyTasksState(isSearchingOrFiltering: Boolean, onAddTaskClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("empty_state_container"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Geometric Balance themed custom illustration anchor
        Box(
            modifier = Modifier
                .size(120.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), RoundedCornerShape(32.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isSearchingOrFiltering) Icons.Default.Search else Icons.Default.CheckCircle,
                contentDescription = "No Tasks Logo Icon",
                modifier = Modifier.size(62.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = if (isSearchingOrFiltering) "No matching tasks found" else "All tasks caught up!",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = if (isSearchingOrFiltering)
                "Try adjusting your keyword filter or switching category rules."
            else
                "You have no tasks remaining. Tap below to create one!",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 32.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        if (!isSearchingOrFiltering) {
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onAddTaskClick,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Create a Task", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditTaskDialog(
    task: Task?,
    categories: List<String>,
    priorities: List<String>,
    onDismiss: () -> Unit,
    onSave: (title: String, description: String, category: String, priority: String, dueDate: Long?, reminderMinutesBefore: Int) -> Unit
) {
    var title by remember { mutableStateOf(task?.title ?: "") }
    var description by remember { mutableStateOf(task?.description ?: "") }
    
    // Custom category input handling
    var selectedCategory by remember { mutableStateOf(task?.category ?: (if (categories.isNotEmpty()) categories.first() else "General")) }
    var customCategoryText by remember { mutableStateOf("") }
    var isCustomCategoryMode by remember { mutableStateOf(false) }

    var selectedPriority by remember { mutableStateOf(task?.priority ?: "Medium") }

    // Task due date: Custom datepicker popup
    var hasDueDate by remember { mutableStateOf(task?.dueDate != null) }
    var dueDateInMs by remember { mutableStateOf(task?.dueDate ?: (System.currentTimeMillis() + 86400000)) } // default tomorrow
    var selectedReminderMinutes by remember { mutableStateOf(task?.reminderMinutesBefore ?: 0) }
    var showDatePicker by remember { mutableStateOf(false) }

    var titleError by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("add_edit_dialog_card"),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = if (task == null) "New Task" else "Edit Task",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Title Input
                OutlinedTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        if (it.isNotEmpty()) titleError = false
                    },
                    label = { Text("Task Title *") },
                    isError = titleError,
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_title_field")
                )
                if (titleError) {
                    Text(
                        text = "Title cannot be empty!",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Description Input
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    minLines = 2,
                    maxLines = 3,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Category Selection with "+ Custom" addition
                Text(
                    text = "Category",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    categories.forEach { category ->
                        val isSel = !isCustomCategoryMode && selectedCategory == category
                        val chipBg = if (isSel) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        val chipText = if (isSel) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurfaceVariant
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(chipBg)
                                .clickable { 
                                    selectedCategory = category 
                                    isCustomCategoryMode = false
                                }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = category,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = chipText
                            )
                        }
                    }

                    // "+ Custom" category chip option
                    val isCustomSel = isCustomCategoryMode
                    val customChipBg = if (isCustomSel) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    val customChipText = if (isCustomSel) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurfaceVariant
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(customChipBg)
                            .clickable { isCustomCategoryMode = true }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = customChipText
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Custom",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = customChipText
                            )
                        }
                    }
                }

                // If Custom selected, display the input field
                if (isCustomCategoryMode) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = customCategoryText,
                        onValueChange = { customCategoryText = it },
                        label = { Text("Custom category name") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("dialog_custom_category_field")
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Priority Buttons Row
                Text(
                    text = "Priority Level",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    priorities.forEach { priority ->
                        val isSel = selectedPriority == priority
                        val col = when (priority) {
                            "High" -> HighPriorityColor
                            "Medium" -> MediumPriorityColor
                            else -> LowPriorityColor
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSel) col.copy(alpha = 0.15f) else Color.Transparent)
                                .border(
                                    width = 1.dp,
                                    color = if (isSel) col else MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable { selectedPriority = priority }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = priority,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isSel) col else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Due Date Switch row & Calendar touch field
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Set Alarm & Due Date Limit",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Switch(
                            checked = hasDueDate,
                            onCheckedChange = { hasDueDate = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.primary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier.testTag("dialog_due_date_switch")
                        )
                    }

                    if (hasDueDate) {
                        Spacer(modifier = Modifier.height(8.dp))
                        val dateFormattedString = remember(dueDateInMs) {
                            val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                            sdf.format(Date(dueDateInMs))
                        }
                        val timeFormattedString = remember(dueDateInMs) {
                            val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
                            sdf.format(Date(dueDateInMs))
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                                    .clickable { showDatePicker = true }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DateRange,
                                    contentDescription = "Active Calendar Selector",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = dateFormattedString,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            val dialogContext = LocalContext.current
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                                    .clickable {
                                        val calendar = Calendar.getInstance().apply { timeInMillis = dueDateInMs }
                                        val timePickerDialog = android.app.TimePickerDialog(
                                            dialogContext,
                                            { _, selectedHour, selectedMinute ->
                                                val newCalendar = Calendar.getInstance().apply {
                                                    timeInMillis = dueDateInMs
                                                    set(Calendar.HOUR_OF_DAY, selectedHour)
                                                    set(Calendar.MINUTE, selectedMinute)
                                                    set(Calendar.SECOND, 0)
                                                    set(Calendar.MILLISECOND, 0)
                                                }
                                                dueDateInMs = newCalendar.timeInMillis
                                            },
                                            calendar.get(Calendar.HOUR_OF_DAY),
                                            calendar.get(Calendar.MINUTE),
                                            false
                                        )
                                        timePickerDialog.show()
                                    }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Notifications,
                                    contentDescription = "Active Alarm Time Selector",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = timeFormattedString,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Reminder Time",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val reminderOptions = listOf(
                                0 to "At start",
                                5 to "5m before",
                                15 to "15m before",
                                30 to "30m before",
                                60 to "1h before",
                                1440 to "1d before"
                            )
                            reminderOptions.forEach { option ->
                                val isSel = selectedReminderMinutes == option.first
                                val chipBg = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                val chipText = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(chipBg)
                                        .clickable { selectedReminderMinutes = option.first }
                                        .padding(horizontal = 14.dp, vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = option.second,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = chipText
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Action Buttons (Dismiss/Save)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("dialog_cancel_button")
                    ) {
                        Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = {
                            if (title.isEmpty()) {
                                titleError = true
                            } else {
                                val savedCategoryText = if (isCustomCategoryMode) {
                                    if (customCategoryText.isNotBlank()) customCategoryText.trim() else "General"
                                } else {
                                    selectedCategory
                                }
                                onSave(
                                    title.trim(),
                                    description.trim(),
                                    savedCategoryText,
                                    selectedPriority,
                                    if (hasDueDate) dueDateInMs else null,
                                    if (hasDueDate) selectedReminderMinutes else 0
                                )
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("dialog_save_button")
                    ) {
                        Text("Save Task", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // Material 3 Custom Date Picker Dialogue Popup Dialog block
    if (showDatePicker && hasDueDate) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = dueDateInMs)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let {
                            dueDateInMs = it
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("Select", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
