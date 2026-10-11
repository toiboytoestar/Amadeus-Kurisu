package com.toiboytoestar.kurisu.core;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fast, offline understanding of everyday commands ("set a timer for ten minutes",
 * "remind me to call mom in 2 hours", "add a task ...").  Anything it does not recognise
 * returns null and is handled by the language model instead.
 */
public final class LocalIntents {
    private LocalIntents() {}

    public enum Type {
        TIMER, ALARM, REMINDER, TASK, NOTE, MEMORY, POMODORO, OPEN_APP, SEARCH,
        TIME, DATE, STOP, NEW_CHAT, HUB
    }

    public static final class Intent {
        public final Type type;
        public long seconds = -1;   // relative time
        public int hour = -1;       // absolute time
        public int minute = -1;
        public String text = "";

        Intent(Type type) {
            this.type = type;
        }

        @Override
        public String toString() {
            return type + "{sec=" + seconds + ", " + hour + ":" + minute + ", text='" + text + "'}";
        }
    }

    private static final Map<String, Integer> WORDS = new HashMap<String, Integer>();

    static {
        String[] w = {"zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten",
                "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen",
                "nineteen", "twenty"};
        for (int i = 0; i < w.length; i++) WORDS.put(w[i], i);
        WORDS.put("thirty", 30);
        WORDS.put("forty", 40);
        WORDS.put("fifty", 50);
        WORDS.put("sixty", 60);
        WORDS.put("ninety", 90);
        WORDS.put("a", 1);
        WORDS.put("an", 1);
    }

    private static final String NUM = "(\\d+(?:\\.\\d+)?|a|an|one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|"
            + "fifteen|twenty|thirty|forty|fifty|sixty|ninety|forty five|twenty five|forty-five|twenty-five|half an?)";
    private static final String UNIT = "(seconds?|secs?|minutes?|mins?|hours?|hrs?)";
    private static final Pattern DURATION = Pattern.compile("\\b" + NUM + "(?:\\s+and\\s+a\\s+half)?\\s*" + UNIT + "\\b");
    private static final Pattern HALF_HOUR = Pattern.compile("\\b(?:half an hour|half hour)\\b");
    private static final Pattern CLOCK = Pattern.compile(
            "\\b(\\d{1,2})(?::(\\d{2}))?\\s*(a\\.?m\\.?|p\\.?m\\.?)?(?![\\d:])");
    private static final Pattern CLOCK_STRICT = Pattern.compile(
            "\\b(\\d{1,2})(?::(\\d{2})\\s*(a\\.?m\\.?|p\\.?m\\.?)?|\\s*(a\\.?m\\.?|p\\.?m\\.?))(?![\\d:])");

    public static Intent parse(String raw) {
        if (raw == null) return null;
        String s = normalize(raw);
        if (s.length() == 0) return null;

        if (s.matches("(stop|stop talking|be quiet|quiet|shut up|enough|that's enough|thats enough|silence|cancel that)")) {
            return new Intent(Type.STOP);
        }
        if (s.matches("(new chat|new conversation|start over|clear (the )?(chat|conversation|history)|forget (this|our) conversation|reset)")) {
            return new Intent(Type.NEW_CHAT);
        }
        if (s.matches("(what('s| is) the time|what time is it|what time is it now|tell me the time|current time|time please|what's the time now)")) {
            return new Intent(Type.TIME);
        }
        if (s.matches("(what('s| is) (the )?(date|day)( today)?|what day is it( today)?|what is today|today's date|tell me the date)")) {
            return new Intent(Type.DATE);
        }

        Matcher m;

        // ---- hub shortcuts
        m = Pattern.compile("^(?:show|open|go to|see|view)(?: me)?(?: my| the)? (tasks|task list|to-?do list|to-?dos|notes|flash ?cards|cards|focus timer|study hub|hub)$").matcher(s);
        if (m.find()) {
            Intent i = new Intent(Type.HUB);
            String t = m.group(1);
            i.text = t.contains("task") || t.contains("do") ? "tasks"
                    : t.contains("note") ? "notes"
                    : t.contains("card") ? "cards"
                    : t.contains("focus") ? "focus" : "tasks";
            return i;
        }
        if (s.matches("(review|study|practice|revise)(?: my)?( the)? (flash ?cards|cards)")) {
            Intent i = new Intent(Type.HUB);
            i.text = "cards";
            return i;
        }

        // ---- pomodoro / focus
        if (s.contains("pomodoro") || s.matches("(start|begin|run|set)( a| an| the)? (focus|study)( session| mode| timer| time)?( for .*)?")) {
            Intent i = new Intent(Type.POMODORO);
            Matcher d = DURATION.matcher(s);
            if (d.find()) i.seconds = durationSeconds(d.group(0));
            return i;
        }

        // ---- reminders
        m = Pattern.compile("^remind me (.+)$").matcher(s);
        if (m.find()) {
            Intent i = parseReminder(m.group(1));
            if (i != null) return i;
        }

        // ---- timer
        if (s.contains("timer") || s.matches("(count ?down|countdown).*")) {
            Matcher d = DURATION.matcher(s);
            if (d.find()) {
                Intent i = new Intent(Type.TIMER);
                i.seconds = durationSeconds(d.group(0));
                return i;
            }
            if (HALF_HOUR.matcher(s).find()) {
                Intent i = new Intent(Type.TIMER);
                i.seconds = 1800;
                return i;
            }
        }

        // ---- alarm
        if (s.contains("alarm") || s.startsWith("wake me")) {
            Matcher d = DURATION.matcher(s);
            if (d.find() && (s.contains(" in ") || s.contains(" after "))) {
                Intent i = new Intent(Type.TIMER);
                i.seconds = durationSeconds(d.group(0));
                return i;
            }
            int[] hm = parseClock(s, false);
            if (hm != null) {
                Intent i = new Intent(Type.ALARM);
                i.hour = hm[0];
                i.minute = hm[1];
                return i;
            }
        }

        // ---- tasks
        m = Pattern.compile("^(?:add|create|make|put|new)(?: a| an| new)? (?:task|todo|to-do|to do)(?: item)?(?: called| named| to| for| that says)?[:,]? (.+)$").matcher(s);
        if (m.find()) return text(Type.TASK, m.group(1), raw);
        m = Pattern.compile("^(?:add|put) (.+?) (?:to|on|onto) (?:my |the )?(?:to-?do|task|tasks|todo)(?: list)?$").matcher(s);
        if (m.find()) return text(Type.TASK, m.group(1), raw);

        // ---- notes
        m = Pattern.compile("^(?:take|make|write|save|add|create)(?: me)?(?: a| an)? note(?: that| saying| down| to self)?[:,]? (.+)$").matcher(s);
        if (m.find()) return text(Type.NOTE, m.group(1), raw);
        m = Pattern.compile("^note (?:that |down )?(.+)$").matcher(s);
        if (m.find()) return text(Type.NOTE, m.group(1), raw);

        // ---- long-term memory
        m = Pattern.compile("^remember (?:that )?(.{4,})$").matcher(s);
        if (m.find()) return text(Type.MEMORY, m.group(1), raw);

        // ---- apps / web
        m = Pattern.compile("^(?:open|launch)(?: the)? (.+?)(?: app| application)?$").matcher(s);
        if (m.find() && m.group(1).split(" ").length <= 4) {
            Intent i = new Intent(Type.OPEN_APP);
            i.text = m.group(1);
            return i;
        }
        m = Pattern.compile("^(?:search(?: the web| online| the internet| google)?(?: for)?|google|web search(?: for)?) (.+)$").matcher(s);
        if (m.find()) {
            Intent i = new Intent(Type.SEARCH);
            i.text = m.group(1);
            return i;
        }
        return null;
    }

    private static Intent text(Type t, String lowered, String raw) {
        Intent i = new Intent(t);
        i.text = recoverCase(lowered, raw);
        return i;
    }

    /** The normalised text is lower-cased; try to hand back the original casing for the tail. */
    private static String recoverCase(String lowered, String raw) {
        String r = raw.trim();
        int idx = r.toLowerCase(Locale.ROOT).lastIndexOf(lowered);
        String out = idx >= 0 ? r.substring(idx, idx + lowered.length()) : lowered;
        out = out.trim();
        if (out.length() > 0) out = Character.toUpperCase(out.charAt(0)) + out.substring(1);
        return out;
    }

    private static Intent parseReminder(String rest) {
        // forms: "in 10 minutes to X", "to X in 10 minutes", "at 5 pm to X", "to X at 5 pm"
        String what = null;
        Intent i = null;
        Matcher m = Pattern.compile("^(?:in|after) (.+?) (?:to|that|about) (.+)$").matcher(rest);
        if (m.find()) {
            Matcher d = DURATION.matcher(m.group(1));
            if (d.find()) {
                i = new Intent(Type.REMINDER);
                i.seconds = durationSeconds(d.group(0));
                what = m.group(2);
            }
        }
        if (i == null) {
            m = Pattern.compile("^(?:to|that|about) (.+?) (?:in|after) (.+)$").matcher(rest);
            if (m.find()) {
                Matcher d = DURATION.matcher(m.group(2));
                if (d.find()) {
                    i = new Intent(Type.REMINDER);
                    i.seconds = durationSeconds(d.group(0));
                    what = m.group(1);
                }
            }
        }
        if (i == null) {
            m = Pattern.compile("^at (.+?) (?:to|that|about) (.+)$").matcher(rest);
            if (m.find()) {
                int[] hm = parseClock(m.group(1), true);
                if (hm != null) {
                    i = new Intent(Type.REMINDER);
                    i.hour = hm[0];
                    i.minute = hm[1];
                    what = m.group(2);
                }
            }
        }
        if (i == null) {
            m = Pattern.compile("^(?:to|that|about) (.+?) at (.+)$").matcher(rest);
            if (m.find()) {
                int[] hm = parseClock(m.group(2), true);
                if (hm != null) {
                    i = new Intent(Type.REMINDER);
                    i.hour = hm[0];
                    i.minute = hm[1];
                    what = m.group(1);
                }
            }
        }
        if (i == null || what == null) return null;
        what = what.trim();
        if (what.length() == 0) return null;
        i.text = Character.toUpperCase(what.charAt(0)) + what.substring(1);
        return i;
    }

    /** Parses "6:30 pm", "6 pm", "18:30" or (lenient) a bare "7". */
    static int[] parseClock(String s, boolean lenient) {
        Matcher m = CLOCK_STRICT.matcher(s);
        boolean found = m.find();
        String ampm = null;
        int h, min;
        if (found) {
            h = Integer.parseInt(m.group(1));
            min = m.group(2) == null ? 0 : Integer.parseInt(m.group(2));
            ampm = m.group(3) != null ? m.group(3) : m.group(4);
        } else {
            // "at 7" / "for 7"
            Matcher b = Pattern.compile("\\b(?:at|for|to) (\\d{1,2})(?![\\d:])").matcher(s);
            if (!b.find()) {
                if (!lenient) return null;
                Matcher c = CLOCK.matcher(s);
                if (!c.find()) return null;
                b = c;
            }
            h = Integer.parseInt(b.group(1));
            min = 0;
        }
        if (min > 59) return null;
        if (ampm != null) {
            boolean pm = ampm.toLowerCase(Locale.ROOT).startsWith("p");
            if (h < 1 || h > 12) return null;
            if (pm && h < 12) h += 12;
            if (!pm && h == 12) h = 0;
        } else if (h > 23) {
            return null;
        }
        if (s.contains("noon")) h = 12;
        return new int[]{h, min};
    }

    static long durationSeconds(String d) {
        Matcher m = DURATION.matcher(d);
        if (!m.find()) return -1;
        String n = m.group(1).replace('-', ' ');
        double v;
        if (n.startsWith("half")) v = 0.5;
        else if (n.matches("\\d+(\\.\\d+)?")) v = Double.parseDouble(n);
        else if (n.indexOf(' ') > 0) {
            String[] p = n.split(" ");
            Integer a = WORDS.get(p[0]);
            Integer b = WORDS.get(p[1]);
            v = (a == null ? 0 : a) + (b == null ? 0 : b);
        } else {
            Integer a = WORDS.get(n);
            v = a == null ? 1 : a;
        }
        if (d.contains("and a half")) v += 0.5;
        String u = m.group(2);
        double mult = u.startsWith("h") ? 3600 : u.startsWith("m") ? 60 : 1;
        return Math.round(v * mult);
    }

    static String normalize(String raw) {
        String s = raw.toLowerCase(Locale.ROOT).trim();
        s = s.replaceAll("[\\u2018\\u2019]", "'");
        s = s.replaceAll("[?!.,;]+$", "").trim();
        String prev;
        do {
            prev = s;
            s = s.replaceFirst("^(hey |hi |ok |okay |so |um |uh )", "");
            s = s.replaceFirst("^(kurisu|makise|assistant|amadeus)[, ]+", "");
            s = s.replaceFirst("^(please |can you |could you |would you |will you |i want you to |i'd like you to |i would like you to |i want to |i'd like to )", "");
            s = s.replaceFirst(" (please|for me)$", "");
            s = s.trim();
        } while (!s.equals(prev));
        return s.replaceAll("\\s+", " ");
    }
}
