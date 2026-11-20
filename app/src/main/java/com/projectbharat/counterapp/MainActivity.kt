package com.projectbharat.counterapp

import android.content.Intent
import android.os.Build
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.foundation.interaction.MutableInteractionSource
import android.os.Bundle
import android.media.ToneGenerator
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.projectbharat.counterapp.data.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
private fun BilanLaunchSplash() {
    val dark = isSystemInDarkTheme()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (dark) Color(0xFF31413A) else Color(0xFFF3EEE3)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.splash_logo),
                contentDescription = "Bilan Counter logo",
                modifier = Modifier.size(164.dp),
                contentScale = ContentScale.Fit
            )
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = "Bilan Counter",
                color = if (dark) Color(0xFFF3EEE3) else Color(0xFF31413A),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

class MainActivity : ComponentActivity() {
    var launchCounterKind by mutableStateOf<String?>(null)
        private set
    var launchCounterQuickStart by mutableStateOf(false)
        private set
    fun consumeLaunchCounterKind() { launchCounterKind = null; launchCounterQuickStart = false }
        private set

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == ACTION_OPEN_COUNTER) {
            launchCounterKind = intent.getStringExtra(CounterQuickActions.EXTRA_COUNTER_KIND)
            launchCounterQuickStart = intent.getBooleanExtra("quick_start", false)
        }
    }

    override fun onResume() {
        super.onResume()
        QuickActionsNotification.show(this)
    }

    override fun onStop() {
        // Foreground step and Japa services continue when the UI is backgrounded.
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        launchCounterKind = intent.getStringExtra(CounterQuickActions.EXTRA_COUNTER_KIND)
        launchCounterQuickStart = intent.getBooleanExtra("quick_start", false)
        // Use the Android SplashScreen API defaults. Android removes the splash
        // automatically as soon as the first app frame is ready.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        SpeechModelDownloadScheduler.enqueueFirstInstall(this)
        DailyReminderScheduler.rescheduleAll(this)
        SeedData.initialize(this)
        val repo = CounterRepository(CounterDatabase.get(this).dao())
        setContent {
            var showBrandSplash by remember { mutableStateOf(true) }
            LaunchedEffect(Unit) {
                delay(900)
                showBrandSplash = false
            }
            if (showBrandSplash) {
                BilanLaunchSplash()
            } else {
                CounterApp(repo)
            }
        }
    }

    companion object {
        const val ACTION_OPEN_COUNTER = "com.projectbharat.counterapp.action.OPEN_COUNTER"
    }
}

class CounterViewModel(private val repo: CounterRepository) : ViewModel() {
    val categories = repo.categories.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val archivedCategories = repo.archivedCategories.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun count(id: Long) = repo.today(id)
    fun logsForDay(id: Long, date: LocalDate) = repo.logsForDay(id, date)
    fun mantraLogsForDay(id: Long, date: LocalDate) = repo.mantraLogsForDay(id, date)
    fun allMantraLogs(id: Long) = repo.allMantraLogs(id)
    fun mantraToday(id: Long, mantra: String) = repo.mantraToday(id, mantra)
    fun mantraAllTime(id: Long, mantra: String) = repo.mantraAllTime(id, mantra)
    fun mantraWeek(id: Long, mantra: String) = repo.mantraWeek(id, mantra)
    suspend fun addMantra(id: Long, mantra: String) = repo.addMantraCount(id, mantra)
    suspend fun updateLog(log: CounterLog) = repo.updateLog(log)
    suspend fun deleteLog(id: Long) = repo.deleteLog(id)
    fun week(id: Long) = repo.week(id)
    fun average(id: Long) = repo.average(id)
    suspend fun addCategory(mr: String, en: String) = repo.addCategory(mr, en)
    suspend fun updateCategory(category: CounterCategory) = repo.updateCategory(category)
    suspend fun archiveCategory(id: Long) = repo.archiveCategory(id)
    suspend fun restoreCategory(id: Long) = repo.restoreCategory(id)
    suspend fun add(id: Long, strength: String?, duration: Long? = null) = repo.add(id, strength, duration)
    suspend fun addCount(id: Long, amount: Int) = repo.addCount(id, amount)
    suspend fun undo(id: Long) = repo.undo(id)
    suspend fun reset(id: Long) = repo.reset(id)
    class Factory(private val repo: CounterRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = CounterViewModel(repo) as T
    }
}

private val Navy = Color(0xFF102E55)
private val Gold = Color(0xFFE7A33C)
private val Cream = Color(0xFFF5F0E6)
private val SoftBlue = Color(0xFFDCE9F7)
private val zone = ZoneId.systemDefault()
private val dateFormat = DateTimeFormatter.ofPattern("dd MMM yyyy")

private fun sessionTime(timestamp: Long): String =
    Instant.ofEpochMilli(timestamp).atZone(zone).format(DateTimeFormatter.ofPattern("hh:mm a"))

private fun periodLabel(period: String, english: Boolean): String = when (period) {
    "MORNING" -> if (english) "Morning" else "सकाळ"
    "AFTERNOON" -> if (english) "Afternoon" else "दुपार"
    "EVENING" -> if (english) "Evening" else "संध्याकाळ"
    else -> if (english) "Night" else "रात्र"
}

@Composable
fun CounterApp(repo: CounterRepository) {
    val vm: CounterViewModel = viewModel(factory = CounterViewModel.Factory(repo))
    val categories by vm.categories.collectAsStateWithLifecycle()
    val archivedCategories by vm.archivedCategories.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var selectedId by remember { mutableLongStateOf(1L) }
    var english by remember(context) {
        mutableStateOf(
            context.getSharedPreferences("app_preferences", android.content.Context.MODE_PRIVATE)
                .getBoolean("english_language", false)
        )
    }
    var voiceModelSummary by remember { mutableStateOf("Checking…") }
    LaunchedEffect(english) {
        context.getSharedPreferences("app_preferences", android.content.Context.MODE_PRIVATE)
            .edit().putBoolean("english_language", english).apply()
    }
    LaunchedEffect(context, english) {
        while (true) {
            voiceModelSummary = withContext(Dispatchers.IO) {
                val ready = listOf(VoskOfflineModelInstaller.isInstalled(context, "hi"), VoskOfflineModelInstaller.isInstalled(context, "en")).count { it }
                val wm = androidx.work.WorkManager.getInstance(context)
                val tags = listOf("vosk_hi", "vosk_en")
                val work = tags.map { tag -> runCatching { wm.getWorkInfosByTag(tag).get().firstOrNull() }.getOrNull() }
                val flags = listOf(VoskOfflineModelInstaller.isInstalled(context, "hi"), VoskOfflineModelInstaller.isInstalled(context, "en"))
                val progress = work.indices.sumOf { index ->
                    when {
                        flags[index] -> 100
                        work[index]?.state == androidx.work.WorkInfo.State.SUCCEEDED -> 100
                        work[index]?.state == androidx.work.WorkInfo.State.RUNNING -> work[index]!!.progress.getInt("progress", 0).coerceIn(0, 100)
                        else -> 0
                    }
                } / 2
                if (ready == 2) if (english) "2/2 ready · 100%" else "२/२ तयार · १००%"
                else if (work.any { it?.state == androidx.work.WorkInfo.State.RUNNING } || progress > 0)
                    if (english) "Japa models · $progress%" else "जप मॉडेल · $progress%"
                else if (english) "$ready/2 ready · $progress%" else "$ready/२ तयार · $progress%"
            }
            delay(1800)
        }
    }
    var weekly by remember { mutableStateOf(false) }
    var monthlyHistory by remember { mutableStateOf(false) }
    var japaReportScreen by remember { mutableStateOf(false) }
    var addDialog by remember { mutableStateOf(false) }
    var remindersScreen by remember { mutableStateOf(false) }
    var categoryScreen by remember { mutableStateOf(false) }
    var backupScreen by remember { mutableStateOf(false) }
    var voiceModelsDialog by remember { mutableStateOf(false) }
    var backupMessage by remember { mutableStateOf<String?>(null) }
    var pendingRestoreUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    var resetDialog by remember { mutableStateOf(false) }
    var historyDate by remember { mutableStateOf(LocalDate.now()) }
    var clock by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val scope = rememberCoroutineScope()
    val activity = context as? MainActivity
    val shortcutKind = activity?.launchCounterKind
    val shortcutQuickStart = activity?.launchCounterQuickStart == true
    var mantraTarget by remember { mutableStateOf(context.getSharedPreferences("chant_settings", android.content.Context.MODE_PRIVATE).getString("target_1", "राम") ?: "राम") }
    var showPermissionIntro by remember {
        mutableStateOf(!context.getSharedPreferences("app_setup", android.content.Context.MODE_PRIVATE).getBoolean("permission_intro_seen", false))
    }
    val firstRunPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        context.getSharedPreferences("app_setup", android.content.Context.MODE_PRIVATE).edit().putBoolean("permission_intro_seen", true).apply()
        showPermissionIntro = false
        QuickActionsNotification.show(context)
    }
    val firstRunPermissions = if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.POST_NOTIFICATIONS) else emptyArray()
    val jsonExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            backupMessage = null
            runCatching { withContext(Dispatchers.IO) { CounterBackupManager.exportJson(context, uri) } }
                .onSuccess { backupMessage = if (english) "Backup saved successfully." else "बॅकअप यशस्वीपणे जतन झाला." }
                .onFailure { backupMessage = (if (english) "Backup failed: " else "बॅकअप अयशस्वी: ") + (it.message ?: "Unknown error") }
        }
    }
    val csvExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) scope.launch {
            backupMessage = null
            runCatching { withContext(Dispatchers.IO) { CounterBackupManager.exportCsv(context, uri) } }
                .onSuccess { backupMessage = if (english) "CSV exported successfully." else "CSV निर्यात यशस्वी." }
                .onFailure { backupMessage = (if (english) "CSV export failed: " else "CSV निर्यात अयशस्वी: ") + (it.message ?: "Unknown error") }
        }
    }
    val jsonImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        pendingRestoreUri = uri
    }
    val haptic = LocalHapticFeedback.current
    val clickTone = remember { ToneGenerator(android.media.AudioManager.STREAM_SYSTEM, 35) }
    DisposableEffect(clickTone) { onDispose { clickTone.release() } }
    LaunchedEffect(categories) {
        CounterQuickActions.publishShortcuts(context, categories)
        CounterHomeWidget.refresh(context)
    }
    LaunchedEffect(categories, shortcutKind, shortcutQuickStart) {
        shortcutKind?.let { kind -> categories.firstOrNull { it.kind == kind }?.let { selectedId = it.id } }
        if (shortcutKind != null) activity?.consumeLaunchCounterKind()
    }
    val selected = categories.firstOrNull { it.id == selectedId } ?: categories.firstOrNull()
    LaunchedEffect(categories) { if (categories.isNotEmpty() && categories.none { it.id == selectedId }) selectedId = categories.first().id }

    LaunchedEffect(selected?.id, selected?.kind) {
        if (selected?.kind == "MANTRA") {
            mantraTarget = context.getSharedPreferences("chant_settings", android.content.Context.MODE_PRIVATE)
                .getString("target_${selected.id}", "राम") ?: "राम"
        }
    }
    val countFlow = remember(selected?.id, selected?.kind, mantraTarget) {
        selected?.let { if (it.kind == "MANTRA") vm.mantraToday(it.id, mantraTarget) else vm.count(it.id) } ?: flowOf(0)
    }
    val mantraTotalFlow = remember(selected?.id, selected?.kind, mantraTarget) {
        selected?.takeIf { it.kind == "MANTRA" }?.let { vm.mantraAllTime(it.id, mantraTarget) } ?: flowOf(0)
    }
    val weekFlow = remember(selected?.id, selected?.kind, mantraTarget) {
        selected?.let { if (it.kind == "MANTRA") vm.mantraWeek(it.id, mantraTarget) else vm.week(it.id) } ?: flowOf(emptyList())
    }
    val historyLogsFlow = remember(selected?.id, selected?.kind, historyDate) {
        selected?.let { if (it.kind == "MANTRA") vm.mantraLogsForDay(it.id, historyDate) else vm.logsForDay(it.id, historyDate) } ?: flowOf(emptyList())
    }
    val allMantraLogsFlow = remember(selected?.id, selected?.kind) {
        selected?.takeIf { it.kind == "MANTRA" }?.let { vm.allMantraLogs(it.id) } ?: flowOf(emptyList())
    }
    val count by countFlow.collectAsStateWithLifecycle(0)
    val mantraTotal by mantraTotalFlow.collectAsStateWithLifecycle(0)
    LaunchedEffect(count, selected?.id) { CounterHomeWidget.refresh(context) }
    val days by weekFlow.collectAsStateWithLifecycle(emptyList())
    val historyLogs by historyLogsFlow.collectAsStateWithLifecycle(emptyList())
    val allMantraLogs by allMantraLogsFlow.collectAsStateWithLifecycle(emptyList())
    val dark = isSystemInDarkTheme()
    val page = if (dark) Color(0xFF0B1420) else Cream
    val ink = if (dark) Color(0xFFF2F5F8) else Navy
    val tapSurface = if (dark) Color(0xFF263D52) else SoftBlue
    val scheme = if (dark) darkColorScheme(primary = Color(0xFFB8D5F2), onPrimary = Color(0xFF102238), secondary = Color(0xFFE3BD73), onSecondary = Color(0xFF30230C), background = Color(0xFF0B1420), onBackground = Color(0xFFF2F5F8), surface = Color(0xFF172638), onSurface = Color(0xFFF2F5F8), surfaceVariant = Color(0xFF22384D), onSurfaceVariant = Color(0xFFC4D0DC), outline = Color(0xFF52677B))
                 else lightColorScheme(primary = Navy, secondary = Gold, background = page, surface = Color.White)

    MaterialTheme(colorScheme = scheme) {
        if (showPermissionIntro) {
            AlertDialog(
                onDismissRequest = {
                    context.getSharedPreferences("app_setup", android.content.Context.MODE_PRIVATE).edit().putBoolean("permission_intro_seen", true).apply()
                    showPermissionIntro = false
                },
                title = { Text(if (english) "Set up device access" else "फोन परवानग्या सेट करा") },
                text = { Text(if (english) "Japa: microphone for Auto Count and notifications for reminders." else "जप: Auto Count साठी मायक्रोफोन व स्मरणपत्रांसाठी सूचना.") },
                confirmButton = {
                    TextButton(onClick = { firstRunPermissionLauncher.launch(firstRunPermissions) }) {
                        Text(if (english) "Continue" else "पुढे")
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        context.getSharedPreferences("app_setup", android.content.Context.MODE_PRIVATE).edit().putBoolean("permission_intro_seen", true).apply()
                        showPermissionIntro = false
                    }) { Text(if (english) "Not now" else "आत्ता नको") }
                }
            )
        }
        if (voiceModelsDialog) VoiceModelsDialog(english = english, onDismiss = { voiceModelsDialog = false })
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet {
                    Column(Modifier.fillMaxWidth().padding(18.dp)) {
                        Text(if (english) "DAILY COUNTER" else "दैनिक मोजणी", style = MaterialTheme.typography.titleLarge, color = ink, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(14.dp))
                        categories.forEach { cat ->
                            val glyph = Icons.Default.SelfImprovement
                            NavigationDrawerItem(
                                icon = { Icon(glyph, null) },
                                label = { Text(if (cat.kind == "MANTRA") { if (english) "Chants" else "जप / मंत्र" } else if (english) cat.titleEn else cat.titleMr) },
                                selected = selected?.id == cat.id && !remindersScreen,
                                onClick = { selectedId = cat.id; remindersScreen = false; weekly = false; monthlyHistory = false; categoryScreen = false; backupScreen = false; scope.launch { drawerState.close() } }
                            )
                        }
                        NavigationDrawerItem(icon = { Icon(Icons.Default.Add, null) }, label = { Text(if (english) "Add counter" else "नवीन मोजणी") }, selected = false, onClick = { addDialog = true; scope.launch { drawerState.close() } })
                        Divider(Modifier.padding(vertical = 8.dp))
                        NavigationDrawerItem(icon = { Icon(Icons.Default.NotificationsActive, null) }, label = { Text(if (english) "Reminders" else "स्मरणपत्रे") }, selected = remindersScreen, onClick = { remindersScreen = true; weekly = false; monthlyHistory = false; categoryScreen = false; backupScreen = false; scope.launch { drawerState.close() } })
                        NavigationDrawerItem(icon = { Icon(Icons.Default.Edit, null) }, label = { Text(if (english) "Manage counters" else "मोजणी व्यवस्थापन") }, selected = categoryScreen, onClick = { categoryScreen = true; backupScreen = false; remindersScreen = false; weekly = false; monthlyHistory = false; scope.launch { drawerState.close() } })
                        NavigationDrawerItem(icon = { Icon(Icons.Default.Save, null) }, label = { Text(if (english) "Backup & export" else "बॅकअप व निर्यात") }, selected = backupScreen, onClick = { backupScreen = true; categoryScreen = false; remindersScreen = false; weekly = false; monthlyHistory = false; scope.launch { drawerState.close() } })
                        NavigationDrawerItem(icon = { Icon(Icons.Default.Download, null) }, label = { Text((if (english) "Offline voice models" else "ऑफलाइन आवाज मॉडेल") + " · " + voiceModelSummary) }, selected = voiceModelsDialog, onClick = { voiceModelsDialog = true; scope.launch { drawerState.close() } })
                        NavigationDrawerItem(icon = { Icon(Icons.Default.BarChart, null) }, label = { Text(if (english) "Weekly report" else "साप्ताहिक अहवाल") }, selected = weekly, onClick = { weekly = true; remindersScreen = false; monthlyHistory = false; categoryScreen = false; backupScreen = false; scope.launch { drawerState.close() } })
                        NavigationDrawerItem(icon = { Icon(Icons.Default.History, null) }, label = { Text(if (english) "History" else "इतिहास") }, selected = monthlyHistory, onClick = { monthlyHistory = true; remindersScreen = false; weekly = false; categoryScreen = false; backupScreen = false; scope.launch { drawerState.close() } })
                        NavigationDrawerItem(
                            icon = { Icon(Icons.Default.Language, null) },
                            label = { Text(if (english) "मराठी" else "English") },
                            selected = false,
                            onClick = {
                                val nextLanguage = !english
                                english = nextLanguage
                                context.getSharedPreferences("app_preferences", android.content.Context.MODE_PRIVATE)
                                    .edit().putBoolean("english_language", nextLanguage).apply()
                                scope.launch { drawerState.close() }
                            }
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            if (english) "Designed & developed by Bharat Rasave" else "निर्मिती व संकल्पना · Bharat Rasave",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 18.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        ) {
        Surface(Modifier.fillMaxSize(), color = page) {
            Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 12.dp)) {
                Row(
                    Modifier.fillMaxWidth()
                        .background(Color(0xFF31413A), RoundedCornerShape(22.dp))
                        .padding(horizontal = 10.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { scope.launch { drawerState.open() } }) { Icon(Icons.Default.Menu, null, tint = Color(0xFFF3EEE3)) }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.bilan_header_logo_light),
                            contentDescription = "Bilan",
                            modifier = Modifier.width(94.dp).height(32.dp),
                            contentScale = ContentScale.Fit
                        )
                        Text(
                            if (english) "Daily Counter" else "दैनिक मोजणी",
                            color = Color(0xFFF3EEE3),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = {
                        remindersScreen = true; weekly = false; monthlyHistory = false
                        categoryScreen = false; backupScreen = false
                    }) {
                        Icon(Icons.Default.NotificationsActive, contentDescription = if (english) "Reminders" else "स्मरणपत्रे", tint = Color(0xFFF3EEE3))
                    }
                    IconButton(onClick = {
                        historyDate = LocalDate.now()
                        monthlyHistory = true; remindersScreen = false; weekly = false
                        categoryScreen = false; backupScreen = false
                    }) {
                        Icon(Icons.Default.History, contentDescription = if (english) "History" else "इतिहास", tint = Color(0xFFF3EEE3))
                    }
                }
                Spacer(Modifier.height(12.dp))

                Column(Modifier.weight(1f)) {
                Box(Modifier.weight(1f)) {
                if (backupScreen) {
                    BackupSettingsScreen(english = english, ink = ink, message = backupMessage,
                        onExportBackup = { jsonExportLauncher.launch("daily-counter-backup.json") },
                        onImportBackup = { jsonImportLauncher.launch(arrayOf("application/json", "text/*")) },
                        onExportCsv = { csvExportLauncher.launch("daily-counter-history.csv") })
                } else if (categoryScreen) {
                    CategoryManagementScreen(categories = categories, archivedCategories = archivedCategories, english = english, ink = ink,
                        onAdd = { mr, en -> scope.launch { vm.addCategory(mr, en) } },
                        onUpdate = { item -> scope.launch { vm.updateCategory(item) } },
                        onArchive = { item -> scope.launch { runCatching { vm.archiveCategory(item.id) }.onFailure { backupMessage = it.message } } },
                        onRestore = { item -> scope.launch { vm.restoreCategory(item.id) } })
                } else if (remindersScreen) {
                    ReminderSettingsScreen(english = english, ink = ink)
                } else
                } else if (japaReportScreen && selected?.kind == "MANTRA") {
                    JapaReportScreen(
                        logs = allMantraLogs, english = english, ink = ink,
                        onBack = { japaReportScreen = false }
                    )
                } else if (weekly) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            StatTile(if (english) "TOTAL" else "एकूण", days.sumOf { it.total }.toString(), ink, Modifier.weight(1f))
                            StatTile(if (english) "DAILY AVG" else "दररोज सरासरी", "%.1f".format(days.sumOf { it.total }.toDouble() / 7.0), ink, Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(14.dp))
                        WeeklyChart(days, Modifier.fillMaxWidth().height(210.dp))
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = {
                            val lines = buildString {
                                append(if (english) "Last 7 days\n" else "मागील ७ दिवसांची नोंद\n")
                                days.forEach { append("${it.date}: ${it.total}\n") }
                                append(if (english) "Total: " else "एकूण: "); append(days.sumOf { it.total })
                            }
                            val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, lines) }
                            context.startActivity(Intent.createChooser(send, if (english) "Share summary" else "सारांश शेअर करा"))
                        }) { Text(if (english) "Share summary" else "सारांश शेअर करा") }
                    }
                } else if (selected?.kind == "MANTRA") {
                    AutoChantCounterScreen(
                        categoryId = selected.id, count = count, totalCount = mantraTotal, english = english, ink = ink,
                        onMantraChange = { mantra -> mantraTarget = mantra },
                        onManualCount = { mantra ->
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            clickTone.startTone(ToneGenerator.TONE_PROP_BEEP, 65)
                            scope.launch { vm.addMantra(selected.id, mantra) }
                        }
                    )
                } else if (selected != null) {
                    CounterTapScreen(
                        english = english, count = count, goal = selected.goal, kind = selected.kind,
                        categoryTitle = if (english) selected.titleEn else selected.titleMr,
                        ink = ink, onTap = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); clickTone.startTone(ToneGenerator.TONE_PROP_BEEP, 65); scope.launch { vm.add(selected.id, null) } },
                        onUndo = { scope.launch { vm.undo(selected.id) } }, onReset = { resetDialog = true }
                    )
                }
                }
                if (selected != null && !backupScreen && !categoryScreen && !remindersScreen && !weekly && !monthlyHistory && !japaReportScreen) {
                    TextButton(onClick = {
                        if (selected.kind == "MANTRA") japaReportScreen = true
                        else { historyDate = LocalDate.now(); monthlyHistory = true }
                    }) {
                        Icon(Icons.Default.History, null); Spacer(Modifier.width(6.dp)); Text(if (selected.kind == "MANTRA") (if (english) "My Japa Report" else "माझा जप अहवाल") else (if (english) "View history & analytics" else "संपूर्ण इतिहास व विश्लेषण"))
                    }
                }
            }
        }
        }
    }

    }
    pendingRestoreUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingRestoreUri = null },
            title = { Text(if (english) "Replace app data?" else "सध्याचा डेटा बदलायचा?") },

            confirmButton = {
                TextButton(onClick = {
                    pendingRestoreUri = null
                    scope.launch {
                        backupMessage = null
                        runCatching { withContext(Dispatchers.IO) { CounterBackupManager.restoreJson(context, uri) } }
                            .onSuccess { backupMessage = if (english) "Backup restored successfully." else "बॅकअप यशस्वीपणे पुनर्संचयित झाला." }
                            .onFailure { backupMessage = (if (english) "Restore failed: " else "पुनर्संचयित करणे अयशस्वी: ") + (it.message ?: "Unknown error") }
                    }
                }) { Text(if (english) "Replace data" else "डेटा बदला") }
            },
            dismissButton = { TextButton(onClick = { pendingRestoreUri = null }) { Text(if (english) "Cancel" else "रद्द") } }
        )
    }
    if (addDialog) {
        var mr by remember { mutableStateOf("") }
        var en by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { addDialog = false }, title = { Text(if (english) "New category" else "नवीन प्रकार") },
            text = { Column { OutlinedTextField(mr, { mr = it }, label = { Text("मराठी नाव") }); OutlinedTextField(en, { en = it }, label = { Text("English name") }) } },
            confirmButton = { TextButton(onClick = { if (mr.isNotBlank() || en.isNotBlank()) scope.launch { vm.addCategory(mr.ifBlank { en }, en.ifBlank { mr }) }; addDialog = false }) { Text(if (english) "Add" else "जोडा") } },
            dismissButton = { TextButton(onClick = { addDialog = false }) { Text(if (english) "Cancel" else "रद्द") } })
    }
    if (resetDialog) AlertDialog(onDismissRequest = { resetDialog = false }, title = { Text(if (english) "Reset today's count?" else "आजची मोजणी रीसेट करायची?") },
        confirmButton = { TextButton(onClick = { scope.launch { selected?.let { vm.reset(it.id) } }; resetDialog = false }) { Text(if (english) "Reset" else "रीसेट") } },
        dismissButton = { TextButton(onClick = { resetDialog = false }) { Text(if (english) "Cancel" else "रद्द") } })

}

@Composable
private fun CounterTapScreen(english:Boolean,count:Int,goal:Int,kind:String,categoryTitle:String,ink:Color,onTap:()->Unit,onUndo:()->Unit,onReset:()->Unit) {
    val mantra = kind == "MANTRA"
    val tapScale = remember { Animatable(1f) }
    val tapScope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize(),horizontalAlignment=Alignment.CenterHorizontally) {
        Card(Modifier.fillMaxWidth(),shape=RoundedCornerShape(26.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)) {
            Column(Modifier.fillMaxWidth().padding(20.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                Box(Modifier.size(58.dp).background(if(mantra) Color(0xFFEDE4FA) else Color(0xFFDDF1E8),CircleShape),contentAlignment=Alignment.Center) {
                    Icon(Icons.Default.SelfImprovement,null,tint=Color(0xFF7855A6),modifier=Modifier.size(30.dp))
                }
                Spacer(Modifier.height(8.dp))
                Text(if(english) "TODAY" else "आज",color=ink.copy(alpha=.55f),style=MaterialTheme.typography.labelMedium,fontWeight=FontWeight.Bold)
                Text(count.toString(),color=ink,style=MaterialTheme.typography.displayLarge,fontWeight=FontWeight.ExtraBold)
                Text(if(mantra) { if(english) "CHANTS" else "जप" } else categoryTitle,color=ink.copy(alpha=.55f),style=MaterialTheme.typography.labelSmall,fontWeight=FontWeight.Bold)
                if(mantra) {
                    Text((count/108).toString()+" "+(if(english) "mala" else "माळ")+" · "+(count%108)+"/108",color=Gold,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=9.dp))
                    Row(Modifier.padding(top=8.dp),horizontalArrangement=Arrangement.Center) { repeat(12) { index -> Box(Modifier.padding(3.dp).size(11.dp).background(if(index<count%108*12/108) Gold else Color(0xFFE8E1D7),CircleShape)) } }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        val tapInteraction = remember { MutableInteractionSource() }
        Box(Modifier.size(214.dp).graphicsLayer(scaleX = tapScale.value, scaleY = tapScale.value).background(Brush.linearGradient(if(mantra) listOf(Color(0xFFE9DDF8),Color(0xFFD8C4F0)) else listOf(Color(0xFFDDF1E8),Color(0xFFBDE2D0))),CircleShape).clickable(interactionSource = tapInteraction, indication = null) { tapScope.launch { tapScale.animateTo(.90f, tween(75)); tapScale.animateTo(1.08f, tween(110)); tapScale.animateTo(1f, tween(180)) }; onTap() },contentAlignment=Alignment.Center) {
            Column(horizontalAlignment=Alignment.CenterHorizontally) {
                Icon(Icons.Default.SelfImprovement,null,tint=ink,modifier=Modifier.size(36.dp))
                Text("+1",color=ink,style=MaterialTheme.typography.displayMedium,fontWeight=FontWeight.ExtraBold)
                Text(if(english) "TAP TO ADD" else "मोजणी वाढवा",color=ink,style=MaterialTheme.typography.labelSmall,fontWeight=FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick=onUndo,modifier=Modifier.weight(1f),shape=RoundedCornerShape(15.dp)) { Icon(Icons.Default.Undo,null); Spacer(Modifier.width(5.dp)); Text(if(english) "Undo" else "मागे") }
            OutlinedButton(onClick=onReset,modifier=Modifier.weight(1f),shape=RoundedCornerShape(15.dp)) { Icon(Icons.Default.Refresh,null); Spacer(Modifier.width(5.dp)); Text(if(english) "Reset" else "रीसेट") }
        }
        Spacer(Modifier.weight(.8f))
    }
}


@Composable
private fun ReminderSettingsScreen(english: Boolean, ink: Color) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    val settings = remember { mutableStateListOf<DailyReminder>().apply { addAll(DailyReminderScheduler.load(context)) } }
    Column(Modifier.fillMaxSize()) {
        Text(if (english) "DAILY REMINDERS" else "दैनिक स्मरणपत्रे", color = ink, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(if (english) "Choose times that fit your routine." else "तुमच्या दिनक्रमानुसार वेळ निवडा.", color = ink.copy(alpha = .65f), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp, bottom = 14.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp), contentPadding = PaddingValues(bottom = 14.dp)) {
            items(settings, key = { it.id }) { item ->
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(if (english) item.titleEn else item.titleMr, color = ink, fontWeight = FontWeight.SemiBold)
                            Text("%02d:%02d".format(item.hour, item.minute), color = Color(0xFF39865B), style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.clickable {
                                    TimePickerDialog(context, { _, hour, minute ->
                                        val index = settings.indexOfFirst { it.id == item.id }
                                        if (index >= 0) {
                                            settings[index] = settings[index].copy(hour = hour, minute = minute)
                                            DailyReminderScheduler.saveAndSchedule(context, settings.toList())
                                        }
                                    }, item.hour, item.minute, false).show()
                                })
                            Text(if (english) "Tap time to change" else "वेळ बदलण्यासाठी टॅप करा", color = ink.copy(alpha = .45f), style = MaterialTheme.typography.labelSmall)
                        }
                        Switch(checked = item.enabled, onCheckedChange = { enabled ->
                            val index = settings.indexOfFirst { it.id == item.id }
                            if (index >= 0) {
                                settings[index] = settings[index].copy(enabled = enabled)
                                DailyReminderScheduler.saveAndSchedule(context, settings.toList())
                                if (enabled && Build.VERSION.SDK_INT >= 33 && ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                                    activity?.let { ActivityCompat.requestPermissions(it, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 4101) }
                                }
                            }
                        })
                    }
                }
            }
        }
        Surface(shape = RoundedCornerShape(16.dp), color = Color(0xFFFFF3DF)) {
            Text(
                if (english) "Baby movement reminders are optional routine prompts, not medical advice. Follow your maternity professional's guidance. If movements become reduced or change from your baby's usual pattern, contact your maternity unit promptly; do not wait for a reminder."
                else "बाळाच्या हालचालींची स्मरणपत्रे ही केवळ ऐच्छिक आठवण आहे, वैद्यकीय सल्ला नाही. डॉक्टरांच्या सूचनांचे पालन करा. हालचाली कमी झाल्या किंवा नेहमीच्या पद्धतीत बदल जाणवला तर स्मरणपत्राची वाट न पाहता त्वरित प्रसूती-सेवा केंद्राशी संपर्क साधा.",
                color = Color(0xFF76531F), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(13.dp)
            )
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, ink: Color, modifier: Modifier = Modifier) {
    Card(modifier, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 13.dp)) {
            Text(label, color = ink.copy(alpha = .55f), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            Text(value, color = ink, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
fun WeeklyChart(days: List<DayTotal>, modifier: Modifier = Modifier) {
    val maxValue=(days.maxOfOrNull{it.total} ?: 0).coerceAtLeast(1)
    Row(modifier, horizontalArrangement=Arrangement.spacedBy(6.dp), verticalAlignment=Alignment.Bottom) {
        days.forEach { day ->
            Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Bottom) {
                Text(day.total.toString(),color=Navy,style=MaterialTheme.typography.labelSmall,fontWeight=FontWeight.Bold)
                Spacer(Modifier.height(5.dp))
                Box(Modifier.fillMaxWidth(.72f).weight(1f,false).height((145f*day.total/maxValue).coerceAtLeast(if(day.total>0) 8f else 3f).dp).clip(RoundedCornerShape(topStart=8.dp,topEnd=8.dp)).background(if(day.total==maxValue) Gold else Color(0xFF9BB6D3)))
                Spacer(Modifier.height(7.dp))
                Text(day.date.takeLast(2),color=Navy.copy(alpha=.65f),style=MaterialTheme.typography.labelSmall)
            }
        }
    }
}

