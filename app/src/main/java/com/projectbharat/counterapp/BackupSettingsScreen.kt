package com.projectbharat.counterapp

import androidx.compose.foundation.layout.*
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
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit,
    onExportCsv: () -> Unit
) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(if (english) "BACKUP & EXPORT" else "बॅकअप व निर्यात", color = ink, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(if (english) "Your data stays on this device unless you choose to export it." else "तुम्ही निर्यात करण्याचा निर्णय घेतल्याशिवाय तुमचा डेटा या उपकरणावरच राहतो.", color = ink.copy(alpha = .65f), style = MaterialTheme.typography.bodySmall)
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Default.Backup, contentDescription = null, tint = ink)
                    Text(if (english) "Full backup (JSON)" else "पूर्ण बॅकअप (JSON)", color = ink, fontWeight = FontWeight.SemiBold)
                }
                Text(if (english) "Includes counters, settings stored with categories, all counter records and baby movement sessions. Restoring replaces current app data." else "यामध्ये मोजणीचे प्रकार, श्रेणींमध्ये साठवलेली माहिती, सर्व नोंदी आणि बाळाच्या हालचालींची सत्रे असतात. पुनर्संचयित केल्यास सध्याचा डेटा बदलला जातो.", color = ink.copy(alpha = .65f), style = MaterialTheme.typography.bodySmall)
                Button(onClick = onExportBackup, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.FileDownload, null); Spacer(Modifier.width(7.dp)); Text(if (english) "Export full backup" else "पूर्ण बॅकअप निर्यात करा") }
                OutlinedButton(onClick = onImportBackup, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.FileUpload, null); Spacer(Modifier.width(7.dp)); Text(if (english) "Restore from backup" else "बॅकअपमधून पुनर्संचयित करा") }
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(if (english) "CSV history export" else "CSV इतिहास निर्यात", color = ink, fontWeight = FontWeight.SemiBold)
                Text(if (english) "Export counter log rows for spreadsheets. Baby session summaries are included in the full JSON backup." else "स्प्रेडशीटमध्ये वापरण्यासाठी मोजणीच्या नोंदी निर्यात करा. बाळाच्या सत्रांचा संपूर्ण तपशील JSON बॅकअपमध्ये समाविष्ट आहे.", color = ink.copy(alpha = .65f), style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = onExportCsv, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.FileDownload, null); Spacer(Modifier.width(7.dp)); Text(if (english) "Export CSV" else "CSV निर्यात करा") }
            }
        }
        if (!message.isNullOrBlank()) {
            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium) {
                Text(message, color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.padding(12.dp))
            }
        }
    }
}
