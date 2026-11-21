package com.projectbharat.counterapp

import android.content.Intent
import android.app.TimePickerDialog
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
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Undo
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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import java.time.LocalDate

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
    fun consumeLaunchCounterKind() {
        launchCounterKind = null
        launchCounterQuickStart = false
    }

    private fun readCounterIntent(intent: Intent) {
        if (intent.action != ACTION_OPEN_COUNTER ||
            intent.getStringExtra(CounterQuickActions.EXTRA_COUNTER_KIND) != "MANTRA") return
        launchCounterQuickStart = intent.getBooleanExtra("quick_start", false)
        launchCounterKind = "MANTRA"
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readCounterIntent(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        readCounterIntent(intent)
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
    fun activeJapaSession(id: Long, mantra: String) = repo.activeJapaSession(id, mantra)
    fun japaGoals(id: Long) = repo.japaGoals(id)
    fun japaDayTotal(id: Long, date: LocalDate) = repo.japaDayTotal(id, date)
    suspend fun saveJapaGoal(id: Long, target: Int) = repo.saveJapaGoal(id, target)
    suspend fun finishJapaSession(session: JapaSession) =
        repo.finishJapaSession(session.categoryId, session.mantra, session.id)
    fun mantraAllTime(id: Long, mantra: String) = repo.mantraAllTime(id, mantra)
    fun mantraWeek(id: Long, mantra: String) = repo.mantraWeek(id, mantra)
    suspend fun addMantra(id: Long, mantra: String) = repo.addMantraCount(id, mantra)
    suspend fun updateLog(log: CounterLog) = repo.updateLog(log)
    suspend fun deleteLog(id: Long) = repo.deleteLog(id)
    fun week(id: Long) = repo.week(id)
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
@Composable
fun CounterApp(repo: CounterRepository) {
    val vm: CounterViewModel = viewModel(factory = CounterViewModel.Factory(repo))
    val categories by vm.categories.collectAsStateWithLifecycle()
    val archivedCategories by vm.archivedCategories.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var selectedId by rememberSaveable { mutableLongStateOf(1L) }
    var english by remember(context) {
        mutableStateOf(
            context.getSharedPreferences("app_preferences", android.content.Context.MODE_PRIVATE)
                .getBoolean("english_language", false)
        )
    }
    LaunchedEffect(english) {
        context.getSharedPreferences("app_preferences", android.content.Context.MODE_PRIVATE)
            .edit().putBoolean("english_language", english).apply()
    }
    var monthlyHistory by remember { mutableStateOf(false) }

    val reportState = rememberSaveableStateHolder()
    var remindersScreen by remember { mutableStateOf(false) }
    var categoryScreen by remember { mutableStateOf(false) }
    var backupScreen by remember { mutableStateOf(false) }
    var voiceModelsDialog by remember { mutableStateOf(false) }
    var backupMessage by remember { mutableStateOf<String?>(null) }
    var backupBusy by remember { mutableStateOf(false) }
    var pendingRestoreUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var tab by rememberSaveable { mutableStateOf(0) }
    var focusSession by rememberSaveable { mutableStateOf(false) }
    var mantraLibrary by rememberSaveable { mutableStateOf(false) }
    var help by rememberSaveable { mutableStateOf(false) }
    fun navigate(destination: Int) {
        tab = destination
        focusSession = false
        mantraLibrary = false
        monthlyHistory = false
        categoryScreen = false
        remindersScreen = false
        backupScreen = false
        help = false
    }
    var resetDialog by remember { mutableStateOf(false) }
    var historyDate by remember { mutableStateOf(LocalDate.now()) }
    var today by remember { mutableStateOf(LocalDate.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            today = LocalDate.now()
            delay(15_000)
        }
    }
    var editGoalDialog by remember { mutableStateOf(false) }
    var finishedSession by remember { mutableStateOf<JapaSession?>(null) }
    var finishingSession by remember { mutableStateOf(false) }
    val child = monthlyHistory || categoryScreen || remindersScreen || backupScreen || help || focusSession || mantraLibrary || finishedSession != null
    fun goBack() {
        when {
            finishedSession != null -> { finishedSession = null; focusSession = false }
            mantraLibrary -> mantraLibrary = false
            focusSession -> focusSession = false
            child -> navigate(tab)
            else -> navigate(0)
        }
    }
    BackHandler(child || tab != 0) { goBack() }
    val preferences = remember { context.getSharedPreferences("practice_feedback", android.content.Context.MODE_PRIVATE) }
    var tapFeedback by remember { mutableStateOf(preferences.getBoolean("tap", true)) }
    var malaFeedback by remember { mutableStateOf(preferences.getBoolean("mala", false)) }
    var soundFeedback by remember { mutableStateOf(preferences.getBoolean("sound", false)) }
    val scope = rememberCoroutineScope()
    val activity = context as? MainActivity
    val shortcutKind = activity?.launchCounterKind
    val shortcutQuickStart = activity?.launchCounterQuickStart == true
    var mantraTarget by remember { mutableStateOf(context.getSharedPreferences("chant_settings", android.content.Context.MODE_PRIVATE).getString("target_1", "राम") ?: "राम") }
    val jsonExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            backupBusy = true
            backupMessage = null
            runCatching { withContext(Dispatchers.IO) { CounterBackupManager.exportJson(context, uri) } }
                .onSuccess { backupMessage = if (english) "Backup saved successfully." else "बॅकअप यशस्वीपणे जतन झाला." }
                .onFailure { backupMessage = (if (english) "Backup failed: " else "बॅकअप अयशस्वी: ") + (it.message ?: "Unknown error") }
            backupBusy = false
        }
    }
    val csvExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) scope.launch {
            backupBusy = true
            backupMessage = null
            runCatching { withContext(Dispatchers.IO) { CounterBackupManager.exportCsv(context, uri) } }
                .onSuccess { backupMessage = if (english) "CSV exported successfully." else "CSV निर्यात यशस्वी." }
                .onFailure { backupMessage = (if (english) "CSV export failed: " else "CSV निर्यात अयशस्वी: ") + (it.message ?: "Unknown error") }
            backupBusy = false
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
        if (shortcutKind == null) return@LaunchedEffect
        val category = categories.firstOrNull { it.kind == "MANTRA" } ?: return@LaunchedEffect
        selectedId = category.id
        navigate(0)
        focusSession = shortcutQuickStart || context.getSharedPreferences("tracking_state", android.content.Context.MODE_PRIVATE).getBoolean("chant_enabled", false)
        if (shortcutQuickStart &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            val prefs = context.getSharedPreferences("chant_settings", android.content.Context.MODE_PRIVATE)
            val target = prefs.getString("target_${category.id}", "राम") ?: "राम"
            mantraTarget = target
            val language = if (target.any { it in '\u0900'..'\u097F' }) "hi" else "en"
            ChantRecognitionService.start(context, category.id, target, language)
        }
        activity?.consumeLaunchCounterKind()
    }
    val selected = categories.firstOrNull { it.id == selectedId } ?: categories.firstOrNull()
    LaunchedEffect(categories) { if (categories.isNotEmpty() && categories.none { it.id == selectedId }) selectedId = categories.first().id }

    LaunchedEffect(selected?.id, selected?.kind) {
        if (selected?.kind == "MANTRA") {
            mantraTarget = context.getSharedPreferences("chant_settings", android.content.Context.MODE_PRIVATE)
                .getString("target_${selected.id}", "राम") ?: "राम"
        }
    }
    val countFlow = remember(selected?.id, selected?.kind, mantraTarget, today) {
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
    val japaCategoryId = selected?.takeIf { it.kind == "MANTRA" }?.id
    val goalsFlow = remember(japaCategoryId) {
        japaCategoryId?.let(vm::japaGoals) ?: flowOf(emptyList())
    }
    val goalHistory by goalsFlow.collectAsStateWithLifecycle(emptyList())
    val dailyGoal = japaGoalOn(goalHistory, today)
    val dayTotalFlow = remember(japaCategoryId, today) {
        japaCategoryId?.let { vm.japaDayTotal(it, today) } ?: flowOf(0)
    }
    val japaDayTotal by dayTotalFlow.collectAsStateWithLifecycle(0)
    val activeJapaFlow = remember(japaCategoryId, mantraTarget) {
        japaCategoryId?.let { vm.activeJapaSession(it, mantraTarget) } ?: flowOf(null)
    }
    val storedSession by activeJapaFlow.collectAsStateWithLifecycle(null)
    val activeJapa = storedSession?.takeIf { it.categoryId == japaCategoryId && it.mantra == mantraTarget.trim() }
    LaunchedEffect(focusSession, activeJapa?.id) {
        if (focusSession || activeJapa != null) QuickActionsNotification.hide(context)
        else QuickActionsNotification.show(context)
    }
    val count by countFlow.collectAsStateWithLifecycle(0)
    val mantraTotal by mantraTotalFlow.collectAsStateWithLifecycle(0)
    var previousMalaCount by remember(selected?.id, mantraTarget) { mutableStateOf<Int?>(null) }
    LaunchedEffect(mantraTotal, malaFeedback, selected?.id, mantraTarget) {
        val completed = mantraTotal / 108
        if (malaFeedback && previousMalaCount?.let { completed > it } == true) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
        previousMalaCount = completed
    }
    LaunchedEffect(count, selected?.id) { CounterHomeWidget.refresh(context) }
    val days by weekFlow.collectAsStateWithLifecycle(emptyList())
    val historyLogs by historyLogsFlow.collectAsStateWithLifecycle(emptyList())
    val allMantraLogs by allMantraLogsFlow.collectAsStateWithLifecycle(emptyList())
    val dark = isSystemInDarkTheme()
    val page = if (dark) Color(0xFF0B1420) else Cream
    val ink = if (dark) Color(0xFFF2F5F8) else Navy
    val scheme = if (dark) darkColorScheme(primary = Color(0xFFB8D5F2), onPrimary = Color(0xFF102238), secondary = Color(0xFFE3BD73), onSecondary = Color(0xFF30230C), background = Color(0xFF0B1420), onBackground = Color(0xFFF2F5F8), surface = Color(0xFF172638), onSurface = Color(0xFFF2F5F8), surfaceVariant = Color(0xFF22384D), onSurfaceVariant = Color(0xFFC4D0DC), outline = Color(0xFF52677B))
                 else lightColorScheme(primary = Navy, secondary = Gold, background = page, surface = Color.White)

    MaterialTheme(colorScheme = scheme) {
        if (voiceModelsDialog) VoiceModelsDialog(english = english, onDismiss = { voiceModelsDialog = false })
        Scaffold(
            modifier = Modifier.background(Brush.verticalGradient(
                if (dark) listOf(Color(0xFF152921), Color(0xFF152033), page)
                else listOf(Color(0xFFE4EDE2), Color(0xFFF7F1E4), Color(0xFFF1EAF5))
            )),
            containerColor = Color.Transparent,
            bottomBar = {
                if (!child) NavigationBar(
                    modifier = Modifier.navigationBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(30.dp)),
                    containerColor = if (dark) Color(0xFF223445) else Color(0xFFFFFCF7),
                    tonalElevation = 5.dp,
                    windowInsets = WindowInsets(0, 0, 0, 0)
                ) {
                    val labels = if (english) listOf("Practice", "Progress", "Settings") else listOf("जप", "प्रगती", "सेटिंग्ज")
                    val icons = listOf(Icons.Default.SelfImprovement, Icons.Default.BarChart, Icons.Default.Settings)
                    labels.forEachIndexed { index, label ->
                        NavigationBarItem(selected = tab == index, onClick = { navigate(index) },
                            icon = { Icon(icons[index], null) }, label = { Text(label) })
                    }
                }
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (child) FilledTonalIconButton(onClick = ::goBack) {
                        Icon(Icons.Default.ArrowBack, if (english) "Back" else "मागे")
                    }
                    Image(painterResource(if (dark) R.drawable.bilan_header_logo_light else R.drawable.bilan_header_logo_dark), "Bilan",
                        Modifier.width(94.dp).height(36.dp), contentScale = ContentScale.Fit)
                    Spacer(Modifier.weight(1f))
                    Text(when {
                        finishedSession != null -> if (english) "Session summary" else "सत्राचा सारांश"
                        mantraLibrary -> if (english) "Mantra library" else "मंत्र संग्रह"
                        focusSession -> if (english) "Japa session" else "जप सत्र"
                        tab == 1 -> if (english) "Progress" else "प्रगती"
                        tab == 2 -> if (english) "Settings" else "सेटिंग्ज"
                        else -> if (english) "Japa Practice" else "जप साधना"
                    }, color = ink, style = MaterialTheme.typography.titleMedium)
                }
                Box(Modifier.weight(1f)) {
                val summary = finishedSession
                if (summary != null) {
                    val title = if (english) "Japa session saved" else "जप सत्र जतन झाले"
                    val card = JapaImageCard(title, summary.mantra, summary.count,
                        if (english) "Today · $japaDayTotal/$dailyGoal" else "आज · $japaDayTotal/$dailyGoal", english,
                        summary.endedAt ?: summary.startedAt)
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        JapaImagePreview(card)
                        Button(onClick = {
                            scope.launch { shareJapaImage(context, card) }
                        }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Share, null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (english) "Share image" else "चित्र शेअर करा")
                        }
                        OutlinedButton(onClick = ::goBack, modifier = Modifier.fillMaxWidth()) {
                            Text(if (english) "Done" else "पूर्ण")
                        }
                    }
                } else if (help) {
                    JapaHelpScreen(english)
                } else if (backupScreen) {
                    BackupSettingsScreen(english = english, ink = ink, message = backupMessage, busy = backupBusy,
                        onExportBackup = { jsonExportLauncher.launch("AppBackup_" + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmm")) + ".json") },
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
                } else if (monthlyHistory && selected != null) {
                    CounterHistoryScreen(
                        english = english, ink = ink, date = historyDate, logs = historyLogs, days = days,
                        onPrevious = { historyDate = historyDate.minusDays(1) },
                        onNext = { if (historyDate.isBefore(LocalDate.now())) historyDate = historyDate.plusDays(1) },
                        onBack = null,
                        onUpdate = { log -> scope.launch { vm.updateLog(log) } },
                        onDelete = { log -> scope.launch { vm.deleteLog(log.id) } }
                    )
                } else if (tab == 1 && selected?.kind == "MANTRA") {
                    reportState.SaveableStateProvider("japa-report") {
                        JapaReportScreen(
                            logs = allMantraLogs, goals = goalHistory, today = today, english = english, ink = ink,
                            onDaySelected = { date -> historyDate = date; monthlyHistory = true },
                            onEditGoal = { editGoalDialog = true }
                        )
                    }
                } else if (tab == 1) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            StatTile(if (english) "TOTAL" else "एकूण", days.sumOf { it.total }.toString(), ink, Modifier.weight(1f))
                            StatTile(if (english) "DAILY AVG" else "दररोज सरासरी", "%.1f".format(days.sumOf { it.total }.toDouble() / 7.0), ink, Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(14.dp))
                        WeeklyChart(days, Modifier.fillMaxWidth().height(210.dp))
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = {
                            val title = if (english) "Weekly progress" else "साप्ताहिक प्रगती"
                            val category = selected?.let { if (english) it.titleEn else it.titleMr }.orEmpty()
                            val card = JapaImageCard(title, category, days.sumOf { it.total },
                                if (english) "Last 7 days" else "मागील ७ दिवस", english)
                            scope.launch { shareJapaImage(context, card) }
                        }) {
                            Icon(Icons.Default.Share, null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (english) "Share image" else "चित्र शेअर करा")
                        }
                    }
                } else if (tab == 2) {
                    JapaSettingsScreen(
                        english, tapFeedback, malaFeedback, soundFeedback,
                        onLanguage = { english = !english },
                        onTap = { tapFeedback = it; preferences.edit().putBoolean("tap", it).apply() },
                        onMala = { malaFeedback = it; preferences.edit().putBoolean("mala", it).apply() },
                        onSound = { soundFeedback = it; preferences.edit().putBoolean("sound", it).apply() },
                        onReminders = { remindersScreen = true },
                        onModels = { voiceModelsDialog = true },
                        onBackup = { backupScreen = true },
                        onCounters = { categoryScreen = true },
                        onHelp = { help = true },
                        counterNames = categories.map { if (english) it.titleEn else it.titleMr },
                        selectedCounter = categories.indexOfFirst { it.id == selected?.id },
                        onCounter = { index -> selectedId = categories[index].id; navigate(0) }
                    )
                } else if (selected?.kind == "MANTRA") {
                    AutoChantCounterScreen(
                        categoryId = selected.id, count = count, totalCount = mantraTotal, english = english, ink = ink,
                        dailyGoal = dailyGoal,
                        dayTotal = japaDayTotal,
                        sessionCount = activeJapa?.count ?: 0,
                        focusSession = focusSession,
                        mantraLibrary = mantraLibrary,
                        onFocus = { focusSession = true },
                        onLibrary = { mantraLibrary = it },
                        onEditGoal = { editGoalDialog = true },
                        onFinish = activeJapa?.takeUnless { finishingSession }?.let { session ->
                            {
                                finishingSession = true
                                scope.launch {
                                    try {
                                        ChantRecognitionService.stop(context)
                                        finishedSession = vm.finishJapaSession(session)
                                        focusSession = false
                                    } finally {
                                        finishingSession = false
                                    }
                                }
                                Unit
                            }
                        },
                        onMantraChange = { mantra -> mantraTarget = mantra },
                        onManualCount = { mantra ->
                            focusSession = true
                            if (tapFeedback) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            if (soundFeedback) clickTone.startTone(ToneGenerator.TONE_PROP_BEEP, 65)
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
                if (tab == 1 && selected?.kind != "MANTRA" && !child) {
                    TextButton(onClick = { historyDate = today; monthlyHistory = true }) {
                        Text(if (english) "Open history" else "इतिहास उघडा")
                    }
                }
            }
        }
    }
    if (editGoalDialog && japaCategoryId != null) {
        var input by remember(japaCategoryId) { mutableStateOf(dailyGoal.toString()) }
        val target = input.toIntOrNull()?.takeIf { it in 1..999_999 }
        AlertDialog(
            onDismissRequest = { editGoalDialog = false },
            icon = { Icon(Icons.Default.Edit, null) },
            title = { Text(if (english) "Daily Japa goal" else "दररोजचे जप लक्ष्य") },
            text = {
                OutlinedTextField(
                    value = input,
                    onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) input = it },
                    label = { Text(if (english) "Chants per day" else "दररोजचे जप") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    target?.let { scope.launch { vm.saveJapaGoal(japaCategoryId, it) } }
                    editGoalDialog = false
                }, enabled = target != null) { Text(if (english) "Save" else "जतन करा") }
            },
            dismissButton = {
                TextButton(onClick = { editGoalDialog = false }) { Text(if (english) "Cancel" else "रद्द") }
            }
        )
    }
    pendingRestoreUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingRestoreUri = null },
            title = { Text(if (english) "Replace app data?" else "सध्याचा डेटा बदलायचा?") },
            text = { Text(if (english) "Restoring replaces all counters, history and session records on this device. Export a backup first if you need to keep the current data." else "बॅकअप पुनर्संचयित केल्याने या उपकरणावरील सर्व मोजण्या, इतिहास आणि सत्रांच्या नोंदी बदलली जातील. सध्याचा डेटा हवा असल्यास आधी बॅकअप घ्या.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingRestoreUri = null
                    scope.launch {
                        backupBusy = true
                        backupMessage = null
                        runCatching { withContext(Dispatchers.IO) { CounterBackupManager.restoreJson(context, uri) } }
                            .onSuccess { backupMessage = if (english) "Backup restored successfully." else "बॅकअप यशस्वीपणे पुनर्संचयित झाला." }
                            .onFailure { backupMessage = (if (english) "Restore failed: " else "पुनर्संचयित करणे अयशस्वी: ") + (it.message ?: "Unknown error") }
                        backupBusy = false
                    }
                }) { Text(if (english) "Replace data" else "डेटा बदला") }
            },
            dismissButton = { TextButton(onClick = { pendingRestoreUri = null }) { Text(if (english) "Cancel" else "रद्द") } }
        )
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
