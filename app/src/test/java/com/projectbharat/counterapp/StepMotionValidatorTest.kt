package com.projectbharat.counterapp

import org.junit.Assert.assertEquals
import org.junit.Test

class StepMotionValidatorTest {
    @Test fun isolatedStepPulseDoesNotCount() {
        val validator = StepMotionValidator()
        addPeak(validator, 1_000_000_000L)
        assertEquals(0, validator.acceptStepDetector(1_000_000_000L))
    }

    @Test fun walkingCadenceIsCountedAfterThreeValidatedPulses() {
        val validator = StepMotionValidator()
        repeat(4) { index ->
            val timestamp = 1_000_000_000L + index * 600_000_000L
            addPeak(validator, timestamp)
            val actual = validator.acceptStepDetector(timestamp)
            assertEquals(if (index == 2) 3 else if (index == 3) 1 else 0, actual)
        }
    }

    @Test fun irregularPulseResetsWalkingCandidate() {
        val validator = StepMotionValidator()
        val times = listOf(1_000_000_000L, 1_600_000_000L, 3_800_000_000L, 4_400_000_000L, 5_000_000_000L)
        val expected = listOf(0, 0, 0, 0, 3)
        times.forEachIndexed { index, timestamp ->
            addPeak(validator, timestamp)
            assertEquals(expected[index], validator.acceptStepDetector(timestamp))
        }
    }

    private fun addPeak(validator: StepMotionValidator, timestamp: Long) {
        validator.addAcceleration(timestamp - 20_000_000L, 0f, 0f, 9.81f)
        validator.addAcceleration(timestamp - 10_000_000L, 0f, 0f, 13.0f)
        validator.addAcceleration(timestamp, 0f, 0f, 9.81f)
    }
}
