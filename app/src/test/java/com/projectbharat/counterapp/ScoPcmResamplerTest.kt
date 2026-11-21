package com.projectbharat.counterapp

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class ScoPcmResamplerTest {
    @Test
    fun convertsSignedSamplesAndInterpolatesAcrossReads() {
        val resampler = ScoPcmResampler(4)
        assertArrayEquals(byteArrayOf(0, -1, 0, -1, 0, 0, 0, 1),
            resampler.convert(byteArrayOf(0, -1, 0, 1), 4).copyOf(8))
        assertArrayEquals(byteArrayOf(0, 2, 0, 3),
            resampler.convert(byteArrayOf(0, 3), 2).copyOf(4))
    }
}
