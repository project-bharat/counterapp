package com.projectbharat.counterapp

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

private data class VoiceModelStatus(val title: String, val installed: Boolean, val state: WorkInfo.State?, val progress: Int, val message: String)

@Composable
fun VoiceModelsDialog(english: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var models by remember { mutableStateOf(emptyList<VoiceModelStatus>()) }
    var refreshing by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        while (isActive) {
            models = withContext(Dispatchers.IO) {
                val wm = WorkManager.getInstance(context)
                listOf("vosk_hi" to "Hindi / Marathi / Sanskrit", "vosk_en" to "English").map { (key, title) ->
                    val lang = if (key == "vosk_hi") "hi" else "en"
                    val installed = VoskOfflineModelInstaller.isInstalled(context, lang)
                    val info = runCatching { wm.getWorkInfosByTag(key).get().lastOrNull() }.getOrNull()
                    VoiceModelStatus(title, installed, info?.state, info?.progress?.getInt("progress", 0) ?: 0, info?.progress?.getString("message").orEmpty())
                }
            }
            refreshing = false
            delay(1000)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (english) "Offline Japa voice models" else "जपासाठी ऑफलाइन आवाज मॉडेल") },
        text = {
            Column(Modifier.fillMaxWidth().height(300.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                models.forEach { model ->
                    Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(model.title, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                                Text(if (model.installed) if (english) "Ready" else "तयार" else when(model.state) {
                                    WorkInfo.State.RUNNING -> if (english) "Downloading" else "डाउनलोड"
                                    WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> if (english) "Queued" else "रांगेत"
                                    WorkInfo.State.FAILED -> if (english) "Failed" else "अयशस्वी"
                                    else -> if (english) "Not installed" else "इन्स्टॉल नाही"
                                }, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            }
                            if (!model.installed && model.state == WorkInfo.State.RUNNING) {
                                LinearProgressIndicator(progress = { model.progress / 100f }, Modifier.fillMaxWidth())
                                Text(model.message.ifBlank { if (english) "Downloading model…" else "मॉडेल डाउनलोड होत आहे…" }, style = MaterialTheme.typography.labelSmall)
                            }
                            if (!model.installed && model.state == WorkInfo.State.FAILED) Text(if (english) "Download failed. Retry using Download models." else "डाउनलोड अयशस्वी. पुन्हा डाउनलोड करा.", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                if (refreshing && models.isEmpty()) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(); Text(if (english) "Checking models…" else "मॉडेल तपासत आहे…")
                }
            }
        },
        confirmButton = { Button(onClick = { SpeechModelDownloadScheduler.enqueueAll(context, wifiOnly = false) }) { Text(if (english) "Download models" else "मॉडेल डाउनलोड") } },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text(if (english) "Close" else "बंद") } }
    )
}
