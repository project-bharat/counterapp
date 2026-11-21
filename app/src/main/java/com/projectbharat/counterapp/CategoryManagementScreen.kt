package com.projectbharat.counterapp

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.projectbharat.counterapp.data.CounterCategory

@Composable
fun CategoryManagementScreen(
    categories: List<CounterCategory>,
    archivedCategories: List<CounterCategory>,
    uiLanguage: UiLanguage,
    ink: Color,
    onAdd: (String, String) -> Unit,
    onUpdate: (CounterCategory) -> Unit,
    onArchive: (CounterCategory) -> Unit,
    onRestore: (CounterCategory) -> Unit
) {
    var editing by remember { mutableStateOf<CounterCategory?>(null) }
    var archiving by remember { mutableStateOf<CounterCategory?>(null) }
    var adding by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(if (uiLanguage.isEnglish) "MANAGE COUNTERS" else uiLanguage.indic("मोजणीचे प्रकार", "काउंटर के प्रकार"), color = ink, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Button(onClick = { adding = true }) { Text(if (uiLanguage.isEnglish) "Add" else uiLanguage.indic("नवीन", "नया")) }
        }
        Text(if (uiLanguage.isEnglish) "Edit names or archive counters you no longer use. Existing records remain in history." else uiLanguage.indic("नावे बदला किंवा न वापरणारे प्रकार संग्रहित करा. जुन्या नोंदी इतिहासात सुरक्षित राहतील.", "नाम बदलें या अनुपयोगी काउंटर संग्रहित करें। पुराने रिकॉर्ड इतिहास में सुरक्षित रहेंगे।"), color = ink.copy(alpha = .65f), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
            items(categories, key = { it.id }) { category ->
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(category.titleMr, color = ink, fontWeight = FontWeight.SemiBold)
                            Text(category.titleEn, color = ink.copy(alpha = .62f), style = MaterialTheme.typography.bodySmall)
                            Text(category.kind, color = ink.copy(alpha = .42f), style = MaterialTheme.typography.labelSmall)
                        }
                        IconButton(onClick = { editing = category }) { Icon(Icons.Default.Edit, contentDescription = if (uiLanguage.isEnglish) "Edit" else uiLanguage.indic("बदला", "बदलें"), tint = ink) }
                        IconButton(onClick = { archiving = category }) { Icon(Icons.Default.Archive, contentDescription = if (uiLanguage.isEnglish) "Archive" else uiLanguage.indic("संग्रहित करा", "संग्रहित करें"), tint = Color(0xFFB27632)) }
                    }
                }
            }
            if (archivedCategories.isNotEmpty()) {
                item {
                    Text(if (uiLanguage.isEnglish) "ARCHIVED" else "संग्रहित", color = ink, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                }
                items(archivedCategories, key = { "archived-${it.id}" }) { category ->
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(category.titleMr, color = ink, fontWeight = FontWeight.SemiBold)
                                Text(category.titleEn, color = ink.copy(alpha = .62f), style = MaterialTheme.typography.bodySmall)
                            }
                            TextButton(onClick = { onRestore(category) }) { Text(if (uiLanguage.isEnglish) "Restore" else uiLanguage.indic("पुन्हा वापरा", "फिर से सक्रिय करें")) }
                        }
                    }
                }
            }
        }
    }
    if (adding) CategoryNameDialog(uiLanguage, null, { mr, en -> onAdd(mr, en); adding = false }, { adding = false })
    editing?.let { item -> CategoryNameDialog(uiLanguage, item, { mr, en -> onUpdate(item.copy(titleMr = mr, titleEn = en)); editing = null }, { editing = null }) }
    archiving?.let { item ->
        AlertDialog(
            onDismissRequest = { archiving = null },
            title = { Text(if (uiLanguage.isEnglish) "Archive counter?" else uiLanguage.indic("हा प्रकार संग्रहित करायचा?", "इस काउंटर को संग्रहित करें?")) },
            text = { Text(if (uiLanguage.isEnglish) "The counter will disappear from the active list. Its saved history will remain." else uiLanguage.indic("हा प्रकार सक्रिय यादीतून काढला जाईल. त्याचा जुना इतिहास सुरक्षित राहील.", "यह काउंटर सक्रिय सूची से हट जाएगा। इसका इतिहास सुरक्षित रहेगा।")) },
            confirmButton = { TextButton(onClick = { onArchive(item); archiving = null }) { Text(if (uiLanguage.isEnglish) "Archive" else uiLanguage.indic("संग्रहित करा", "संग्रहित करें")) } },
            dismissButton = { TextButton(onClick = { archiving = null }) { Text(if (uiLanguage.isEnglish) "Cancel" else uiLanguage.indic("रद्द", "रद्द करें")) } }
        )
    }
}

@Composable
private fun CategoryNameDialog(
    uiLanguage: UiLanguage,
    category: CounterCategory?,
    onSave: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var mr by remember(category?.id) { mutableStateOf(category?.titleMr.orEmpty()) }
    var en by remember(category?.id) { mutableStateOf(category?.titleEn.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (category == null) { if (uiLanguage.isEnglish) "New counter" else uiLanguage.indic("नवीन मोजणी", "नया काउंटर") } else { if (uiLanguage.isEnglish) "Edit counter" else uiLanguage.indic("मोजणी बदला", "काउंटर संपादित करें") }) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(mr, { mr = it }, label = { Text(uiLanguage.indic("मराठी नाव", "मराठी नाम")) }, singleLine = true)
                OutlinedTextField(en, { en = it }, label = { Text("English name") }, singleLine = true)
            }
        },
        confirmButton = { TextButton(onClick = { if (mr.isNotBlank() && en.isNotBlank()) onSave(mr.trim(), en.trim()) }) { Text(if (uiLanguage.isEnglish) "Save" else uiLanguage.indic("जतन करा", "सहेजें")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(if (uiLanguage.isEnglish) "Cancel" else uiLanguage.indic("रद्द", "रद्द करें")) } }
    )
}
