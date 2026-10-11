package com.toiboytoestar.kurisu.core;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class GeminiTtsTest {
    private HttpServer server;
    private String base;
    private final List<String> bodies = new ArrayList<String>();
    private volatile String key;
    private volatile int rejectFirst; // number of leading 400s

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
                bodies.add(new String(bo.toByteArray(), Charset.forName("UTF-8")));
                key = ex.getRequestHeaders().getFirst("x-goog-api-key");
                String resp;
                int status = 200;
                if (rejectFirst > 0) {
                    rejectFirst--;
                    status = 400;
                    resp = "{\"error\":{\"message\":\"Invalid JSON payload\"}}";
                } else {
                    // WAV: 44-byte header + 4 PCM bytes (1,2,3,4); base64 of that
                    byte[] wav = new byte[48];
                    wav[0] = 'R'; wav[1] = 'I'; wav[2] = 'F'; wav[3] = 'F';
                    wav[8] = 'W'; wav[9] = 'A'; wav[10] = 'V'; wav[11] = 'E';
                    wav[12] = 'f'; wav[13] = 'm'; wav[14] = 't'; wav[15] = ' '; wav[16] = 16;
                    wav[36] = 'd'; wav[37] = 'a'; wav[38] = 't'; wav[39] = 'a'; wav[40] = 4;
                    wav[44] = 1; wav[45] = 2; wav[46] = 3; wav[47] = 4;
                    resp = "{\"candidates\":[{\"content\":{\"parts\":[{\"inlineData\":{\"mimeType\":\"audio/wav\",\"data\":\"" + enc(wav) + "\"}}]}}]}";
                }
                byte[] out = resp.getBytes(Charset.forName("UTF-8"));
                ex.getResponseHeaders().add("Content-Type", "application/json");
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

    private static String enc(byte[] b) {
        String t = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < b.length; i += 3) {
            int v = (b[i] & 0xff) << 16 | (i + 1 < b.length ? (b[i + 1] & 0xff) << 8 : 0) | (i + 2 < b.length ? (b[i + 2] & 0xff) : 0);
            sb.append(t.charAt(v >> 18 & 63)).append(t.charAt(v >> 12 & 63));
            sb.append(i + 1 < b.length ? t.charAt(v >> 6 & 63) : '=').append(i + 2 < b.length ? t.charAt(v & 63) : '=');
        }
        return sb.toString();
    }

    @Test public void base64Decodes() {
        assertArrayEquals("Hello, world!".getBytes(), GeminiTts.base64("SGVsbG8sIHdvcmxkIQ=="));
        assertArrayEquals(new byte[]{(byte) 0xfb, (byte) 0xff}, GeminiTts.base64("+/8="));
    }

    @Test public void returnsPcmWithoutWavHeader() throws Exception {
        GeminiTts t = new GeminiTts(base, "KEY", "gemini-3.8-flash-tts", "Leda");
        byte[] pcm = t.synthesize("Hello there", "happy");
        assertArrayEquals(new byte[]{1, 2, 3, 4}, pcm);
        assertEquals("KEY", key);
        assertTrue(bodies.get(0).contains("Leda"));
        assertTrue(bodies.get(0).contains("cheerfully"));
        assertTrue(bodies.get(0).contains("\"AUDIO\""));
    }

    @Test public void retriesOtherRequestShapeOn400() throws Exception {
        rejectFirst = 1;
        GeminiTts t = new GeminiTts(base, "KEY", "gemini-3.8-flash-tts", "Leda");
        assertEquals(4, t.synthesize("Hi", "normal").length);
        assertEquals(2, bodies.size());
        assertTrue(bodies.get(0).contains("\"voice\":\"Leda\""));          // new-style first for 3.x models
        assertTrue(bodies.get(1).contains("prebuiltVoiceConfig"));          // then the older form
    }

    @Test(expected = LlmClient.LlmException.class)
    public void failsWhenBothShapesRejected() throws Exception {
        rejectFirst = 2;
        new GeminiTts(base, "KEY", "gemini-3.8-flash-tts", "Leda").synthesize("Hi", "normal");
    }
}
