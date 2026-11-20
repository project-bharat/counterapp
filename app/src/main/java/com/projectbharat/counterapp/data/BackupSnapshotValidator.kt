package com.projectbharat.counterapp.data

import java.time.LocalDate

object BackupSnapshotValidator {
    fun validate(categories: List<CounterCategory>, logs: List<CounterLog>) {
        require(categories.isNotEmpty()) { "Backup contains no Japa categories" }
        val ids = categories.map { it.id }
        require(ids.all { it > 0 } && ids.toSet().size == ids.size) { "Backup contains invalid or duplicate counter IDs" }
        require(categories.all { it.kind == "MANTRA" && it.titleMr.isNotBlank() && it.titleEn.isNotBlank() && it.goal >= 0 }) { "Backup contains invalid Japa category details" }
        val logIds = logs.map { it.id }
        require(logIds.all { it > 0 } && logIds.toSet().size == logIds.size) { "Backup contains invalid or duplicate record IDs" }
        val validIds = ids.toSet()
        require(logs.all { it.categoryId in validIds && it.count > 0 && it.timestamp >= 0 && validDate(it.date) && (it.note == null || it.note.startsWith("MANTRA:")) }) { "Backup contains invalid Japa records" }
    }
    private fun validDate(value: String): Boolean = runCatching { LocalDate.parse(value) }.isSuccess
}
