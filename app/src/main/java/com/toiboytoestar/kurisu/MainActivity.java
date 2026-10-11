package com.toiboytoestar.kurisu;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.SpeechRecognizer;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** The conversation screen: Kurisu's face, live transcript, and the voice loop. */
public class MainActivity extends Activity implements Brain.Ui, SpeechInput.Events {

    static final String EXTRA_BRIEFING = "briefing";
    static final String PREF_BRIEFING_TOKEN = "briefing_token";
    /** A prompt handed over by our own screens (Hub, widget, check-in). Only honoured with a fresh token: this activity is exported. */
    static final String EXTRA_PROMPT = "prompt";
    static final String PREF_PROMPT_TOKEN = "prompt_token";
    static final String EXTRA_DISPLAY = "display";
    /** Start listening right away (widget). Same token guard as above. */
    static final String EXTRA_LISTEN = "listen";
    static final String PREF_LISTEN_TOKEN = "listen_token";
    private static final int REQ_MIC = 11302;
    private static final int REQ_NOTIF = 11303;
    private static final int REQ_PICK = 11304;

    private final Handler h = new Handler(Looper.getMainLooper());
    private Prefs prefs;
    private Brain brain;
    private SpeechInput ears;
    private Avatar avatar;

    private TextView status, userLine, reply, focusChip, autoBtn, kbBtn;
    private ScrollView replyScroll;
    private ImageView mic;
    private View textRow;
    private EditText input;

    private boolean listening;
    private boolean textMode;
    private boolean pendingListenAfterPermission;
    private boolean startOnResume;
    private int silentRetries;
    private int busyRetries;
    private int hardErrors;
    private boolean resumed;
    private final StringBuilder replyBuf = new StringBuilder();

    private final Runnable focusTick = new Runnable() {
        @Override
        public void run() {
            updateFocusChip();
            h.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        prefs = new Prefs(this);

        status = (TextView) findViewById(R.id.textView_status);
        userLine = (TextView) findViewById(R.id.textView_user);
        reply = (TextView) findViewById(R.id.textView_subtitles);
        replyScroll = (ScrollView) findViewById(R.id.scroll_reply);
        focusChip = (TextView) findViewById(R.id.textView_focus);
        autoBtn = (TextView) findViewById(R.id.button_auto);
        kbBtn = (TextView) findViewById(R.id.button_keyboard);
        mic = (ImageView) findViewById(R.id.button_mic);
        textRow = findViewById(R.id.row_text);
        input = (EditText) findViewById(R.id.editText_input);

        avatar = new Avatar((ImageView) findViewById(R.id.imageView_kurisu));
        avatar.setMood("happy");
        ears = new SpeechInput(this, this);
        brain = new Brain(this, this);

        replyScroll.getLayoutParams().height = (int) (getResources().getDisplayMetrics().heightPixels * 0.17f);

        mic.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onMicTapped();
            }
        });
        kbBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setTextMode(!textMode);
            }
        });
        autoBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean on = !prefs.autoListen();
                prefs.setHandsFree(on);
                if (!on) prefs.setWakeWord(false);
                refreshAuto();
                if (prefs.autoListen() && !brain.isBusy() && !listening) startListening();
                if (!prefs.autoListen() && listening) stopEverything();
            }
        });
        findViewById(R.id.button_hub).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openHub("");
            }
        });
        findViewById(R.id.button_settings).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
            }
        });
        findViewById(R.id.button_new).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopListeningQuietly();
                brain.newChat();
                Toast.makeText(MainActivity.this, "New conversation", Toast.LENGTH_SHORT).show();
            }
        });
        focusChip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openHub("focus");
            }
        });
        findViewById(R.id.button_send).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendTyped();
            }
        });
        input.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent e) {
                if (actionId == EditorInfo.IME_ACTION_SEND || (e != null && e.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                    sendTyped();
                    return true;
                }
                return false;
            }
        });
        buildChips();
        refreshAuto();

        Intent in = getIntent();
        if (!handleIntent(in)) {
            brain.greet();
        }
    }

    // ------------------------------------------------------------------ intents

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    /** Handles briefing, shared text and "Ask Kurisu" text selection. Returns true if it started a turn. */
    private boolean handleIntent(Intent in) {
        if (in == null) return false;
        if (in.getBooleanExtra(EXTRA_BRIEFING, false)) {
            in.removeExtra(EXTRA_BRIEFING);
            // MainActivity is exported (share target): only honour the briefing if the call screen just asked for it
            long ts = prefs.raw().getLong(PREF_BRIEFING_TOKEN, 0);
            prefs.raw().edit().remove(PREF_BRIEFING_TOKEN).apply();
            if (System.currentTimeMillis() - ts < 15000) {
                brain.briefing();
                return true;
            }
            return false;
        }
        if (in.hasExtra(EXTRA_PROMPT)) {
            String p = in.getStringExtra(EXTRA_PROMPT);
            String disp = in.getStringExtra(EXTRA_DISPLAY);
            in.removeExtra(EXTRA_PROMPT);
            in.removeExtra(EXTRA_DISPLAY);
            long ts = prefs.raw().getLong(PREF_PROMPT_TOKEN, 0);
            prefs.raw().edit().remove(PREF_PROMPT_TOKEN).apply();
            if (p != null && p.trim().length() > 0 && System.currentTimeMillis() - ts < 15000 && prefs.llmConfigured()) {
                if (disp != null && disp.length() > 0) brain.submit(disp, p);
                else brain.submit(p);
                return true;
            }
            return false;
        }
        if (in.getBooleanExtra(EXTRA_LISTEN, false)) {
            in.removeExtra(EXTRA_LISTEN);
            long ts = prefs.raw().getLong(PREF_LISTEN_TOKEN, 0);
            prefs.raw().edit().remove(PREF_LISTEN_TOKEN).apply();
            if (System.currentTimeMillis() - ts < 15000) {
                startOnResume = true;
                return true; // skip the greeting: she should listen straight away
            }
            return false;
        }
        String action = in.getAction();
        CharSequence shared = null;
        boolean selection = false;
        if (Intent.ACTION_SEND.equals(action)) {
            shared = in.getCharSequenceExtra(Intent.EXTRA_TEXT);
        } else if (Build.VERSION.SDK_INT >= 23 && Intent.ACTION_PROCESS_TEXT.equals(action)) {
            shared = in.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT);
            selection = true;
        }
        if (shared == null || shared.toString().trim().length() == 0) return false;
        in.setAction(Intent.ACTION_MAIN); // don't re-handle on rotation/resume
        String text = shared.toString().trim();
        if (text.length() > 12000) text = text.substring(0, 12000);
        boolean url = text.matches("(?s)https?://\\S+");
        String display = url ? "Summarize this link: " + text : (selection ? "Explain: " : "Summarize: ")
                + (text.length() > 140 ? text.substring(0, 140) + "…" : text);
        String prompt = url
                ? "The user shared this link: " + text + "\nRead it with the fetch tool, then give a brief spoken summary of the key points and offer to save a note."
                : (selection ? "The user selected this text and wants it explained clearly and briefly:\n\"\"\"\n" + text + "\n\"\"\""
                : "The user shared this text. Summarize the key points briefly (spoken style) and offer to save a note or make flashcards:\n\"\"\"\n" + text + "\n\"\"\"");
        if (!prefs.llmConfigured()) {
            Toast.makeText(this, "Add an AI key in settings so I can read shared text.", Toast.LENGTH_LONG).show();
            return false;
        }
        brain.submit(display, prompt);
        return true;
    }

    // ------------------------------------------------------------------ chips

    private void buildChips() {
        LinearLayout row = (LinearLayout) findViewById(R.id.chips);
        addChip(row, R.string.ui_chip_plan, "Plan my day based on my agenda.", true);
        addChip(row, R.string.ui_chip_explain, "Explain: ", false);
        addChip(row, R.string.ui_chip_quiz, "Quiz me on: ", false);
        addChip(row, R.string.ui_chip_research, "Research: ", false);
        addChip(row, R.string.ui_chip_cards, "Make flashcards about: ", false);
        addChip(row, R.string.ui_chip_summarize, "Summarize: ", false);
        addChip(row, R.string.ui_chip_focus, "start a pomodoro", true);
        TextView photo = new TextView(this, null, 0, R.style.Chip);
        photo.setText("📎 Photo / PDF");
        photo.setTextSize(12);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        plp.rightMargin = UiKit.dp(this, 6);
        photo.setLayoutParams(plp);
        photo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickAttachment();
            }
        });
        row.addView(photo, 0);
        TextView vn = new TextView(this, null, 0, R.style.Chip);
        vn.setText("🎙 Voice note");
        vn.setTextSize(12);
        LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        vlp.rightMargin = UiKit.dp(this, 6);
        vn.setLayoutParams(vlp);
        vn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startDictation();
            }
        });
        row.addView(vn, 1);
    }

    // ------------------------------------------------------------- photo / PDF

    private void pickAttachment() {
        if (!prefs.llmConfigured()) {
            Toast.makeText(this, "Add an AI key in settings first (Gemini free works with photos).", Toast.LENGTH_LONG).show();
            return;
        }
        stopListeningQuietly();
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/*", "application/pdf"});
        try {
            startActivityForResult(i, REQ_PICK);
        } catch (Exception e) {
            Toast.makeText(this, "No file picker available.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_PICK || res != RESULT_OK || data == null || data.getData() == null) return;
        final android.net.Uri uri = data.getData();
        final String typed = input.getText().toString().trim();
        input.setText("");
        setStatus("Reading your file…");
        new Thread(new Runnable() {
            @Override
            public void run() {
                final Attachments.Result r = Attachments.load(MainActivity.this, uri);
                h.post(new Runnable() {
                    @Override
                    public void run() {
                        if (isFinishing() || isDestroyed()) return;
                        if (r.error != null) {
                            setStatus(r.error);
                            Toast.makeText(MainActivity.this, r.error, Toast.LENGTH_LONG).show();
                            return;
                        }
                        String ask = typed.length() > 0 ? typed
                                : "Look at this and help me with it. If it's homework or a problem, guide me step by step and explain the idea, "
                                + "not just the final answer. If it's a document or notes, summarize the key points.";
                        String what = r.label.equals("PDF")
                                ? "PDF (" + (r.totalPages > r.images.size() ? "first " + r.images.size() + " of " + r.totalPages + " pages" : r.totalPages + " pages") + ")"
                                : "photo";
                        stopListeningQuietly(); // hands-free may have reopened the mic while the file loaded
                        brain.submit("📎 " + what + (typed.length() > 0 ? ": " + typed : ""), ask, r.images);
                    }
                });
            }
        }).start();
    }

    private void addChip(LinearLayout row, int label, final String prompt, final boolean sendNow) {
        TextView c = new TextView(this, null, 0, R.style.Chip);
        c.setText(label);
        c.setTextSize(12);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = UiKit.dp(this, 6);
        c.setLayoutParams(lp);
        c.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (sendNow) {
                    stopListeningQuietly();
                    brain.submit(prompt);
                } else {
                    setTextMode(true);
                    input.setText(prompt);
                    input.setSelection(prompt.length());
                    input.requestFocus();
                }
            }
        });
        row.addView(c);
    }

    // ------------------------------------------------------------- text / mic mode

    private void setTextMode(boolean on) {
        textMode = on;
        textRow.setVisibility(on ? View.VISIBLE : View.GONE);
        kbBtn.setText(on ? R.string.ui_voice : R.string.ui_keyboard);
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (on) {
            stopListeningQuietly();
            input.requestFocus();
            imm.showSoftInput(input, 0);
        } else {
            imm.hideSoftInputFromWindow(input.getWindowToken(), 0);
        }
    }

    private void sendTyped() {
        String t = input.getText().toString().trim();
        if (t.length() == 0) return;
        input.setText("");
        stopListeningQuietly();
        brain.submit(t);
    }

    private void refreshAuto() {
        if (prefs.wakeWord()) autoBtn.setText("👂 HEY KURISU");
        else autoBtn.setText(prefs.handsFree() ? R.string.ui_handsfree_on : R.string.ui_handsfree_off);
        autoBtn.setBackground(prefs.autoListen() ? UiKit.box(this, 0x66FFCF40, UiKit.GOLD, 16) : UiKit.box(this, 0x33000000, 0x88FFCF40, 16));
        autoBtn.setTextColor(prefs.autoListen() ? 0xFFFFFFFF : UiKit.GOLD);
    }

    // ------------------------------------------------------------------ listening

    // ---------------------------------------------------------------- voice note (dictation)

    private long lastActive;
    private boolean dictating;
    private boolean dictFinish;
    private final StringBuilder dictBuf = new StringBuilder();

    private void startDictation() {
        if (!prefs.llmConfigured()) {
            Toast.makeText(this, "Add an AI key in settings first; Kurisu turns your dictation into a clean note.", Toast.LENGTH_LONG).show();
            return;
        }
        if (!SpeechInput.available(this)) {
            setStatus(getString(R.string.ui_no_speech));
            return;
        }
        if (brain.isBusy()) brain.interrupt();
        setTextMode(false);
        dictating = true;
        dictFinish = false;
        dictQuiet = 0;
        dictBuf.setLength(0);
        ears.setSilenceMs(2500);
        reply.setText("🎙 Voice note: talk as long as you like. Tap the mic when you're done and I'll turn it into a clean note.");
        replyBuf.setLength(0);
        startListening();
    }

    private int dictQuiet;

    /** Abandons a voice note in progress (typing, new chat, leaving the screen...). */
    private void cancelDictation() {
        if (!dictating) return;
        dictating = false;
        dictFinish = false;
        dictBuf.setLength(0);
        ears.setSilenceMs(1300);
    }

    private void dictationRestart() {
        if (++dictQuiet > 12) { // endless silent restarts (revoked permission, dead recognizer): give up with what we have
            dictQuiet = 0;
            finishDictation();
            return;
        }
        h.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (dictating && resumed && !listening) startListening();
            }
        }, 250);
    }

    private void showDictationProgress() {
        int words = dictBuf.toString().trim().length() == 0 ? 0 : dictBuf.toString().trim().split("\\s+").length;
        userLine.setText("🎙 " + words + " words captured… tap the mic to finish");
    }

    private void finishDictation() {
        dictating = false;
        dictFinish = false;
        ears.setSilenceMs(1300);
        String text = dictBuf.toString().trim();
        dictBuf.setLength(0);
        if (text.length() == 0) {
            setStatus(getString(R.string.ui_status_idle));
            userLine.setText("");
            return;
        }
        int words = text.split("\\s+").length;
        String prompt = "The user dictated a voice note (" + words + " words, speech-to-text so expect small errors). "
                + "Clean it up and save it with a [[note:Title|Body]] tag: a short title, a 1-2 line summary, then the content as tidy points or short paragraphs, "
                + "fixing obvious mishearings but never inventing facts. If it contains to-dos, also add [[task:...]] tags for them. "
                + "If it is study material, offer to make flashcards. Then say in one or two sentences what you saved.\n\"\"\"\n" + text + "\n\"\"\"";
        brain.submit("🎙 Voice note (" + words + " words)", prompt);
    }

    private void onMicTapped() {
        if (dictating) {
            dictFinish = true;
            if (listening) ears.stopListening();
            else finishDictation();
            return;
        }
        if (listening) {
            ears.stopListening(); // finish the utterance now
            return;
        }
        if (brain.isBusy()) brain.interrupt(); // barge-in
        startListening();
    }

    private boolean forceOnline;
    private String langOverride;

    private boolean hasMicPermission() {
        return Build.VERSION.SDK_INT < 23 || checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    private void startListening() {
        if (!resumed || listening) return;
        if (!SpeechInput.available(this)) {
            setStatus(getString(R.string.ui_no_speech));
            setTextMode(true);
            return;
        }
        if (!hasMicPermission()) {
            pendingListenAfterPermission = true;
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
            return;
        }
        listening = true;
        if (!dictating) userLine.setText("");
        setMicActive(true);
        setStatus(getString(R.string.ui_status_listening));
        avatar.setMood("pleasant");
        ears.start(langOverride != null ? langOverride : prefs.speechLang(), prefs.preferOffline() && !forceOnline);
    }

    private void stopListeningQuietly() {
        cancelDictation();
        if (listening) {
            ears.cancel();
            listening = false;
            setMicActive(false);
        }
    }

    /** Stops everything: listening, speaking, generating. */
    private void stopEverything() {
        stopListeningQuietly();
        brain.interrupt();
        setStatus(getString(R.string.ui_status_idle));
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        if (code == REQ_MIC) {
            boolean ok = results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED;
            if (ok && pendingListenAfterPermission) startOnResume = true;
            if (!ok) {
                setStatus(getString(R.string.ui_need_mic));
                setTextMode(true);
            }
            pendingListenAfterPermission = false;
        }
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED
                && !prefs.raw().getBoolean("asked_notif", false)) {
            prefs.raw().edit().putBoolean("asked_notif", true).apply();
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, REQ_NOTIF);
        }
    }

    // SpeechInput.Events -------------------------------------------------------

    @Override
    public void onListening() { }

    @Override
    public void onLevel(float level) {
        float s = 1f + 0.22f * level;
        mic.setScaleX(s);
        mic.setScaleY(s);
    }

    @Override
    public void onPartial(String text) {
        userLine.setText("› " + text);
    }

    @Override
    public void onFinal(String text) {
        listening = false;
        setMicActive(false);
        String t = text == null ? "" : text.trim();
        if (dictating) {
            if (t.length() > 0) {
                dictBuf.append(t).append(' ');
                dictQuiet = 0;
            }
            showDictationProgress();
            if (dictFinish) finishDictation();
            else dictationRestart();
            return;
        }
        if (t.length() == 0) {
            onNothingHeard();
            return;
        }
        if (prefs.wakeWord()) {
            String cmd = com.toiboytoestar.kurisu.core.WakeWord.extract(t);
            boolean inConversation = System.currentTimeMillis() - lastActive < 20000; // follow-ups need no wake word
            if (cmd == null && !inConversation) {
                setStatus("Say \"Hey Kurisu\"…");
                onNothingHeard();
                return;
            }
            if (cmd != null) {
                if (cmd.length() == 0) {
                    lastActive = System.currentTimeMillis();
                    brain.submit("🎙 Hey Kurisu", "(The user just called your name. Answer in one short, friendly sentence and ask what they need.)");
                    return;
                }
                t = cmd;
            }
        }
        lastActive = System.currentTimeMillis();
        silentRetries = 0;
        busyRetries = 0;
        hardErrors = 0;
        brain.submit(t);
    }

    @Override
    public void onError(int code) {
        listening = false;
        setMicActive(false);
        if (dictating && code != 12 && code != 13) {
            boolean quiet = code == SpeechRecognizer.ERROR_NO_MATCH || code == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                    || code == SpeechRecognizer.ERROR_CLIENT || code == SpeechRecognizer.ERROR_RECOGNIZER_BUSY;
            if (quiet && !dictFinish) {
                if (code == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) ears.destroy();
                dictationRestart();
                return;
            }
            if (dictBuf.toString().trim().length() > 0 || dictFinish) {
                finishDictation(); // real error or user finished: keep what we have
                return;
            }
            dictating = false;
            ears.setSilenceMs(1300);
        }
        switch (code) {
            case SpeechRecognizer.ERROR_NO_MATCH:
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                onNothingHeard();
                break;
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                ears.destroy();
                setStatus("…");
                if (++busyRetries <= 3) {
                    h.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if (!brain.isBusy() && resumed) startListening();
                        }
                    }, 600);
                }
                break;
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                pendingListenAfterPermission = true;
                requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
                break;
            case SpeechRecognizer.ERROR_CLIENT:
                setStatus(getString(R.string.ui_status_idle));
                break;
            case 12: // ERROR_LANGUAGE_NOT_SUPPORTED
            case 13: // ERROR_LANGUAGE_UNAVAILABLE (language pack / offline model missing)
                ears.destroy();
                if (!forceOnline) {
                    forceOnline = true; // 1st: stop insisting on the offline model
                    setStatus("…");
                    h.postDelayed(new Runnable() { public void run() { if (resumed && !listening) startListening(); } }, 300);
                } else if (langOverride == null && !"en-US".equals(prefs.speechLang())) {
                    langOverride = "en-US"; // 2nd: fall back to US English recognition
                    setStatus("Speech language unavailable, trying English (US)…");
                    h.postDelayed(new Runnable() { public void run() { if (resumed && !listening) startListening(); } }, 300);
                } else {
                    setStatus("Speech language pack missing. Open Google app > Settings > Voice > Languages, download yours, or type with Aa.");
                    setTextMode(true);
                }
                break;
            default:
                setStatus("Speech service problem (" + code + "). Try again or type.");
                if (prefs.autoListen() && ++hardErrors >= 3) {
                    prefs.setHandsFree(false);
                    prefs.setWakeWord(false);
                    hardErrors = 0;
                    refreshAuto();
                }
        }
    }

    private void onNothingHeard() {
        setStatus(getString(R.string.ui_status_idle));
        if (prefs.autoListen() && (prefs.wakeWord() || silentRetries < 2) && resumed) {
            silentRetries++;
            h.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (!brain.isBusy() && !listening) startListening();
                }
            }, 300);
        } else {
            silentRetries = 0;
        }
    }

    // ---------------------------------------------------------------- Brain.Ui

    @Override
    public void onState(Brain.State s) {
        if (listening) return;
        switch (s) {
            case THINKING:
                setStatus(getString(R.string.ui_status_thinking));
                break;
            case SPEAKING:
                setStatus(getString(R.string.ui_status_speaking));
                break;
            default:
                String base = prefs.llmConfigured() ? getString(R.string.ui_status_idle) : getString(R.string.ui_status_offline);
                int streak = prefs.streak();
                setStatus(streak >= 2 ? base + "  🔥" + streak : base);
        }
    }

    @Override
    public void onUserLine(String text) {
        userLine.setText("› " + text);
    }

    @Override
    public void onReplyClear() {
        replyBuf.setLength(0);
        reply.setText("");
    }

    @Override
    public void onReplyAppend(String text) {
        replyBuf.append(text);
        reply.setText(replyBuf.toString().trim());
        replyScroll.post(new Runnable() {
            @Override
            public void run() {
                replyScroll.fullScroll(View.FOCUS_DOWN);
            }
        });
    }

    @Override
    public void onMood(String mood) {
        avatar.setMood(mood);
    }

    @Override
    public void onTalking(boolean talking) {
        avatar.setTalking(talking);
    }

    @Override
    public void onTurnFinished() {
        lastActive = System.currentTimeMillis();
        requestNotificationPermission(); // asked after the first greeting so it doesn't cut it off
        if (prefs.autoListen() && resumed && !textMode && !listening) {
            h.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (!brain.isBusy() && !listening && prefs.autoListen() && resumed) startListening();
                }
            }, 450);
        }
    }

    @Override
    public void onError(String message) {
        replyBuf.append(replyBuf.length() > 0 ? "\n" : "").append("⚠ ").append(message);
        reply.setText(replyBuf.toString());
    }

    @Override
    public void onOpenHub(String tab) {
        openHub(tab);
    }

    @Override
    public void onChatCleared() {
        userLine.setText("");
        reply.setText("");
        replyBuf.setLength(0);
    }

    // --------------------------------------------------------------------- misc

    private void openHub(String tab) {
        Intent i = new Intent(this, HubActivity.class);
        if (tab != null && tab.length() > 0) i.putExtra(HubActivity.EXTRA_TAB, tab);
        startActivity(i);
    }

    private void setStatus(String s) {
        status.setText(s);
    }

    private void setMicActive(boolean on) {
        mic.setBackgroundResource(on ? R.drawable.bg_mic_active : R.drawable.bg_mic);
        mic.setImageResource(R.drawable.ic_mic);
        if (!on) {
            mic.setScaleX(1f);
            mic.setScaleY(1f);
        }
    }

    private void updateFocusChip() {
        String phase = FocusTimer.phase(this);
        if (phase.length() == 0) {
            focusChip.setVisibility(View.GONE);
            return;
        }
        String done = FocusTimer.completeIfDue(this);
        if (done.length() > 0) {
            focusChip.setVisibility(View.GONE);
            return;
        }
        focusChip.setVisibility(View.VISIBLE);
        focusChip.setText((FocusTimer.FOCUS.equals(phase) ? "FOCUS " : "BREAK ") + FocusTimer.format(FocusTimer.remainingMs(this))
                + (FocusTimer.paused(this) ? " ⏸" : ""));
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        avatar.start();
        brain.applyVoiceSettings();
        refreshAuto();
        onState(brain.state());
        h.post(focusTick);
        if (prefs.autoListen() && !textMode && !startOnResume) {
            startOnResume = true;
        }
        if (startOnResume) {
            startOnResume = false;
            h.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (!brain.isBusy()) startListening();
                }
            }, 300);
        }
        if (prefs.showXpIcon()) Notifier.showXp(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        resumed = false;
        h.removeCallbacksAndMessages(null);
        stopListeningQuietly();
        brain.interrupt();
        avatar.stop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ears.destroy();
        brain.shutdown();
        avatar.release();
    }
}
