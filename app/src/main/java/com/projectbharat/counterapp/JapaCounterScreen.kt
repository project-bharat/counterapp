package com.projectbharat.counterapp

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun JapaCounterScreen(
    categoryId: Long,
    count: Int,
    totalCount: Int,
    mantra: String,
    english: Boolean,
    ink: Color,
    voiceActive: Boolean,
    onMantraChange: (String) -> Unit,
    onManualCount: (String) -> Unit,
    onUndo: () -> Unit,
    onReset: () -> Unit,
    onVoiceToggle: (Boolean) -> Unit
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(if (english) "JAPA / MANTRA" else "जप / मंत्र", color = ink, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        OutlinedTextField(value = mantra, onValueChange = onMantraChange, label = { Text(if (english) "Mantra" else "मंत्र") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(vertical = 22.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(count.toString(), style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.ExtraBold, color = ink)
                Text(if (english) "TODAY" else "आजचा जप", color = ink.copy(alpha = .65f), style = MaterialTheme.typography.labelLarge)
                Text((if (english) "All-time: " else "एकूण: ") + totalCount, color = ink.copy(alpha = .7f), style = MaterialTheme.typography.bodyMedium)
            }
        }
        Button(onClick = { onManualCount(mantra) }, modifier = Modifier.fillMaxWidth().height(68.dp), enabled = mantra.isNotBlank()) {
            Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text(if (english) "Count one" else "एक जप मोजा", style = MaterialTheme.typography.titleMedium)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onUndo, modifier = Modifier.weight(1f), enabled = count > 0) { Icon(Icons.Default.Remove, null); Text(if (english) "Undo" else "मागे") }
            OutlinedButton(onClick = onReset, modifier = Modifier.weight(1f), enabled = count > 0) { Icon(Icons.Default.Refresh, null); Text(if (english) "Reset today" else "आज रीसेट") }
        }
        OutlinedButton(onClick = { onVoiceToggle(voiceActive) }, modifier = Modifier.fillMaxWidth().height(54.dp)) {
            Icon(if (voiceActive) Icons.Default.MicOff else Icons.Default.Mic, null)
            Spacer(Modifier.width(8.dp))
            Text(if (voiceActive) if (english) "Stop voice counting" else "आवाज मोजणी थांबवा" else if (english) "Start voice counting" else "आवाज मोजणी सुरू करा")
        }
        Text(if (english) "Voice counting works offline after its model is installed." else "आवाज मोजणीचे मॉडेल इन्स्टॉल झाल्यानंतर ही सुविधा ऑफलाइन चालते.", color = ink.copy(alpha = .6f), style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
    }
}
