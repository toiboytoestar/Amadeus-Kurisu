package com.toiboytoestar.kurisu.core;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;

/** Small HttpURLConnection helpers shared by the clients and tools. */
public final class Http {
    private Http() {}

    static final Charset UTF8 = Charset.forName("UTF-8");
    public static final int CONNECT_TIMEOUT = 15000;
    public static final int READ_TIMEOUT = 90000;

    public static HttpURLConnection open(String url, String method) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(CONNECT_TIMEOUT);
        c.setReadTimeout(READ_TIMEOUT);
        c.setRequestMethod(method);
        c.setRequestProperty("User-Agent", "AmadeusKurisu/1.0 (Android)");
        return c;
    }

    public static void writeBody(HttpURLConnection c, String body) throws IOException {
        byte[] bytes = body.getBytes(UTF8);
        c.setDoOutput(true);
        // No streaming mode: it makes HttpURLConnection drop the error body of 401 responses.
        OutputStream os = c.getOutputStream();
        try {
            os.write(bytes);
            os.flush();
        } finally {
            os.close();
        }
    }

    public static BufferedReader reader(InputStream in) {
        return new BufferedReader(new InputStreamReader(in, UTF8), 4096);
    }

    public static String readAll(InputStream in, int maxBytes) throws IOException {
        if (in == null) return "";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        try {
            while ((n = in.read(buf)) != -1) {
                if (out.size() + n > maxBytes) {
                    out.write(buf, 0, Math.max(0, maxBytes - out.size()));
                    break;
                }
                out.write(buf, 0, n);
            }
        } finally {
            try { in.close(); } catch (IOException ignored) { }
        }
        return new String(out.toByteArray(), UTF8);
    }

    /** Builds a human friendly error for a non-2xx response. */
    public static LlmClient.LlmException httpError(HttpURLConnection c) throws IOException {
        int code = c.getResponseCode();
        String body = "";
        try {
            body = readAll(c.getErrorStream(), 8192);
        } catch (IOException ignored) { }
        String msg = extractMessage(body);
        String hint;
        if (code == 401 || code == 403) hint = "The API key was rejected";
        else if (code == 404) hint = "Endpoint or model not found";
        else if (code == 429) hint = "Rate limit or quota reached";
        else if (code >= 500) hint = "The AI service had a problem";
        else hint = "Request failed";
        String low = msg.toLowerCase(java.util.Locale.ROOT);
        if (code == 402 || low.contains("credit balance") || low.contains("insufficient_quota") || low.contains("insufficient credit")
                || low.contains("insufficient funds")) {
            hint = "This key has no credits. Switch to a free provider (Gemini or Groq) in Settings";
        }
        StringBuilder sb = new StringBuilder(hint).append(" (HTTP ").append(code).append(")");
        if (msg.length() > 0) sb.append(": ").append(msg);
        return new LlmClient.LlmException(sb.toString(), code);
    }

    static String extractMessage(String body) {
        if (body == null || body.trim().length() == 0) return "";
        try {
            String t = body.trim();
            if (t.startsWith("[")) t = new org.json.JSONArray(t).getJSONObject(0).toString();
            JSONObject o = new JSONObject(t);
            Object err = o.opt("error");
            if (err instanceof JSONObject) {
                String m = ((JSONObject) err).optString("message", "");
                if (m.length() > 0) return clip(m);
            } else if (err instanceof String && ((String) err).length() > 0) {
                return clip((String) err);
            }
            String m = o.optString("message", "");
            if (m.length() > 0) return clip(m);
        } catch (Exception ignored) { }
        return clip(body.trim());
    }

    private static String clip(String s) {
        return s.length() > 300 ? s.substring(0, 300) + "…" : s;
    }
}
