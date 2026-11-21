package com.projectbharat.counterapp.data

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class JapaGoalTest {
    @Test
    fun laterGoalsKeepHistoricalTargets() {
        val goals = listOf(
            JapaGoal(1, "2026-11-20", 216),
            JapaGoal(1, "2026-11-22", 54)
        )

        assertEquals(108, japaGoalOn(goals, LocalDate.parse("2026-11-19")))
        assertEquals(216, japaGoalOn(goals, LocalDate.parse("2026-11-20")))
        assertEquals(216, japaGoalOn(goals, LocalDate.parse("2026-11-21")))
        assertEquals(54, japaGoalOn(goals, LocalDate.parse("2026-11-22")))
    }
}
