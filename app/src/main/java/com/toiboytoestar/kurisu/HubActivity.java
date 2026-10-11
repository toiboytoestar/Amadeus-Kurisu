package com.toiboytoestar.kurisu;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Study & life hub: tasks, notes, flashcards (spaced repetition), focus timer and conversation log. */
public class HubActivity extends Activity {

    static final String EXTRA_TAB = "tab";
    private static final String[] TABS = {"tasks", "exams", "notes", "cards", "memory", "focus", "log"};
    private static final String[] TAB_LABELS = {"TASKS", "EXAMS", "NOTES", "CARDS", "MEMORY", "FOCUS", "LOG"};

    private final Handler h = new Handler(Looper.getMainLooper());
    private Db db;
    private LinearLayout content;
    private TextView[] tabViews;
    private String tab = "tasks";
    private long pendingDue;

    // review state
    private List<Db.Card> queue;
    private int qi;
    private boolean revealed;
    private int reviewed;

    // focus view
    private TextView focusClock;
    private TextView focusLabel;
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!"focus".equals(tab) || focusClock == null) return;
            String done = FocusTimer.completeIfDue(HubActivity.this);
            if (done.length() > 0) {
                Toast.makeText(HubActivity.this, FocusTimer.FOCUS.equals(done) ? "Focus session complete. Nice work!" : "Break over.", Toast.LENGTH_LONG).show();
                show("focus");
                return;
            }
            String phase = FocusTimer.phase(HubActivity.this);
            focusClock.setText(phase.length() == 0 ? "00:00" : FocusTimer.format(FocusTimer.remainingMs(HubActivity.this)));
            h.postDelayed(this, 500);
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        db = Db.get(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(UiKit.BG);
        root.setPadding(UiKit.dp(this, 14), UiKit.dp(this, 28), UiKit.dp(this, 14), 0);

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = UiKit.text(this, "STUDY HUB", 18, UiKit.GOLD);
        title.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        head.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView close = UiKit.button(this, "BACK");
        close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        head.addView(close);
        root.addView(head);

        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout bar = new LinearLayout(this);
        tabViews = new TextView[TABS.length];
        for (int i = 0; i < TABS.length; i++) {
            final String t = TABS[i];
            TextView v = UiKit.button(this, TAB_LABELS[i]);
            v.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View x) {
                    show(t);
                }
            });
            bar.addView(v, UiKit.lp(this, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 10, 6, 6));
            tabViews[i] = v;
        }
        hs.addView(bar);
        root.addView(hs);

        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, 0, 0, UiKit.dp(this, 24));
        sv.addView(content, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(root);

        String start = getIntent().getStringExtra(EXTRA_TAB);
        show(start != null && start.length() > 0 ? start : "tasks");
    }

    @Override
    protected void onResume() {
        super.onResume();
        if ("focus".equals(tab)) {
            h.removeCallbacks(tick);
            h.post(tick);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        h.removeCallbacks(tick);
    }

    private void show(String which) {
        tab = which;
        h.removeCallbacks(tick);
        for (int i = 0; i < TABS.length; i++) {
            boolean on = TABS[i].equals(which);
            tabViews[i].setTextColor(on ? 0xFF000000 : UiKit.GOLD);
            tabViews[i].setBackground(on ? UiKit.box(this, UiKit.GOLD, UiKit.GOLD, 16) : UiKit.box(this, 0x33000000, 0x88FFCF40, 16));
        }
        content.removeAllViews();
        if ("memory".equals(which)) showMemory();
        else if ("exams".equals(which)) showExams();
        else if ("notes".equals(which)) showNotes();
        else if ("cards".equals(which)) showCards();
        else if ("focus".equals(which)) showFocus();
        else if ("log".equals(which)) showLog();
        else showTasks();
    }

    private void add(View v) {
        content.addView(v, UiKit.lp(this, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 4, 0, 4));
    }

    private void addRaw(View v, LinearLayout.LayoutParams lp) {
        content.addView(v, lp);
    }

    private static String when(long ms) {
        return new SimpleDateFormat("EEE d MMM, HH:mm", Locale.getDefault()).format(new Date(ms));
    }

    // ------------------------------------------------------------------- tasks

    private void showTasks() {
        LinearLayout in = new LinearLayout(this);
        in.setGravity(Gravity.CENTER_VERTICAL);
        final EditText e = UiKit.edit(this, "New task…", false);
        e.setSingleLine(true);
        in.addView(e, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        final TextView dueBtn = UiKit.button(this, pendingDue > 0 ? "⏰ " + when(pendingDue) : "⏰");
        dueBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickDateTime(new PickCallback() {
                    @Override
                    public void picked(long ms) {
                        pendingDue = ms;
                        dueBtn.setText("⏰ " + when(ms));
                    }
                });
            }
        });
        in.addView(dueBtn, UiKit.lp(this, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 6, 0, 0, 0));
        TextView addBtn = UiKit.button(this, "ADD");
        addBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String t = e.getText().toString().trim();
                if (t.length() == 0) return;
                long id = db.addTask(t, pendingDue);
                if (pendingDue > System.currentTimeMillis()) {
                    db.setTaskReminder(id, Reminders.schedule(HubActivity.this, Reminders.KIND_TASK, pendingDue, "Task due", t));
                }
                pendingDue = 0;
                show("tasks");
            }
        });
        in.addView(addBtn, UiKit.lp(this, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 6, 0, 0, 0));
        add(in);

        List<Db.Task> all = db.tasks(true);
        int open = 0, done = 0;
        for (Db.Task t : all) {
            if (t.done) done++;
            else open++;
        }
        if (open == 0) add(UiKit.text(this, "Nothing to do. Suspiciously productive, senpai.", 13, UiKit.DIM));
        for (Db.Task t : all) if (!t.done) add(taskRow(t));
        if (done > 0) {
            add(UiKit.title(this, "Done (" + done + ")"));
            for (Db.Task t : all) if (t.done) add(taskRow(t));
            TextView clear = UiKit.button(this, "CLEAR DONE");
            clear.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    for (Db.Task t : db.tasks(true)) if (t.done) db.deleteTask(t.id);
                    show("tasks");
                }
            });
            add(clear);
        }

        List<Db.Reminder> rem = new ArrayList<Db.Reminder>();
        for (Db.Reminder r : db.reminders(true)) if (!Reminders.KIND_CALL.equals(r.kind) && !Reminders.KIND_CHECKIN.equals(r.kind) && !Reminders.KIND_FOCUS.equals(r.kind)) rem.add(r);
        if (!rem.isEmpty()) {
            add(UiKit.title(this, "Upcoming reminders & timers"));
            for (final Db.Reminder r : rem) {
                LinearLayout row = new LinearLayout(this);
                row.setGravity(Gravity.CENTER_VERTICAL);
                TextView t = UiKit.text(this, when(r.at) + "  " + (r.body.length() > 0 ? r.body : r.title), 13, UiKit.INK);
                row.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                TextView x = UiKit.button(this, "✕");
                x.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Reminders.cancel(HubActivity.this, r.id);
                        show("tasks");
                    }
                });
                row.addView(x);
                add(UiKit.card(this, row));
            }
        }
    }

    private View taskRow(final Db.Task t) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        CheckBox cb = new CheckBox(this);
        cb.setChecked(t.done);
        cb.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton b, boolean checked) {
                db.setTaskDone(t.id, checked);
                if (checked && t.reminder > 0) {
                    Reminders.cancel(HubActivity.this, t.reminder);
                    db.setTaskReminder(t.id, 0);
                }
                show("tasks");
            }
        });
        row.addView(cb);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView title = UiKit.text(this, t.title, 14, t.done ? UiKit.DIM : UiKit.INK);
        if (t.done) title.setPaintFlags(title.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
        col.addView(title);
        if (t.due > 0) {
            boolean late = !t.done && t.due < System.currentTimeMillis();
            col.addView(UiKit.text(this, "due " + when(t.due), 11, late ? 0xFFFF6A5A : UiKit.GOLD));
        }
        row.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView x = UiKit.button(this, "✕");
        x.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (t.reminder > 0) Reminders.cancel(HubActivity.this, t.reminder);
                db.deleteTask(t.id);
                show("tasks");
            }
        });
        row.addView(x);
        return UiKit.card(this, row);
    }

    interface PickCallback {
        void picked(long ms);
    }

    private void pickDateTime(final PickCallback cb) {
        final Calendar c = Calendar.getInstance();
        new DatePickerDialog(this, android.R.style.Theme_Material_Dialog_Alert, new DatePickerDialog.OnDateSetListener() {
            @Override
            public void onDateSet(DatePicker view, final int y, final int m, final int d) {
                new TimePickerDialog(HubActivity.this, android.R.style.Theme_Material_Dialog_Alert, new TimePickerDialog.OnTimeSetListener() {
                    @Override
                    public void onTimeSet(TimePicker v, int hour, int min) {
                        Calendar r = Calendar.getInstance();
                        r.clear();
                        r.set(y, m, d, hour, min, 0);
                        cb.picked(r.getTimeInMillis());
                    }
                }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true).show();
            }
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
    }

    // ------------------------------------------------------------------ memory

    private void showMemory() {
        final Prefs prefs = new Prefs(this);
        add(UiKit.text(this, "Things Kurisu remembers about you. She uses these in every conversation. Delete anything you don't want her to keep, or tell her \"forget …\".", 12, UiKit.DIM));
        LinearLayout in = new LinearLayout(this);
        in.setGravity(Gravity.CENTER_VERTICAL);
        final EditText e = UiKit.edit(this, "Add something to remember…", false);
        e.setSingleLine(true);
        in.addView(e, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView addBtn = UiKit.button(this, "ADD");
        addBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String t = e.getText().toString().trim();
                if (t.length() == 0) return;
                prefs.addMemory(t);
                show("memory");
            }
        });
        in.addView(addBtn, UiKit.lp(this, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 6, 0, 0, 0));
        add(in);
        final List<String> lines = prefs.memoryLines();
        if (lines.isEmpty()) add(UiKit.text(this, "Nothing yet. Tell Kurisu things like \"I study biology at university\" and she will remember.", 13, UiKit.DIM));
        for (int i = 0; i < lines.size(); i++) {
            final int idx = i;
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.addView(UiKit.text(this, lines.get(i), 14, UiKit.INK), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            TextView x = UiKit.button(this, "✕");
            x.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    List<String> now = prefs.memoryLines();
                    if (idx < now.size()) now.remove(idx);
                    prefs.setMemoryLines(now);
                    show("memory");
                }
            });
            row.addView(x);
            add(UiKit.card(this, row));
        }
        if (!lines.isEmpty()) {
            TextView clear = UiKit.button(this, "FORGET EVERYTHING");
            clear.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    UiKit.dialog(HubActivity.this).setMessage("Delete everything Kurisu remembers about you?")
                            .setPositiveButton("Delete", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface d, int w) {
                                    prefs.setMemory("");
                                    show("memory");
                                }
                            }).setNegativeButton("Cancel", null).show();
                }
            });
            add(clear);
        }
    }

    // ------------------------------------------------------------------- exams

    private long pendingExam;

    private void showExams() {
        add(UiKit.text(this, "Add your exams and deadlines. Kurisu counts the days down, mentions them in your morning briefing and can build a study plan.", 12, UiKit.DIM));
        LinearLayout in = new LinearLayout(this);
        in.setGravity(Gravity.CENTER_VERTICAL);
        final EditText e = UiKit.edit(this, "Exam / deadline name…", false);
        e.setSingleLine(true);
        in.addView(e, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        final TextView dateBtn = UiKit.button(this, pendingExam > 0 ? "📅 " + new SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(new Date(pendingExam)) : "📅 DATE");
        dateBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final Calendar c = Calendar.getInstance();
                new DatePickerDialog(HubActivity.this, android.R.style.Theme_Material_Dialog_Alert, new DatePickerDialog.OnDateSetListener() {
                    @Override
                    public void onDateSet(DatePicker view, int y, int m, int d) {
                        Calendar r = Calendar.getInstance();
                        r.clear();
                        r.set(y, m, d, 9, 0, 0);
                        pendingExam = r.getTimeInMillis();
                        dateBtn.setText("📅 " + new SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(new Date(pendingExam)));
                    }
                }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
            }
        });
        in.addView(dateBtn, UiKit.lp(this, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 6, 0, 0, 0));
        TextView addBtn = UiKit.button(this, "ADD");
        addBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String t = e.getText().toString().trim();
                if (t.length() == 0 || pendingExam <= 0) {
                    Toast.makeText(HubActivity.this, "Enter a name and pick a date", Toast.LENGTH_SHORT).show();
                    return;
                }
                db.addExam(t, pendingExam);
                pendingExam = 0;
                show("exams");
            }
        });
        in.addView(addBtn, UiKit.lp(this, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 6, 0, 0, 0));
        add(in);

        List<Db.Exam> exams = db.upcomingExams();
        if (exams.isEmpty()) add(UiKit.text(this, "No exams yet. Tell Kurisu \"I have a physics exam on 20 November\" and she will save it.", 13, UiKit.DIM));
        for (final Db.Exam ex : exams) {
            int days = com.toiboytoestar.kurisu.core.TimeParse.daysUntil(System.currentTimeMillis(), ex.at, java.util.TimeZone.getDefault());
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            TextView big = UiKit.text(this, days <= 0 ? "TODAY" : days + "d", 20, days <= 3 ? 0xFFFF6A5A : UiKit.GOLD);
            big.setTypeface(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD);
            big.setMinWidth(UiKit.dp(this, 70));
            row.addView(big);
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.addView(UiKit.text(this, ex.title, 15, UiKit.INK));
            col.addView(UiKit.text(this, new SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault()).format(new Date(ex.at)), 12, UiKit.DIM));
            row.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            TextView x = UiKit.button(this, "✕");
            x.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    db.deleteExam(ex.id);
                    show("exams");
                }
            });
            row.addView(x);
            add(UiKit.card(this, row));
        }
        if (!exams.isEmpty()) {
            TextView plan = UiKit.button(this, "ASK KURISU FOR A STUDY PLAN");
            plan.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    askKurisu("Plan my study time for my upcoming exams. Start with the nearest one, be realistic about my agenda, and ask me what I still need to cover.");
                }
            });
            add(plan);
        }
    }

    /** Hands a prompt to the conversation screen (guarded by a fresh token, see MainActivity). */
    private void askKurisu(String prompt) {
        getSharedPreferences("amadeus_prefs", MODE_PRIVATE).edit().putLong(MainActivity.PREF_PROMPT_TOKEN, System.currentTimeMillis()).apply();
        Intent i = new Intent(this, MainActivity.class);
        i.putExtra(MainActivity.EXTRA_PROMPT, prompt);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(i);
        finish();
    }

    // ------------------------------------------------------------------- notes

    private void showNotes() {
        TextView add = UiKit.button(this, "+ NEW NOTE");
        add.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                editNote(null);
            }
        });
        add(add);
        List<Db.Note> notes = db.notes();
        if (notes.isEmpty()) add(UiKit.text(this, "No notes yet. Ask me to \"take a note\", or save research summaries here.", 13, UiKit.DIM));
        for (final Db.Note n : notes) {
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            TextView t = UiKit.text(this, n.title, 15, UiKit.GOLD);
            t.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
            col.addView(t);
            String preview = n.body.length() > 140 ? n.body.substring(0, 140) + "…" : n.body;
            col.addView(UiKit.text(this, preview, 13, UiKit.INK));
            col.addView(UiKit.text(this, when(n.updated), 11, UiKit.DIM));
            View card = UiKit.card(this, col);
            card.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    editNote(n);
                }
            });
            add(card);
        }
    }

    private void editNote(final Db.Note n) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(UiKit.dp(this, 16), UiKit.dp(this, 8), UiKit.dp(this, 16), 0);
        final EditText title = UiKit.edit(this, "Title", false);
        final EditText body = UiKit.edit(this, "Write something…", true);
        body.setMinLines(6);
        body.setGravity(Gravity.TOP);
        if (n != null) {
            title.setText(n.title);
            body.setText(n.body);
        }
        l.addView(title);
        l.addView(body, UiKit.lp(this, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 8, 0, 0));
        AlertDialog.Builder b = UiKit.dialog(this).setView(l)
                .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        String t = title.getText().toString();
                        String bd = body.getText().toString();
                        if (t.trim().length() == 0 && bd.trim().length() == 0) return;
                        if (n == null) db.addNote(t, bd);
                        else db.updateNote(n.id, t, bd);
                        show("notes");
                    }
                })
                .setNegativeButton("Cancel", null);
        if (n != null) {
            b.setNeutralButton("Delete", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface d, int w) {
                    db.deleteNote(n.id);
                    show("notes");
                }
            });
        }
        b.show();
    }

    // ------------------------------------------------------------------- cards

    private void showCards() {
        if (queue != null) {
            showReview();
            return;
        }
        int total = db.cardCount();
        final int due = db.dueCardCount();
        add(UiKit.text(this, total + " cards · " + due + " due now", 14, UiKit.INK));
        LinearLayout row = new LinearLayout(this);
        TextView review = UiKit.button(this, "REVIEW (" + due + ")");
        review.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                queue = db.dueCards(50);
                qi = 0;
                revealed = false;
                reviewed = 0;
                if (queue.isEmpty()) {
                    queue = null;
                    Toast.makeText(HubActivity.this, "Nothing due. Come back later!", Toast.LENGTH_SHORT).show();
                }
                show("cards");
            }
        });
        TextView addCard = UiKit.button(this, "+ ADD CARD");
        addCard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addCardDialog();
            }
        });
        row.addView(review);
        row.addView(addCard, UiKit.lp(this, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 8, 0, 0, 0));
        add(row);
        add(UiKit.text(this, "Tip: tell Kurisu \"make flashcards about the Krebs cycle\" and she will build a deck.", 12, UiKit.DIM));
        add(UiKit.title(this, "Recent cards"));
        for (final Db.Card c : db.recentCards(30)) {
            LinearLayout r = new LinearLayout(this);
            r.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.addView(UiKit.text(this, c.front, 14, UiKit.GOLD));
            col.addView(UiKit.text(this, c.back, 13, UiKit.INK));
            r.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            TextView x = UiKit.button(this, "✕");
            x.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    db.deleteCard(c.id);
                    show("cards");
                }
            });
            r.addView(x);
            add(UiKit.card(this, r));
        }
    }

    private void addCardDialog() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(UiKit.dp(this, 16), UiKit.dp(this, 8), UiKit.dp(this, 16), 0);
        final EditText f = UiKit.edit(this, "Front (question)", true);
        final EditText b = UiKit.edit(this, "Back (answer)", true);
        l.addView(f);
        l.addView(b, UiKit.lp(this, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 8, 0, 0));
        UiKit.dialog(this).setView(l).setPositiveButton("Add", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface d, int w) {
                if (f.getText().toString().trim().length() == 0 || b.getText().toString().trim().length() == 0) return;
                db.addCard(f.getText().toString(), b.getText().toString());
                show("cards");
            }
        }).setNegativeButton("Cancel", null).show();
    }

    private void showReview() {
        if (qi >= queue.size()) {
            add(UiKit.title(this, "Session complete"));
            add(UiKit.text(this, "Reviewed " + reviewed + " cards. Good work, senpai.", 15, UiKit.INK));
            TextView done = UiKit.button(this, "DONE");
            done.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    queue = null;
                    show("cards");
                }
            });
            add(done);
            return;
        }
        final Db.Card c = queue.get(qi);
        add(UiKit.text(this, "Card " + (qi + 1) + " / " + queue.size(), 12, UiKit.DIM));
        TextView front = UiKit.text(this, c.front, 20, UiKit.GOLD);
        front.setPadding(0, UiKit.dp(this, 16), 0, UiKit.dp(this, 8));
        add(front);
        if (!revealed) {
            TextView show = UiKit.button(this, "SHOW ANSWER");
            show.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    revealed = true;
                    show("cards");
                }
            });
            add(show);
        } else {
            add(UiKit.card(this, UiKit.text(this, c.back, 17, UiKit.INK)));
            LinearLayout row = new LinearLayout(this);
            String[] labels = {"AGAIN", "HARD", "GOOD", "EASY"};
            for (int i = 0; i < 4; i++) {
                final int grade = i;
                TextView g = UiKit.button(this, labels[i]);
                g.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        db.gradeCard(c, grade);
                        reviewed++;
                        qi++;
                        revealed = false;
                        show("cards");
                    }
                });
                row.addView(g, UiKit.lp(this, 0, ViewGroup.LayoutParams.WRAP_CONTENT, 3, 8, 3, 0));
                ((LinearLayout.LayoutParams) g.getLayoutParams()).weight = 1;
            }
            add(row);
        }
        TextView quit = UiKit.button(this, "END SESSION");
        quit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                queue = null;
                show("cards");
            }
        });
        addRaw(quit, UiKit.lp(this, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 24, 0, 0));
    }

    // ------------------------------------------------------------------- focus

    private void showFocus() {
        String phase = FocusTimer.phase(this);
        focusLabel = UiKit.text(this, phase.length() == 0 ? "READY" : FocusTimer.FOCUS.equals(phase) ? "FOCUS" : "BREAK", 14, UiKit.GOLD);
        focusLabel.setGravity(Gravity.CENTER);
        focusLabel.setPadding(0, UiKit.dp(this, 24), 0, 0);
        add(focusLabel);
        focusClock = UiKit.text(this, "00:00", 64, UiKit.INK);
        focusClock.setGravity(Gravity.CENTER);
        add(focusClock);
        if (phase.length() == 0) {
            add(UiKit.text(this, "Pick a session. I'll notify you when time is up, even if the app is closed.", 12, UiKit.DIM));
            LinearLayout r1 = new LinearLayout(this);
            r1.setGravity(Gravity.CENTER);
            int[] mins = {15, 25, 50};
            for (final int m : mins) {
                TextView b = UiKit.button(this, "FOCUS " + m);
                b.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        FocusTimer.start(HubActivity.this, FocusTimer.FOCUS, m);
                        show("focus");
                    }
                });
                r1.addView(b, UiKit.lp(this, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 4, 8, 4, 0));
            }
            add(r1);
            LinearLayout r2 = new LinearLayout(this);
            r2.setGravity(Gravity.CENTER);
            int[] br = {5, 10};
            for (final int m : br) {
                TextView b = UiKit.button(this, "BREAK " + m);
                b.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        FocusTimer.start(HubActivity.this, FocusTimer.BREAK, m);
                        show("focus");
                    }
                });
                r2.addView(b, UiKit.lp(this, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 4, 8, 4, 0));
            }
            add(r2);
        } else {
            LinearLayout r = new LinearLayout(this);
            r.setGravity(Gravity.CENTER);
            final boolean paused = FocusTimer.paused(this);
            TextView pr = UiKit.button(this, paused ? "RESUME" : "PAUSE");
            pr.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (paused) FocusTimer.resume(HubActivity.this);
                    else FocusTimer.pause(HubActivity.this);
                    show("focus");
                }
            });
            TextView stop = UiKit.button(this, "STOP");
            stop.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    FocusTimer.stop(HubActivity.this);
                    show("focus");
                }
            });
            r.addView(pr, UiKit.lp(this, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 4, 12, 4, 0));
            r.addView(stop, UiKit.lp(this, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 4, 12, 4, 0));
            add(r);
        }
        TextView stats = UiKit.text(this, "Today: " + FocusTimer.todaySessions(this) + " sessions · " + FocusTimer.todayMinutes(this) + " min focused", 13, UiKit.INK);
        stats.setGravity(Gravity.CENTER);
        stats.setPadding(0, UiKit.dp(this, 20), 0, 0);
        add(stats);
        h.post(tick);
    }

    // --------------------------------------------------------------------- log

    private void showLog() {
        LinearLayout row = new LinearLayout(this);
        TextView share = UiKit.button(this, "SHARE");
        final List<Object[]> log = db.log(100);
        share.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                StringBuilder sb = new StringBuilder();
                for (Object[] m : log) sb.append("user".equals(m[0]) ? "You: " : "Kurisu: ").append(m[1]).append("\n\n");
                Intent i = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, sb.toString());
                startActivity(Intent.createChooser(i, "Share conversation"));
            }
        });
        TextView clear = UiKit.button(this, "CLEAR");
        clear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                db.clearMessages();
                show("log");
            }
        });
        row.addView(share);
        row.addView(clear, UiKit.lp(this, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 8, 0, 0, 0));
        add(row);
        if (log.isEmpty()) add(UiKit.text(this, "Nothing here yet. Talk to me.", 13, UiKit.DIM));
        for (Object[] m : log) {
            boolean user = "user".equals(m[0]);
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.addView(UiKit.text(this, (user ? "YOU" : "KURISU") + "  " + when((Long) m[2]), 10, user ? UiKit.DIM : UiKit.GOLD));
            TextView t = UiKit.text(this, (String) m[1], 14, UiKit.INK);
            t.setTextIsSelectable(true);
            col.addView(t);
            add(UiKit.card(this, col));
        }
    }
}
