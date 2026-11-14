package com.projectbharat.counterapp

import kotlin.math.*

/** Confirms sensor candidates only after sustained GPS movement, not a single noisy location jump. */
internal class GpsWalkVerifier {
    private var anchorLat = Double.NaN
    private var anchorLon = Double.NaN
    private var anchorTimeMs = 0L
    private var candidateLat = Double.NaN
    private var candidateLon = Double.NaN
    private var candidateTimeMs = 0L

    fun acceptLocation(latitude: Double, longitude: Double, accuracyMeters: Float, timestampMs: Long, pendingSteps: Int): Int {
        if (!latitude.isFinite() || !longitude.isFinite() || accuracyMeters < 0f || accuracyMeters > MAX_ACCURACY_METERS) return 0
        if (anchorLat.isNaN()) {
            setAnchor(latitude, longitude, timestampMs)
            return 0
        }
        if (pendingSteps <= 0) return 0

        val distanceFromAnchor = distanceMeters(anchorLat, anchorLon, latitude, longitude)
        val requiredDistance = max(MIN_DISPLACEMENT_METERS, accuracyMeters.toDouble() * ACCURACY_FACTOR)
        if (distanceFromAnchor < requiredDistance) return 0

        if (candidateLat.isNaN() || timestampMs - candidateTimeMs > MAX_CANDIDATE_AGE_MS) {
            candidateLat = latitude
            candidateLon = longitude
            candidateTimeMs = timestampMs
            return 0
        }

        val first = localVector(anchorLat, anchorLon, candidateLat, candidateLon)
        val second = localVector(candidateLat, candidateLon, latitude, longitude)
        val firstLength = hypot(first.first, first.second)
        val secondLength = hypot(second.first, second.second)
        if (firstLength < requiredDistance || secondLength < MIN_CONTINUATION_METERS) {
            candidateLat = latitude
            candidateLon = longitude
            candidateTimeMs = timestampMs
            return 0
        }
        val dot = first.first * second.first + first.second * second.second
        val forwardProjection = dot / firstLength
        val directionAgreement = dot / (firstLength * secondLength)
        val elapsedSeconds = ((timestampMs - anchorTimeMs).coerceAtLeast(1L)) / 1000.0
        val averageSpeed = distanceFromAnchor / elapsedSeconds
        if (forwardProjection < MIN_CONTINUATION_METERS || directionAgreement < MIN_DIRECTION_AGREEMENT ||
            averageSpeed < MIN_WALK_SPEED_MPS || averageSpeed > MAX_WALK_SPEED_MPS) {
            candidateLat = latitude
            candidateLon = longitude
            candidateTimeMs = timestampMs
            return 0
        }

        setAnchor(latitude, longitude, timestampMs)
        return min(pendingSteps, max(1, (distanceFromAnchor / ESTIMATED_STRIDE_METERS).roundToInt()))
    }

    private fun setAnchor(latitude: Double, longitude: Double, timestampMs: Long) {
        anchorLat = latitude
        anchorLon = longitude
        anchorTimeMs = timestampMs
        candidateLat = Double.NaN
        candidateLon = Double.NaN
        candidateTimeMs = 0L
    }

    private fun localVector(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double): Pair<Double, Double> {
        val radius = 6_371_000.0
        val meanLat = Math.toRadians((fromLat + toLat) / 2.0)
        val east = Math.toRadians(toLon - fromLon) * radius * cos(meanLat)
        val north = Math.toRadians(toLat - fromLat) * radius
        return east to north
    }

    private fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val vector = localVector(lat1, lon1, lat2, lon2)
        return hypot(vector.first, vector.second)
    }

    companion object {
        private const val MAX_ACCURACY_METERS = 15f
        private const val ACCURACY_FACTOR = 0.8
        private const val MIN_DISPLACEMENT_METERS = 5.0
        private const val MIN_CONTINUATION_METERS = 1.5
        private const val MIN_DIRECTION_AGREEMENT = 0.35
        private const val MIN_WALK_SPEED_MPS = 0.35
        private const val MAX_WALK_SPEED_MPS = 3.0
        private const val MAX_CANDIDATE_AGE_MS = 12_000L
        private const val ESTIMATED_STRIDE_METERS = 0.72
    }
}
