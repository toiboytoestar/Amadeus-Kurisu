package com.toiboytoestar.kurisu;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Pomodoro-style focus/break timer. State lives in preferences so it survives leaving the app. */
final class FocusTimer {
    private FocusTimer() {}

    static final String FOCUS = "focus";
    static final String BREAK = "break";

    static SharedPreferences sp(Context c) {
        return new Prefs(c).raw();
    }

    static String phase(Context c) {
        return sp(c).getString("focus_phase", "");
    }

    static boolean paused(Context c) {
        return sp(c).getLong("focus_left", 0) > 0;
    }

    static long remainingMs(Context c) {
        SharedPreferences p = sp(c);
        if (phase(c).length() == 0) return 0;
        long left = p.getLong("focus_left", 0);
        if (left > 0) return left;
        return Math.max(0, p.getLong("focus_end", 0) - System.currentTimeMillis());
    }

    static void start(Context c, String phase, int minutes) {
        clearAlarm(c);
        long end = System.currentTimeMillis() + minutes * 60000L;
        long rid = Reminders.schedule(c, Reminders.KIND_FOCUS, end,
                FOCUS.equals(phase) ? "Focus session complete" : "Break is over",
                FOCUS.equals(phase) ? "Nice work. Time for a break." : "Ready for the next focus session?");
        sp(c).edit().putString("focus_phase", phase).putLong("focus_end", end).putLong("focus_left", 0)
                .putInt("focus_total", minutes).putLong("focus_rid", rid).apply();
    }

    static void pause(Context c) {
        if (phase(c).length() == 0 || paused(c)) return;
        long left = Math.max(1000, remainingMs(c));
        clearAlarm(c);
        sp(c).edit().putLong("focus_left", left).apply();
    }

    static void resume(Context c) {
        SharedPreferences p = sp(c);
        long left = p.getLong("focus_left", 0);
        String phase = phase(c);
        if (left <= 0 || phase.length() == 0) return;
        long end = System.currentTimeMillis() + left;
        long rid = Reminders.schedule(c, Reminders.KIND_FOCUS, end,
                FOCUS.equals(phase) ? "Focus session complete" : "Break is over",
                FOCUS.equals(phase) ? "Nice work. Time for a break." : "Ready for the next focus session?");
        p.edit().putLong("focus_end", end).putLong("focus_left", 0).putLong("focus_rid", rid).apply();
    }

    static void stop(Context c) {
        clearAlarm(c);
        sp(c).edit().putString("focus_phase", "").putLong("focus_left", 0).apply();
    }

    private static void clearAlarm(Context c) {
        long rid = sp(c).getLong("focus_rid", 0);
        if (rid > 0) Reminders.cancel(c, rid);
        sp(c).edit().putLong("focus_rid", 0).apply();
    }

    /** If the running phase has ended, records it and returns the finished phase; otherwise "". */
    static String completeIfDue(Context c) {
        SharedPreferences p = sp(c);
        String phase = phase(c);
        if (phase.length() == 0 || paused(c)) return "";
        if (System.currentTimeMillis() < p.getLong("focus_end", 0)) return "";
        if (FOCUS.equals(phase)) addToday(c, p.getInt("focus_total", 0));
        p.edit().putString("focus_phase", "").putLong("focus_rid", 0).apply();
        return phase;
    }

    private static String today() {
        return new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date());
    }

    private static void addToday(Context c, int minutes) {
        SharedPreferences p = sp(c);
        int have = today().equals(p.getString("focus_day", "")) ? p.getInt("focus_today", 0) : 0;
        int sessions = today().equals(p.getString("focus_day", "")) ? p.getInt("focus_sessions", 0) : 0;
        p.edit().putString("focus_day", today()).putInt("focus_today", have + minutes)
                .putInt("focus_sessions", sessions + 1).apply();
    }

    static int todayMinutes(Context c) {
        SharedPreferences p = sp(c);
        return today().equals(p.getString("focus_day", "")) ? p.getInt("focus_today", 0) : 0;
    }

    static int todaySessions(Context c) {
        SharedPreferences p = sp(c);
        return today().equals(p.getString("focus_day", "")) ? p.getInt("focus_sessions", 0) : 0;
    }

    static String format(long ms) {
        long s = (ms + 999) / 1000;
        return String.format(Locale.US, "%02d:%02d", s / 60, s % 60);
    }
}
