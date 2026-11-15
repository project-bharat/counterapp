package com.projectbharat.counterapp

import org.junit.Assert.assertEquals
import org.junit.Test

class StepCounterDeltaTrackerTest {
    @Test fun firstReadingSetsBaselineWithoutAddingSteps() {
        val tracker = StepCounterDeltaTracker()
        assertEquals(0, tracker.update(120))
        assertEquals(3, tracker.update(123))
    }

    @Test fun repeatedReadingsDoNotDuplicateSteps() {
        val tracker = StepCounterDeltaTracker(50)
        assertEquals(0, tracker.update(50))
        assertEquals(4, tracker.update(54))
        assertEquals(0, tracker.update(54))
    }

    @Test fun lowerReadingResetsBaselineAfterReboot() {
        val tracker = StepCounterDeltaTracker(900)
        assertEquals(0, tracker.update(12))
        assertEquals(2, tracker.update(14))
    }
}