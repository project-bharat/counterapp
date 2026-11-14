package com.projectbharat.counterapp

import org.junit.Assert.assertEquals
import org.junit.Test

class GpsWalkVerifierTest {
    @Test fun stationaryGpsNoiseDoesNotConfirmSteps() {
        val verifier = GpsWalkVerifier()
        assertEquals(0, verifier.acceptLocation(18.5204, 73.8567, 4f, 1_000L, 0))
        assertEquals(0, verifier.acceptLocation(18.52041, 73.85671, 4f, 3_000L, 12))
    }
    @Test fun meaningfulDisplacementConfirmsOnlyPendingSteps() {
        val verifier = GpsWalkVerifier()
        verifier.acceptLocation(18.5204, 73.8567, 4f, 1_000L, 0)
        assertEquals(5, verifier.acceptLocation(18.5205, 73.8567, 4f, 12_000L, 5))
    }
    @Test fun inaccurateFixIsIgnored() {
        val verifier = GpsWalkVerifier()
        verifier.acceptLocation(18.5204, 73.8567, 4f, 1_000L, 0)
        assertEquals(0, verifier.acceptLocation(18.5205, 73.8567, 30f, 12_000L, 5))
    }
}