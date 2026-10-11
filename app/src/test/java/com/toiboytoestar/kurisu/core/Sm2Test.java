package com.toiboytoestar.kurisu.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class Sm2Test {
    @Test
    public void goodReviewsGrowIntervals() {
        Sm2.State s = Sm2.next(2.5, 0, 0, 2);
        assertEquals(1, s.intervalDays);
        s = Sm2.next(s.ease, s.intervalDays, s.reps, 2);
        assertEquals(3, s.intervalDays);
        s = Sm2.next(s.ease, s.intervalDays, s.reps, 2);
        assertEquals(8, s.intervalDays); // 3 * 2.5 = 7.5 -> 8
        assertEquals(3, s.reps);
    }

    @Test
    public void againResetsAndLowersEase() {
        Sm2.State s = Sm2.next(2.5, 10, 4, 0);
        assertEquals(0, s.intervalDays);
        assertEquals(0, s.reps);
        assertTrue(s.ease < 2.5);
        long now = 1000000L;
        assertEquals(now + Sm2.AGAIN_MS, Sm2.dueAt(now, s));
    }

    @Test
    public void easeNeverDropsBelowFloor() {
        Sm2.State s = new Sm2.State(1.4, 5, 3);
        for (int i = 0; i < 10; i++) s = Sm2.next(s.ease, s.intervalDays, s.reps, 0);
        assertTrue(s.ease >= 1.3);
    }

    @Test
    public void easyIsLongerThanGood() {
        Sm2.State g = Sm2.next(2.5, 6, 3, 2);
        Sm2.State e = Sm2.next(2.5, 6, 3, 3);
        assertTrue(e.intervalDays > g.intervalDays);
    }
}
