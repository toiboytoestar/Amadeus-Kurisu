package com.toiboytoestar.kurisu.core;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Detects "Hey Kurisu" in recognized speech (recognizers often mishear the name). */
public final class WakeWord {
    private WakeWord() {}

    private static final Pattern WAKE = Pattern.compile(
            "(?i)\\b(?:(?:hey|hi|hello|ok|okay|yo|oi|hey there)[ ,]+)?"
                    + "(?:kurisu(?:[- ]?san)?|kuris+|kourisu|kurisu|curi?s+u|cur[ie]e? ?sue|kuri ?su|kree ?sue|makise)\\b[ ,.!?:;-]*");

    /**
     * @return null if the wake word was not said; otherwise the command that followed it
     * (empty string when she was only called by name). Only a wake word near the start counts, so
     * a sentence that merely mentions Kurisu in passing does not trigger her.
     */
    public static String extract(String heard) {
        if (heard == null) return null;
        String t = heard.trim();
        Matcher m = WAKE.matcher(t);
        if (!m.find()) return null;
        if (m.start() > 12) return null; // not at the start of the utterance
        String rest = t.substring(m.end()).trim();
        return rest;
    }
}
