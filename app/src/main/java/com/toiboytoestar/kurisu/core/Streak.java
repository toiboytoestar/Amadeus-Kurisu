package com.toiboytoestar.kurisu.core;

import java.util.TimeZone;

/** Day-streak arithmetic (consecutive days with at least one conversation). */
public final class Streak {
    private Streak() {}

    /** Days since 1970-01-01 in the given time zone. */
    public static int epochDay(long now, TimeZone tz) {
        long t = now + tz.getOffset(now);
        return (int) (t >= 0 ? t / 86400000L : -((-t + 86399999L) / 86400000L)); // floor; Math.floorDiv needs API 24
    }

    /** @return {newCount, newLastDay}. Same day keeps the count, the next day extends it, a gap restarts at 1. */
    public static int[] advance(int lastDay, int count, int today) {
        if (lastDay == today) return new int[]{Math.max(1, count), today};
        if (lastDay == today - 1) return new int[]{count + 1, today};
        return new int[]{1, today};
    }

    /** What to display right now: a streak that already lapsed (missed yesterday) shows as 0. */
    public static int current(int lastDay, int count, int today) {
        return (lastDay == today || lastDay == today - 1) ? count : 0;
    }
}
