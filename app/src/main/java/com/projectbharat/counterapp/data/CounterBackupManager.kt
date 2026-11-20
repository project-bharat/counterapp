package com.projectbharat.counterapp.data

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

object CounterBackupManager {
    private const val FORMAT_VERSION = 1
    suspend fun exportJson(context: Context, uri: Uri) {
        val dao = CounterDatabase.get(context).dao()
        val categories = dao.allCategories().filter { it.kind == "MANTRA" }
        val categoryIds = categories.map { it.id }.toSet()
        val logs = dao.allLogs().filter { it.categoryId in categoryIds && (it.note == null || it.note.startsWith("MANTRA:")) }
        val root = JSONObject().put("formatVersion", FORMAT_VERSION).put("app", "Japa Counter").put("exportedAt", System.currentTimeMillis())
        root.put("categories", JSONArray().apply { categories.forEach { c ->
            put(JSONObject().put("id", c.id).put("titleMr", c.titleMr).put("titleEn", c.titleEn).put("kind", c.kind).put("goal", c.goal).put("enabled", c.enabled))
        } })
        root.put("logs", JSONArray().apply { logs.forEach { l ->
            put(JSONObject().put("id", l.id).put("date", l.date).put("timestamp", l.timestamp).put("categoryId", l.categoryId)
                .put("count", l.count).putNullable("sessionDurationMs", l.sessionDurationMs).putNullable("strength", l.strength).putNullable("note", l.note))
        } })
        // Empty legacy arrays maintain compatibility with older version-1 backup readers.
        root.put("babySessions", JSONArray()).put("babyDetections", JSONArray()).put("walkingSessions", JSONArray())
        context.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use { it.write(root.toString(2)) } ?: error("Unable to create backup file")
    }

    suspend fun restoreJson(context: Context, uri: Uri) {
        val text = context.contentResolver.openInputStream(uri)?.use { BufferedReader(InputStreamReader(it, Charsets.UTF_8)).readText() } ?: error("Unable to read backup file")
        val root = JSONObject(text)
        require(root.optInt("formatVersion", -1) == FORMAT_VERSION) { "Unsupported backup format" }
        val categoryArray = root.getJSONArray("categories")
        val logArray = root.getJSONArray("logs")
        val importedCategories = (0 until categoryArray.length()).map { i ->
            val o = categoryArray.getJSONObject(i)
            CounterCategory(o.getLong("id"), o.getString("titleMr"), o.getString("titleEn"), o.optString("kind", "GENERAL"), o.optInt("goal", 0), o.optBoolean("enabled", true))
        }.filter { it.kind == "MANTRA" }
        require(importedCategories.isNotEmpty()) { "Backup contains no Japa counter" }
        val ids = importedCategories.map { it.id }.toSet()
        val importedLogs = (0 until logArray.length()).map { i ->
            val o = logArray.getJSONObject(i)
            CounterLog(o.getLong("id"), o.getString("date"), o.getLong("timestamp"), o.getLong("categoryId"),
                o.optInt("count", 1), o.longOrNull("sessionDurationMs"), o.stringOrNull("strength"), o.stringOrNull("note"))
        }.filter { it.categoryId in ids && (it.note == null || it.note.startsWith("MANTRA:")) }
        BackupSnapshotValidator.validate(importedCategories, importedLogs)
        CounterDatabase.get(context).dao().restoreSnapshot(importedCategories, importedLogs)
    }

    suspend fun exportCsv(context: Context, uri: Uri) {
        val dao = CounterDatabase.get(context).dao()
        val categories = dao.allCategories().filter { it.kind == "MANTRA" }.associateBy { it.id }
        val logs = dao.allLogs().filter { it.categoryId in categories.keys && (it.note == null || it.note.startsWith("MANTRA:")) }.sortedBy { it.timestamp }
        val output = context.contentResolver.openOutputStream(uri, "wt") ?: error("Unable to create CSV file")
        output.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write('\uFEFF'.code)
            writer.write("date,timestamp,category_id,category_marathi,category_english,count,strength,note")
            writer.newLine()
            logs.forEach { log ->
                val category = categories[log.categoryId]
                val values = listOf(log.date, log.timestamp.toString(), log.categoryId.toString(), category?.titleMr.orEmpty(), category?.titleEn.orEmpty(), log.count.toString(), log.strength.orEmpty(), log.note.orEmpty())
                writer.write(values.joinToString(",") { csv(it) }); writer.newLine()
            }
        }
    }
    private fun csv(value: String): String = "\"" + value.replace("\"", "\"\"") + "\""
    private fun JSONObject.putNullable(key: String, value: Any?): JSONObject = put(key, value ?: JSONObject.NULL)
    private fun JSONObject.longOrNull(key: String): Long? = if (!has(key) || isNull(key)) null else getLong(key)
    private fun JSONObject.stringOrNull(key: String): String? = if (!has(key) || isNull(key)) null else getString(key)
}
