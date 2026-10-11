package com.toiboytoestar.kurisu;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Fires when a reminder, timer, focus session or the wake-up call is due. */
public class ReminderReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        long id = intent.getLongExtra(Reminders.EXTRA_ID, -1);
        if (id < 0) return;
        Db db = Db.get(ctx);
        Db.Reminder r = db.reminder(id);
        if (r == null) return;
        db.deleteReminder(id);

        if (Reminders.KIND_CALL.equals(r.kind)) {
            Notifier.postCall(ctx, ctx.getString(R.string.call));
            Reminders.scheduleWakeCall(ctx); // roll over to the next day
            return;
        }
        if (Reminders.KIND_CHECKIN.equals(r.kind)) {
            Reminders.scheduleCheckin(ctx); // roll over to tomorrow
            Intent t = new Intent(ctx, TrampolineActivity.class);
            t.putExtra(TrampolineActivity.EXTRA_CHECKIN, true);
            PendingIntent tp = PendingIntent.getActivity(ctx, 9100, t, PendingIntent.FLAG_UPDATE_CURRENT | Notifier.immutableFlag());
            Notifier.post(ctx, 9100, Notifier.CH_REMINDERS, "Evening check-in",
                    "How did today go? Tap to talk it over with Kurisu.", tp);
            return;
        }
        String channel = Reminders.KIND_TIMER.equals(r.kind) || Reminders.KIND_FOCUS.equals(r.kind)
                ? Notifier.CH_TIMER : Notifier.CH_REMINDERS;
        Intent open = new Intent(ctx, LaunchActivity.class);
        PendingIntent pi = PendingIntent.getActivity(ctx, (int) (id % Integer.MAX_VALUE), open,
                PendingIntent.FLAG_UPDATE_CURRENT | Notifier.immutableFlag());
        Notifier.post(ctx, (int) (100000 + id % 800000), channel, r.title, r.body.length() > 0 ? r.body : r.title, pi);
    }
}
