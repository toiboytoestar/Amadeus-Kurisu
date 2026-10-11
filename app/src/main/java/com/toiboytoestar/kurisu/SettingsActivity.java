package com.toiboytoestar.kurisu;

import android.app.Activity;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import com.toiboytoestar.kurisu.core.Providers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;

/** Settings: AI back-end, voice, wake-up call and personal info. */
public class SettingsActivity extends Activity {

    private static final String[][] LANGS = {
            {"English (US)", "en-US"}, {"English (UK)", "en-GB"}, {"English (India)", "en-IN"},
            {"हिन्दी (Hindi)", "hi-IN"}, {"日本語 (Japanese)", "ja-JP"}, {"Deutsch", "de-DE"},
            {"Français", "fr-FR"}, {"Español", "es-ES"}, {"Português (Brasil)", "pt-BR"},
            {"Italiano", "it-IT"}, {"Русский", "ru-RU"}, {"中文 (简体)", "zh-CN"}, {"한국어", "ko-KR"}};
    private static final String[][] RINGTONES = {
            {"Gate of Steiner", "ringtone_gate_of_steiner"}, {"Beginning of Fight", "ringtone_beginning_of_fight"},
            {"Easygoingness", "ringtone_easygoingness"}, {"Over the Sky", "ringtone_over_the_sky"},
            {"Precaution", "ringtone_precaution"}, {"Reunion", "ringtone_reunion"}, {"Village", "ringtone_village"}};
    private static final String[] DAY_LABELS = {"Su", "Mo", "Tu", "We", "Th", "Fr", "Sa"};

    private final Handler h = new Handler(Looper.getMainLooper());
    private Prefs prefs;
    private LinearLayout root;

    private Spinner providerSp, langSp, voiceSp, ringSp;
    private EditText baseUrl, model, apiKey, name, memory;
    private TextView hint, testResult, wakeTime, checkinTime;
    private int checkinHour, checkinMinute;
    private Switch speak, offline, eggs, xp, wake, checkin, wakeWordSw, cloudSw;
    private Spinner cloudVoiceSp;
    private EditText cloudKey, cloudModel;
    private TextView cloudResult;
    private final PcmPlayer previewPlayer = new PcmPlayer();
    private SeekBar rate, pitch;
    private final CompoundButton[] dayBoxes = new CompoundButton[7];
    private int wakeHour, wakeMinute;
    private int lastProviderIndex = -1;

    private TextToSpeech tts;
    private boolean ttsReady;
    private final List<String> voiceNames = new ArrayList<String>();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = new Prefs(this);
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(UiKit.BG);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(UiKit.dp(this, 16), UiKit.dp(this, 30), UiKit.dp(this, 16), UiKit.dp(this, 40));
        sv.addView(root);
        setContentView(sv);
        build();
        tts = new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                ttsReady = status == TextToSpeech.SUCCESS;
                h.post(new Runnable() {
                    @Override
                    public void run() {
                        refreshVoices();
                    }
                });
            }
        });
    }

    private void row(View v) {
        root.addView(v, UiKit.lp(this, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 4, 0, 4));
    }

    private Switch sw(String label, boolean on) {
        Switch s = new Switch(this);
        s.setText(label);
        s.setTextColor(UiKit.INK);
        s.setChecked(on);
        s.setTypeface(android.graphics.Typeface.MONOSPACE);
        return s;
    }

    private Spinner spinner(List<String> items) {
        Spinner sp = new Spinner(this);
        ArrayAdapter<String> ad = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, items);
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sp.setAdapter(ad);
        return sp;
    }

    private void build() {
        TextView title = UiKit.text(this, "SETTINGS", 18, UiKit.GOLD);
        title.setTypeface(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD);
        row(title);

        // ---------------- AI
        root.addView(UiKit.title(this, "AI brain"));
        row(UiKit.text(this, "Kurisu needs an AI service to hold real conversations. Your key stays on this device and is sent only to the service you choose.", 12, UiKit.DIM));
        List<String> names = new ArrayList<String>();
        for (Providers.P p : Providers.ALL) names.add(p.label);
        providerSp = spinner(names);
        providerSp.setSelection(Providers.indexOf(prefs.provider()));
        lastProviderIndex = providerSp.getSelectedItemPosition();
        row(providerSp);
        hint = UiKit.text(this, Providers.byId(prefs.provider()).hint, 12, UiKit.GOLD);
        row(hint);
        baseUrl = UiKit.edit(this, "Base URL", false);
        baseUrl.setText(prefs.baseUrl().length() > 0 ? prefs.baseUrl() : Providers.byId(prefs.provider()).baseUrl);
        baseUrl.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        row(baseUrl);
        model = UiKit.edit(this, "Model", false);
        model.setText(prefs.model());
        model.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        row(model);
        apiKey = UiKit.edit(this, "API key", false);
        apiKey.setText(prefs.apiKey());
        apiKey.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        row(apiKey);
        LinearLayout t = new LinearLayout(this);
        t.setGravity(Gravity.CENTER_VERTICAL);
        TextView test = UiKit.button(this, "TEST CONNECTION");
        testResult = UiKit.text(this, "", 12, UiKit.INK);
        test.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                runTest();
            }
        });
        t.addView(test);
        t.addView(testResult, UiKit.lp(this, 0, ViewGroup.LayoutParams.WRAP_CONTENT, 10, 0, 0, 0));
        ((LinearLayout.LayoutParams) testResult.getLayoutParams()).weight = 1;
        row(t);
        providerSp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                if (pos == lastProviderIndex) return;
                Providers.P old = Providers.ALL[lastProviderIndex];
                Providers.P cur = Providers.ALL[pos];
                lastProviderIndex = pos;
                // replace fields only if the user hasn't customised them
                if (baseUrl.getText().toString().trim().equals(old.baseUrl) || baseUrl.getText().toString().trim().length() == 0)
                    baseUrl.setText(cur.baseUrl);
                if (model.getText().toString().trim().equals(old.model) || model.getText().toString().trim().length() == 0)
                    model.setText(cur.model);
                hint.setText(cur.hint);
            }

            @Override
            public void onNothingSelected(AdapterView<?> p) { }
        });

        // ---------------- you
        root.addView(UiKit.title(this, "About you"));
        name = UiKit.edit(this, "What should I call you?", false);
        name.setText(prefs.userName());
        row(name);
        row(UiKit.text(this, "Things I remember about you (one per line; I add to this when you ask me to remember something):", 12, UiKit.DIM));
        memory = UiKit.edit(this, "e.g. Studying neuroscience, exam on 14 Nov", true);
        memory.setMinLines(3);
        memory.setGravity(Gravity.TOP);
        memory.setText(prefs.memory());
        row(memory);

        // ---------------- voice
        root.addView(UiKit.title(this, "Voice"));
        List<String> ln = new ArrayList<String>();
        int sel = 0;
        for (int i = 0; i < LANGS.length; i++) {
            ln.add(LANGS[i][0]);
            if (LANGS[i][1].equals(prefs.speechLang())) sel = i;
        }
        if (!LANGS[sel][1].equals(prefs.speechLang())) {
            for (int i = 0; i < LANGS.length; i++) {
                if (LANGS[i][1].startsWith(prefs.langCode() + "-")) {
                    sel = i;
                    break;
                }
            }
        }
        langSp = spinner(ln);
        langSp.setSelection(sel);
        row(UiKit.text(this, "Language I listen and speak in", 12, UiKit.DIM));
        row(langSp);
        langSp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                refreshVoices();
            }

            @Override
            public void onNothingSelected(AdapterView<?> p) { }
        });
        row(UiKit.text(this, "Voice (from your phone's speech engine)", 12, UiKit.DIM));
        voiceSp = spinner(new ArrayList<String>(Collections.singletonList("Default")));
        row(voiceSp);
        rate = new SeekBar(this);
        rate.setMax(100);
        rate.setProgress((int) ((prefs.ttsRate() - 0.6f) / 1.0f * 100));
        row(UiKit.text(this, "Speed", 12, UiKit.DIM));
        row(rate);
        pitch = new SeekBar(this);
        pitch.setMax(100);
        pitch.setProgress((int) ((prefs.ttsPitch() - 0.6f) / 1.0f * 100));
        row(UiKit.text(this, "Pitch", 12, UiKit.DIM));
        row(pitch);
        TextView preview = UiKit.button(this, "PREVIEW VOICE");
        preview.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                previewVoice();
            }
        });
        row(preview);
        speak = sw("Speak replies aloud", prefs.speakReplies());
        row(speak);
        offline = sw("Prefer offline speech recognition (faster)", prefs.preferOffline());
        row(offline);
        eggs = sw("Original voice lines for easter eggs", prefs.classicEggs());
        row(eggs);

        // ---------------- wake-up call
        root.addView(UiKit.title(this, "Wake-up call"));
        row(UiKit.text(this, "Kurisu rings you like in the show; answer and she briefs you on your day.", 12, UiKit.DIM));
        wake = sw("Call me", prefs.wakeEnabled());
        row(wake);
        wakeHour = prefs.wakeHour();
        wakeMinute = prefs.wakeMinute();
        wakeTime = UiKit.button(this, fmtTime());
        wakeTime.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new TimePickerDialog(SettingsActivity.this, android.R.style.Theme_Material_Dialog_Alert, new TimePickerDialog.OnTimeSetListener() {
                    @Override
                    public void onTimeSet(TimePicker p, int hh, int mm) {
                        wakeHour = hh;
                        wakeMinute = mm;
                        wakeTime.setText(fmtTime());
                    }
                }, wakeHour, wakeMinute, true).show();
            }
        });
        row(wakeTime);
        LinearLayout days = new LinearLayout(this);
        for (int i = 0; i < 7; i++) {
            android.widget.CheckBox cb = new android.widget.CheckBox(this);
            cb.setText(DAY_LABELS[i]);
            cb.setTextColor(UiKit.INK);
            cb.setChecked((prefs.wakeDays() & (1 << i)) != 0);
            dayBoxes[i] = cb;
            days.addView(cb, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        }
        row(days);
        List<String> rn = new ArrayList<String>();
        int rs = 0;
        for (int i = 0; i < RINGTONES.length; i++) {
            rn.add(RINGTONES[i][0]);
            if (RINGTONES[i][1].equals(prefs.ringtone())) rs = i;
        }
        ringSp = spinner(rn);
        ringSp.setSelection(rs);
        row(UiKit.text(this, "Ringtone", 12, UiKit.DIM));
        row(ringSp);

        // ---------------- natural voice
        root.addView(UiKit.title(this, "Natural voice (Gemini)"));
        row(UiKit.text(this, "A much more natural, expressive voice than the phone's built-in one, and it follows her mood. Uses your Gemini key (free tier works). "
                + "If it fails or you hit a limit she falls back to the phone voice automatically.", 12, UiKit.DIM));
        cloudSw = sw("Use natural voice", prefs.cloudTts());
        row(cloudSw);
        List<String> cv = new ArrayList<String>();
        int cvSel = 0;
        for (int i = 0; i < com.toiboytoestar.kurisu.core.GeminiTts.VOICES.length; i++) {
            String vn = com.toiboytoestar.kurisu.core.GeminiTts.VOICES[i];
            cv.add(vn);
            if (vn.equals(prefs.cloudVoice())) cvSel = i;
        }
        cloudVoiceSp = spinner(cv);
        cloudVoiceSp.setSelection(cvSel);
        row(UiKit.text(this, "Voice (Leda = youthful, Kore = firm, Zephyr = bright, Sulafat = warm)", 12, UiKit.DIM));
        row(cloudVoiceSp);
        cloudKey = UiKit.edit(this, "Gemini key for voice (optional if your AI provider is Gemini)", false);
        cloudKey.setSingleLine(true);
        cloudKey.setText(prefs.raw().getString("cloud_key", ""));
        row(cloudKey);
        cloudModel = UiKit.edit(this, "Voice model (leave empty for default)", false);
        cloudModel.setSingleLine(true);
        cloudModel.setText(prefs.cloudModel());
        row(cloudModel);
        TextView cloudTest = UiKit.button(this, "TEST NATURAL VOICE");
        cloudResult = UiKit.text(this, "", 12, UiKit.INK);
        cloudTest.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                testCloudVoice();
            }
        });
        row(cloudTest);
        row(cloudResult);

        // ---------------- wake word
        root.addView(UiKit.title(this, "Hey Kurisu"));
        row(UiKit.text(this, "Keeps listening while the app is open and only answers when you say \"Hey Kurisu\" (follow-ups within 20 seconds need no wake word). "
                + "It uses the microphone and battery while the app is on screen. It does not listen when the app is closed.", 12, UiKit.DIM));
        wakeWordSw = sw("Wake word mode", prefs.wakeWord());
        row(wakeWordSw);

        // ---------------- check-in
        root.addView(UiKit.title(this, "Evening check-in & streak"));
        row(UiKit.text(this, "A nightly nudge: Kurisu asks how your day went, reminds you of exams and open tasks, and gives a weekly summary on Sundays.", 12, UiKit.DIM));
        checkin = sw("Evening check-in", prefs.checkinEnabled());
        row(checkin);
        checkinHour = prefs.checkinHour();
        checkinMinute = prefs.checkinMinute();
        checkinTime = UiKit.button(this, String.format(Locale.US, "🌙 %02d:%02d", checkinHour, checkinMinute));
        checkinTime.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new TimePickerDialog(SettingsActivity.this, android.R.style.Theme_Material_Dialog_Alert, new TimePickerDialog.OnTimeSetListener() {
                    @Override
                    public void onTimeSet(TimePicker p, int hh, int mm) {
                        checkinHour = hh;
                        checkinMinute = mm;
                        checkinTime.setText(String.format(Locale.US, "🌙 %02d:%02d", hh, mm));
                    }
                }, checkinHour, checkinMinute, true).show();
            }
        });
        row(checkinTime);
        row(UiKit.text(this, "Current streak: " + prefs.streak() + " day(s) in a row", 12, UiKit.GOLD));

        // ---------------- display
        root.addView(UiKit.title(this, "Other"));
        xp = sw("Show \"XP\" icon in the notification bar", prefs.showXpIcon());
        row(xp);

        TextView save = UiKit.button(this, "SAVE");
        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                save();
                Toast.makeText(SettingsActivity.this, "Saved", Toast.LENGTH_SHORT).show();
                finish();
            }
        });
        root.addView(save, UiKit.lp(this, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 24, 0, 0));
        // ---------------- about
        root.addView(UiKit.title(this, "About"));
        row(UiKit.text(this, "This app is made by TOIBOY TOESTAR", 14, UiKit.GOLD));
        TextView insta = UiKit.text(this, "Instagram: @toiboytoestar", 14, 0xFF7CC8FF);
        insta.setPaintFlags(insta.getPaintFlags() | android.graphics.Paint.UNDERLINE_TEXT_FLAG);
        insta.setPadding(0, UiKit.dp(this, 6), 0, UiKit.dp(this, 6));
        insta.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse("https://www.instagram.com/toiboytoestar/")));
                } catch (Exception e) {
                    Toast.makeText(SettingsActivity.this, "No app found to open the link", Toast.LENGTH_SHORT).show();
                }
            }
        });
        row(insta);
        row(UiKit.text(this, "Steins;Gate assets belong to their owners; non-commercial fan use.", 11, UiKit.DIM));
    }

    private String fmtTime() {
        return String.format(Locale.US, "⏰ %02d:%02d", wakeHour, wakeMinute);
    }

    private String selectedLang() {
        return LANGS[Math.max(0, langSp.getSelectedItemPosition())][1];
    }

    private void refreshVoices() {
        if (!ttsReady || voiceSp == null) return;
        Locale loc = Locale.forLanguageTag(selectedLang());
        voiceNames.clear();
        List<String> labels = new ArrayList<String>();
        labels.add("Default");
        voiceNames.add("");
        try {
            List<Voice> vs = new ArrayList<Voice>(tts.getVoices());
            Collections.sort(vs, new Comparator<Voice>() {
                @Override
                public int compare(Voice a, Voice b) {
                    return a.getName().compareTo(b.getName());
                }
            });
            for (Voice v : vs) {
                if (!v.getLocale().getLanguage().equals(loc.getLanguage())) continue;
                voiceNames.add(v.getName());
                labels.add(v.getName() + (v.isNetworkConnectionRequired() ? " (online)" : ""));
            }
        } catch (Exception ignored) { }
        ArrayAdapter<String> ad = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, labels);
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        voiceSp.setAdapter(ad);
        int idx = voiceNames.indexOf(prefs.ttsVoice());
        voiceSp.setSelection(idx < 0 ? 0 : idx);
    }

    private float rateValue() {
        return 0.6f + rate.getProgress() / 100f;
    }

    private float pitchValue() {
        return 0.6f + pitch.getProgress() / 100f;
    }

    private void previewVoice() {
        if (!ttsReady) {
            Toast.makeText(this, "No speech engine available.", Toast.LENGTH_SHORT).show();
            return;
        }
        Locale loc = Locale.forLanguageTag(selectedLang());
        tts.setLanguage(loc);
        int vi = voiceSp.getSelectedItemPosition();
        if (vi > 0 && vi < voiceNames.size()) {
            try {
                for (Voice v : tts.getVoices()) {
                    if (v.getName().equals(voiceNames.get(vi))) {
                        tts.setVoice(v);
                        break;
                    }
                }
            } catch (Exception ignored) { }
        }
        tts.setSpeechRate(rateValue());
        tts.setPitch(pitchValue());
        tts.speak("Hello. I'm Makise Kurisu. Ask me whatever you want.", TextToSpeech.QUEUE_FLUSH, null, "preview");
    }

    private void testCloudVoice() {
        String key = cloudKey.getText().toString().trim();
        if (key.length() == 0 && "gemini".equals(Providers.ALL[providerSp.getSelectedItemPosition()].id)) key = apiKey.getText().toString().trim();
        if (key.length() == 0) {
            cloudResult.setTextColor(0xFFFF6A5A);
            cloudResult.setText("Enter a Gemini key (or choose Gemini as the AI provider and enter its key).");
            return;
        }
        cloudResult.setTextColor(UiKit.INK);
        cloudResult.setText("Generating…");
        final String k = key;
        final String model = cloudModel.getText().toString().trim();
        final String voice = com.toiboytoestar.kurisu.core.GeminiTts.VOICES[cloudVoiceSp.getSelectedItemPosition()];
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    byte[] pcm = new com.toiboytoestar.kurisu.core.GeminiTts("", k, model, voice)
                            .synthesize("Hello. I'm Makise Kurisu. Ask me whatever you want.", "happy");
                    h.post(new Runnable() {
                        @Override
                        public void run() {
                            cloudResult.setTextColor(0xFF7CFF8A);
                            cloudResult.setText("Playing ✓");
                        }
                    });
                    previewPlayer.play(pcm, com.toiboytoestar.kurisu.core.GeminiTts.SAMPLE_RATE, new PcmPlayer.Cancel() {
                        @Override
                        public boolean cancelled() {
                            return isFinishing();
                        }
                    });
                } catch (final Exception e) {
                    h.post(new Runnable() {
                        @Override
                        public void run() {
                            cloudResult.setTextColor(0xFFFF6A5A);
                            cloudResult.setText(Brain.friendly(e));
                        }
                    });
                }
            }
        }).start();
    }

    private void runTest() {
        String pid = Providers.ALL[providerSp.getSelectedItemPosition()].id;
        testResult.setTextColor(UiKit.INK);
        testResult.setText("Testing…");
        Brain.testConnection(prefs, pid, baseUrl.getText().toString(), model.getText().toString(), apiKey.getText().toString(),
                h, Executors.newSingleThreadExecutor(), new Brain.Runnable0() {
                    @Override
                    public void run() {
                        // A working key must not be lost just because SAVE was not tapped.
                        saveAiOnly();
                        testResult.setTextColor(0xFF7CFF8A);
                        testResult.setText("Connected ✓ and saved");
                    }
                }, new Brain.Callback1() {
                    @Override
                    public void call(String s) {
                        testResult.setTextColor(0xFFFF6A5A);
                        testResult.setText(s);
                    }
                });
    }

    private void saveAiOnly() {
        Providers.P p = Providers.ALL[providerSp.getSelectedItemPosition()];
        String url = baseUrl.getText().toString().trim();
        String m = model.getText().toString().trim();
        // store "" when equal to the preset so future preset updates apply
        prefs.saveAi(p.id, (p.keyRequired && url.equals(p.baseUrl)) ? "" : url, m.equals(p.model) ? "" : m, apiKey.getText().toString());
    }

    private void save() {
        saveAiOnly();
        prefs.setUserName(name.getText().toString());
        prefs.setMemory(memory.getText().toString().trim());
        prefs.setSpeechLang(selectedLang());
        int vi = voiceSp.getSelectedItemPosition();
        android.content.SharedPreferences.Editor e = prefs.raw().edit();
        e.putString("tts_voice", vi > 0 && vi < voiceNames.size() ? voiceNames.get(vi) : "");
        e.putFloat("tts_rate", rateValue());
        e.putFloat("tts_pitch", pitchValue());
        e.putBoolean("speak", speak.isChecked());
        e.putBoolean("prefer_offline", offline.isChecked());
        e.putBoolean("classic_eggs", eggs.isChecked());
        e.putBoolean("show_notification", xp.isChecked());
        e.putBoolean("wake_enabled", wake.isChecked());
        e.putInt("wake_hour", wakeHour);
        e.putInt("wake_minute", wakeMinute);
        int days = 0;
        for (int i = 0; i < 7; i++) if (dayBoxes[i].isChecked()) days |= 1 << i;
        e.putInt("wake_days", days);
        e.putBoolean("wake_word", wakeWordSw.isChecked());
        e.putBoolean("cloud_tts", cloudSw.isChecked());
        e.putString("cloud_voice", com.toiboytoestar.kurisu.core.GeminiTts.VOICES[Math.max(0, cloudVoiceSp.getSelectedItemPosition())]);
        e.putString("cloud_key", cloudKey.getText().toString().trim());
        e.putString("cloud_model", cloudModel.getText().toString().trim());
        e.putBoolean("checkin_enabled", checkin.isChecked());
        e.putInt("checkin_hour", checkinHour);
        e.putInt("checkin_minute", checkinMinute);
        e.putString("ringtone", RINGTONES[Math.max(0, ringSp.getSelectedItemPosition())][1]);
        e.apply();
        Notifier.ensureChannelsForRingtoneChange(this);
        Reminders.scheduleWakeCall(this);
        Reminders.scheduleCheckin(this);
        if (xp.isChecked()) Notifier.showXp(this);
        else Notifier.cancel(this, Notifier.ID_XP);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        previewPlayer.stop();
        h.removeCallbacksAndMessages(null);
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
    }
}
