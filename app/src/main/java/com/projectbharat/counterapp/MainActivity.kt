package com.projectbharat.counterapp

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.projectbharat.counterapp.data.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SeedData.initialize(this)
        val repo = CounterRepository(CounterDatabase.get(this).dao())
        setContent { CounterApp(repo) }
    }
}

class CounterViewModel(private val repo: CounterRepository) : ViewModel() {
    val categories = repo.categories.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun count(id: Long) = repo.today(id)
    fun week(id: Long) = repo.week(id)
    fun average(id: Long) = repo.average(id)
    fun averageBabyTimeToTen(id: Long) = repo.averageBabyTimeToTen(id)
    fun sessions(id: Long, date: String) = repo.babySessionsForDay(id, date)
    fun latestSession(id: Long) = repo.latestBabySession(id)
    suspend fun startSession(id: Long) = repo.startBabySession(id)
    suspend fun addMovement(id: Long, sessionId: Long) = repo.addBabyMovement(id, sessionId, null)
    suspend fun undoMovement(sessionId: Long) = repo.undoBabyMovement(sessionId)
    suspend fun finishSession(sessionId: Long, strength: String?) = repo.finishBabySession(sessionId, strength)
    suspend fun addCategory(mr: String, en: String) = repo.addCategory(mr, en)
    suspend fun add(id: Long, strength: String?, duration: Long? = null) = repo.add(id, strength, duration)
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
    var selectedId by remember { mutableLongStateOf(1L) }
    var english by remember { mutableStateOf(false) }
    var weekly by remember { mutableStateOf(false) }
    var addDialog by remember { mutableStateOf(false) }
    var resetDialog by remember { mutableStateOf(false) }
    var finishDialog by remember { mutableStateOf(false) }
    var finalStrength by remember { mutableStateOf("MEDIUM") }
    var historyDate by remember { mutableStateOf(LocalDate.now()) }
    var clock by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val selected = categories.firstOrNull { it.id == selectedId } ?: categories.firstOrNull()
    LaunchedEffect(Unit) { while (true) { delay(1000); clock = System.currentTimeMillis() } }
    LaunchedEffect(categories) { if (categories.isNotEmpty() && categories.none { it.id == selectedId }) selectedId = categories.first().id }

    val countFlow = remember(selected?.id) { selected?.let { vm.count(it.id) } ?: flowOf(0) }
    val weekFlow = remember(selected?.id) { selected?.let { vm.week(it.id) } ?: flowOf(emptyList()) }
    val averageFlow = remember(selected?.id) { selected?.let { if (it.kind == "BABY") vm.averageBabyTimeToTen(it.id) else vm.average(it.id) } ?: flowOf(null) }
    val sessionFlow = remember(selected?.id, historyDate) { selected?.let { vm.sessions(it.id, historyDate.toString()) } ?: flowOf(emptyList()) }
    val latestFlow = remember(selected?.id) { selected?.let { vm.latestSession(it.id) } ?: flowOf(null) }
    val count by countFlow.collectAsStateWithLifecycle(0)
    val days by weekFlow.collectAsStateWithLifecycle(emptyList())
    val average by averageFlow.collectAsStateWithLifecycle(null)
    val sessions by sessionFlow.collectAsStateWithLifecycle(emptyList())
    val latestSession by latestFlow.collectAsStateWithLifecycle(null)
    val isBaby = selected?.kind == "BABY"
    val activeSession = latestSession?.takeIf { it.endedAt == null && clock < it.startedAt + CounterRepository.TWO_HOURS_MS }
    val lockedSession = latestSession?.takeIf { clock < it.startedAt + CounterRepository.TWO_HOURS_MS }
    val remainingMs = activeSession?.let { (it.startedAt + CounterRepository.TWO_HOURS_MS - clock).coerceAtLeast(0L) } ?: 0L
    val dark = isSystemInDarkTheme()
    val page = if (dark) Color(0xFF0D1B2A) else Cream
    val ink = if (dark) Color(0xFFEAF0F6) else Navy
    val tapSurface = if (dark) Color(0xFF23466A) else SoftBlue
    val scheme = if (dark) darkColorScheme(primary = Color(0xFF9FC7ED), secondary = Gold, background = page, surface = Color(0xFF172A3E))
                 else lightColorScheme(primary = Navy, secondary = Gold, background = page, surface = Color.White)

    MaterialTheme(colorScheme = scheme) {
        Surface(Modifier.fillMaxSize(), color = page) {
            Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(if (english) "Daily Counter" else "दैनिक मोजणी", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = ink)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { english = !english }) { Icon(Icons.Default.Language, contentDescription = "Language") }
                        IconButton(onClick = { weekly = !weekly }) { Icon(Icons.Default.BarChart, contentDescription = "Weekly report") }
                    }
                }
                if (categories.isNotEmpty()) {
                    var expanded by remember { mutableStateOf(false) }
                    Box(Modifier.align(Alignment.CenterHorizontally).padding(vertical = 8.dp)) {
                        OutlinedButton(onClick = { expanded = true }, shape = RoundedCornerShape(16.dp)) {
                            Text(if (english) selected?.titleEn ?: "" else selected?.titleMr ?: "")
                        }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            categories.forEach { cat -> DropdownMenuItem(text = { Text(if (english) cat.titleEn else cat.titleMr) }, onClick = { selectedId = cat.id; weekly = false; expanded = false }) }
                            DropdownMenuItem(text = { Text(if (english) "+ Add category" else "+ नवीन प्रकार") }, onClick = { expanded = false; addDialog = true })
                        }
                    }
                }

                if (weekly) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (english) "Last 7 days" else "मागील ७ दिवस", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(18.dp))
                        WeeklyChart(days.map { it.total }, Modifier.fillMaxWidth().height(210.dp))
                        Text(if (english) "Daily total" else "दररोजची मोजणी")
                        if (isBaby) Text((if (english) "Average time to 10 movements: " else "१० हालचालींसाठी सरासरी वेळ: ") + (average?.let { "%.1f min".format(it / 60000.0) } ?: "—"))
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
                } else if (isBaby && selected != null) {
                    BabyMovementScreen(
                        english = english,
                        ink = ink,
                        tapSurface = tapSurface,
                        latestSession = latestSession,
                        activeSession = activeSession,
                        lockedSession = lockedSession,
                        remainingMs = remainingMs,
                        sessions = sessions,
                        historyDate = historyDate,
                        onPreviousDay = { historyDate = historyDate.minusDays(1) },
                        onNextDay = { if (historyDate.isBefore(LocalDate.now())) historyDate = historyDate.plusDays(1) },
                        onStart = { scope.launch { vm.startSession(selected.id) } },
                        onTap = { sessionId ->
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            scope.launch { vm.addMovement(selected.id, sessionId) }
                        },
                        onUndo = { sessionId -> scope.launch { vm.undoMovement(sessionId) } },
                        onFinish = { finishDialog = true }
                    )
                } else if (selected != null) {
                    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Spacer(Modifier.height(16.dp))
                        Text(if (english) "TODAY" else "आज", color = ink.copy(alpha = .65f))
                        Text(count.toString(), color = ink, style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Bold)
                        if (selected.kind == "MANTRA") Text("${count / 108} माळ  •  ${count % 108}/108", color = Gold, style = MaterialTheme.typography.titleMedium)
                        if (selected.goal > 0) Text("${count} / ${selected.goal} ${if (english) "goal" else "लक्ष्य"}", color = ink)
                        Spacer(Modifier.weight(1f))
                        Box(Modifier.size(240.dp).background(tapSurface, CircleShape).clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            scope.launch { vm.add(selected.id, null) }
                        }, contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("+1", style = MaterialTheme.typography.displayLarge, color = ink, fontWeight = FontWeight.Bold)
                                Text(if (english) "TAP TO COUNT" else "मोजण्यासाठी स्पर्श करा", color = Navy)
                            }
                        }
                        Spacer(Modifier.height(20.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            OutlinedButton(onClick = { scope.launch { vm.undo(selected.id) } }) { Text(if (english) "Undo −1" else "मागे घ्या −१") }
                            OutlinedButton(onClick = { resetDialog = true }) { Text(if (english) "Reset" else "रीसेट") }
                        }
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
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
    if (finishDialog && activeSession != null) {
        AlertDialog(
            onDismissRequest = { finishDialog = false },
            title = { Text(if (english) "Rate movement strength" else "बाळाच्या हालचालींची ताकद नोंदवा") },
            text = {
                Column {
                    Text(if (english) "How strong did the movements feel during this session?" else "या सत्रातील हालचालींची ताकद कशी जाणवली?")
                    listOf("LIGHT", "MEDIUM", "STRONG").forEach { value ->
                        val label = when (value) { "LIGHT" -> if (english) "Light" else "हलकी"; "MEDIUM" -> if (english) "Medium" else "मध्यम"; else -> if (english) "Strong" else "जोरात" }
                        Row(Modifier.fillMaxWidth().clickable { finalStrength = value }.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = finalStrength == value, onClick = { finalStrength = value })
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { scope.launch { vm.finishSession(activeSession.id, finalStrength) }; finishDialog = false }) { Text(if (english) "Save session" else "सत्र जतन करा") } },
            dismissButton = { TextButton(onClick = { finishDialog = false }) { Text(if (english) "Cancel" else "रद्द") } }
        )
    }
}

@Composable
private fun BabyMovementScreen(
    english: Boolean,
    ink: Color,
    tapSurface: Color,
    latestSession: BabySession?,
    activeSession: BabySession?,
    lockedSession: BabySession?,
    remainingMs: Long,
    sessions: List<BabySession>,
    historyDate: LocalDate,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    onStart: () -> Unit,
    onTap: (Long) -> Unit,
    onUndo: (Long) -> Unit,
    onFinish: () -> Unit
) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (activeSession != null) {
            val elapsed = (System.currentTimeMillis() - activeSession.startedAt).coerceAtLeast(0)
            Text(if (english) "BABY MOVEMENT SESSION" else "बाळाच्या हालचालींचे सत्र", color = ink.copy(alpha = .7f), style = MaterialTheme.typography.labelLarge)
            Text(activeSession.movementCount.toString(), color = ink, style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Bold)
            Text(if (english) "Movements recorded" else "हालचाली नोंदवल्या", color = ink)
            if (activeSession.movementCount >= 10) Text(if (english) "10 movements reached. Continue observing your baby’s usual pattern." else "१० हालचाली नोंदल्या. बाळाच्या नेहमीच्या हालचालींचे निरीक्षण सुरू ठेवा.", color = Gold, style = MaterialTheme.typography.bodySmall)
            Text("${periodLabel(activeSession.dayPeriod, english)}  •  ${sessionTime(activeSession.startedAt)}", color = Gold, style = MaterialTheme.typography.titleMedium)
            Text((if (english) "Session time " else "सत्राचा वेळ ") + "${elapsed / 60000}:${(elapsed / 1000 % 60).toString().padStart(2, '0')}", color = ink)
            Text((if (english) "Session window remaining " else "सत्रासाठी उर्वरित वेळ ") + "${remainingMs / 60000} min", color = ink.copy(alpha = .7f), style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.weight(.5f))
            Box(Modifier.size(236.dp).background(tapSurface, CircleShape).clickable { onTap(activeSession.id) }, contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("+1", style = MaterialTheme.typography.displayLarge, color = ink, fontWeight = FontWeight.Bold)
                    Text(if (english) "TAP FOR EACH MOVEMENT" else "प्रत्येक हालचालीसाठी स्पर्श करा", color = ink, style = MaterialTheme.typography.labelSmall)
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                OutlinedButton(onClick = { onUndo(activeSession.id) }, enabled = activeSession.movementCount > 0) { Text(if (english) "Undo −1" else "मागे घ्या −१") }
                Button(onClick = onFinish) { Text(if (english) "Finish session" else "सत्र पूर्ण करा") }
            }
            Spacer(Modifier.weight(.35f))
        } else {
            Spacer(Modifier.height(16.dp))
            Text(if (english) "Baby movements" else "बाळाची हालचाल", style = MaterialTheme.typography.titleLarge, color = ink, fontWeight = FontWeight.SemiBold)
            if (lockedSession != null) {
                Text(if (english) "This session is saved. A new session can start after its 2-hour window." else "हे सत्र जतन केले आहे. सुरुवातीपासून २ तास पूर्ण झाल्यावरच नवीन सत्र सुरू करता येईल.", color = ink, modifier = Modifier.padding(18.dp))
                Text("${periodLabel(lockedSession.dayPeriod, english)}  •  ${sessionTime(lockedSession.startedAt)}", color = Gold)
                Text(if (english) "Recorded movements: ${lockedSession.movementCount}" else "नोंदवलेल्या हालचाली: ${lockedSession.movementCount}", color = ink)
                val unlockAt = lockedSession.startedAt + CounterRepository.TWO_HOURS_MS
                Text((if (english) "Available after " else "पुन्हा उपलब्ध: ") + sessionTime(unlockAt), color = ink)
            } else {
                Text(if (english) "Choose a time when your baby is usually active. Count kicks, rolls and nudges; do not count hiccups." else "बाळ नेहमी सक्रिय असते ती वेळ निवडा. लाथा, वळणे आणि हलके धक्के मोजा; उचक्या मोजू नका.", color = ink, modifier = Modifier.padding(18.dp))
                Button(onClick = onStart, modifier = Modifier.padding(top = 12.dp)) { Text(if (english) "Start kick session" else "हालचालींचे सत्र सुरू करा") }
            }
            Spacer(Modifier.weight(1f))
        }

        Divider(color = ink.copy(alpha = .16f))
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(if (english) "Sessions by day" else "दिवसानुसार सत्रे", color = ink, fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onPreviousDay) { Text("‹") }
                Text(historyDate.format(dateFormat), color = ink, style = MaterialTheme.typography.labelMedium)
                TextButton(onClick = onNextDay, enabled = historyDate.isBefore(LocalDate.now())) { Text("›") }
            }
        }
        if (sessions.isEmpty()) {
            Text(if (english) "No sessions recorded for this day" else "या दिवशी कोणतेही सत्र नोंदलेले नाही", color = ink.copy(alpha = .6f), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp))
        } else {
            LazyColumn(Modifier.fillMaxWidth().weight(.9f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(sessions, key = { it.id }) { session ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(12.dp)) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("${periodLabel(session.dayPeriod, english)}  •  ${sessionTime(session.startedAt)}", color = ink, fontWeight = FontWeight.Medium)
                                Text(
                                    (if (english) "Duration: " else "कालावधी: ") +
                                        "${((session.endedAt ?: minOf(session.startedAt + CounterRepository.TWO_HOURS_MS, System.currentTimeMillis())) - session.startedAt).coerceAtLeast(0) / 60000} min" +
                                        (session.strength?.let { "  •  ${when(it) { "LIGHT" -> if (english) "Light" else "हलकी"; "MEDIUM" -> if (english) "Medium" else "मध्यम"; "STRONG" -> if (english) "Strong" else "जोरात"; else -> it }}" } ?: ""),
                                    color = ink.copy(alpha = .65f), style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Text(session.movementCount.toString(), color = Gold, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeeklyChart(values: List<Int>, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val maxValue = (values.maxOrNull() ?: 0).coerceAtLeast(1)
        val gap = size.width / 14f
        val barWidth = size.width / 18f
        values.forEachIndexed { index, value ->
            val height = size.height * value / maxValue.toFloat() * .85f
            val left = gap + index * gap * 2
            drawRoundRect(Gold, androidx.compose.ui.geometry.Offset(left, size.height - height), androidx.compose.ui.geometry.Size(barWidth, height), androidx.compose.ui.geometry.CornerRadius(12f))
        }
        drawLine(Navy.copy(alpha = .25f), androidx.compose.ui.geometry.Offset(0f, size.height), androidx.compose.ui.geometry.Offset(size.width, size.height), strokeWidth = 2f, cap = StrokeCap.Round)
    }
}
