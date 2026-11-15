package com.projectbharat.counterapp

import org.junit.Assert.assertEquals
import org.junit.Test

class StepMotionValidatorTest {
    @Test fun isolatedStepPulseDoesNotCount() {
        val validator = StepMotionValidator()
        addPeak(validator, 1_000_000_000L)
        assertEquals(0, validator.acceptStepDetector(1_000_000_000L))
    }

    @Test fun sustainedWalkingIsReleasedAfterEightValidatedSteps() {
        val validator = StepMotionValidator()
        repeat(10) { index ->
            val timestamp = 1_000_000_000L + index * 600_000_000L
            addPeak(validator, timestamp)
            val actual = validator.acceptStepDetector(timestamp)
            assertEquals(if (index == 7) 8 else if (index > 7) 1 else 0, actual)
        }
    }

    @Test fun irregularPulseResetsWalkingCandidate() {
        val validator = StepMotionValidator()
        val times = mutableListOf(1_000_000_000L, 1_600_000_000L, 3_800_000_000L)
        times += (0..7).map { 4_400_000_000L + it * 600_000_000L }
        val expected = listOf(0, 0, 0, 0, 0, 0, 0, 0, 8, 1)
        times.forEachIndexed { index, timestamp ->
            addPeak(validator, timestamp)
            assertEquals(expected[index], validator.acceptStepDetector(timestamp))
        }
    }

    @Test fun rapidShakeLikePulsesDoNotConfirmWalking() {
        val validator = StepMotionValidator()
        repeat(12) { index ->
            val timestamp = 1_000_000_000L + index * 180_000_000L
            addPeak(validator, timestamp)
            assertEquals(0, validator.acceptStepDetector(timestamp))
        }
    }

    private fun addPeak(validator: StepMotionValidator, timestamp: Long) {
        validator.addAcceleration(timestamp - 20_000_000L, 0f, 0f, 9.81f)
        validator.addAcceleration(timestamp - 10_000_000L, 0f, 0f, 13.0f)
        validator.addAcceleration(timestamp, 0f, 0f, 9.81f)
    }
}
