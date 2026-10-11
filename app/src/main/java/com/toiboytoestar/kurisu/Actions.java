package com.toiboytoestar.kurisu;

import android.app.Activity;
import android.app.SearchManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.provider.AlarmClock;
import android.provider.CalendarContract;

import com.toiboytoestar.kurisu.core.TimeParse;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Things Kurisu can do on the phone. Used both for the model's [[tags]] and for offline voice commands.
 * Anything that leaves the app (open an app, web search, calendar) is deferred until she finished talking.
 */
final class Actions {
    private final Activity act;
    private final Prefs prefs;
    private final Db db;
    private final List<Runnable> deferred = new ArrayList<Runnable>();

    Actions(Activity act, Prefs prefs, Db db) {
        this.act = act;
        this.prefs = prefs;
        this.db = db;
    }

    // ------------------------------------------------------------------ deferral

    boolean hasDeferred() {
        return !deferred.isEmpty();
    }

    void flushDeferred() {
        List<Runnable> run = new ArrayList<Runnable>(deferred);
        deferred.clear();
        for (Runnable r : run) {
            try {
                r.run();
            } catch (ActivityNotFoundException ignored) {
            } catch (SecurityException ignored) {
            }
        }
    }

    void clearDeferred() {
        deferred.clear();
    }

    private void launch(final Intent i) {
        deferred.add(new Runnable() {
            @Override
            public void run() {
                act.startActivity(i);
            }
        });
    }

    // ---------------------------------------------------------------- primitives

    static String human(long seconds) {
        if (seconds % 3600 == 0 && seconds >= 3600) return (seconds / 3600) + (seconds == 3600 ? " hour" : " hours");
        if (seconds >= 3600) {
            long m = Math.round(seconds / 60.0);
            return (m / 60) + " h " + (m % 60) + " min";
        }
        if (seconds % 60 == 0) return (seconds / 60) + (seconds == 60 ? " minute" : " minutes");
        if (seconds > 60) return String.format(Locale.US, "%.1f minutes", seconds / 60.0);
        return seconds + " seconds";
    }

    static String clock(long at) {
        long now = System.currentTimeMillis();
        String pat = at - now > 20L * 3600 * 1000 ? "EEE d MMM HH:mm" : "HH:mm";
        return new SimpleDateFormat(pat, Locale.getDefault()).format(new Date(at));
    }

    String timer(long seconds, String label) {
        if (seconds < 1) seconds = 1;
        long at = System.currentTimeMillis() + seconds * 1000;
        String body = label != null && label.length() > 0 ? label : "Your " + human(seconds) + " timer is done.";
        Reminders.schedule(act, Reminders.KIND_TIMER, at, "Timer finished", body);
        return "Timer set for " + human(seconds) + ".";
    }

    String remind(long at, String text) {
        if (at <= System.currentTimeMillis()) at = System.currentTimeMillis() + 60000;
        Reminders.schedule(act, Reminders.KIND_REMINDER, at, "Reminder", text);
        return "I'll remind you at " + clock(at) + ": " + text;
    }

    String alarm(int hour, int minute, String label) {
        Intent i = new Intent(AlarmClock.ACTION_SET_ALARM)
                .putExtra(AlarmClock.EXTRA_HOUR, hour)
                .putExtra(AlarmClock.EXTRA_MINUTES, minute)
                .putExtra(AlarmClock.EXTRA_MESSAGE, label == null ? "" : label)
                .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        String when = String.format(Locale.US, "%02d:%02d", hour, minute);
        if (i.resolveActivity(act.getPackageManager()) != null) {
            try {
                act.startActivity(i); // SKIP_UI: sets the alarm without showing anything, so no need to defer
                return "Alarm set for " + when + ".";
            } catch (RuntimeException ignored) { }
        }
        // no clock app that accepts it: fall back to our own notification
        long at = TimeParse.nextOccurrence(System.currentTimeMillis(), hour, minute, TimeZone.getDefault());
        Reminders.schedule(act, Reminders.KIND_REMINDER, at, "Alarm", label != null && label.length() > 0 ? label : "Alarm for " + when);
        return "Alarm set for " + when + " (as a notification).";
    }

    String task(String title, long due) {
        long id = db.addTask(title, due);
        if (due > System.currentTimeMillis()) {
            long rid = Reminders.schedule(act, Reminders.KIND_TASK, due, "Task due", title);
            db.setTaskReminder(id, rid);
            return "Added to your tasks, due " + clock(due) + ": " + title;
        }
        return "Added to your tasks: " + title;
    }

    String exam(String title, long at) {
        db.addExam(title, at);
        int d = TimeParse.daysUntil(System.currentTimeMillis(), at, TimeZone.getDefault());
        return "Exam saved: " + title + (d >= 0 ? " in " + d + " days." : ".");
    }

    String note(String title, String body) {
        if (body == null || body.trim().length() == 0) {
            body = title;
            title = title.length() > 40 ? title.substring(0, 40) + "…" : title;
        }
        db.addNote(title, body);
        return "Saved a note: " + title;
    }

    String card(String front, String back) {
        db.addCard(front, back);
        return "Flashcard added.";
    }

    String focus(int minutes) {
        if (minutes < 1) minutes = 25;
        if (minutes > 180) minutes = 180;
        FocusTimer.start(act, FocusTimer.FOCUS, minutes);
        return "Focus session started: " + minutes + " minutes.";
    }

    String mem(String fact) {
        prefs.addMemory(fact);
        return "I'll remember that.";
    }

    // --------------------------------------------------------------- navigation

    /** Finds an installed launchable app by (fuzzy) name; null if none. */
    Intent resolveApp(String name) {
        String n = name.trim().toLowerCase(Locale.ROOT);
        if (n.length() == 0) return null;
        PackageManager pm = act.getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> apps = pm.queryIntentActivities(main, 0);
        ResolveInfo best = null;
        int bestScore = 0;
        for (ResolveInfo ri : apps) {
            String label = String.valueOf(ri.loadLabel(pm)).toLowerCase(Locale.ROOT);
            int score = 0;
            if (label.equals(n)) score = 100;
            else if (label.startsWith(n)) score = 80;
            else if (label.contains(n)) score = 60;
            else if (n.contains(label) && label.length() >= 4) score = 50;
            else if (ri.activityInfo.packageName.toLowerCase(Locale.ROOT).contains(n.replace(" ", ""))) score = 30;
            if (score > bestScore) {
                bestScore = score;
                best = ri;
            }
        }
        if (best != null) {
            Intent i = pm.getLaunchIntentForPackage(best.activityInfo.packageName);
            if (i != null) return i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        if (n.equals("browser") || n.equals("web browser") || n.equals("internet")) {
            return new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        if (n.equals("phone") || n.equals("dialer")) {
            return new Intent(Intent.ACTION_DIAL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        if (n.equals("camera")) {
            return new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        if (n.equals("settings")) {
            return new Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        return null;
    }

    boolean openApp(String name) {
        Intent i = resolveApp(name);
        if (i == null) return false;
        launch(i);
        return true;
    }

    void search(String query) {
        Intent i = new Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, query)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (i.resolveActivity(act.getPackageManager()) == null) {
            i = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(query)))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        launch(i);
    }

    void event(long start, String title, int minutes) {
        if (minutes <= 0) minutes = 60;
        Intent i = new Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)
                .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start)
                .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, start + minutes * 60000L)
                .putExtra(CalendarContract.Events.TITLE, title);
        launch(i);
    }

    // ---------------------------------------------------------------- tag router

    /**
     * Executes an action tag from the model. Returns true if it was understood and run.
     * restricted = true when untrusted web content is in the conversation: tags that leave the
     * app (open/search/event/alarm) are refused so a malicious page cannot drive the phone.
     */
    boolean dispatch(String name, String arg, boolean restricted) {
        long now = System.currentTimeMillis();
        TimeZone tz = TimeZone.getDefault();
        try {
            if (name.equals("task")) {
                String[] p = arg.split("\\|", 2);
                if (p[0].trim().length() == 0) return false;
                long due = p.length > 1 ? TimeParse.parse(p[1], now, tz) : 0;
                task(p[0].trim(), Math.max(0, due));
                return true;
            }
            if (name.equals("exam")) {
                String[] p = arg.split("\\|", 2);
                long at = p.length > 1 ? TimeParse.parse(p[1], now, tz) : -1;
                if (p[0].trim().length() == 0 || at < 0) return false;
                exam(p[0].trim(), at);
                return true;
            }
            if (name.equals("note")) {
                String[] p = arg.split("\\|", 2);
                if (p[0].trim().length() == 0) return false;
                note(p[0].trim(), p.length > 1 ? p[1].trim() : "");
                return true;
            }
            if (name.equals("card")) {
                String[] p = arg.split("\\|", 2);
                if (p.length < 2 || p[0].trim().length() == 0 || p[1].trim().length() == 0) return false;
                card(p[0], p[1]);
                return true;
            }
            if (name.equals("timer")) {
                String[] p = arg.split("\\|", 2);
                double minutes = Double.parseDouble(p[0].trim());
                if (minutes <= 0 || minutes > 24 * 60) return false;
                timer(Math.round(minutes * 60), p.length > 1 ? p[1].trim() : "");
                return true;
            }
            if (name.equals("remind")) {
                String[] p = arg.split("\\|", 2);
                long at = TimeParse.parse(p[0], now, tz);
                if (at < 0 || at <= now || p.length < 2 || p[1].trim().length() == 0) return false;
                remind(at, p[1].trim());
                return true;
            }
            if (name.equals("focus")) {
                focus((int) Math.round(Double.parseDouble(arg.trim())));
                return true;
            }
            if (restricted) return false; // untrusted content in context: no memory writes, no leaving the app
            if (name.equals("forget")) {
                return arg.length() > 0 && prefs.forgetMemory(arg) > 0;
            }
            if (name.equals("mem")) {
                if (arg.length() == 0) return false;
                mem(arg);
                return true;
            }
            if (name.equals("alarm")) {
                String[] p = arg.split("\\|", 2);
                String[] hm = p[0].trim().split(":");
                int h = Integer.parseInt(hm[0].trim());
                int m = hm.length > 1 ? Integer.parseInt(hm[1].trim()) : 0;
                if (h < 0 || h > 23 || m < 0 || m > 59) return false;
                alarm(h, m, p.length > 1 ? p[1].trim() : "");
                return true;
            }
            if (name.equals("open")) {
                return arg.length() > 0 && openApp(arg);
            }
            if (name.equals("search")) {
                if (arg.length() == 0) return false;
                search(arg);
                return true;
            }
            if (name.equals("event")) {
                String[] p = arg.split("\\|");
                long at = TimeParse.parse(p[0], now, tz);
                if (at < 0 || p.length < 2) return false;
                int dur = 60;
                if (p.length > 2) {
                    try {
                        dur = Integer.parseInt(p[2].trim());
                    } catch (NumberFormatException ignored) { }
                }
                event(at, p[1].trim(), dur);
                return true;
            }
        } catch (RuntimeException e) {
            return false; // malformed numbers etc.
        }
        return false;
    }
}
