package com.projectbharat.counterapp

import java.util.ArrayDeque
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Confirms a sustained walking rhythm by matching hardware step-detector events
 * with accelerometer impacts. Isolated or irregular pulses are discarded.
 */
internal class StepMotionValidator {
    private var gravityX = 0f
    private var gravityY = 0f
    private var gravityZ = 0f
    private var initialized = false
    private var previousMagnitude = 0f
    private var lastPeakNs = 0L
    private val accelerationPeaks = ArrayDeque<Long>()
    private var lastAcceptedStepNs = 0L
    private var cadenceStreak = 0
    private var walkingConfirmed = false

    fun addAcceleration(timestampNs: Long, x: Float, y: Float, z: Float) {
        if (!initialized) {
            gravityX = x
            gravityY = y
            gravityZ = z
            initialized = true
            return
        }
        val alpha = 0.82f
        gravityX = alpha * gravityX + (1f - alpha) * x
        gravityY = alpha * gravityY + (1f - alpha) * y
        gravityZ = alpha * gravityZ + (1f - alpha) * z
        val dx = x - gravityX
        val dy = y - gravityY
        val dz = z - gravityZ
        val magnitude = sqrt(dx * dx + dy * dy + dz * dz)
        if (magnitude > MIN_IMPACT && previousMagnitude <= magnitude && timestampNs - lastPeakNs > PEAK_GAP_NS) {
            accelerationPeaks.addLast(timestampNs)
            lastPeakNs = timestampNs
        }
        previousMagnitude = magnitude
        while (accelerationPeaks.isNotEmpty() && timestampNs - accelerationPeaks.first() > PEAK_RETENTION_NS) {
            accelerationPeaks.removeFirst()
        }
    }

    /**
     * Returns 0 while validating. On the eighth consecutive valid step it
     * releases the buffered eight; subsequent valid steps are returned as 1.
     */
    fun acceptStepDetector(timestampNs: Long): Int {
        while (accelerationPeaks.isNotEmpty() && timestampNs - accelerationPeaks.first() > MATCH_WINDOW_NS) {
            accelerationPeaks.removeFirst()
        }
        val match = accelerationPeaks.firstOrNull { abs(timestampNs - it) <= MATCH_WINDOW_NS }
        if (match == null) {
            resetCandidate()
            return 0
        }
        accelerationPeaks.remove(match)

        val interval = if (lastAcceptedStepNs == 0L) 0L else timestampNs - lastAcceptedStepNs
        if (lastAcceptedStepNs != 0L && interval !in MIN_STEP_INTERVAL_NS..MAX_STEP_INTERVAL_NS) {
            resetCandidate()
        }
        cadenceStreak++
        lastAcceptedStepNs = timestampNs

        if (!walkingConfirmed && cadenceStreak >= REQUIRED_CONSECUTIVE_STEPS) {
            walkingConfirmed = true
            return cadenceStreak
        }
        return if (walkingConfirmed) 1 else 0
    }

    private fun resetCandidate() {
        cadenceStreak = 0
        lastAcceptedStepNs = 0L
        walkingConfirmed = false
    }

    companion object {
        private const val MIN_IMPACT = 1.35f
        private const val PEAK_GAP_NS = 180_000_000L
        private const val PEAK_RETENTION_NS = 1_000_000_000L
        private const val MATCH_WINDOW_NS = 280_000_000L
        private const val MIN_STEP_INTERVAL_NS = 300_000_000L
        private const val MAX_STEP_INTERVAL_NS = 1_500_000_000L
        private const val REQUIRED_CONSECUTIVE_STEPS = 8
    }
}
