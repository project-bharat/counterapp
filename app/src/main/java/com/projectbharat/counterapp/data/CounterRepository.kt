package com.projectbharat.counterapp.data

import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.flow.Flow

class CounterRepository(private val dao: CounterDao) {
    val categories = dao.categories()
    suspend fun addCategory(mr: String, en: String) = dao.addCategory(CounterCategory(titleMr = mr, titleEn = en))
    fun today(categoryId: Long): Flow<Int> = dao.todayCount(categoryId, LocalDate.now().toString())
    suspend fun add(categoryId: Long, strength: String?, duration: Long? = null): Long {
        val date = LocalDate.now().toString()
        val next = dao.countNow(categoryId, date) + 1
        return dao.addLog(CounterLog(date = date, timestamp = System.currentTimeMillis(), categoryId = categoryId, strength = strength, sessionDurationMs = if (next == 10) duration else null))
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

    fun babySessionsForDay(categoryId: Long, date: String = LocalDate.now().toString()) =
        dao.babySessionsForDay(categoryId, date)

    fun babySessionsForMonth(categoryId: Long, month: LocalDate): Flow<List<BabySession>> {
        val first = month.withDayOfMonth(1)
        return dao.babySessionsForMonth(categoryId, first.toString(), first.withDayOfMonth(first.lengthOfMonth()).toString())
    }

    fun averageBabyTimeToTen(categoryId: Long): Flow<Double?> {
        val today = LocalDate.now()
        return dao.averageBabyTimeToTen(categoryId, today.minusDays(6).toString(), today.toString())
    }

    fun latestBabySession(categoryId: Long): Flow<BabySession?> =
        dao.latestSessionFlow(categoryId, System.currentTimeMillis() - TWO_HOURS_MS)

    suspend fun startBabySession(categoryId: Long): Long? {
        val now = System.currentTimeMillis()
        val previous = dao.latestBabySession(categoryId, now - TWO_HOURS_MS)
        if (previous != null) return null
        val time = LocalTime.now()
        val period = when (time.hour) {
            in 5..10 -> "MORNING"
            in 11..15 -> "AFTERNOON"
            in 16..18 -> "EVENING"
            else -> "NIGHT"
        }
        return dao.insertBabySession(
            BabySession(
                categoryId = categoryId,
                date = LocalDate.now().toString(),
                startedAt = now,
                dayPeriod = period
            )
        )
    }

    suspend fun addBabyMovement(categoryId: Long, sessionId: Long, strength: String?) {
        val now = System.currentTimeMillis()
        val session = dao.babySession(sessionId) ?: return
        if (session.endedAt != null || now >= session.startedAt + TWO_HOURS_MS) return
        dao.addLog(
            CounterLog(
                date = session.date,
                timestamp = now,
                categoryId = categoryId,
                strength = strength,
                sessionId = sessionId
            )
        )
        dao.incrementBabySession(sessionId, now - session.startedAt)
    }

    suspend fun undoBabyMovement(sessionId: Long) {
        dao.latestSessionLog(sessionId)?.let {
            dao.deleteSessionLog(it.id)
            dao.decrementBabySession(sessionId)
        }
    }

    suspend fun finishBabySession(sessionId: Long, strength: String?) {
        val now = System.currentTimeMillis()
        val session = dao.babySession(sessionId) ?: return
        if (session.endedAt == null) dao.finishBabySession(sessionId, minOf(now, session.startedAt + TWO_HOURS_MS), strength)
    }

    suspend fun completeExpiredBabySession(sessionId: Long) {
        val session = dao.babySession(sessionId) ?: return
        val completedAt = session.startedAt + TWO_HOURS_MS
        if (session.endedAt == null && System.currentTimeMillis() >= completedAt) {
            dao.finishBabySession(sessionId, completedAt, session.strength)
        }
    }

    companion object {
        const val TWO_HOURS_MS = 2 * 60 * 60 * 1000L
    }
}
