package com.projectbharat.counterapp.data

import org.junit.Assert.assertThrows
import org.junit.Test

class BackupSnapshotValidatorTest {
    private val category = CounterCategory(id = 1L, titleMr = "जप", titleEn = "Japa", kind = "MANTRA")

    @Test fun acceptsValidJapaBackup() {
        val log = CounterLog(id = 20L, date = "2026-11-14", timestamp = 2_000L, categoryId = 1L, count = 4, note = "MANTRA:राम")
        BackupSnapshotValidator.validate(listOf(category), listOf(log))
    }

    @Test fun rejectsDuplicateCategoryIds() {
        assertThrows(IllegalArgumentException::class.java) {
            BackupSnapshotValidator.validate(listOf(category, category.copy(titleEn = "Duplicate")), emptyList())
        }
    }

    @Test fun rejectsNonMantraCategory() {
        assertThrows(IllegalArgumentException::class.java) {
            BackupSnapshotValidator.validate(listOf(category.copy(kind = "BABY")), emptyList())
        }
    }

    @Test fun rejectsInvalidDates() {
        val log = CounterLog(id = 20L, date = "not-a-date", timestamp = 2_000L, categoryId = 1L)
        assertThrows(IllegalArgumentException::class.java) { BackupSnapshotValidator.validate(listOf(category), listOf(log)) }
    }

    @Test fun rejectsNonMantraLogNotes() {
        val log = CounterLog(id = 20L, date = "2026-11-14", timestamp = 2_000L, categoryId = 1L, note = "unrelated")
        assertThrows(IllegalArgumentException::class.java) { BackupSnapshotValidator.validate(listOf(category), listOf(log)) }
    }
}
