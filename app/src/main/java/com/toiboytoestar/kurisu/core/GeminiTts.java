package com.toiboytoestar.kurisu.core;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;

/** Natural-sounding speech from the Gemini API. Returns raw 16-bit mono PCM at 24 kHz. Blocking: use a background thread. */
public class GeminiTts {
    public static final int SAMPLE_RATE = 24000;
    public static final String DEFAULT_MODEL = "gemini-3.8-flash-tts";
    public static final String DEFAULT_VOICE = "Leda";
    public static final String[] VOICES = {"Leda", "Kore", "Zephyr", "Aoede", "Sulafat", "Achird", "Vindemiatrix", "Callirrhoe", "Puck", "Charon"};

    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final String voice;

    public GeminiTts(String baseUrl, String apiKey, String model, String voice) {
        this.baseUrl = AnthropicClient.trimSlash(baseUrl.length() == 0 ? "https://generativelanguage.googleapis.com" : baseUrl);
        this.apiKey = apiKey;
        this.model = model.length() == 0 ? DEFAULT_MODEL : model;
        this.voice = voice.length() == 0 ? DEFAULT_VOICE : voice;
    }

    /** How she should sound for a facial expression. */
    public static String styleFor(String mood) {
        if (mood == null) return "in a calm, clear, slightly dry young woman's voice";
        String m = mood.toLowerCase(java.util.Locale.ROOT);
        if (m.equals("happy") || m.equals("winking")) return "warmly and cheerfully";
        if (m.equals("annoyed") || m.equals("angry") || m.equals("pissed")) return "irritated, a bit sharp, like a tsundere scientist";
        if (m.equals("sad") || m.equals("disappointed")) return "softly and sadly";
        if (m.equals("worried")) return "gently, with concern";
        if (m.equals("blush")) return "shyly and a little flustered";
        if (m.equals("surprised")) return "surprised and quick";
        if (m.equals("sleepy")) return "sleepy and quiet";
        if (m.equals("thinking")) return "thoughtfully";
        return "in a calm, clear, slightly dry young woman's voice";
    }

    String buildBody(String text, String mood, boolean prebuiltForm) throws JSONException {
        String prompt = "Say " + styleFor(mood) + ": " + text;
        JSONObject voiceCfg = prebuiltForm
                ? new JSONObject().put("prebuiltVoiceConfig", new JSONObject().put("voiceName", voice))
                : new JSONObject().put("voice", voice);
        JSONObject gen = new JSONObject()
                .put("responseModalities", new JSONArray().put("AUDIO"))
                .put("speechConfig", new JSONObject().put("voiceConfig", voiceCfg));
        return new JSONObject()
                .put("contents", new JSONArray().put(new JSONObject().put("parts", new JSONArray().put(new JSONObject().put("text", prompt)))))
                .put("generationConfig", gen).toString();
    }

    /** @return raw PCM (16-bit LE mono, 24 kHz). */
    public byte[] synthesize(String text, String mood) throws IOException {
        boolean oldModel = model.contains("2.5");
        LlmClient.LlmException first = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            boolean prebuilt = (attempt == 0) == oldModel; // older models use prebuiltVoiceConfig; newer docs use "voice"
            try {
                return request(text, mood, prebuilt);
            } catch (LlmClient.LlmException e) {
                if (first == null) first = e;
                if (e.status != 400) throw e; // only a rejected request shape is worth retrying in the other form
            }
        }
        throw first;
    }

    private byte[] request(String text, String mood, boolean prebuilt) throws IOException {
        String json;
        try {
            json = buildBody(text, mood, prebuilt);
        } catch (JSONException e) {
            throw new IOException(e);
        }
        HttpURLConnection c = Http.open(baseUrl + "/v1beta/models/" + model + ":generateContent", "POST");
        c.setRequestProperty("Content-Type", "application/json");
        c.setRequestProperty("x-goog-api-key", apiKey);
        try {
            Http.writeBody(c, json);
            if (c.getResponseCode() / 100 != 2) throw Http.httpError(c);
            String body = Http.readAll(c.getInputStream(), 12 * 1024 * 1024);
            byte[] pcm = parseAudio(body);
            if (pcm.length == 0) throw new LlmClient.LlmException("The voice service returned no audio.", 0);
            return pcm;
        } finally {
            c.disconnect();
        }
    }

    /** Extracts the inline audio from a generateContent response and strips a WAV header if there is one. */
    static byte[] parseAudio(String responseJson) throws IOException {
        try {
            JSONArray cands = new JSONObject(responseJson).optJSONArray("candidates");
            if (cands != null && cands.length() > 0) {
                JSONObject content = cands.getJSONObject(0).optJSONObject("content");
                JSONArray parts = content == null ? null : content.optJSONArray("parts");
                for (int i = 0; parts != null && i < parts.length(); i++) {
                    JSONObject p = parts.getJSONObject(i);
                    JSONObject inline = p.optJSONObject("inlineData");
                    if (inline == null) inline = p.optJSONObject("inline_data");
                    if (inline != null && inline.optString("data", "").length() > 0) {
                        return stripWav(base64(inline.getString("data")));
                    }
                }
            }
        } catch (JSONException e) {
            throw new IOException("Unreadable voice response");
        }
        return new byte[0];
    }

    static byte[] stripWav(byte[] b) {
        if (b.length > 44 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F') {
            int i = 12;
            while (i + 8 <= b.length) {
                int size = (b[i + 4] & 0xff) | (b[i + 5] & 0xff) << 8 | (b[i + 6] & 0xff) << 16 | (b[i + 7] & 0xff) << 24;
                if (b[i] == 'd' && b[i + 1] == 'a' && b[i + 2] == 't' && b[i + 3] == 'a') {
                    int start = i + 8;
                    int len = size < 0 || start + size > b.length ? b.length - start : size;
                    byte[] out = new byte[len];
                    System.arraycopy(b, start, out, 0, len);
                    return out;
                }
                if (size < 0) break;
                i += 8 + size + (size & 1);
            }
        }
        return b;
    }

    /** Minimal Base64 decoder (java.util.Base64 needs Android 8; this app supports Android 5). */
    static byte[] base64(String s) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(s.length() * 3 / 4);
        int acc = 0, bits = 0;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            int v;
            if (ch >= 'A' && ch <= 'Z') v = ch - 'A';
            else if (ch >= 'a' && ch <= 'z') v = ch - 'a' + 26;
            else if (ch >= '0' && ch <= '9') v = ch - '0' + 52;
            else if (ch == '+' || ch == '-') v = 62;
            else if (ch == '/' || ch == '_') v = 63;
            else continue; // padding, whitespace
            acc = (acc << 6) | v;
            bits += 6;
            if (bits >= 8) {
                bits -= 8;
                out.write((acc >> bits) & 0xff);
            }
        }
        return out.toByteArray();
    }
}
