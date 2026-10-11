package com.toiboytoestar.kurisu;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import com.toiboytoestar.kurisu.core.CheckIn;

import java.util.Calendar;

/**
 * Not exported, so only our own PendingIntents (check-in notification, widget) can reach it. It stamps a short-lived token
 * and forwards a prompt / listen request to the exported MainActivity, which only honours requests carrying that token.
 */
public class TrampolineActivity extends Activity {
    static final String EXTRA_CHECKIN = "checkin";
    static final String EXTRA_LISTEN = "listen";

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Intent in = getIntent();
        Prefs p = new Prefs(this);
        Intent go = new Intent(this, MainActivity.class);
        go.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if (in != null && in.getBooleanExtra(EXTRA_CHECKIN, false)) {
            if (p.llmConfigured()) {
                p.raw().edit().putLong(MainActivity.PREF_PROMPT_TOKEN, System.currentTimeMillis()).apply();
                go.putExtra(MainActivity.EXTRA_PROMPT, checkinPrompt(p));
                go.putExtra(MainActivity.EXTRA_DISPLAY, "🌙 Evening check-in");
            }
        } else if (in != null && in.getBooleanExtra(EXTRA_LISTEN, false)) {
            p.raw().edit().putLong(MainActivity.PREF_LISTEN_TOKEN, System.currentTimeMillis()).apply();
            go.putExtra(MainActivity.EXTRA_LISTEN, true);
        }
        startActivity(go);
        finish();
    }

    private String checkinPrompt(Prefs p) {
        Db db = Db.get(this);
        long weekAgo = System.currentTimeMillis() - 7L * 24 * 3600 * 1000;
        CheckIn.Stats s = new CheckIn.Stats();
        s.streak = p.streak();
        s.tasksDoneWeek = db.tasksDoneSince(weekAgo);
        s.notesWeek = db.notesSince(weekAgo);
        s.cardsWeek = db.cardsSince(weekAgo);
        s.openTasks = db.tasks(false).size();
        s.dueCards = db.dueCardCount();
        s.focusMinutesToday = FocusTimer.todayMinutes(this);
        java.util.List<Db.Exam> ex = db.upcomingExams();
        if (!ex.isEmpty()) {
            int d = com.toiboytoestar.kurisu.core.TimeParse.daysUntil(System.currentTimeMillis(), ex.get(0).at, java.util.TimeZone.getDefault());
            s.nearestExam = ex.get(0).title + (d <= 0 ? " is today" : " in " + d + " days");
        }
        boolean sunday = Calendar.getInstance().get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY;
        return CheckIn.prompt(s, sunday);
    }
}
