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
    english: Boolean,
    ink: Color,
    onAdd: (String, String) -> Unit,
    onUpdate: (CounterCategory) -> Unit,
    onArchive: (CounterCategory) -> Unit
) {
    var editing by remember { mutableStateOf<CounterCategory?>(null) }
    var archiving by remember { mutableStateOf<CounterCategory?>(null) }
    var adding by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(if (english) "MANAGE COUNTERS" else "मोजणीचे प्रकार", color = ink, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Button(onClick = { adding = true }) { Text(if (english) "Add" else "नवीन") }
        }
        Text(if (english) "Edit names or archive counters you no longer use. Existing records remain in history." else "नावे बदला किंवा न वापरणारे प्रकार संग्रहित करा. जुन्या नोंदी इतिहासात सुरक्षित राहतील.", color = ink.copy(alpha = .65f), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
            items(categories, key = { it.id }) { category ->
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(category.titleMr, color = ink, fontWeight = FontWeight.SemiBold)
                            Text(category.titleEn, color = ink.copy(alpha = .62f), style = MaterialTheme.typography.bodySmall)
                            Text(category.kind, color = ink.copy(alpha = .42f), style = MaterialTheme.typography.labelSmall)
                        }
                        IconButton(onClick = { editing = category }) { Icon(Icons.Default.Edit, contentDescription = if (english) "Edit" else "बदला", tint = ink) }
                        IconButton(onClick = { archiving = category }) { Icon(Icons.Default.Archive, contentDescription = if (english) "Archive" else "संग्रहित करा", tint = Color(0xFFB27632)) }
                    }
                }
            }
        }
    }
    if (adding) CategoryNameDialog(english, null, { mr, en -> onAdd(mr, en); adding = false }, { adding = false })
    editing?.let { item -> CategoryNameDialog(english, item, { mr, en -> onUpdate(item.copy(titleMr = mr, titleEn = en)); editing = null }, { editing = null }) }
    archiving?.let { item ->
        AlertDialog(
            onDismissRequest = { archiving = null },
            title = { Text(if (english) "Archive counter?" else "हा प्रकार संग्रहित करायचा?") },
            text = { Text(if (english) "The counter will disappear from the active list. Its saved history will remain." else "हा प्रकार सक्रिय यादीतून काढला जाईल. त्याचा जुना इतिहास सुरक्षित राहील.") },
            confirmButton = { TextButton(onClick = { onArchive(item); archiving = null }) { Text(if (english) "Archive" else "संग्रहित करा") } },
            dismissButton = { TextButton(onClick = { archiving = null }) { Text(if (english) "Cancel" else "रद्द") } }
        )
    }
}

@Composable
private fun CategoryNameDialog(
    english: Boolean,
    category: CounterCategory?,
    onSave: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var mr by remember(category?.id) { mutableStateOf(category?.titleMr.orEmpty()) }
    var en by remember(category?.id) { mutableStateOf(category?.titleEn.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (category == null) { if (english) "New counter" else "नवीन मोजणी" } else { if (english) "Edit counter" else "मोजणी बदला" }) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(mr, { mr = it }, label = { Text("मराठी नाव") }, singleLine = true)
                OutlinedTextField(en, { en = it }, label = { Text("English name") }, singleLine = true)
            }
        },
        confirmButton = { TextButton(onClick = { if (mr.isNotBlank() && en.isNotBlank()) onSave(mr.trim(), en.trim()) }) { Text(if (english) "Save" else "जतन करा") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(if (english) "Cancel" else "रद्द") } }
    )
}
