package com.projectbharat.counterapp

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun BackupSettingsScreen(
    english: Boolean,
    ink: Color,
    message: String?,
    busy: Boolean,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit,
    onExportCsv: () -> Unit
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(if (english) "Backup & sync" else "बॅकअप व सिंक", color = ink, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(if (english) "Your data stays on this device until you export it." else "निर्यात करेपर्यंत तुमचा डेटा याच उपकरणावर राहतो.", color = ink.copy(alpha = .65f), style = MaterialTheme.typography.bodySmall)
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Backup, contentDescription = null, tint = ink)
                    Text(if (english) "Device backup" else "उपकरणाचा बॅकअप", color = ink, fontWeight = FontWeight.SemiBold)
                }
                Text(if (english) "JSON includes counters, records and session records. Restore replaces current data." else "JSON मध्ये मोजण्या, नोंदी आणि सत्रांच्या नोंदी असतात. पुनर्संचयित केल्यास सध्याचा डेटा बदलतो.", color = ink.copy(alpha = .65f), style = MaterialTheme.typography.bodySmall)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Button(onClick = onExportBackup, enabled = !busy) {
                        Icon(Icons.Default.FileDownload, null); Spacer(Modifier.width(6.dp)); Text(if (english) "Export backup file" else "बॅकअप फाइल निर्यात")
                    }
                    OutlinedButton(onClick = onImportBackup, enabled = !busy) {
                        Icon(Icons.Default.FileUpload, null); Spacer(Modifier.width(6.dp)); Text(if (english) "Restore from file" else "फाइलमधून पुनर्संचयित")
                    }
                }
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text(if (english) "Spreadsheet export" else "स्प्रेडशीट निर्यात", color = ink, fontWeight = FontWeight.SemiBold)
                Text(if (english) "Export counter entries as CSV." else "मोजणीच्या नोंदी CSV स्वरूपात निर्यात करा.", color = ink.copy(alpha = .65f), style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = onExportCsv, enabled = !busy) {
                    Icon(Icons.Default.FileDownload, null); Spacer(Modifier.width(6.dp)); Text(if (english) "Export CSV" else "CSV निर्यात")
                }
            }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        DrivePreviewCard(english)
        if (!message.isNullOrBlank()) {
            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium) {
                Text(message, color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.padding(12.dp))
            }
        }
    }
}
