package com.toiboytoestar.kurisu.core;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.TimeZone;
import org.junit.Test;

public class StreakTest {
    @Test public void advance() {
        assertArrayEquals(new int[]{3, 100}, Streak.advance(100, 3, 100));   // same day
        assertArrayEquals(new int[]{4, 101}, Streak.advance(100, 3, 101));   // next day
        assertArrayEquals(new int[]{1, 105}, Streak.advance(100, 3, 105));   // gap
        assertArrayEquals(new int[]{1, 7}, Streak.advance(0, 0, 7));         // first ever
    }

    @Test public void current() {
        assertEquals(5, Streak.current(100, 5, 100));
        assertEquals(5, Streak.current(100, 5, 101));
        assertEquals(0, Streak.current(100, 5, 102));
    }

    @Test public void epochDayUsesLocalMidnight() {
        TimeZone ist = TimeZone.getTimeZone("Asia/Kolkata");
        long justAfterMidnightIst = TimeParse.parse("2026-10-12 00:10", 0, ist);
        long justBeforeMidnightIst = TimeParse.parse("2026-10-11 23:50", 0, ist);
        assertEquals(1, Streak.epochDay(justAfterMidnightIst, ist) - Streak.epochDay(justBeforeMidnightIst, ist));
    }

    @Test public void checkInPrompt() {
        CheckIn.Stats s = new CheckIn.Stats();
        s.streak = 4; s.tasksDoneWeek = 9; s.openTasks = 2; s.nearestExam = "Physics in 3 days";
        String p = CheckIn.prompt(s, true);
        assertTrue(p.contains("4-day streak"));
        assertTrue(p.contains("9 tasks completed"));
        assertTrue(p.contains("Physics in 3 days"));
        assertTrue(!CheckIn.prompt(s, false).contains("WEEKLY SUMMARY"));
    }
}
