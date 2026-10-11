package com.toiboytoestar.kurisu.core;

import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** Parses the local date-times the model writes in action tags. */
public final class TimeParse {
    private TimeParse() {}

    /** Whole calendar days from now's date to target's date (0 = same day, negative = past). */
    public static int daysUntil(long now, long target, TimeZone tz) {
        Calendar a = Calendar.getInstance(tz);
        a.setTimeInMillis(now);
        Calendar b = Calendar.getInstance(tz);
        b.setTimeInMillis(target);
        a.set(Calendar.HOUR_OF_DAY, 12); a.set(Calendar.MINUTE, 0); a.set(Calendar.SECOND, 0); a.set(Calendar.MILLISECOND, 0);
        b.set(Calendar.HOUR_OF_DAY, 12); b.set(Calendar.MINUTE, 0); b.set(Calendar.SECOND, 0); b.set(Calendar.MILLISECOND, 0);
        return (int) Math.round((b.getTimeInMillis() - a.getTimeInMillis()) / 86400000.0);
    }

    /** @return epoch millis, or -1 if unparseable. */
    public static long parse(String raw, long now, TimeZone tz) {
        if (raw == null) return -1;
        String s = raw.trim().replace('T', ' ');
        if (s.length() == 0) return -1;
        String[] patterns = {"yyyy-MM-dd HH:mm", "yyyy-MM-dd H:mm", "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd"};
        for (String p : patterns) {
            SimpleDateFormat f = new SimpleDateFormat(p, Locale.US);
            f.setLenient(false);
            f.setTimeZone(tz);
            ParsePosition pos = new ParsePosition(0);
            Date d = f.parse(s, pos);
            if (d != null && pos.getIndex() == s.length()) {
                if (p.equals("yyyy-MM-dd")) {
                    Calendar c = Calendar.getInstance(tz);
                    c.setTime(d);
                    c.set(Calendar.HOUR_OF_DAY, 9);
                    return c.getTimeInMillis();
                }
                return d.getTime();
            }
        }
        SimpleDateFormat f = new SimpleDateFormat("HH:mm", Locale.US);
        f.setLenient(false);
        f.setTimeZone(tz);
        ParsePosition pos = new ParsePosition(0);
        Date d = f.parse(s, pos);
        if (d != null && pos.getIndex() == s.length()) {
            int[] hm = hm(d, tz);
            return nextOccurrence(now, hm[0], hm[1], tz);
        }
        return -1;
    }

    private static int[] hm(Date d, TimeZone tz) {
        Calendar c = Calendar.getInstance(tz);
        c.setTime(d);
        return new int[]{c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE)};
    }

    /** Next time (strictly after now) at which the wall clock reads hour:minute. */
    public static long nextOccurrence(long now, int hour, int minute, TimeZone tz) {
        Calendar c = Calendar.getInstance(tz);
        c.setTimeInMillis(now);
        c.set(Calendar.HOUR_OF_DAY, hour);
        c.set(Calendar.MINUTE, minute);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        if (c.getTimeInMillis() <= now) c.add(Calendar.DAY_OF_YEAR, 1);
        return c.getTimeInMillis();
    }

    /**
     * Next wake-up time. days is a 7-bit mask, bit 0 = Sunday ... bit 6 = Saturday (Calendar.DAY_OF_WEEK - 1).
     * Returns -1 if no day is selected.
     */
    public static long nextWake(long now, int hour, int minute, int days, TimeZone tz) {
        if ((days & 0x7f) == 0) return -1;
        long t = nextOccurrence(now, hour, minute, tz);
        for (int i = 0; i < 8; i++) {
            Calendar c = Calendar.getInstance(tz);
            c.setTimeInMillis(t);
            int bit = c.get(Calendar.DAY_OF_WEEK) - 1;
            if ((days & (1 << bit)) != 0) return t;
            c.add(Calendar.DAY_OF_YEAR, 1);
            t = c.getTimeInMillis();
        }
        return -1;
    }
}
