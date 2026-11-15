package com.projectbharat.counterapp

/**
 * Converts Android's cumulative TYPE_STEP_COUNTER value into non-duplicated deltas.
 * A lower reading means the hardware counter reset (normally after a reboot).
 */
internal class StepCounterDeltaTracker(initialValue: Int = -1) {
    var lastValue: Int = initialValue
        private set

    fun update(currentValue: Int): Int {
        if (currentValue < 0) return 0
        if (lastValue < 0 || currentValue < lastValue) {
            lastValue = currentValue
            return 0
        }
        val delta = currentValue - lastValue
        lastValue = currentValue
        return delta
    }
}