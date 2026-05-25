package com.example

import android.os.Bundle
import androidx.compose.foundation.Image
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
    val initialNotificationPermissionStatus = remember {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
    val initialExactAlarmPermissionStatus = remember {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(android.content.Context.ALARM_SERVICE) as android.app.AlarmManager
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    var isNotificationPermissionGranted by remember { mutableStateOf(initialNotificationPermissionStatus) }
    var isExactAlarmPermissionGranted by remember { mutableStateOf(initialExactAlarmPermissionStatus) }

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
                    .padding(bottom = 16.dp, end = 8.dp)
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(18.dp)
                    ),
                shape = RoundedCornerShape(18.dp),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
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
        // High-fidelity custom checklist logo using SVG asset
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f))
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(16.dp)
                )
                .testTag("app_logo_icon"),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_taskly_logo),
                contentDescription = "Taskly App Logo",
                modifier = Modifier
                    .size(38.dp)
                    .padding(2.dp)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "task",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = (-1).sp,
                    modifier = Modifier.testTag("app_title_text")
                )
                Text(
                    text = "ly",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Light,
                    color = MaterialTheme.colorScheme.onBackground,
                    letterSpacing = (-1).sp
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.DateRange,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = todayDateString,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )
            }
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

    val darkTheme = androidx.compose.foundation.isSystemInDarkTheme()
    val containerBg = if (darkTheme) Color(0xFF2C1E1E) else Color(0xFFFFF1F1)
    val cardBorderColor = if (darkTheme) Color(0xFFEF4444).copy(alpha = 0.5f) else Color(0xFFFCA5A5)
    val mainWarningColor = if (darkTheme) Color(0xFFFCA5A5) else Color(0xFF991B1B)
    val accentIconColor = if (darkTheme) Color(0xFFEF4444) else Color(0xFFDC2626)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .testTag("permissions_checklist_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = containerBg
        ),
        border = BorderStroke(1.5.dp, cardBorderColor)
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
                    tint = accentIconColor,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "System Setup Checklist",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = mainWarningColor
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Certain features require system access. Tap inactive items to grant authorizations.",
                style = MaterialTheme.typography.bodySmall,
                color = if (darkTheme) Color(0xFFE2E8F0) else Color(0xFF475569)
            )
            Spacer(modifier = Modifier.height(14.dp))

            // 1. Notification Permission Item
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { if (!isNotificationGranted) onRequestNotificationPermission() }
                    .background(
                        if (isNotificationGranted) {
                            if (darkTheme) Color(0xFF0F2D1D) else Color(0xFFE6F4EA)
                        } else {
                            if (darkTheme) Color(0xFF381A1A) else Color(0xFFFFEAEA)
                        }
                    )
                    .border(
                        width = 1.dp,
                        color = if (isNotificationGranted) {
                            if (darkTheme) Color(0xFF10B981).copy(alpha = 0.6f) else Color(0xFFA7F3D0)
                        } else {
                            if (darkTheme) Color(0xFFEF4444).copy(alpha = 0.5f) else Color(0xFFFCA5A5)
                        },
                        shape = RoundedCornerShape(14.dp)
                    )
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isNotificationGranted) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = "Status icon",
                    tint = if (isNotificationGranted) {
                        if (darkTheme) Color(0xFF34D399) else Color(0xFF059669)
                    } else {
                        accentIconColor
                    },
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "1. Enable Push Notifications",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isNotificationGranted) {
                            if (darkTheme) Color(0xFF989898) else Color(0xFF6B7280)
                        } else {
                            if (darkTheme) Color(0xFFFCA5A5) else Color(0xFF991B1B)
                        },
                        textDecoration = if (isNotificationGranted) TextDecoration.LineThrough else TextDecoration.None
                    )
                    Text(
                        text = if (isNotificationGranted) "Enabled & Active" else "Pending action - Tap to authorize",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isNotificationGranted) {
                            if (darkTheme) Color(0xFF34D399) else Color(0xFF059669)
                        } else {
                            if (darkTheme) Color(0xFFF87171) else Color(0xFFB91C1C)
                        }
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
                        if (isAlarmGranted) {
                            if (darkTheme) Color(0xFF0F2D1D) else Color(0xFFE6F4EA)
                        } else {
                            if (darkTheme) Color(0xFF381A1A) else Color(0xFFFFEAEA)
                        }
                    )
                    .border(
                        width = 1.dp,
                        color = if (isAlarmGranted) {
                            if (darkTheme) Color(0xFF10B981).copy(alpha = 0.6f) else Color(0xFFA7F3D0)
                        } else {
                            if (darkTheme) Color(0xFFEF4444).copy(alpha = 0.5f) else Color(0xFFFCA5A5)
                        },
                        shape = RoundedCornerShape(14.dp)
                    )
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isAlarmGranted) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = "Status icon",
                    tint = if (isAlarmGranted) {
                        if (darkTheme) Color(0xFF34D399) else Color(0xFF059669)
                    } else {
                        accentIconColor
                    },
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "2. Allow Exact Alarms & Reminders",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isAlarmGranted) {
                            if (darkTheme) Color(0xFF989898) else Color(0xFF6B7280)
                        } else {
                            if (darkTheme) Color(0xFFFCA5A5) else Color(0xFF991B1B)
                        },
                        textDecoration = if (isAlarmGranted) TextDecoration.LineThrough else TextDecoration.None
                    )
                    Text(
                        text = if (isAlarmGranted) "Enabled & Active" else "Pending action - Tap to allow in Settings",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isAlarmGranted) {
                            if (darkTheme) Color(0xFF34D399) else Color(0xFF059669)
                        } else {
                            if (darkTheme) Color(0xFFF87171) else Color(0xFFB91C1C)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun MetricsSection(completedCount: Int, totalCount: Int, progress: Float) {
    val activeCount = totalCount - completedCount
    val darkTheme = androidx.compose.foundation.isSystemInDarkTheme()
    
    val gradientBrush = remember(darkTheme) {
        if (darkTheme) {
            Brush.horizontalGradient(
                colors = listOf(
                    Color(0xFF0F2D2A).copy(alpha = 0.8f), // Deep Teal tint
                    Color(0xFF090D16).copy(alpha = 0.9f)  // Background Slate
                )
            )
        } else {
            Brush.horizontalGradient(
                colors = listOf(
                    Color(0xFFE6F4F1), // Soft Menthol/Teal
                    Color(0xFFF1F5F9)  // Slate Light
                )
            )
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .testTag("metrics_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (darkTheme) Color(0xFF14B8A6).copy(alpha = 0.25f) else Color(0xFF0D9488).copy(alpha = 0.15f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(gradientBrush)
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "DAILY FOCUS FLOW",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.5.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "$completedCount",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "/$totalCount",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "tasks complete",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (progress >= 1f && totalCount > 0) 
                        "Perfect harmony! Extraordinary effort today." 
                    else if (progress >= 0.5f) 
                        "Over halfway! Tap into your flow state." 
                    else if (totalCount > 0) 
                        "Steady progress. Let's tackle them one by one." 
                    else 
                        "Clean slate! Tap the '+' button below to add tasks.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Premium Custom Circular Arc Canvas
            val primaryColor = MaterialTheme.colorScheme.primary
            val trackColor = if (darkTheme) Color(0xFF1E293B) else Color(0xFFE2E8F0)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(82.dp)
            ) {
                Canvas(modifier = Modifier.size(72.dp)) {
                    // Frame trace track spacer
                    drawCircle(
                        color = trackColor,
                        style = Stroke(width = 6.dp.toPx())
                    )
                    // High-contrast primary sweep
                    drawArc(
                        color = primaryColor,
                        startAngle = -90f,
                        sweepAngle = progress * 360f,
                        useCenter = false,
                        style = Stroke(
                            width = 7.dp.toPx(),
                            cap = androidx.compose.ui.graphics.StrokeCap.Round
                        )
                    )
                }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "${(progress * 100).toInt()}%",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "DONE",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
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
    val darkTheme = androidx.compose.foundation.isSystemInDarkTheme()
    val segmentedBg = if (darkTheme) Color(0xFF131C30) else Color(0xFFE2E8F0)
    val activeTabBg = if (darkTheme) Color(0xFF1E293B) else Color(0xFFFFFFFF)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
         // Search text field with sleek rounded corners and magnifying glass icon
         OutlinedTextField(
             value = searchQuery,
             onValueChange = onQueryChange,
             placeholder = { 
                 Text(
                     text = "Search tasks...", 
                     style = MaterialTheme.typography.bodyMedium,
                     color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                 ) 
             },
             leadingIcon = { 
                 Icon(
                     imageVector = Icons.Default.Search, 
                     contentDescription = "Search Icon",
                     tint = MaterialTheme.colorScheme.primary,
                     modifier = Modifier.size(20.dp)
                 ) 
             },
             trailingIcon = {
                 if (searchQuery.isNotEmpty()) {
                     IconButton(onClick = { onQueryChange("") }) {
                         Icon(
                             imageVector = Icons.Default.Close, 
                             contentDescription = "Clear search",
                             tint = MaterialTheme.colorScheme.onSurfaceVariant
                         )
                     }
                 }
             },
             singleLine = true,
             shape = RoundedCornerShape(16.dp),
             colors = OutlinedTextFieldDefaults.colors(
                 focusedContainerColor = MaterialTheme.colorScheme.surface,
                 unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                 focusedBorderColor = MaterialTheme.colorScheme.primary,
                 unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                 focusedLabelColor = MaterialTheme.colorScheme.primary
             ),
             modifier = Modifier
                 .fillMaxWidth()
                 .testTag("search_field")
         )

         Spacer(modifier = Modifier.height(14.dp))

         // Premium Segmented Status Tabs (All, Pending, Completed)
         Row(
             modifier = Modifier
                 .fillMaxWidth()
                 .clip(RoundedCornerShape(14.dp))
                 .background(segmentedBg)
                 .padding(3.dp),
             horizontalArrangement = Arrangement.SpaceBetween,
             verticalAlignment = Alignment.CenterVertically
         ) {
             val statuses = listOf("All", "Pending", "Completed")
             statuses.forEach { status ->
                 val isSelected = selectedStatus == status
                 Box(
                     modifier = Modifier
                         .weight(1f)
                         .clip(RoundedCornerShape(11.dp))
                         .background(if (isSelected) activeTabBg else Color.Transparent)
                         .clickable { onStatusChange(status) }
                         .padding(vertical = 10.dp)
                         .testTag("status_filter_$status"),
                     contentAlignment = Alignment.Center
                 ) {
                     Text(
                         text = status,
                         style = MaterialTheme.typography.bodyMedium,
                         fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                         color = if (isSelected) {
                             MaterialTheme.colorScheme.primary
                         } else {
                             MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                         }
                     )
                 }
             }
         }

         Spacer(modifier = Modifier.height(12.dp))

         // Category scroll chips row
         Row(
             modifier = Modifier
                 .fillMaxWidth()
                 .horizontalScroll(rememberScrollState()),
             horizontalArrangement = Arrangement.spacedBy(8.dp),
             verticalAlignment = Alignment.CenterVertically
         ) {
             val allCategories = listOf("All") + categories
             allCategories.forEach { category ->
                 val isSelected = selectedCategory == category
                 FilterChip(
                     selected = isSelected,
                     onClick = { onCategoryChange(category) },
                     label = { Text(category) },
                     colors = FilterChipDefaults.filterChipColors(
                         selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                         selectedLabelColor = MaterialTheme.colorScheme.primary,
                         containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                         labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                     ),
                     shape = RoundedCornerShape(12.dp),
                     border = FilterChipDefaults.filterChipBorder(
                         enabled = true,
                         selected = isSelected,
                         borderColor = Color.Transparent,
                         selectedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                         borderWidth = 1.dp
                     ),
                     modifier = Modifier.testTag("category_filter_$category")
                 )
             }
         }

         Spacer(modifier = Modifier.height(8.dp))

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
                                 )
                                 Spacer(modifier = Modifier.width(6.dp))
                             }
                             Text(priority)
                         }
                     },
                     colors = FilterChipDefaults.filterChipColors(
                         selectedContainerColor = badgeColor.copy(alpha = 0.12f),
                         selectedLabelColor = badgeColor,
                         containerColor = Color.Transparent,
                         labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                     ),
                     shape = RoundedCornerShape(12.dp),
                     border = FilterChipDefaults.filterChipBorder(
                         enabled = true,
                         selected = isSelected,
                         borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                         selectedBorderColor = badgeColor,
                         borderWidth = 1.dp
                     ),
                     modifier = Modifier.testTag("priority_filter_$priority")
                 )
             }
         }

         Spacer(modifier = Modifier.height(8.dp))

         // Sort filter row
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
                         borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                         selectedBorderColor = MaterialTheme.colorScheme.primary,
                         borderWidth = 1.dp
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

    val darkTheme = androidx.compose.foundation.isSystemInDarkTheme()
    val completedBg = if (darkTheme) Color(0xFF0C1917) else Color(0xFFEAF5F2)
    val cardBackground = if (task.isCompleted) completedBg else MaterialTheme.colorScheme.surface
    val cardBorderColor = if (task.isCompleted) {
        if (darkTheme) Color(0xFF10B981).copy(alpha = 0.2f) else Color(0xFFA7F3D0)
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("task_card_${task.id}"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = cardBackground
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (task.isCompleted) 0.dp else 1.dp
        ),
        border = BorderStroke(
            width = 1.dp,
            color = cardBorderColor
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
                // Priority color notch formatted as a smooth curved vertical gradient bar
                Box(
                    modifier = Modifier
                        .width(6.dp)
                        .height(if (isExpanded) 110.dp else 74.dp)
                        .align(Alignment.CenterVertically)
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(barrierColor, barrierColor.copy(alpha = 0.4f))
                            ),
                            shape = RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp)
                        )
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Modern Animated Circular Check Checkbox
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(
                                if (task.isCompleted) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f) else Color.Transparent
                            )
                            .border(
                                width = 2.dp,
                                color = if (task.isCompleted) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                                shape = CircleShape
                            )
                            .clickable { onToggleComplete() }
                            .testTag("task_checkbox_${task.id}"),
                        contentAlignment = Alignment.Center
                    ) {
                        if (task.isCompleted) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Task Completed Checkmark",
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    // Task Core Text (Title, description, badges)
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = task.title,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (task.isCompleted)
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
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
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                maxLines = if (isExpanded) 10 else 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // High fidelity tags pills: Category, Priority and Alarms
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.horizontalScroll(rememberScrollState())
                        ) {
                            // Category Badge
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = task.category,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Priority Badge
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(barrierColor.copy(alpha = 0.1f))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = task.priority,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = barrierColor,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Due date alerts
                            if (task.dueDate != null) {
                                val formattedDueDate = remember(task.dueDate) {
                                    val sdf = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
                                    sdf.format(Date(task.dueDate))
                                }
                                val isOverdue = !task.isCompleted && task.dueDate < System.currentTimeMillis()
                                val dueColor = if (isOverdue) HighPriorityColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                val badgeBg = if (isOverdue) HighPriorityColor.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(badgeBg)
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
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
                                        if (m >= 60) " (${m / 60}h early)" else " (${m}m early)"
                                    } else ""
                                    Text(
                                        text = formattedDueDate + reminderSuffix + if (isOverdue) " (Overdue)" else "",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = dueColor,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Action tools (Edit, Delete)
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
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.size(18.dp)
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
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // Inline subtask listings, with full-width progress tracks
            if (hasSubtasks || isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, bottom = 18.dp)
                ) {
                    if (hasSubtasks) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp),
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
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        LinearProgressIndicator(
                            progress = subtaskRate,
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .clip(CircleShape)
                                .padding(bottom = 2.dp)
                        )
                    }

                    if (isExpanded) {
                        Spacer(modifier = Modifier.height(10.dp))

                        subtasks.forEachIndexed { index, sub ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
                                    .padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Subtask Checkbox Circle
                                Box(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clip(CircleShape)
                                        .background(if (sub.isCompleted) MaterialTheme.colorScheme.primary else Color.Transparent)
                                        .border(
                                            width = 1.5.dp,
                                            color = if (sub.isCompleted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                            shape = CircleShape
                                        )
                                        .clickable { viewModel.toggleSubtaskCompletion(context, task, index) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (sub.isCompleted) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(10.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(10.dp))

                                Text(
                                    text = sub.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    textDecoration = if (sub.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                                    color = if (sub.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )

                                IconButton(
                                    onClick = { viewModel.removeSubtask(context, task, index) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Delete subtask",
                                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Stylish subtask input line
                        var newSubtaskText by remember { mutableStateOf("") }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = newSubtaskText,
                                onValueChange = { newSubtaskText = it },
                                placeholder = { 
                                    Text(
                                        text = "Add checklist item...",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    ) 
                                },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
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
            .testTag("empty_state_container")
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // High fidelity custom illustrated icon card using concentric circles & gradient glows
        Box(
            modifier = Modifier
                .size(130.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.02f)
                        )
                    ),
                    shape = CircleShape
                )
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surface,
                        shape = CircleShape
                    )
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isSearchingOrFiltering) Icons.Default.Search else Icons.Default.CheckCircle,
                    contentDescription = "No Tasks Logo Icon",
                    modifier = Modifier.size(42.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = if (isSearchingOrFiltering) "No matching tasks" else "All caught up!",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onBackground,
            letterSpacing = (-0.5).sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = if (isSearchingOrFiltering)
                "Try adjusting your filters or editing your search keyword to find what you need."
            else
                "Your slate is completely clean. Tap the + icon to create your next focus point!",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            modifier = Modifier.padding(horizontal = 24.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            fontWeight = FontWeight.Medium
        )
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
                        datePickerState.selectedDateMillis?.let { dateMillis ->
                            val calendar = java.util.Calendar.getInstance().apply { timeInMillis = dueDateInMs }
                            val h = calendar.get(java.util.Calendar.HOUR_OF_DAY)
                            val m = calendar.get(java.util.Calendar.MINUTE)
                            
                            val newCalendar = java.util.Calendar.getInstance().apply {
                                timeInMillis = dateMillis
                                set(java.util.Calendar.HOUR_OF_DAY, h)
                                set(java.util.Calendar.MINUTE, m)
                                set(java.util.Calendar.SECOND, 0)
                                set(java.util.Calendar.MILLISECOND, 0)
                            }
                            dueDateInMs = newCalendar.timeInMillis
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
