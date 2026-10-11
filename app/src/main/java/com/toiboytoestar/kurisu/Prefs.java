package com.toiboytoestar.kurisu;

import android.content.Context;
import android.content.SharedPreferences;

import com.toiboytoestar.kurisu.core.Providers;

import java.util.Locale;

/** Typed access to all user settings. */
public final class Prefs {
    private final SharedPreferences sp;

    public Prefs(Context ctx) {
        sp = ctx.getApplicationContext().getSharedPreferences("amadeus_prefs", Context.MODE_PRIVATE);
    }

    public SharedPreferences raw() {
        return sp;
    }

    // ---- AI
    public String provider() { return sp.getString("provider", "gemini"); }
    public String baseUrl() { return sp.getString("base_url", ""); }
    public String model() {
        String m = sp.getString("model", "");
        return m.length() > 0 ? m : Providers.byId(provider()).model;
    }
    public String apiKey() { return sp.getString("api_key", ""); }
    public boolean llmConfigured() { return Providers.isConfigured(provider(), baseUrl(), apiKey(), model()); }

    public void saveAi(String provider, String baseUrl, String model, String key) {
        sp.edit().putString("provider", provider).putString("base_url", baseUrl.trim())
                .putString("model", model.trim()).putString("api_key", key.trim()).apply();
    }

    // ---- personal
    public String userName() { return sp.getString("user_name", ""); }
    public void setUserName(String n) { sp.edit().putString("user_name", n.trim()).apply(); }

    public String memory() { return sp.getString("memory", ""); }
    public void setMemory(String m) { sp.edit().putString("memory", m).apply(); }

    /** Appends a remembered fact (de-duplicated, capped). */
    public void addMemory(String fact) {
        String f = fact.trim().replace('\n', ' ');
        if (f.length() == 0) return;
        if (f.length() > 200) f = f.substring(0, 200);
        String[] lines = memory().length() == 0 ? new String[0] : memory().split("\n");
        StringBuilder sb = new StringBuilder();
        int keep = Math.max(0, lines.length - 39);
        for (int i = keep; i < lines.length; i++) {
            if (lines[i].equalsIgnoreCase(f)) return;
            sb.append(lines[i]).append('\n');
        }
        sb.append(f);
        setMemory(sb.toString());
    }

    public java.util.List<String> memoryLines() {
        java.util.List<String> out = new java.util.ArrayList<String>();
        for (String l : memory().split("\n")) if (l.trim().length() > 0) out.add(l.trim());
        return out;
    }

    public void setMemoryLines(java.util.List<String> lines) {
        StringBuilder sb = new StringBuilder();
        for (String l : lines) sb.append(sb.length() > 0 ? "\n" : "").append(l);
        setMemory(sb.toString());
    }

    /** Removes every remembered fact containing the keyword (case-insensitive). Returns how many were removed. */
    public int forgetMemory(String keyword) {
        String k = keyword.trim().toLowerCase(Locale.ROOT);
        if (k.length() < 2) return 0;
        java.util.List<String> keep = new java.util.ArrayList<String>();
        int removed = 0;
        for (String l : memoryLines()) {
            if (l.toLowerCase(Locale.ROOT).contains(k)) removed++;
            else keep.add(l);
        }
        if (removed > 0) setMemoryLines(keep);
        return removed;
    }

    // ---- evening check-in & streak
    public boolean checkinEnabled() { return sp.getBoolean("checkin_enabled", false); }
    public int checkinHour() { return sp.getInt("checkin_hour", 21); }
    public int checkinMinute() { return sp.getInt("checkin_minute", 0); }

    /** Records that the user talked to Kurisu today and returns the streak length. */
    public int touchStreak() {
        int today = com.toiboytoestar.kurisu.core.Streak.epochDay(System.currentTimeMillis(), java.util.TimeZone.getDefault());
        int[] n = com.toiboytoestar.kurisu.core.Streak.advance(sp.getInt("streak_last", 0), sp.getInt("streak_count", 0), today);
        sp.edit().putInt("streak_count", n[0]).putInt("streak_last", n[1]).apply();
        return n[0];
    }

    public int streak() {
        int today = com.toiboytoestar.kurisu.core.Streak.epochDay(System.currentTimeMillis(), java.util.TimeZone.getDefault());
        return com.toiboytoestar.kurisu.core.Streak.current(sp.getInt("streak_last", 0), sp.getInt("streak_count", 0), today);
    }

    // ---- voice
    public String speechLang() {
        String v = sp.getString("speech_lang", "");
        if (v.length() > 0) return v;
        Locale l = Locale.getDefault();
        String tag = l.getLanguage() + (l.getCountry().length() > 0 ? "-" + l.getCountry() : "");
        return tag.length() < 2 ? "en-US" : tag;
    }
    public void setSpeechLang(String tag) { sp.edit().putString("speech_lang", tag).apply(); }
    public String langCode() { return speechLang().split("-")[0]; }

    // natural cloud voice (Gemini speech API)
    public boolean cloudTts() { return sp.getBoolean("cloud_tts", false); }
    public String cloudVoice() { return sp.getString("cloud_voice", com.toiboytoestar.kurisu.core.GeminiTts.DEFAULT_VOICE); }
    public String cloudModel() { return sp.getString("cloud_model", ""); }
    /** Dedicated key, or the AI key when the AI provider is Gemini. */
    public String cloudKey() {
        String k = sp.getString("cloud_key", "").trim();
        if (k.length() == 0 && "gemini".equals(provider())) k = apiKey();
        return k;
    }

    public boolean speakReplies() { return sp.getBoolean("speak", true); }
    /** "Hey Kurisu" mode: keeps listening but only answers when addressed (while the app is open). */
    public boolean wakeWord() { return sp.getBoolean("wake_word", false); }
    public void setWakeWord(boolean v) { sp.edit().putBoolean("wake_word", v).apply(); }
    public boolean autoListen() { return handsFree() || wakeWord(); }
    public boolean handsFree() { return sp.getBoolean("hands_free", false); }
    public void setHandsFree(boolean v) { sp.edit().putBoolean("hands_free", v).apply(); }
    public boolean preferOffline() { return sp.getBoolean("prefer_offline", false); }
    public float ttsRate() { return sp.getFloat("tts_rate", 1.05f); }
    public float ttsPitch() { return sp.getFloat("tts_pitch", 1.12f); }
    public String ttsVoice() { return sp.getString("tts_voice", ""); }
    public boolean classicEggs() { return sp.getBoolean("classic_eggs", true); }

    // ---- display
    public boolean showXpIcon() { return sp.getBoolean("show_notification", false); }

    // ---- wake-up call
    public boolean wakeEnabled() { return sp.getBoolean("wake_enabled", false); }
    public int wakeHour() { return sp.getInt("wake_hour", 7); }
    public int wakeMinute() { return sp.getInt("wake_minute", 0); }
    /** Bit mask, bit 0 = Sunday. Default every day. */
    public int wakeDays() { return sp.getInt("wake_days", 0x7f); }
    public String ringtone() { return sp.getString("ringtone", "ringtone_gate_of_steiner"); }
}
