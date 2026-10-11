package com.toiboytoestar.kurisu.core;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Cleans model output so a TTS engine reads it naturally. */
public final class Speech {
    private Speech() {}

    private static final Pattern MD_LINK = Pattern.compile("\\[([^\\]]+)\\]\\((https?://[^)\\s]+)\\)");
    private static final Pattern URL = Pattern.compile("https?://\\S+");
    private static final Pattern CODE_FENCE = Pattern.compile("```[\\s\\S]*?```");
    private static final Pattern BULLET = Pattern.compile("(?m)^\\s*(?:[-*•]|\\d+[.)])\\s+");
    private static final Pattern HEADING = Pattern.compile("(?m)^\\s*#{1,6}\\s*");
    private static final Pattern WS = Pattern.compile("\\s+");

    public static String clean(String raw) {
        if (raw == null) return "";
        String s = raw;
        s = CODE_FENCE.matcher(s).replaceAll(" ");
        s = MD_LINK.matcher(s).replaceAll("$1");
        s = URL.matcher(s).replaceAll("link");
        s = HEADING.matcher(s).replaceAll("");
        s = BULLET.matcher(s).replaceAll("");
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '*' || c == '_' || c == '`' || c == '#' || c == '~' || c == '|' || c == '>') {
                if (c == '_' && i > 0 && i + 1 < s.length()
                        && Character.isLetterOrDigit(s.charAt(i - 1)) && Character.isLetterOrDigit(s.charAt(i + 1))) {
                    b.append(' '); // snake_case -> words
                }
                continue;
            }
            if (Character.isHighSurrogate(c)) { // emoji & other astral symbols
                if (i + 1 < s.length()) i++;
                continue;
            }
            if (c >= '☀' && c <= '➿') continue; // misc symbols / dingbats
            if (c == '️' || c == '‍') continue;
            b.append(c);
        }
        return WS.matcher(b).replaceAll(" ").trim();
    }

    /** Splits text into chunks shorter than max, preferring sentence boundaries. */
    public static java.util.List<String> chunk(String text, int max) {
        java.util.ArrayList<String> out = new java.util.ArrayList<String>();
        String t = text.trim();
        while (t.length() > max) {
            int cut = -1;
            for (int i = max - 1; i > max / 2; i--) {
                char c = t.charAt(i);
                if (c == '.' || c == '!' || c == '?' || c == '\n') { cut = i + 1; break; }
            }
            if (cut < 0) cut = t.lastIndexOf(' ', max - 1) + 1;
            if (cut <= 0) cut = max;
            out.add(t.substring(0, cut).trim());
            t = t.substring(cut).trim();
        }
        if (t.length() > 0) out.add(t);
        return out;
    }

    /** First match of group 1, or null. */
    static String find(Pattern p, String s) {
        Matcher m = p.matcher(s);
        return m.find() ? m.group(1) : null;
    }
}
