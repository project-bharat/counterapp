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
        val categories = dao.allCategories()
        val logs = dao.allLogs()
        val sessions = dao.allBabySessions()
        val root = JSONObject().put("formatVersion", FORMAT_VERSION)
            .put("app", "Daily Counter")
            .put("exportedAt", System.currentTimeMillis())
        root.put("categories", JSONArray().apply {
            categories.forEach { c -> put(JSONObject().put("id", c.id).put("titleMr", c.titleMr).put("titleEn", c.titleEn).put("kind", c.kind).put("goal", c.goal).put("enabled", c.enabled)) }
        })
        root.put("logs", JSONArray().apply {
            logs.forEach { l -> put(JSONObject().put("id", l.id).put("date", l.date).put("timestamp", l.timestamp).put("categoryId", l.categoryId).put("count", l.count).putNullable("sessionDurationMs", l.sessionDurationMs).putNullable("strength", l.strength).putNullable("note", l.note).putNullable("sessionId", l.sessionId)) }
        })
        root.put("babySessions", JSONArray().apply {
            sessions.forEach { s -> put(JSONObject().put("id", s.id).put("categoryId", s.categoryId).put("date", s.date).put("startedAt", s.startedAt).putNullable("endedAt", s.endedAt).put("dayPeriod", s.dayPeriod).put("movementCount", s.movementCount).putNullable("timeToTenMs", s.timeToTenMs).putNullable("strength", s.strength).putNullable("note", s.note)) }
        })
        context.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use { it.write(root.toString(2)) }
            ?: error("Unable to create backup file")
    }

    suspend fun restoreJson(context: Context, uri: Uri) {
        val text = context.contentResolver.openInputStream(uri)?.use { input ->
            BufferedReader(InputStreamReader(input, Charsets.UTF_8)).readText()
        } ?: error("Unable to read backup file")
        val root = JSONObject(text)
        require(root.optInt("formatVersion", -1) == FORMAT_VERSION) { "Unsupported backup format" }
        val categoryArray = root.getJSONArray("categories")
        val logArray = root.getJSONArray("logs")
        val sessionArray = root.getJSONArray("babySessions")
        require(categoryArray.length() > 0) { "Backup contains no categories" }
        val categories = (0 until categoryArray.length()).map { i ->
            val o = categoryArray.getJSONObject(i)
            CounterCategory(o.getLong("id"), o.getString("titleMr"), o.getString("titleEn"), o.optString("kind", "GENERAL"), o.optInt("goal", 0), o.optBoolean("enabled", true))
        }
        val logs = (0 until logArray.length()).map { i ->
            val o = logArray.getJSONObject(i)
            CounterLog(o.getLong("id"), o.getString("date"), o.getLong("timestamp"), o.getLong("categoryId"), o.optInt("count", 1), o.longOrNull("sessionDurationMs"), o.stringOrNull("strength"), o.stringOrNull("note"), o.longOrNull("sessionId"))
        }
        val sessions = (0 until sessionArray.length()).map { i ->
            val o = sessionArray.getJSONObject(i)
            BabySession(o.getLong("id"), o.getLong("categoryId"), o.getString("date"), o.getLong("startedAt"), o.longOrNull("endedAt"), o.getString("dayPeriod"), o.optInt("movementCount", 0), o.longOrNull("timeToTenMs"), o.stringOrNull("strength"), o.stringOrNull("note"))
        }
        require(logs.all { it.count > 0 && it.categoryId in categories.map(CounterCategory::id) }) { "Backup contains invalid counter records" }
        require(sessions.all { it.categoryId in categories.map(CounterCategory::id) }) { "Backup contains invalid session records" }
        CounterDatabase.get(context).dao().restoreSnapshot(categories, logs, sessions)
    }

    suspend fun exportCsv(context: Context, uri: Uri) {
        val dao = CounterDatabase.get(context).dao()
        val categories = dao.allCategories().associateBy { it.id }
        val logs = dao.allLogs().sortedBy { it.timestamp }
        val output = context.contentResolver.openOutputStream(uri, "wt") ?: error("Unable to create CSV file")
        output.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write("date,timestamp,category_id,category_marathi,category_english,count,strength,note,session_id")
            writer.newLine()
            logs.forEach { log ->
                val category = categories[log.categoryId]
                val values = listOf(log.date, log.timestamp.toString(), log.categoryId.toString(), category?.titleMr.orEmpty(), category?.titleEn.orEmpty(), log.count.toString(), log.strength.orEmpty(), log.note.orEmpty(), log.sessionId?.toString().orEmpty())
                writer.write(values.joinToString(",") { csv(it) })
                writer.newLine()
            }
        }
    }

    private fun csv(value: String): String = "\"" + value.replace("\"", "\"\"") + "\""
    private fun JSONObject.putNullable(key: String, value: Any?): JSONObject = put(key, value ?: JSONObject.NULL)
    private fun JSONObject.longOrNull(key: String): Long? = if (isNull(key) || !has(key)) null else getLong(key)
    private fun JSONObject.stringOrNull(key: String): String? = if (isNull(key) || !has(key)) null else getString(key)
}
