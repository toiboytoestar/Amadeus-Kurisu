package com.toiboytoestar.kurisu.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class LlmClientsTest {
    private HttpServer server;
    private String base;
    private volatile String lastBody;
    private volatile String lastPath;
    private volatile String lastAuth;
    private volatile String lastApiKey;
    private volatile int status = 200;
    private volatile String response = "";

    @Before
    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", new HttpHandler() {
            public void handle(HttpExchange ex) throws IOException {
                InputStream in = ex.getRequestBody();
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                byte[] b = new byte[1024];
                int n;
                while ((n = in.read(b)) > 0) bo.write(b, 0, n);
                lastBody = new String(bo.toByteArray(), Charset.forName("UTF-8"));
                lastPath = ex.getRequestURI().getPath();
                lastAuth = ex.getRequestHeaders().getFirst("Authorization");
                lastApiKey = ex.getRequestHeaders().getFirst("x-api-key");
                byte[] out = response.getBytes(Charset.forName("UTF-8"));
                ex.getResponseHeaders().add("Content-Type", status == 200 ? "text/event-stream" : "application/json");
                ex.sendResponseHeaders(status, out.length);
                OutputStream os = ex.getResponseBody();
                os.write(out);
                os.close();
            }
        });
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @After
    public void stop() {
        server.stop(0);
    }

    static class Collect implements LlmClient.Callback {
        final StringBuilder sb = new StringBuilder();
        int cancelAfter = Integer.MAX_VALUE;
        int deltas = 0;

        public void onDelta(String t) { sb.append(t); deltas++; }
        public boolean isCancelled() { return deltas >= cancelAfter; }
    }

    @Test
    public void anthropicStreamsTextAndMergesRoles() throws Exception {
        response = "event: message_start\ndata: {\"type\":\"message_start\",\"message\":{}}\n\n"
                + ": ping\n\n"
                + "event: content_block_delta\ndata: {\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"text_delta\",\"text\":\"Hel\"}}\n\n"
                + "event: content_block_delta\ndata: {\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"text_delta\",\"text\":\"lo \\u3053\\u3093\"}}\n\n"
                + "event: content_block_delta\ndata: {\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"input_json_delta\",\"partial_json\":\"x\"}}\n\n"
                + "event: message_stop\ndata: {\"type\":\"message_stop\"}\n\n";
        AnthropicClient c = new AnthropicClient(base + "/", "sk-test", "claude-haiku-5-5", 512);
        Collect col = new Collect();
        List<Msg> msgs = Arrays.asList(
                new Msg("assistant", "orphan assistant first"),
                new Msg("user", "one"), new Msg("user", "two"),
                new Msg("assistant", "reply"), new Msg("user", "three"));
        c.stream("SYSTEM", msgs, col);
        assertEquals("Hello こん", col.sb.toString());
        assertEquals("/v1/messages", lastPath);
        assertEquals("sk-test", lastApiKey);
        JSONObject body = new JSONObject(lastBody);
        assertEquals("claude-haiku-5-5", body.getString("model"));
        assertEquals("SYSTEM", body.getString("system"));
        assertTrue(body.getBoolean("stream"));
        JSONArray arr = body.getJSONArray("messages");
        assertEquals(3, arr.length());
        assertEquals("user", arr.getJSONObject(0).getString("role"));
        assertEquals("one\n\ntwo", arr.getJSONObject(0).getString("content"));
        assertEquals("assistant", arr.getJSONObject(1).getString("role"));
        assertEquals("user", arr.getJSONObject(2).getString("role"));
    }

    @Test
    public void anthropicSurfacesAuthErrors() throws Exception {
        status = 401;
        response = "{\"type\":\"error\",\"error\":{\"type\":\"authentication_error\",\"message\":\"invalid x-api-key\"}}";
        AnthropicClient c = new AnthropicClient(base, "bad", "m", 100);
        try {
            c.stream("", new ArrayList<Msg>(Arrays.asList(new Msg("user", "hi"))), new Collect());
            fail("expected exception");
        } catch (LlmClient.LlmException e) {
            assertEquals(401, e.status);
            assertTrue(e.getMessage(), e.getMessage().contains("API key was rejected"));
            assertTrue(e.getMessage(), e.getMessage().contains("invalid x-api-key"));
        }
    }

    @Test
    public void anthropicMidStreamErrorIsReported() throws Exception {
        response = "event: content_block_delta\ndata: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"a\"}}\n\n"
                + "event: error\ndata: {\"type\":\"error\",\"error\":{\"type\":\"overloaded_error\",\"message\":\"Overloaded\"}}\n\n";
        AnthropicClient c = new AnthropicClient(base, "k", "m", 100);
        Collect col = new Collect();
        try {
            c.stream("", Arrays.asList(new Msg("user", "hi")), col);
            fail("expected exception");
        } catch (LlmClient.LlmException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("Overloaded"));
        }
        assertEquals("a", col.sb.toString());
    }

    @Test
    public void openAiStreamsAndStopsAtDone() throws Exception {
        response = "data: {\"choices\":[{\"delta\":{\"role\":\"assistant\",\"content\":\"\"}}]}\n\n"
                + "data: {\"choices\":[{\"delta\":{\"content\":\"Hi\"}}]}\n\n"
                + "data: {\"choices\":[{\"delta\":{\"content\":null}}]}\n\n"
                + "data: {\"choices\":[{\"delta\":{\"content\":\" there\"}}]}\n\n"
                + "data: [DONE]\n\n"
                + "data: {\"choices\":[{\"delta\":{\"content\":\"NEVER\"}}]}\n\n";
        OpenAiClient c = new OpenAiClient(base + "/v1", "sk-open", "gpt-x");
        Collect col = new Collect();
        c.stream("SYS", Arrays.asList(new Msg("user", "hello")), col);
        assertEquals("Hi there", col.sb.toString());
        assertEquals("/v1/chat/completions", lastPath);
        assertEquals("Bearer sk-open", lastAuth);
        JSONArray arr = new JSONObject(lastBody).getJSONArray("messages");
        assertEquals("system", arr.getJSONObject(0).getString("role"));
        assertEquals("hello", arr.getJSONObject(1).getString("content"));
    }

    @Test
    public void openAiWithoutKeySendsNoAuthHeader() throws Exception {
        response = "data: {\"choices\":[{\"delta\":{\"content\":\"ok\"}}]}\n\ndata: [DONE]\n\n";
        OpenAiClient c = new OpenAiClient(base, "", "llama3");
        Collect col = new Collect();
        c.stream("", Arrays.asList(new Msg("user", "hello")), col);
        assertEquals("ok", col.sb.toString());
        assertEquals(null, lastAuth);
        assertEquals("/chat/completions", lastPath);
    }

    @Test
    public void cancellationStopsReading() throws Exception {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 50; i++) sb.append("data: {\"choices\":[{\"delta\":{\"content\":\"x\"}}]}\n\n");
        response = sb.toString();
        OpenAiClient c = new OpenAiClient(base, "k", "m");
        Collect col = new Collect();
        col.cancelAfter = 3;
        c.stream("", Arrays.asList(new Msg("user", "hello")), col);
        assertEquals(3, col.deltas);
    }

    @Test
    public void openAiRateLimitMessage() throws Exception {
        status = 429;
        response = "[{\"error\":{\"message\":\"Quota exceeded\"}}]"; // Gemini style array
        OpenAiClient c = new OpenAiClient(base, "k", "m");
        try {
            c.stream("", Arrays.asList(new Msg("user", "hello")), new Collect());
            fail();
        } catch (LlmClient.LlmException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("Rate limit"));
            assertTrue(e.getMessage(), e.getMessage().contains("Quota exceeded"));
        }
    }

    @Test
    public void fetchPageParsesHtmlFromPublicLookingHostOnly() throws Exception {
        // the loopback test server must be refused by the SSRF guard
        try {
            WebTools.fetchPage(base + "/page", 1000);
            fail("loopback must be refused");
        } catch (IOException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("private"));
        }
    }

    @Test
    public void imagesAreSentAsContentBlocks() throws Exception {
        java.util.List<String> imgs = java.util.Arrays.asList("QUJD");
        AnthropicClient a = new AnthropicClient("http://x", "k", "m", 10);
        JSONObject ab = new JSONObject(a.buildBody("", java.util.Arrays.asList(new Msg("user", "what is this?", imgs))));
        JSONArray content = ab.getJSONArray("messages").getJSONObject(0).getJSONArray("content");
        assertEquals("image", content.getJSONObject(0).getString("type"));
        assertEquals("QUJD", content.getJSONObject(0).getJSONObject("source").getString("data"));
        assertEquals("text", content.getJSONObject(1).getString("type"));

        OpenAiClient o = new OpenAiClient("http://x", "k", "m");
        JSONObject ob = new JSONObject(o.buildBody("", java.util.Arrays.asList(new Msg("user", "what is this?", imgs))));
        JSONArray parts = ob.getJSONArray("messages").getJSONObject(0).getJSONArray("content");
        assertEquals("text", parts.getJSONObject(0).getString("type"));
        assertEquals("data:image/jpeg;base64,QUJD", parts.getJSONObject(1).getJSONObject("image_url").getString("url"));

        Msg stripped = new Msg("user", "look", imgs).withoutImages();
        assertEquals(false, stripped.hasImages());
    }
}
