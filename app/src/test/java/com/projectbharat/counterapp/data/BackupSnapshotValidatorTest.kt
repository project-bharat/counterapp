package com.projectbharat.counterapp.data

import org.junit.Assert.assertThrows
import org.junit.Test

class BackupSnapshotValidatorTest {
    private val category = CounterCategory(
        id = 1L,
        titleMr = "जप",
        titleEn = "Chants",
        kind = "MANTRA"
    )

    @Test
    fun acceptsAConsistentOfflineSnapshot() {
        val session = BabySession(
            id = 10L,
            categoryId = 1L,
            date = "2026-11-14",
            startedAt = 1_000L,
            dayPeriod = "MORNING",
            movementCount = 1
        )
        val log = CounterLog(
            id = 20L,
            date = "2026-11-14",
            timestamp = 2_000L,
            categoryId = 1L,
            sessionId = 10L
        )

        BackupSnapshotValidator.validate(listOf(category), listOf(log), listOf(session))
    }

    @Test
    fun rejectsDuplicateCategoryIds() {
        assertThrows(IllegalArgumentException::class.java) {
            BackupSnapshotValidator.validate(
                listOf(category, category.copy(titleEn = "Duplicate")),
                emptyList(),
                emptyList()
            )
        }
    }

    @Test
    fun rejectsLogsThatReferenceMissingSessions() {
        val log = CounterLog(
            id = 20L,
            date = "2026-11-14",
            timestamp = 2_000L,
            categoryId = 1L,
            sessionId = 999L
        )
        assertThrows(IllegalArgumentException::class.java) {
            BackupSnapshotValidator.validate(listOf(category), listOf(log), emptyList())
        }
    }

    @Test
    fun rejectsInvalidDatesBeforeRestore() {
        val log = CounterLog(
            id = 20L,
            date = "not-a-date",
            timestamp = 2_000L,
            categoryId = 1L
        )
        assertThrows(IllegalArgumentException::class.java) {
            BackupSnapshotValidator.validate(listOf(category), listOf(log), emptyList())
        }
    }
}
