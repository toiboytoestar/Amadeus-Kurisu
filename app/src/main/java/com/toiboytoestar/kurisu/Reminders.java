package com.toiboytoestar.kurisu;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import com.toiboytoestar.kurisu.core.TimeParse;

import java.util.List;
import java.util.TimeZone;

/** Schedules reminders, timers, focus-session ends and Kurisu's wake-up call. */
public final class Reminders {
    private Reminders() {}

    public static final String KIND_REMINDER = "reminder";
    public static final String KIND_TIMER = "timer";
    public static final String KIND_FOCUS = "focus";
    public static final String KIND_CALL = "call";
    public static final String KIND_TASK = "task";
    public static final String KIND_CHECKIN = "checkin";

    static final String EXTRA_ID = "rid";

    private static PendingIntent pending(Context ctx, long id) {
        Intent i = new Intent(ctx, ReminderReceiver.class);
        i.setAction("com.toiboytoestar.kurisu.REMINDER." + id);
        i.putExtra(EXTRA_ID, id);
        return PendingIntent.getBroadcast(ctx, (int) (id % Integer.MAX_VALUE), i,
                PendingIntent.FLAG_UPDATE_CURRENT | Notifier.immutableFlag());
    }

    /** Stores and arms a reminder. Returns its id. */
    public static long schedule(Context ctx, String kind, long atMillis, String title, String body) {
        long id = Db.get(ctx).addReminder(kind, atMillis, title, body);
        arm(ctx, id, kind, atMillis);
        return id;
    }

    private static void arm(Context ctx, long id, String kind, long at) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pi = pending(ctx, id);
        if (KIND_CHECKIN.equals(kind)) {
            try {
                if (android.os.Build.VERSION.SDK_INT >= 23) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
                else am.set(AlarmManager.RTC_WAKEUP, at, pi);
            } catch (SecurityException e) {
                am.set(AlarmManager.RTC_WAKEUP, at, pi); // exact alarms not allowed: a slightly late nudge is fine
            }
            return;
        }
        try {
            // setAlarmClock is exact, survives Doze and needs no special permission
            Intent show = new Intent(ctx, LaunchActivity.class);
            PendingIntent showPi = PendingIntent.getActivity(ctx, 9, show, PendingIntent.FLAG_UPDATE_CURRENT | Notifier.immutableFlag());
            am.setAlarmClock(new AlarmManager.AlarmClockInfo(at, showPi), pi);
        } catch (SecurityException e) {
            am.set(AlarmManager.RTC_WAKEUP, at, pi);
        }
    }

    public static void cancel(Context ctx, long id) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        am.cancel(pending(ctx, id));
        Db.get(ctx).deleteReminder(id);
    }

    /** Re-arms everything after a reboot; drops stale one-shots, rolls the wake-up call forward. */
    public static void rescheduleAll(Context ctx) {
        Db db = Db.get(ctx);
        long now = System.currentTimeMillis();
        List<Db.Reminder> all = db.reminders(false);
        for (Db.Reminder r : all) {
            if (KIND_CALL.equals(r.kind) || KIND_CHECKIN.equals(r.kind)) continue;
            if (r.at > now) arm(ctx, r.id, r.kind, r.at);
            else db.deleteReminder(r.id);
        }
        scheduleWakeCall(ctx);
        scheduleCheckin(ctx);
    }

    /** (Re)creates the daily wake-up call from the current preferences. */
    public static void scheduleWakeCall(Context ctx) {
        Db db = Db.get(ctx);
        for (Db.Reminder r : db.reminders(false)) {
            if (KIND_CALL.equals(r.kind)) {
                ((AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE)).cancel(pending(ctx, r.id));
            }
        }
        db.deleteRemindersOfKind(KIND_CALL);
        Prefs p = new Prefs(ctx);
        if (!p.wakeEnabled()) return;
        long at = TimeParse.nextWake(System.currentTimeMillis(), p.wakeHour(), p.wakeMinute(), p.wakeDays(), TimeZone.getDefault());
        if (at < 0) return;
        schedule(ctx, KIND_CALL, at, ctx.getString(R.string.incoming_call), "");
    }

    /** (Re)creates the daily evening check-in from the current preferences. */
    public static void scheduleCheckin(Context ctx) {
        Db db = Db.get(ctx);
        for (Db.Reminder r : db.reminders(false)) {
            if (KIND_CHECKIN.equals(r.kind)) {
                ((AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE)).cancel(pending(ctx, r.id));
            }
        }
        db.deleteRemindersOfKind(KIND_CHECKIN);
        Prefs p = new Prefs(ctx);
        if (!p.checkinEnabled()) return;
        long at = TimeParse.nextOccurrence(System.currentTimeMillis(), p.checkinHour(), p.checkinMinute(), TimeZone.getDefault());
        schedule(ctx, KIND_CHECKIN, at, "Evening check-in", "");
    }
}
