package com.toiboytoestar.kurisu;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import com.toiboytoestar.kurisu.core.TimeParse;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/** Home-screen widget: nearest exam, today's open tasks, streak, and a one-tap "talk" button. */
public class KurisuWidget extends AppWidgetProvider {

    @Override
    public void onUpdate(Context ctx, AppWidgetManager mgr, int[] ids) {
        for (int id : ids) mgr.updateAppWidget(id, build(ctx));
    }

    /** Call after tasks or exams change so the widget does not wait for its 30-minute refresh. */
    static void refresh(Context ctx) {
        try {
            AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
            int[] ids = mgr.getAppWidgetIds(new ComponentName(ctx, KurisuWidget.class));
            if (ids != null && ids.length > 0) for (int id : ids) mgr.updateAppWidget(id, build(ctx));
        } catch (Exception ignored) { }
    }

    private static RemoteViews build(Context ctx) {
        RemoteViews v = new RemoteViews(ctx.getPackageName(), R.layout.widget_kurisu);
        Prefs p = new Prefs(ctx);
        Db db = Db.get(ctx);
        StringBuilder body = new StringBuilder();
        List<Db.Exam> exams = db.upcomingExams();
        if (!exams.isEmpty()) {
            int d = TimeParse.daysUntil(System.currentTimeMillis(), exams.get(0).at, TimeZone.getDefault());
            body.append("📅 ").append(exams.get(0).title).append(d <= 0 ? " TODAY" : " in " + d + "d").append("\n");
        }
        List<Db.Task> tasks = db.tasks(false);
        SimpleDateFormat f = new SimpleDateFormat("HH:mm", Locale.getDefault());
        for (int i = 0; i < tasks.size() && i < 3; i++) {
            Db.Task t = tasks.get(i);
            body.append("• ").append(t.title);
            if (t.due > 0) body.append("  ").append(f.format(new Date(t.due)));
            body.append("\n");
        }
        if (tasks.size() > 3) body.append("+").append(tasks.size() - 3).append(" more\n");
        if (tasks.isEmpty() && exams.isEmpty()) body.append("Nothing planned. Ask me to plan your day.");
        int due = db.dueCardCount();
        if (due > 0) body.append("🃏 ").append(due).append(" cards due");
        int streak = p.streak();
        v.setTextViewText(R.id.widget_title, streak >= 2 ? "KURISU  🔥 " + streak : "KURISU");
        v.setTextViewText(R.id.widget_body, body.toString().trim());

        Intent talk = new Intent(ctx, TrampolineActivity.class);
        talk.putExtra(TrampolineActivity.EXTRA_LISTEN, true);
        v.setOnClickPendingIntent(R.id.widget_talk,
                PendingIntent.getActivity(ctx, 9200, talk, PendingIntent.FLAG_UPDATE_CURRENT | Notifier.immutableFlag()));
        Intent open = new Intent(ctx, HubActivity.class);
        v.setOnClickPendingIntent(R.id.widget_body,
                PendingIntent.getActivity(ctx, 9201, open, PendingIntent.FLAG_UPDATE_CURRENT | Notifier.immutableFlag()));
        v.setOnClickPendingIntent(R.id.widget_title,
                PendingIntent.getActivity(ctx, 9202, new Intent(ctx, LaunchActivity.class), PendingIntent.FLAG_UPDATE_CURRENT | Notifier.immutableFlag()));
        return v;
    }
}
