package com.toiboytoestar.kurisu;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.toiboytoestar.kurisu.core.Msg;
import com.toiboytoestar.kurisu.core.Sm2;

import java.util.ArrayList;
import java.util.List;

/** Local storage for tasks, notes, flashcards, chat history and scheduled reminders. */
public final class Db extends SQLiteOpenHelper {
    private static Db instance;

    public static synchronized Db get(Context ctx) {
        if (instance == null) instance = new Db(ctx.getApplicationContext());
        return instance;
    }

    private final Context appCtx;

    private Db(Context ctx) {
        super(ctx, "kurisu.db", null, 2);
        appCtx = ctx;
    }

    @Override
    public void onCreate(SQLiteDatabase d) {
        d.execSQL("CREATE TABLE tasks(_id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, due INTEGER NOT NULL DEFAULT 0, done INTEGER NOT NULL DEFAULT 0, created INTEGER NOT NULL, reminder INTEGER NOT NULL DEFAULT 0, done_at INTEGER NOT NULL DEFAULT 0)");
        d.execSQL("CREATE TABLE notes(_id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, body TEXT NOT NULL, updated INTEGER NOT NULL)");
        d.execSQL("CREATE TABLE cards(_id INTEGER PRIMARY KEY AUTOINCREMENT, front TEXT NOT NULL, back TEXT NOT NULL, ease REAL NOT NULL, interval INTEGER NOT NULL, reps INTEGER NOT NULL, due INTEGER NOT NULL, created INTEGER NOT NULL)");
        d.execSQL("CREATE TABLE messages(_id INTEGER PRIMARY KEY AUTOINCREMENT, role TEXT NOT NULL, text TEXT NOT NULL, ts INTEGER NOT NULL)");
        d.execSQL("CREATE TABLE reminders(_id INTEGER PRIMARY KEY AUTOINCREMENT, kind TEXT NOT NULL, at INTEGER NOT NULL, title TEXT NOT NULL, body TEXT NOT NULL)");
        d.execSQL("CREATE TABLE exams(_id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, at INTEGER NOT NULL)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase d, int o, int n) {
        if (o < 2) {
            d.execSQL("ALTER TABLE tasks ADD COLUMN done_at INTEGER NOT NULL DEFAULT 0");
            d.execSQL("CREATE TABLE IF NOT EXISTS exams(_id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, at INTEGER NOT NULL)");
        }
    }

    // ------------------------------------------------------------------ models

    public static final class Task {
        public long id;
        public String title;
        public long due;
        public boolean done;
        public long reminder;
    }

    public static final class Note {
        public long id;
        public String title;
        public String body;
        public long updated;
    }

    public static final class Card {
        public long id;
        public String front;
        public String back;
        public double ease;
        public int interval;
        public int reps;
        public long due;
    }

    public static final class Reminder {
        public long id;
        public String kind;
        public long at;
        public String title;
        public String body;
    }

    // ------------------------------------------------------------------- tasks

    public long addTask(String title, long due) {
        ContentValues v = new ContentValues();
        v.put("title", title.trim());
        v.put("due", due);
        v.put("created", System.currentTimeMillis());
        long id = getWritableDatabase().insert("tasks", null, v);
        KurisuWidget.refresh(appCtx);
        return id;
    }

    public void setTaskReminder(long taskId, long reminderId) {
        ContentValues v = new ContentValues();
        v.put("reminder", reminderId);
        getWritableDatabase().update("tasks", v, "_id=?", new String[]{String.valueOf(taskId)});
    }

    public void setTaskDone(long id, boolean done) {
        ContentValues v = new ContentValues();
        v.put("done", done ? 1 : 0);
        v.put("done_at", done ? System.currentTimeMillis() : 0);
        getWritableDatabase().update("tasks", v, "_id=?", new String[]{String.valueOf(id)});
        KurisuWidget.refresh(appCtx);
    }

    public void deleteTask(long id) {
        getWritableDatabase().delete("tasks", "_id=?", new String[]{String.valueOf(id)});
        KurisuWidget.refresh(appCtx);
    }

    /** Open tasks first (soonest due first, undated last), then done ones. */
    public List<Task> tasks(boolean includeDone) {
        List<Task> out = new ArrayList<Task>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT _id,title,due,done,reminder FROM tasks " + (includeDone ? "" : "WHERE done=0 ")
                        + "ORDER BY done ASC, CASE WHEN due=0 THEN 1 ELSE 0 END, due ASC, _id DESC", null);
        try {
            while (c.moveToNext()) {
                Task t = new Task();
                t.id = c.getLong(0);
                t.title = c.getString(1);
                t.due = c.getLong(2);
                t.done = c.getInt(3) != 0;
                t.reminder = c.getLong(4);
                out.add(t);
            }
        } finally {
            c.close();
        }
        return out;
    }

    // ------------------------------------------------------------------- exams & stats

    public static final class Exam {
        public long id;
        public String title;
        public long at;
    }

    public long addExam(String title, long at) {
        ContentValues v = new ContentValues();
        v.put("title", title.trim());
        v.put("at", at);
        long id = getWritableDatabase().insert("exams", null, v);
        KurisuWidget.refresh(appCtx);
        return id;
    }

    public void deleteExam(long id) {
        getWritableDatabase().delete("exams", "_id=?", new String[]{String.valueOf(id)});
        KurisuWidget.refresh(appCtx);
    }

    /** Exams that have not passed yet (the exam day itself still counts), soonest first. */
    public List<Exam> upcomingExams() {
        List<Exam> out = new ArrayList<Exam>();
        java.util.Calendar sod = java.util.Calendar.getInstance();
        sod.set(java.util.Calendar.HOUR_OF_DAY, 0);
        sod.set(java.util.Calendar.MINUTE, 0);
        sod.set(java.util.Calendar.SECOND, 0);
        sod.set(java.util.Calendar.MILLISECOND, 0);
        long from = sod.getTimeInMillis(); // today's exam still counts, yesterday's does not
        Cursor c = getReadableDatabase().rawQuery("SELECT _id,title,at FROM exams WHERE at>=? ORDER BY at ASC",
                new String[]{String.valueOf(from)});
        try {
            while (c.moveToNext()) {
                Exam e = new Exam();
                e.id = c.getLong(0);
                e.title = c.getString(1);
                e.at = c.getLong(2);
                out.add(e);
            }
        } finally {
            c.close();
        }
        return out;
    }

    private int count(String sql, long since) {
        Cursor c = getReadableDatabase().rawQuery(sql, new String[]{String.valueOf(since)});
        try {
            return c.moveToFirst() ? c.getInt(0) : 0;
        } finally {
            c.close();
        }
    }

    public int tasksDoneSince(long ts) { return count("SELECT COUNT(*) FROM tasks WHERE done=1 AND done_at>=?", ts); }

    public int notesSince(long ts) { return count("SELECT COUNT(*) FROM notes WHERE updated>=?", ts); }

    public int cardsSince(long ts) { return count("SELECT COUNT(*) FROM cards WHERE created>=?", ts); }

    // ------------------------------------------------------------------- notes

    public long addNote(String title, String body) {
        ContentValues v = new ContentValues();
        v.put("title", title.trim().length() == 0 ? "Untitled" : title.trim());
        v.put("body", body);
        v.put("updated", System.currentTimeMillis());
        return getWritableDatabase().insert("notes", null, v);
    }

    public void updateNote(long id, String title, String body) {
        ContentValues v = new ContentValues();
        v.put("title", title.trim().length() == 0 ? "Untitled" : title.trim());
        v.put("body", body);
        v.put("updated", System.currentTimeMillis());
        getWritableDatabase().update("notes", v, "_id=?", new String[]{String.valueOf(id)});
    }

    public void deleteNote(long id) {
        getWritableDatabase().delete("notes", "_id=?", new String[]{String.valueOf(id)});
    }

    public List<Note> notes() {
        List<Note> out = new ArrayList<Note>();
        Cursor c = getReadableDatabase().rawQuery("SELECT _id,title,body,updated FROM notes ORDER BY updated DESC", null);
        try {
            while (c.moveToNext()) {
                Note n = new Note();
                n.id = c.getLong(0);
                n.title = c.getString(1);
                n.body = c.getString(2);
                n.updated = c.getLong(3);
                out.add(n);
            }
        } finally {
            c.close();
        }
        return out;
    }

    // ------------------------------------------------------------------- cards

    public long addCard(String front, String back) {
        ContentValues v = new ContentValues();
        v.put("front", front.trim());
        v.put("back", back.trim());
        v.put("ease", 2.5);
        v.put("interval", 0);
        v.put("reps", 0);
        v.put("due", 0);
        v.put("created", System.currentTimeMillis());
        return getWritableDatabase().insert("cards", null, v);
    }

    public void deleteCard(long id) {
        getWritableDatabase().delete("cards", "_id=?", new String[]{String.valueOf(id)});
    }

    public void gradeCard(Card c, int grade) {
        Sm2.State s = Sm2.next(c.ease, c.interval, c.reps, grade);
        ContentValues v = new ContentValues();
        v.put("ease", s.ease);
        v.put("interval", s.intervalDays);
        v.put("reps", s.reps);
        v.put("due", Sm2.dueAt(System.currentTimeMillis(), s));
        getWritableDatabase().update("cards", v, "_id=?", new String[]{String.valueOf(c.id)});
    }

    public int cardCount() {
        return (int) android.database.DatabaseUtils.queryNumEntries(getReadableDatabase(), "cards");
    }

    public int dueCardCount() {
        return (int) android.database.DatabaseUtils.queryNumEntries(getReadableDatabase(), "cards",
                "due<=?", new String[]{String.valueOf(System.currentTimeMillis())});
    }

    private Card readCard(Cursor c) {
        Card k = new Card();
        k.id = c.getLong(0);
        k.front = c.getString(1);
        k.back = c.getString(2);
        k.ease = c.getDouble(3);
        k.interval = c.getInt(4);
        k.reps = c.getInt(5);
        k.due = c.getLong(6);
        return k;
    }

    public List<Card> dueCards(int limit) {
        List<Card> out = new ArrayList<Card>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT _id,front,back,ease,interval,reps,due FROM cards WHERE due<=? ORDER BY due ASC LIMIT " + limit,
                new String[]{String.valueOf(System.currentTimeMillis())});
        try {
            while (c.moveToNext()) out.add(readCard(c));
        } finally {
            c.close();
        }
        return out;
    }

    public List<Card> recentCards(int limit) {
        List<Card> out = new ArrayList<Card>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT _id,front,back,ease,interval,reps,due FROM cards ORDER BY _id DESC LIMIT " + limit, null);
        try {
            while (c.moveToNext()) out.add(readCard(c));
        } finally {
            c.close();
        }
        return out;
    }

    // ---------------------------------------------------------------- messages

    public void addMessage(String role, String text) {
        if (text == null || text.trim().length() == 0) return;
        ContentValues v = new ContentValues();
        v.put("role", role);
        v.put("text", text);
        v.put("ts", System.currentTimeMillis());
        SQLiteDatabase d = getWritableDatabase();
        d.insert("messages", null, v);
        // keep the log bounded
        d.execSQL("DELETE FROM messages WHERE _id <= (SELECT MAX(_id) FROM messages) - 500");
    }

    public List<Msg> recentMessages(int limit) {
        List<Msg> out = new ArrayList<Msg>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT role,text FROM messages ORDER BY _id DESC LIMIT " + limit, null);
        try {
            while (c.moveToNext()) out.add(0, new Msg(c.getString(0), c.getString(1)));
        } finally {
            c.close();
        }
        return out;
    }

    public List<Object[]> log(int limit) {
        List<Object[]> out = new ArrayList<Object[]>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT role,text,ts FROM messages ORDER BY _id DESC LIMIT " + limit, null);
        try {
            while (c.moveToNext()) out.add(0, new Object[]{c.getString(0), c.getString(1), c.getLong(2)});
        } finally {
            c.close();
        }
        return out;
    }

    public void clearMessages() {
        getWritableDatabase().delete("messages", null, null);
    }

    // --------------------------------------------------------------- reminders

    public long addReminder(String kind, long at, String title, String body) {
        ContentValues v = new ContentValues();
        v.put("kind", kind);
        v.put("at", at);
        v.put("title", title);
        v.put("body", body);
        return getWritableDatabase().insert("reminders", null, v);
    }

    public void deleteReminder(long id) {
        getWritableDatabase().delete("reminders", "_id=?", new String[]{String.valueOf(id)});
    }

    public void deleteRemindersOfKind(String kind) {
        getWritableDatabase().delete("reminders", "kind=?", new String[]{kind});
    }

    public Reminder reminder(long id) {
        Cursor c = getReadableDatabase().rawQuery("SELECT _id,kind,at,title,body FROM reminders WHERE _id=?",
                new String[]{String.valueOf(id)});
        try {
            return c.moveToFirst() ? readReminder(c) : null;
        } finally {
            c.close();
        }
    }

    public List<Reminder> reminders(boolean upcomingOnly) {
        List<Reminder> out = new ArrayList<Reminder>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT _id,kind,at,title,body FROM reminders " + (upcomingOnly ? "WHERE at>" + (System.currentTimeMillis() - 1000) + " " : "") + "ORDER BY at ASC", null);
        try {
            while (c.moveToNext()) out.add(readReminder(c));
        } finally {
            c.close();
        }
        return out;
    }

    private Reminder readReminder(Cursor c) {
        Reminder r = new Reminder();
        r.id = c.getLong(0);
        r.kind = c.getString(1);
        r.at = c.getLong(2);
        r.title = c.getString(3);
        r.body = c.getString(4);
        return r;
    }
}
