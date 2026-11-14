package com.projectbharat.counterapp

import java.util.ArrayDeque
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Rejects isolated step-detector pulses unless they align with an accelerometer
 * impact and form a plausible walking cadence.
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

    fun addAcceleration(timestampNs: Long, x: Float, y: Float, z: Float) {
        if (!initialized) {
            gravityX = x; gravityY = y; gravityZ = z
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

    /** Returns zero until three consecutive, motion-validated steps establish walking. */
    fun acceptStepDetector(timestampNs: Long): Int {
        while (accelerationPeaks.isNotEmpty() && timestampNs - accelerationPeaks.first() > MATCH_WINDOW_NS) {
            accelerationPeaks.removeFirst()
        }
        val match = accelerationPeaks.firstOrNull { abs(timestampNs - it) <= MATCH_WINDOW_NS }
        if (match == null) {
            cadenceStreak = 0
            lastAcceptedStepNs = 0L
            return 0
        }
        accelerationPeaks.remove(match)
        val interval = if (lastAcceptedStepNs == 0L) 0L else timestampNs - lastAcceptedStepNs
        cadenceStreak = if (lastAcceptedStepNs == 0L || interval !in MIN_STEP_INTERVAL_NS..MAX_STEP_INTERVAL_NS) 1 else cadenceStreak + 1
        lastAcceptedStepNs = timestampNs
        return when {
            cadenceStreak == 3 -> 3
            cadenceStreak > 3 -> 1
            else -> 0
        }
    }

    companion object {
        private const val MIN_IMPACT = 1.35f
        private const val PEAK_GAP_NS = 180_000_000L
        private const val PEAK_RETENTION_NS = 1_000_000_000L
        private const val MATCH_WINDOW_NS = 280_000_000L
        private const val MIN_STEP_INTERVAL_NS = 280_000_000L
        private const val MAX_STEP_INTERVAL_NS = 1_500_000_000L
    }
}
