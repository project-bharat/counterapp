package com.projectbharat.counterapp.data

import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

class CounterRepository(private val dao: CounterDao) {
    val categories = dao.categories()
    suspend fun addCategory(mr: String, en: String) = dao.addCategory(CounterCategory(titleMr = mr, titleEn = en))
    fun today(categoryId: Long): Flow<Int> = dao.todayCount(categoryId, LocalDate.now().toString())
    suspend fun add(categoryId: Long, strength: String?, duration: Long? = null) =
        dao.addLog(CounterLog(date = LocalDate.now().toString(), timestamp = System.currentTimeMillis(), categoryId = categoryId, strength = strength, sessionDurationMs = duration))
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
