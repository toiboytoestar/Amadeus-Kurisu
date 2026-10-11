package com.toiboytoestar.kurisu.core;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.List;

/** Streaming client for OpenAI-compatible chat/completions endpoints (OpenAI, Gemini, Groq, OpenRouter, Ollama...). */
public class OpenAiClient implements LlmClient {
    private final String baseUrl;
    private final String apiKey;
    private final String model;

    public OpenAiClient(String baseUrl, String apiKey, String model) {
        this.baseUrl = AnthropicClient.trimSlash(baseUrl.length() == 0 ? "https://api.openai.com/v1" : baseUrl);
        this.apiKey = apiKey;
        this.model = model;
    }

    String buildBody(String system, List<Msg> messages) throws JSONException {
        JSONObject body = new JSONObject();
        body.put("model", model);
        body.put("stream", true);
        JSONArray arr = new JSONArray();
        if (system != null && system.length() > 0) {
            arr.put(new JSONObject().put("role", "system").put("content", system));
        }
        for (Msg m : messages) {
            boolean empty = m.text == null || m.text.trim().length() == 0;
            if (empty && !m.hasImages()) continue;
            if (!m.hasImages()) {
                arr.put(new JSONObject().put("role", m.role).put("content", m.text));
                continue;
            }
            JSONArray parts = new JSONArray();
            if (!empty) parts.put(new JSONObject().put("type", "text").put("text", m.text));
            for (String img : m.images) {
                parts.put(new JSONObject().put("type", "image_url").put("image_url",
                        new JSONObject().put("url", "data:image/jpeg;base64," + img)));
            }
            arr.put(new JSONObject().put("role", m.role).put("content", parts));
        }
        body.put("messages", arr);
        return body.toString();
    }

    @Override
    public void stream(String system, List<Msg> messages, final Callback cb) throws IOException {
        String json;
        try {
            json = buildBody(system, messages);
        } catch (JSONException e) {
            throw new IOException(e);
        }
        final HttpURLConnection c = Http.open(baseUrl + "/chat/completions", "POST");
        c.setRequestProperty("Content-Type", "application/json");
        c.setRequestProperty("Accept", "text/event-stream");
        if (apiKey != null && apiKey.length() > 0) c.setRequestProperty("Authorization", "Bearer " + apiKey);
        try {
            Http.writeBody(c, json);
            if (c.getResponseCode() / 100 != 2) throw Http.httpError(c);
            BufferedReader in = Http.reader(c.getInputStream());
            try {
                Sse.read(in, new Sse.Handler() {
                    @Override
                    public boolean onEvent(String event, String data) throws IOException {
                        if (cb.isCancelled()) return false;
                        String d = data.trim();
                        if (d.length() == 0) return true;
                        if ("[DONE]".equals(d)) return false;
                        try {
                            JSONObject o = new JSONObject(d);
                            JSONObject err = o.optJSONObject("error");
                            if (err != null) {
                                throw new LlmException("The AI service reported an error: "
                                        + err.optString("message", "unknown error"), 0);
                            }
                            JSONArray choices = o.optJSONArray("choices");
                            if (choices != null && choices.length() > 0) {
                                JSONObject delta = choices.getJSONObject(0).optJSONObject("delta");
                                if (delta != null && !delta.isNull("content")) {
                                    String t = delta.optString("content", "");
                                    if (t.length() > 0) cb.onDelta(t);
                                }
                            }
                        } catch (JSONException ignored) {
                            // ignore malformed event
                        }
                        return true;
                    }
                });
            } finally {
                in.close();
            }
        } finally {
            c.disconnect();
        }
    }
}
