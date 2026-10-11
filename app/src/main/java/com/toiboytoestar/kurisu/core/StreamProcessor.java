package com.toiboytoestar.kurisu.core;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Turns a stream of raw LLM text deltas into things the UI/voice can consume in real time:
 *
 *  - display text (action tags such as [[mood:happy]] removed),
 *  - speakable sentences (emitted as soon as a sentence boundary is seen, so TTS can start
 *    while the model is still generating),
 *  - action tags ([[name:arg]]).
 *
 * Tags may be split across deltas ("[[mo" + "od:happy]]"); they are buffered until complete.
 * Pure Java (no Android dependencies) so it can be unit tested on the JVM.
 */
public class StreamProcessor {

    public interface Listener {
        /** Text that should be shown on screen (tags removed, markdown untouched). */
        void onDisplay(String text);

        /** A chunk that is ready to be spoken (already cleaned for TTS). */
        void onSentence(String speakable);

        /** An action tag. name is lower-case, arg may be empty. */
        void onTag(String name, String arg);
    }

    private static final int MAX_TAG_LEN = 400;
    private static final int FIRST_MIN = 6;
    private static final int NEXT_MIN = 18;
    private static final int EARLY_COMMA_LEN = 70;

    private static final Set<String> ABBREVIATIONS = new HashSet<String>(Arrays.asList(
            "e.g", "i.e", "vs", "dr", "mr", "mrs", "ms", "prof", "etc", "fig", "eq", "al",
            "no", "st", "jr", "sr", "approx", "cf", "ca", "inc", "ph.d", "u.s", "u.k"));

    private final Listener listener;
    private final StringBuilder tag = new StringBuilder();
    private final StringBuilder sentence = new StringBuilder();
    private boolean inTag = false;
    private boolean pendingBracket = false; // saw a single '[' that might start "[["
    private boolean emittedFirst = false;

    public StreamProcessor(Listener listener) {
        this.listener = listener;
    }

    public void feed(String delta) {
        if (delta == null) return;
        StringBuilder plain = new StringBuilder();
        for (int i = 0; i < delta.length(); i++) {
            char c = delta.charAt(i);
            if (inTag) {
                tag.append(c);
                int n = tag.length();
                if (n >= 2 && tag.charAt(n - 1) == ']' && tag.charAt(n - 2) == ']') {
                    String body = tag.substring(0, n - 2);
                    inTag = false;
                    tag.setLength(0);
                    flushPlain(plain);
                    dispatchTag(body);
                } else if (n > MAX_TAG_LEN) {
                    // not a real tag; give the text back
                    String text = "[[" + tag;
                    inTag = false;
                    tag.setLength(0);
                    plain.append(text);
                }
                continue;
            }
            if (pendingBracket) {
                pendingBracket = false;
                if (c == '[') {
                    inTag = true;
                    tag.setLength(0);
                    continue;
                }
                plain.append('[');
                // fall through to handle c normally
            }
            if (c == '[') {
                pendingBracket = true;
                continue;
            }
            plain.append(c);
        }
        flushPlain(plain);
    }

    /** Call when the stream is finished. */
    public void finish() {
        StringBuilder plain = new StringBuilder();
        if (pendingBracket) {
            plain.append('[');
            pendingBracket = false;
        }
        if (inTag) {
            plain.append("[[").append(tag);
            inTag = false;
            tag.setLength(0);
        }
        flushPlain(plain);
        String rest = sentence.toString();
        sentence.setLength(0);
        emitSpeakable(rest);
    }

    private void dispatchTag(String body) {
        String name = body;
        String arg = "";
        int colon = body.indexOf(':');
        if (colon >= 0) {
            name = body.substring(0, colon);
            arg = body.substring(colon + 1);
        }
        name = name.trim().toLowerCase(java.util.Locale.ROOT);
        arg = arg.trim();
        if (name.length() == 0 || name.length() > 16) return;
        listener.onTag(name, arg);
    }

    private void flushPlain(StringBuilder plain) {
        if (plain.length() == 0) return;
        String text = plain.toString();
        plain.setLength(0);
        listener.onDisplay(text);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            sentence.append(c);
            checkBoundary(c);
        }
    }

    /** Looks at the end of the sentence buffer, deciding whether to emit. */
    private void checkBoundary(char c) {
        int len = sentence.length();
        if (c == '\n') {
            emitIfLongEnough(true);
            return;
        }
        if (c == '。' || c == '！' || c == '？' || c == '।') { // 。！？ and Devanagari danda
            emitIfLongEnough(false);
            return;
        }
        // For latin punctuation we need to see the following whitespace, so decide when we get it.
        if (Character.isWhitespace(c) && len >= 2) {
            char prev = sentence.charAt(len - 2);
            if (prev == '.' || prev == '!' || prev == '?' || prev == '…') {
                if (prev == '.' && endsWithAbbreviation()) return;
                emitIfLongEnough(false);
                return;
            }
            if (!emittedFirst && len >= EARLY_COMMA_LEN && (prev == ',' || prev == ';' || prev == ':')) {
                emitIfLongEnough(false);
            }
        }
    }

    private boolean endsWithAbbreviation() {
        // sentence ends with "<word>. " -> look at <word>
        int end = sentence.length() - 2; // index of '.'
        int start = end;
        while (start > 0) {
            char ch = sentence.charAt(start - 1);
            if (Character.isLetter(ch) || ch == '.') start--;
            else break;
        }
        if (start >= end) return false;
        String word = sentence.substring(start, end).toLowerCase(java.util.Locale.ROOT);
        if (word.length() == 1 && Character.isLetter(word.charAt(0))) return true; // initials "J. K."
        return ABBREVIATIONS.contains(word);
    }

    private void emitIfLongEnough(boolean force) {
        int min = emittedFirst ? NEXT_MIN : FIRST_MIN;
        String s = sentence.toString();
        if (s.trim().length() < min && !force) return;
        if (s.trim().length() == 0) {
            sentence.setLength(0);
            return;
        }
        sentence.setLength(0);
        emitSpeakable(s);
    }

    private void emitSpeakable(String raw) {
        String clean = Speech.clean(raw);
        if (clean.length() == 0) return;
        emittedFirst = true;
        listener.onSentence(clean);
    }
}
