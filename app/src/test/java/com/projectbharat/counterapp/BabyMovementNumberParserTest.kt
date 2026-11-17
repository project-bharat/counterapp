package com.projectbharat.counterapp

import org.junit.Assert.assertEquals
import org.junit.Test

class BabyMovementNumberParserTest {
    @Test fun englishNumberInSentenceIsExtracted() {
        assertEquals(3, BabyMovementNumberParser.extract("The baby moved three times today", true))
    }

    @Test fun englishMultipleNumbersAreSummed() {
        assertEquals(5, BabyMovementNumberParser.extract("I felt two and then three", true))
    }

    @Test fun marathiNumberWordsAreExtracted() {
        assertEquals(3, BabyMovementNumberParser.extract("बाळाची हालचाल एक दोन वेळा झाली", false))
    }

    @Test fun marathiNumberAndDevanagariDigitsAreExtracted() {
        assertEquals(7, BabyMovementNumberParser.extract("आज तीन आणि ४ हालचाली", false))
    }

    @Test fun unrelatedWordsDoNotAddCounts() {
        assertEquals(0, BabyMovementNumberParser.extract("baby is moving gently", true))
        assertEquals(0, BabyMovementNumberParser.extract("बाळ हलत आहे", false))
    }
}
