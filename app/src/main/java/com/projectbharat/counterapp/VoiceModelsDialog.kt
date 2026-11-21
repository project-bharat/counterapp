package com.projectbharat.counterapp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

private data class VoiceModelStatus(
    val key: String,
    val title: String,
    val purpose: String,
    val installed: Boolean,
    val state: WorkInfo.State?,
    val progress: Int,
    val message: String
)

@Composable
fun VoiceModelsDialog(uiLanguage: UiLanguage, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var models by remember { mutableStateOf(emptyList<VoiceModelStatus>()) }
    var refreshing by remember { mutableStateOf(true) }

    LaunchedEffect(uiLanguage) {
        while (isActive) {
            val snapshot = withContext(Dispatchers.IO) {
                val workManager = WorkManager.getInstance(context)
                val definitions = listOf(
                    Triple("vosk_hi", if (uiLanguage.isEnglish) "Devanagari voice model"
                        else uiLanguage.indic("देवनागरी आवाज मॉडेल", "देवनागरी आवाज़ मॉडल"),
                        if (uiLanguage.isEnglish) "Used with either mantra display script"
                        else uiLanguage.indic("दोन्ही मंत्र लिप्यांसोबत वापरले जाते", "मंत्र की दोनों लिपियों के साथ इस्तेमाल होता है"))
                )
                definitions.map { (key, title, purpose) ->
                    val installed = when (key) {
                        "vosk_hi" -> VoskOfflineModelInstaller.isInstalled(context, "hi")
                        else -> VoskOfflineModelInstaller.isInstalled(context, "en")
                    }
                    val info = runCatching { workManager.getWorkInfosByTag(key).get().lastOrNull() }.getOrNull()
                    VoiceModelStatus(
                        key, title, purpose, installed, info?.state,
                        info?.progress?.getInt("progress", 0) ?: 0,
                        info?.progress?.getString("message").orEmpty()
                    )
                }
            }
            models = snapshot
            refreshing = false
            delay(900)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (uiLanguage.isEnglish) "Offline voice models" else uiLanguage.indic("ऑफलाइन आवाज मॉडेल", "ऑफ़लाइन आवाज़ मॉडल")) },
        text = {
            Column(
                Modifier.fillMaxWidth().height(390.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                models.forEach { model ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(model.title, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                                Text(
                                    when {
                                        model.installed -> if (uiLanguage.isEnglish) "Ready" else uiLanguage.indic("तयार", "तैयार")
                                        model.state == WorkInfo.State.RUNNING -> if (uiLanguage.isEnglish) "Working" else uiLanguage.indic("सुरू", "जारी")
                                        model.state == WorkInfo.State.ENQUEUED || model.state == WorkInfo.State.BLOCKED -> if (uiLanguage.isEnglish) "Queued" else uiLanguage.indic("रांगेत", "कतार में")
                                        model.state == WorkInfo.State.FAILED -> if (uiLanguage.isEnglish) "Failed" else uiLanguage.indic("अयशस्वी", "विफल")
                                        model.state == WorkInfo.State.SUCCEEDED -> if (uiLanguage.isEnglish) "Verifying" else uiLanguage.indic("तपासत आहे", "जाँच जारी है")
                                        else -> if (uiLanguage.isEnglish) "Not downloaded" else uiLanguage.indic("डाउनलोड नाही", "डाउनलोड नहीं है")
                                    },
                                    color = if (model.installed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(model.purpose, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (!model.installed && model.state == WorkInfo.State.RUNNING) {
                                LinearProgressIndicator(
                                    progress = { (model.progress / 100f).coerceIn(0f, 1f) },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Text(
                                    model.message.ifBlank { if (uiLanguage.isEnglish) "Preparing model…" else uiLanguage.indic("मॉडेल तयार होत आहे…", "मॉडल तैयार हो रहा है…") },
                                    style = MaterialTheme.typography.labelSmall
                                )
                            } else if (!model.installed && (model.state == WorkInfo.State.ENQUEUED || model.state == WorkInfo.State.BLOCKED)) {
                                Text(if (uiLanguage.isEnglish) "Waiting for the previous model to finish" else uiLanguage.indic("मागील मॉडेल पूर्ण होण्याची प्रतीक्षा", "पिछले मॉडल का इंतज़ार है"), style = MaterialTheme.typography.labelSmall)
                            } else if (!model.installed && model.state == WorkInfo.State.FAILED) {
                                Text(if (uiLanguage.isEnglish) "Download failed. Use Download all to retry." else uiLanguage.indic("डाउनलोड अयशस्वी. पुन्हा प्रयत्न करण्यासाठी सर्व डाउनलोड करा.", "डाउनलोड विफल रहा। फिर से कोशिश करने के लिए सभी मॉडल डाउनलोड करें।"), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
                if (refreshing && models.isEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator()
                        Text(if (uiLanguage.isEnglish) "Checking installed models…" else uiLanguage.indic("डाउनलोड तपासत आहे…", "डाउनलोड की जाँच जारी है…"))
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { SpeechModelDownloadScheduler.enqueueAll(context, wifiOnly = false) }) {
                Text(if (uiLanguage.isEnglish) "Download all models" else uiLanguage.indic("सर्व मॉडेल डाउनलोड करा", "सभी मॉडल डाउनलोड करें"))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text(if (uiLanguage.isEnglish) "Close" else uiLanguage.indic("बंद करा", "बंद करें")) }
        }
    )
}
