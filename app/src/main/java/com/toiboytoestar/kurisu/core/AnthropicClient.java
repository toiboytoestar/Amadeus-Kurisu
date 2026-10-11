package com.toiboytoestar.kurisu.core;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.List;

/** Streaming client for the Anthropic Messages API. */
public class AnthropicClient implements LlmClient {
    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final int maxTokens;

    public AnthropicClient(String baseUrl, String apiKey, String model, int maxTokens) {
        this.baseUrl = trimSlash(baseUrl.length() == 0 ? "https://api.anthropic.com" : baseUrl);
        this.apiKey = apiKey;
        this.model = model;
        this.maxTokens = maxTokens;
    }

    static String trimSlash(String s) {
        s = s.trim();
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        return s;
    }

    String buildBody(String system, List<Msg> messages) throws JSONException {
        JSONObject body = new JSONObject();
        body.put("model", model);
        body.put("max_tokens", maxTokens);
        body.put("stream", true);
        if (system != null && system.length() > 0) body.put("system", system);
        JSONArray arr = new JSONArray();
        String lastRole = null;
        JSONArray blocks = null;
        boolean anyImage = false;
        StringBuilder plain = null;
        // The API requires strictly alternating roles starting with "user": merge neighbours.
        for (Msg m : messages) {
            boolean empty = m.text == null || m.text.trim().length() == 0;
            if (empty && !m.hasImages()) continue;
            if (lastRole == null && !"user".equals(m.role)) continue;
            if (!m.role.equals(lastRole)) {
                if (lastRole != null) arr.put(finish(lastRole, blocks, plain, anyImage));
                lastRole = m.role;
                blocks = new JSONArray();
                plain = new StringBuilder();
                anyImage = false;
            }
            for (String img : m.images) {
                blocks.put(new JSONObject().put("type", "image").put("source", new JSONObject()
                        .put("type", "base64").put("media_type", "image/jpeg").put("data", img)));
                anyImage = true;
            }
            if (!empty) {
                blocks.put(new JSONObject().put("type", "text").put("text", m.text));
                if (plain.length() > 0) plain.append("\n\n");
                plain.append(m.text);
            }
        }
        if (lastRole != null) arr.put(finish(lastRole, blocks, plain, anyImage));
        body.put("messages", arr);
        return body.toString();
    }

    private static JSONObject finish(String role, JSONArray blocks, StringBuilder plain, boolean anyImage) throws JSONException {
        return new JSONObject().put("role", role).put("content", anyImage ? (Object) blocks : plain.toString());
    }

    @Override
    public void stream(String system, List<Msg> messages, final Callback cb) throws IOException {
        String json;
        try {
            json = buildBody(system, messages);
        } catch (JSONException e) {
            throw new IOException(e);
        }
        final HttpURLConnection c = Http.open(baseUrl + "/v1/messages", "POST");
        c.setRequestProperty("Content-Type", "application/json");
        c.setRequestProperty("Accept", "text/event-stream");
        c.setRequestProperty("x-api-key", apiKey);
        c.setRequestProperty("anthropic-version", "2023-06-01");
        try {
            Http.writeBody(c, json);
            if (c.getResponseCode() / 100 != 2) throw Http.httpError(c);
            BufferedReader in = Http.reader(c.getInputStream());
            try {
                Sse.read(in, new Sse.Handler() {
                    @Override
                    public boolean onEvent(String event, String data) throws IOException {
                        if (cb.isCancelled()) return false;
                        if (data.length() == 0) return true;
                        try {
                            JSONObject o = new JSONObject(data);
                            String type = o.optString("type", event == null ? "" : event);
                            if ("content_block_delta".equals(type)) {
                                JSONObject d = o.optJSONObject("delta");
                                if (d != null && "text_delta".equals(d.optString("type"))) {
                                    String t = d.optString("text", "");
                                    if (t.length() > 0) cb.onDelta(t);
                                }
                            } else if ("error".equals(type)) {
                                JSONObject e = o.optJSONObject("error");
                                String msg = e != null ? e.optString("message", "unknown error") : "unknown error";
                                throw new LlmException("The AI service reported an error: " + msg, 0);
                            } else if ("message_stop".equals(type)) {
                                return false;
                            }
                        } catch (JSONException ignored) {
                            // ignore malformed keep-alive style events
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
