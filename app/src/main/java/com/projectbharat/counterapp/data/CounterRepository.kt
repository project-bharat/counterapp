package com.projectbharat.counterapp.data

import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

class CounterRepository(private val dao: CounterDao) {
    val categories = dao.categories()
    val archivedCategories = dao.archivedCategories()
    suspend fun addCategory(mr: String, en: String) = dao.addCategory(CounterCategory(titleMr = mr.trim(), titleEn = en.trim()))
    suspend fun updateCategory(category: CounterCategory) {
        require(category.titleMr.isNotBlank() && category.titleEn.isNotBlank()) { "Both category names are required" }
        dao.updateCategory(category.copy(titleMr = category.titleMr.trim(), titleEn = category.titleEn.trim()))
    }
    suspend fun archiveCategory(id: Long) {
        require(dao.allCategories().count { it.enabled } > 1) { "Keep at least one active counter" }
        dao.disableCategory(id)
    }
    suspend fun restoreCategory(id: Long) = dao.enableCategory(id)
    fun today(categoryId: Long): Flow<Int> = dao.todayCount(categoryId, LocalDate.now().toString())
    fun logsForDay(categoryId: Long, date: LocalDate): Flow<List<CounterLog>> = dao.logsForDay(categoryId, date.toString())
    fun mantraLogsForDay(categoryId: Long, date: LocalDate): Flow<List<CounterLog>> = dao.mantraLogsForDay(categoryId, date.toString())
    fun allMantraLogs(categoryId: Long): Flow<List<CounterLog>> = dao.allMantraLogs(categoryId)
    fun mantraToday(categoryId: Long, mantra: String): Flow<Int> = dao.mantraTodayCount(categoryId, LocalDate.now().toString(), "MANTRA:" + mantra)
    fun mantraAllTime(categoryId: Long, mantra: String): Flow<Int> = dao.mantraAllTimeCount(categoryId, "MANTRA:" + mantra)
    fun mantraWeek(categoryId: Long, mantra: String): Flow<List<DayTotal>> {
        val today = LocalDate.now()
        return dao.mantraWeeklyTotals(categoryId, today.minusDays(6).toString(), today.toString(), "MANTRA:" + mantra)
    }
    suspend fun addMantraCount(categoryId: Long, mantra: String) {
        require(mantra.isNotBlank()) { "Choose a mantra first" }
        dao.incrementMantraDaily(categoryId, LocalDate.now().toString(), mantra.trim(), 1)
    }
    fun logsForRange(categoryId: Long, from: LocalDate, to: LocalDate): Flow<List<CounterLog>> = dao.logsForRange(categoryId, from.toString(), to.toString())
    suspend fun updateLog(log: CounterLog) {
        require(log.count > 0) { "Count must be positive" }
        dao.updateLog(log.copy(note = log.note?.trim()?.takeIf { it.isNotEmpty() }))
    }
    suspend fun deleteLog(id: Long) = dao.deleteLog(id)
    suspend fun add(categoryId: Long, strength: String? = null, duration: Long? = null): Long {
        val date = LocalDate.now().toString()
        return dao.addLog(CounterLog(date = date, timestamp = System.currentTimeMillis(), categoryId = categoryId, strength = strength, sessionDurationMs = duration))
    }
    suspend fun addCount(categoryId: Long, amount: Int) {
        if (amount <= 0) return
        dao.addLog(CounterLog(date = LocalDate.now().toString(), timestamp = System.currentTimeMillis(), categoryId = categoryId, count = amount))
    }
    suspend fun undo(categoryId: Long) {
        dao.latestLog(categoryId, LocalDate.now().toString())?.let { dao.deleteLog(it.id) }
    }
    suspend fun reset(categoryId: Long) = dao.resetDay(categoryId, LocalDate.now().toString())
    fun week(categoryId: Long): Flow<List<DayTotal>> {
        val today = LocalDate.now()
        return dao.weeklyTotals(categoryId, today.minusDays(6).toString(), today.toString())
    }
    fun average(categoryId: Long): Flow<Double?> {
        val today = LocalDate.now()
        return dao.averageSession(categoryId, today.minusDays(6).toString(), today.toString())
    }
}
