package com.toiboytoestar.kurisu.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class WakeCareTest {
    @Test public void wakeWord() {
        assertEquals("what time is it", WakeWord.extract("hey kurisu what time is it"));
        assertEquals("set a timer", WakeWord.extract("Okay Kurisu, set a timer"));
        assertEquals("", WakeWord.extract("Kurisu"));
        assertEquals("explain entropy", WakeWord.extract("hey kourisu explain entropy"));
        assertNull(WakeWord.extract("what time is it"));
        assertNull(WakeWord.extract(null));
        // mentioned in passing, not at the start
        assertNull(WakeWord.extract("I was telling my friend yesterday that kurisu is a great app"));
    }

    @Test public void care() {
        assertTrue(Care.hint("I'm so stressed about tomorrow", 1).contains("sleep"));
        assertTrue(Care.hint("I'm so stressed about tomorrow", 15).contains("ONE small step"));
        assertTrue(Care.hint("I want to die", 15).contains("crisis line"));
        assertTrue(Care.isCrisis("sometimes I think about suicide"));
        assertEquals("", Care.hint("set a timer for ten minutes", 12));
        assertFalse(Care.isCrisis("this exam will kill me lol"));
    }
}
