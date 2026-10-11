package com.toiboytoestar.kurisu;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;

/** Notification channels and helpers. */
final class Notifier {
    private Notifier() {}

    static final String CH_REMINDERS = "reminders";
    static final String CH_CALL_BASE = "kurisu_call_";
    static final String CH_TIMER = "timers";
    static final String CH_XP = "xp_icon";
    static final int ID_CALL = 7001;
    static final int ID_XP = 7002;

    static int immutableFlag() {
        return Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0;
    }

    static Uri ringtoneUri(Context ctx, String rawName) {
        int id = ctx.getResources().getIdentifier(rawName, "raw", R.class.getPackage().getName());
        if (id == 0) id = R.raw.tone;
        return Uri.parse("android.resource://" + ctx.getPackageName() + "/" + id);
    }

    /** Channel sounds cannot be changed after creation, so each ringtone gets its own channel id. */
    static String callChannel(Context ctx) {
        return CH_CALL_BASE + new Prefs(ctx).ringtone();
    }

    static void ensureChannelsForRingtoneChange(Context ctx) {
        ensureChannels(ctx);
    }

    static void ensureChannels(Context ctx) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationChannel xpc = new NotificationChannel(CH_XP, "XP icon", NotificationManager.IMPORTANCE_LOW);
        xpc.setShowBadge(false);
        nm.createNotificationChannel(xpc);
        NotificationChannel r = new NotificationChannel(CH_REMINDERS, "Reminders & tasks", NotificationManager.IMPORTANCE_HIGH);
        r.enableVibration(true);
        nm.createNotificationChannel(r);

        AudioAttributes alarm = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build();
        NotificationChannel t = new NotificationChannel(CH_TIMER, "Timers & focus sessions", NotificationManager.IMPORTANCE_HIGH);
        t.enableVibration(true);
        t.setSound(android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI, alarm);
        nm.createNotificationChannel(t);

        AudioAttributes ring = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build();
        NotificationChannel c = new NotificationChannel(callChannel(ctx), "Kurisu calls", NotificationManager.IMPORTANCE_HIGH);
        c.enableVibration(true);
        c.setVibrationPattern(new long[]{0, 600, 400, 600, 400, 600});
        c.setSound(ringtoneUri(ctx, new Prefs(ctx).ringtone()), ring);
        c.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(c);
    }

    @SuppressWarnings("deprecation")
    static Notification.Builder builder(Context ctx, String channel) {
        if (Build.VERSION.SDK_INT >= 26) return new Notification.Builder(ctx, channel);
        return new Notification.Builder(ctx);
    }

    static void post(Context ctx, int id, String channel, String title, String text, PendingIntent content) {
        ensureChannels(ctx);
        Notification.Builder b = builder(ctx, channel)
                .setSmallIcon(R.drawable.xp2)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .setContentIntent(content);
        applyLegacyAlert(b, channel);
        notify(ctx, id, b.build());
    }

    @SuppressWarnings("deprecation")
    private static void applyLegacyAlert(Notification.Builder b, String channel) {
        if (Build.VERSION.SDK_INT >= 26) return;
        b.setPriority(Notification.PRIORITY_HIGH);
        b.setDefaults(Notification.DEFAULT_ALL);
    }

    static void postCall(Context ctx, String text) {
        ensureChannels(ctx);
        Intent i = new Intent(ctx, LaunchActivity.class);
        i.putExtra(LaunchActivity.EXTRA_INCOMING, true);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(ctx, ID_CALL, i, PendingIntent.FLAG_UPDATE_CURRENT | immutableFlag());
        Notification.Builder b = builder(ctx, callChannel(ctx))
                .setSmallIcon(R.drawable.xp2)
                .setContentTitle(ctx.getString(R.string.incoming_call))
                .setContentText(text)
                .setAutoCancel(true)
                .setOngoing(false)
                .setContentIntent(pi);
        if (Build.VERSION.SDK_INT >= 21) b.setCategory(Notification.CATEGORY_CALL);
        if (Build.VERSION.SDK_INT >= 26) b.setTimeoutAfter(90000);
        b.setFullScreenIntent(pi, true);
        legacyCall(ctx, b);
        Notification n = b.build();
        n.flags |= Notification.FLAG_INSISTENT;
        notify(ctx, ID_CALL, n);
    }

    @SuppressWarnings("deprecation")
    private static void legacyCall(Context ctx, Notification.Builder b) {
        if (Build.VERSION.SDK_INT >= 26) return;
        b.setPriority(Notification.PRIORITY_MAX);
        b.setSound(ringtoneUri(ctx, new Prefs(ctx).ringtone()), android.media.AudioManager.STREAM_RING);
        b.setVibrate(new long[]{0, 600, 400, 600, 400, 600});
    }

    static void cancel(Context ctx, int id) {
        ((NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE)).cancel(id);
    }

    private static void notify(Context ctx, int id, Notification n) {
        try {
            ((NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE)).notify(id, n);
        } catch (SecurityException ignored) {
            // POST_NOTIFICATIONS not granted
        }
    }

    /** Persistent "XP" icon like in the original app. */
    static void showXp(Context ctx) {
        ensureChannels(ctx);
        Intent i = new Intent(ctx, LaunchActivity.class);
        PendingIntent pi = PendingIntent.getActivity(ctx, ID_XP, i, PendingIntent.FLAG_UPDATE_CURRENT | immutableFlag());
        Notification.Builder b = builder(ctx, CH_XP)
                .setSmallIcon(R.drawable.xp2)
                .setContentTitle(ctx.getString(R.string.app_name))
                .setContentText(ctx.getString(R.string.notification_text))
                .setOnlyAlertOnce(true)
                .setContentIntent(pi);
        notify(ctx, ID_XP, b.build());
    }
}
