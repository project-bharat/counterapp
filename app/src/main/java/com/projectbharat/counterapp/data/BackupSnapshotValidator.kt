package com.projectbharat.counterapp.data

import java.time.LocalDate

object BackupSnapshotValidator {
    fun validate(
        categories: List<CounterCategory>,
        logs: List<CounterLog>,
        sessions: List<BabySession>
    ) {
        require(categories.isNotEmpty()) { "Backup contains no categories" }
        val categoryIds = categories.map { it.id }
        val categoryIdSet = categoryIds.toSet()
        val sessionIds = sessions.map { it.id }
        val sessionById = sessions.associateBy { it.id }

        require(categoryIds.all { it > 0 } && categoryIdSet.size == categoryIds.size) {
            "Backup contains invalid or duplicate counter IDs"
        }
        require(categories.all {
            it.titleMr.isNotBlank() && it.titleEn.isNotBlank() && it.goal >= 0
        }) { "Backup contains invalid counter details" }
        require(sessionIds.all { it > 0 } && sessionIds.toSet().size == sessionIds.size) {
            "Backup contains invalid or duplicate session IDs"
        }
        require(sessions.all {
            it.categoryId in categoryIdSet &&
                it.movementCount >= 0 &&
                (it.endedAt == null || it.endedAt >= it.startedAt) &&
                (it.timeToTenMs == null || it.timeToTenMs >= 0) &&
                validDate(it.date)
        }) { "Backup contains invalid baby movement sessions" }
        val logIds = logs.map { it.id }
        require(logIds.all { it > 0 } && logIds.toSet().size == logIds.size) {
            "Backup contains invalid or duplicate record IDs"
        }
        require(logs.all {
            it.categoryId in categoryIdSet && it.count > 0 &&
                it.timestamp >= 0 && validDate(it.date) &&
                (it.sessionId == null || sessionById[it.sessionId]?.categoryId == it.categoryId)
        }) { "Backup contains invalid counter records" }
    }

    private fun validDate(value: String): Boolean =
        runCatching { LocalDate.parse(value) }.isSuccess
}
