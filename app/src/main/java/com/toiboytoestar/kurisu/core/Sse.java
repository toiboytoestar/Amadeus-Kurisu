package com.toiboytoestar.kurisu.core;

import java.io.BufferedReader;
import java.io.IOException;

/** Minimal Server-Sent-Events reader: yields (event, data) pairs. */
public final class Sse {
    private Sse() {}

    public interface Handler {
        /** @return false to stop reading. */
        boolean onEvent(String event, String data) throws IOException;
    }

    public static void read(BufferedReader in, Handler h) throws IOException {
        String event = null;
        StringBuilder data = new StringBuilder();
        String line;
        while ((line = in.readLine()) != null) {
            if (line.length() == 0) {
                if (data.length() > 0 || event != null) {
                    if (!h.onEvent(event, data.toString())) return;
                }
                event = null;
                data.setLength(0);
                continue;
            }
            if (line.charAt(0) == ':') continue; // comment / keep-alive
            int colon = line.indexOf(':');
            String field = colon < 0 ? line : line.substring(0, colon);
            String value = colon < 0 ? "" : line.substring(colon + 1);
            if (value.startsWith(" ")) value = value.substring(1);
            if ("event".equals(field)) event = value;
            else if ("data".equals(field)) {
                if (data.length() > 0) data.append('\n');
                data.append(value);
            }
        }
        if (data.length() > 0 || event != null) h.onEvent(event, data.toString());
    }
}
