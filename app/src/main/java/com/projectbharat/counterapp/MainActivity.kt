package com.projectbharat.counterapp

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
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
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate

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

@Composable
fun CounterApp(repo: CounterRepository) {
    val vm: CounterViewModel = viewModel(factory = CounterViewModel.Factory(repo))
    val categories by vm.categories.collectAsStateWithLifecycle()
    var selectedId by remember { mutableLongStateOf(1L) }
    var english by remember { mutableStateOf(false) }
    var weekly by remember { mutableStateOf(false) }
    var addDialog by remember { mutableStateOf(false) }
    var resetDialog by remember { mutableStateOf(false) }
    var strength by remember { mutableStateOf<String?>(null) }
    var timerStart by remember { mutableLongStateOf(0L) }
    var elapsed by remember { mutableLongStateOf(0L) }
    var timerRunning by remember { mutableStateOf(false) }
    var todayKey by remember { mutableStateOf(LocalDate.now().toString()) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val selected = categories.firstOrNull { it.id == selectedId } ?: categories.firstOrNull()
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(60_000)
            todayKey = LocalDate.now().toString()
        }
    }
    LaunchedEffect(categories) { if (categories.isNotEmpty() && categories.none { it.id == selectedId }) selectedId = categories.first().id }
    LaunchedEffect(timerRunning) {
        while (timerRunning) {
            kotlinx.coroutines.delay(1000)
            elapsed = System.currentTimeMillis() - timerStart
        }
    }
    val countFlow = remember(selected?.id, todayKey) { selected?.let { vm.count(it.id) } ?: flowOf(0) }
    val weekFlow = remember(selected?.id, todayKey) { selected?.let { vm.week(it.id) } ?: flowOf(emptyList()) }
    val averageFlow = remember(selected?.id, todayKey) { selected?.let { vm.average(it.id) } ?: flowOf(null) }
    val count by countFlow.collectAsStateWithLifecycle(0)
    val days by weekFlow.collectAsStateWithLifecycle(emptyList())
    val average by averageFlow.collectAsStateWithLifecycle(null)
    val isBaby = selected?.kind == "BABY"
    val dark = isSystemInDarkTheme()
    val page = if (dark) Color(0xFF0D1B2A) else Cream
    val ink = if (dark) Color(0xFFEAF0F6) else Navy
    val tapSurface = if (dark) Color(0xFF23466A) else SoftBlue
    val scheme = if (dark) darkColorScheme(primary = Color(0xFF9FC7ED), secondary = Gold, background = page, surface = Color(0xFF172A3E))
                 else lightColorScheme(primary = Navy, secondary = Gold, background = page, surface = Color.White)
    MaterialTheme(colorScheme = scheme) {
        Surface(Modifier.fillMaxSize(), color = page) {
            Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(if (english) "Daily Counter" else "दैनिक मोजणी", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = ink)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { english = !english }) { Icon(Icons.Default.Language, contentDescription = "Language") }
                        IconButton(onClick = { weekly = !weekly }) { Icon(Icons.Default.BarChart, contentDescription = "Weekly report") }
                    }
                }
                Spacer(Modifier.height(18.dp))
                if (categories.isNotEmpty()) {
                    var expanded by remember { mutableStateOf(false) }
                    Box {
                        OutlinedButton(onClick = { expanded = true }, shape = RoundedCornerShape(16.dp)) {
                            Text(if (english) selected?.titleEn ?: "" else selected?.titleMr ?: "")
                        }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            categories.forEach { cat -> DropdownMenuItem(text = { Text(if (english) cat.titleEn else cat.titleMr) }, onClick = { selectedId = cat.id; strength = null; weekly = false; expanded = false }) }
                            DropdownMenuItem(text = { Text(if (english) "+ Add category" else "+ नवीन प्रकार") }, onClick = { expanded = false; addDialog = true })
                        }
                    }
                }
                if (weekly) {
                    Text(if (english) "Last 7 days" else "मागील ७ दिवस", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 22.dp))
                    Spacer(Modifier.height(20.dp))
                    WeeklyChart(days.map { it.total }, Modifier.fillMaxWidth().height(210.dp))
                    Text(if (english) "Daily total" else "दररोजची मोजणी", color = ink)
                    if (isBaby) Text((if (english) "Average session: " else "सरासरी सत्र: ") + (average?.let { "%.1f min".format(it / 60000.0) } ?: "—"))
                    Spacer(Modifier.height(18.dp))
                    Button(onClick = {
                        val lines = buildString {
                            append(if (english) "Last 7 days\n" else "मागील ७ दिवसांची नोंद\n")
                            days.forEach { append("${it.date}: ${it.total}\n") }
                            append(if (english) "Total: " else "एकूण: ")
                            append(days.sumOf { it.total })
                        }
                        val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, lines) }
                        context.startActivity(Intent.createChooser(send, if (english) "Share summary" else "सारांश शेअर करा"))
                    }) { Text(if (english) "Share summary" else "सारांश शेअर करा") }
                } else {
                    Spacer(Modifier.height(18.dp))
                    Text(if (english) "TODAY" else "आज", color = ink.copy(alpha = .65f), style = MaterialTheme.typography.labelLarge)
                    Text(count.toString(), color = ink, style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Bold)
                    if (selected?.kind == "MANTRA") Text("${count / 108} माळ  •  ${count % 108}/108", color = Gold, style = MaterialTheme.typography.titleMedium)
                    if ((selected?.goal ?: 0) > 0) Text("${count} / ${selected?.goal} ${if (english) "goal" else "लक्ष्य"}", color = ink)
                    Spacer(Modifier.weight(1f))
                    Box(Modifier.size(252.dp).background(tapSurface, CircleShape).clickable(enabled = selected != null) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        scope.launch { selected?.let { vm.add(it.id, strength, if (timerStart > 0L && isBaby) System.currentTimeMillis() - timerStart else null) } }
                        if (isBaby) strength = null
                    }, contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("+1", style = MaterialTheme.typography.displayLarge, color = ink, fontWeight = FontWeight.Bold)
                            Text(if (english) "TAP TO COUNT" else "मोजण्यासाठी स्पर्श करा", color = Navy, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    if (isBaby) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("हलकी", "मध्यम", "जोरात").forEachIndexed { index, label ->
                                FilterChip(selected = strength == label, onClick = { strength = if (strength == label) null else label }, label = { Text(if (english) listOf("Light","Medium","Strong")[index] else label) })
                            }
                        }
                    }
                    if (isBaby) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Timer, contentDescription = null, tint = ink)
                            Text(if (timerRunning) "${elapsed / 60000}:${(elapsed / 1000 % 60).toString().padStart(2,'0')}" else if (english) "Session timer" else "सत्र वेळ")
                            TextButton(onClick = {
                                if (timerRunning) { elapsed = System.currentTimeMillis() - timerStart; timerRunning = false }
                                else { timerStart = System.currentTimeMillis(); elapsed = 0; timerRunning = true }
                            }) { Text(if (timerRunning) if (english) "Stop" else "थांबवा" else if (english) "Start" else "सुरू करा") }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        OutlinedButton(onClick = { scope.launch { selected?.let { vm.undo(it.id) } } }) { Text(if (english) "Undo −1" else "मागे घ्या −१") }
                        OutlinedButton(onClick = { resetDialog = true }) { Text(if (english) "Reset" else "रीसेट") }
                    }
                    Spacer(Modifier.weight(0.7f))
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
            drawRoundRect(Color(0xFFE2A13C), androidx.compose.ui.geometry.Offset(left, size.height - height), androidx.compose.ui.geometry.Size(barWidth, height), androidx.compose.ui.geometry.CornerRadius(12f))
        }
        drawLine(Navy.copy(alpha = .25f), androidx.compose.ui.geometry.Offset(0f, size.height), androidx.compose.ui.geometry.Offset(size.width, size.height), strokeWidth = 2f, cap = StrokeCap.Round)
    }
}
