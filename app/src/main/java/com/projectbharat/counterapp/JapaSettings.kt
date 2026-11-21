package com.projectbharat.counterapp

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

internal fun shareJapaText(context: Context, title: String, message: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain")
        .putExtra(Intent.EXTRA_SUBJECT, title).putExtra(Intent.EXTRA_TEXT, message)
    context.startActivity(Intent.createChooser(send, title))
}

@Composable
internal fun JapaSettingsScreen(
    english: Boolean, tap: Boolean, mala: Boolean, sound: Boolean,
    onLanguage: () -> Unit, onTap: (Boolean) -> Unit, onMala: (Boolean) -> Unit,
    onSound: (Boolean) -> Unit, onReminders: () -> Unit, onModels: () -> Unit,
    onBackup: () -> Unit, onCounters: () -> Unit, onHelp: () -> Unit,
    counterNames: List<String>, selectedCounter: Int, onCounter: (Int) -> Unit
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingsGroup(if (english) "Practice preferences" else "जप प्राधान्ये") {
            PreferenceSwitch(if (english) "Tap vibration" else "स्पर्श कंपन", tap, onTap)
            PreferenceSwitch(if (english) "Mala completion vibration" else "माळ पूर्ण झाल्यावर कंपन", mala, onMala)
            PreferenceSwitch(if (english) "Tap sound" else "स्पर्श ध्वनी", sound, onSound)
            SettingsLink(if (english) "Language · English" else "भाषा · मराठी", onLanguage)
        }
        SettingsGroup(if (english) "Reminders and data" else "स्मरणपत्रे व डेटा") {
            SettingsLink(if (english) "Reminders" else "स्मरणपत्रे", onReminders)
            SettingsLink(if (english) "Offline voice models" else "ऑफलाइन आवाज मॉडेल", onModels)
            SettingsLink(if (english) "Backup & sync" else "बॅकअप व सिंक", onBackup)
        }
        SettingsGroup(if (english) "Counters" else "मोजण्या") {
            counterNames.forEachIndexed { index, name ->
                SettingsLink(if (index == selectedCounter) "$name ✓" else name) { onCounter(index) }
            }
            SettingsLink(if (english) "Manage counters" else "मोजणी व्यवस्थापन", onCounters)
        }
        SettingsGroup(if (english) "Support" else "मदत") {
            SettingsLink(if (english) "Help & About" else "मदत व अॅपची माहिती", onHelp)
        }
    }
}

@Composable
internal fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 6.dp))
            content()
        }
    }
}

@Composable
internal fun SettingsLink(title: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onClick).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f))
        Text("›")
    }
}

@Composable
internal fun PreferenceSwitch(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, Modifier.weight(1f))
        Switch(checked, onChange)
    }
}

@Composable
internal fun JapaHelpScreen(english: Boolean) {
    val context = LocalContext.current
    val version = remember(context) {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingsGroup(if (english) "Voice counting" else "आवाज मोजणी") {
            Text(if (english) "Allow microphone access and install an offline model. You can always count by tapping."
                else "मायक्रोफोनची परवानगी द्या व ऑफलाइन मॉडेल डाउनलोड करा. स्पर्श करून जप मोजता येतात.")
        }
        SettingsGroup(if (english) "Data and privacy" else "डेटा व गोपनीयता") {
            Text(if (english) "Voice is processed on the device. Export a backup before changing phones. Restoring a file replaces local records."
                else "आवाजाची प्रक्रिया उपकरणावर होते. फोन बदलण्यापूर्वी बॅकअप घ्या. फाइल पुनर्संचयित केल्यावर स्थानिक नोंदी बदलतात.")
        }
        Text(if (english) "Bilan Japa Counter · $version" else "बिलान जप मोजणी · $version")
    }
}

@Composable
internal fun DrivePreviewCard(english: Boolean) {
    val context = LocalContext.current
    val debuggable = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    var preview by remember { mutableStateOf(false) }
    var state by remember { mutableIntStateOf(0) }
    var autoSync by remember { mutableStateOf(false) }
    SettingsGroup(if (english) "Google Drive Cloud Sync · Pro" else "Google Drive क्लाउड सिंक · Pro") {
        Text(if (english) "Private backup and restore on your Google Drive." else "तुमच्या Google Drive वर खाजगी बॅकअप व पुनर्संचयित.")
        if (debuggable) {
            TextButton(onClick = { preview = true }) { Text(if (english) "Upgrade to Pro" else "Pro घ्या") }
        }
    }
    if (debuggable && preview) {
        val labels = if (english) listOf("Upgrade", "Link account", "Connected", "Syncing", "Error", "Conflict")
            else listOf("अपग्रेड", "खाते जोडा", "जोडले आहे", "सिंक सुरू", "त्रुटी", "विरोध")
        AlertDialog(
            onDismissRequest = { preview = false },
            title = { Text(if (english) "Japa Plus · Debug preview" else "Japa Plus · चाचणी पूर्वदृश्य") },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    var menu by remember { mutableStateOf(false) }
                    Box {
                        OutlinedButton(onClick = { menu = true }) { Text(labels[state]) }
                        DropdownMenu(menu, { menu = false }) {
                            labels.forEachIndexed { index, label ->
                                DropdownMenuItem(text = { Text(label) }, onClick = { state = index; menu = false })
                            }
                        }
                    }
                    when (state) {
                        0 -> {
                            Text(if (english) "Automatic private backups, Sync Now, and cloud restore." else "स्वयंचलित खाजगी बॅकअप, सिंक व क्लाउड पुनर्संचयित.")
                            Button(onClick = { state = 1 }) { Text(if (english) "Preview account setup" else "खाते सेटअप पहा") }
                        }
                        1 -> Button(onClick = { state = 2 }) {
                            Text(if (english) "Connect Google Account" else "Google खाते जोडा")
                        }
                        else -> {
                            Text("preview@example.com")
                            TextButton(onClick = { state = 1; autoSync = false }) { Text(if (english) "Disconnect" else "खाते वेगळे करा") }
                            PreferenceSwitch(if (english) "Auto-Sync" else "स्वयंचलित सिंक", autoSync) { autoSync = it }
                            Text(if (english) "Last synced · 21 Nov 2026, 10:30" else "शेवटचा सिंक · २१ नोव्हेंबर २०२६, १०:३०")
                            Button(onClick = { state = 3 }, enabled = state != 3) { Text(if (english) "Sync Now" else "आता सिंक करा") }
                            OutlinedButton(onClick = { state = 4 }, enabled = state != 3) { Text(if (english) "Restore from Cloud" else "क्लाउडवरून पुनर्संचयित") }
                            if (state == 3) {
                                LinearProgressIndicator(Modifier.fillMaxWidth())
                                TextButton(onClick = { state = 2 }) { Text(if (english) "Preview success" else "यशस्वी स्थिती पहा") }
                            }
                            if (state == 4) Text(if (english) "Cloud access needs authorization." else "क्लाउड प्रवेशासाठी परवानगी आवश्यक आहे.")
                            if (state == 5) Text(if (english) "Local and Drive backups both changed. Review both before continuing." else "स्थानिक व Drive बॅकअप बदलले आहेत. पुढे जाण्यापूर्वी दोन्ही तपासा.")
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { preview = false }) { Text(if (english) "Close" else "बंद करा") } }
        )
    }
}
