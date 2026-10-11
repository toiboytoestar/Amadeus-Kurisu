package com.toiboytoestar.kurisu.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Calendar;
import java.util.TimeZone;

public class TimeParseTest {
    private static final TimeZone TZ = TimeZone.getTimeZone("Asia/Kolkata");

    private long at(int y, int mo, int d, int h, int mi) {
        Calendar c = Calendar.getInstance(TZ);
        c.clear();
        c.set(y, mo - 1, d, h, mi, 0);
        return c.getTimeInMillis();
    }

    @Test
    public void parsesFullAndIsoForms() {
        long now = at(2026, 10, 9, 15, 0);
        assertEquals(at(2026, 10, 10, 18, 30), TimeParse.parse("2026-10-10 18:30", now, TZ));
        assertEquals(at(2026, 10, 10, 18, 30), TimeParse.parse("2026-10-10T18:30", now, TZ));
        assertEquals(at(2026, 10, 10, 9, 0), TimeParse.parse("2026-10-10", now, TZ));
        assertEquals(-1, TimeParse.parse("tomorrow", now, TZ));
        assertEquals(-1, TimeParse.parse("2026-13-40 10:00", now, TZ));
        assertEquals(-1, TimeParse.parse("", now, TZ));
    }

    @Test
    public void bareClockRollsToNextDay() {
        long now = at(2026, 10, 9, 15, 0);
        assertEquals(at(2026, 10, 9, 18, 0), TimeParse.parse("18:00", now, TZ));
        assertEquals(at(2026, 10, 10, 7, 30), TimeParse.parse("07:30", now, TZ));
    }

    @Test
    public void nextWakeHonoursWeekdayMask() {
        long fri = at(2026, 10, 9, 15, 0); // Friday
        int weekdays = 0x3e; // Mon-Fri = bits 1..5
        long next = TimeParse.nextWake(fri, 7, 0, weekdays, TZ);
        assertEquals(at(2026, 10, 12, 7, 0), next); // Monday
        assertEquals(at(2026, 10, 10, 7, 0), TimeParse.nextWake(fri, 7, 0, 0x7f, TZ));
        assertEquals(-1, TimeParse.nextWake(fri, 7, 0, 0, TZ));
        assertTrue(TimeParse.nextWake(fri, 7, 0, 1 << 5, TZ) == at(2026, 10, 9, 7, 0) + 7 * 86400000L);
    }
}
