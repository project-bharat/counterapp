package com.projectbharat.counterapp

import kotlin.math.*

/** Confirms sensor candidate steps only after a meaningful GPS displacement. */
internal class GpsWalkVerifier {
    private var anchorLat = Double.NaN
    private var anchorLon = Double.NaN
    private var anchorTimeMs = 0L

    fun acceptLocation(latitude: Double, longitude: Double, accuracyMeters: Float, timestampMs: Long, pendingSteps: Int): Int {
        if (!latitude.isFinite() || !longitude.isFinite() || accuracyMeters < 0f || accuracyMeters > MAX_ACCURACY_METERS) return 0
        if (anchorLat.isNaN()) {
            anchorLat = latitude
            anchorLon = longitude
            anchorTimeMs = timestampMs
            return 0
        }
        if (pendingSteps <= 0) return 0
        val distance = distanceMeters(anchorLat, anchorLon, latitude, longitude)
        val elapsedSeconds = ((timestampMs - anchorTimeMs).coerceAtLeast(1L)) / 1000.0
        val speed = distance / elapsedSeconds
        val requiredDistance = max(MIN_DISPLACEMENT_METERS, accuracyMeters.toDouble() * ACCURACY_FACTOR)
        if (distance < requiredDistance || (speed < MIN_WALK_SPEED_MPS && distance < STRONG_DISPLACEMENT_METERS)) return 0
        anchorLat = latitude
        anchorLon = longitude
        anchorTimeMs = timestampMs
        return min(pendingSteps, max(1, (distance / ESTIMATED_STRIDE_METERS).roundToInt()))
    }

    private fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return earthRadius * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    companion object {
        private const val MAX_ACCURACY_METERS = 15f
        private const val ACCURACY_FACTOR = 0.8f
        private const val MIN_DISPLACEMENT_METERS = 5.0
        private const val STRONG_DISPLACEMENT_METERS = 8.0
        private const val MIN_WALK_SPEED_MPS = 0.3
        private const val ESTIMATED_STRIDE_METERS = 0.72
    }
}