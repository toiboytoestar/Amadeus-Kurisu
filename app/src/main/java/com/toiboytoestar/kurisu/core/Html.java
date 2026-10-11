package com.toiboytoestar.kurisu.core;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Crude but dependable HTML to text conversion for feeding web pages to the model. */
public final class Html {
    private Html() {}

    private static final Pattern SCRIPTS = Pattern.compile("(?is)<(script|style|noscript|svg|head|nav|footer|form|iframe)\\b.*?</\\1>");
    private static final Pattern COMMENTS = Pattern.compile("(?s)<!--.*?-->");
    private static final Pattern BLOCKS = Pattern.compile("(?i)</?(p|div|br|li|ul|ol|h[1-6]|tr|table|section|article|blockquote|pre)\\b[^>]*>");
    private static final Pattern TAGS = Pattern.compile("(?s)<[^>]*>");
    private static final Pattern TITLE = Pattern.compile("(?is)<title[^>]*>(.*?)</title>");

    public static String title(String html) {
        Matcher m = TITLE.matcher(html);
        return m.find() ? decode(TAGS.matcher(m.group(1)).replaceAll("")).trim() : "";
    }

    public static String toText(String html, int maxChars) {
        String s = COMMENTS.matcher(html).replaceAll(" ");
        s = SCRIPTS.matcher(s).replaceAll(" ");
        s = BLOCKS.matcher(s).replaceAll("\n");
        s = TAGS.matcher(s).replaceAll("");
        s = decode(s);
        s = s.replaceAll("[ \\t\\u00A0]+", " ").replaceAll(" ?\\n ?", "\n").replaceAll("\\n{3,}", "\n\n").trim();
        if (s.length() > maxChars) s = s.substring(0, maxChars) + "\n[truncated]";
        return s;
    }

    public static String decode(String s) {
        if (s.indexOf('&') < 0) return s;
        s = s.replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
                .replace("&#39;", "'").replace("&apos;", "'").replace("&ndash;", "-").replace("&mdash;", "-")
                .replace("&hellip;", "...").replace("&rsquo;", "'").replace("&lsquo;", "'")
                .replace("&ldquo;", "\"").replace("&rdquo;", "\"");
        Matcher m = Pattern.compile("&#(x?)([0-9a-fA-F]+);").matcher(s);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String repl = " ";
            try {
                int cp = Integer.parseInt(m.group(2), m.group(1).length() > 0 ? 16 : 10);
                repl = new String(Character.toChars(cp));
            } catch (Exception ignored) { }
            m.appendReplacement(sb, Matcher.quoteReplacement(repl));
        }
        m.appendTail(sb);
        return sb.toString().replace("&amp;", "&");
    }
}
