package com.toiboytoestar.kurisu.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class StreamProcessorTest {

    static class Rec implements StreamProcessor.Listener {
        final StringBuilder display = new StringBuilder();
        final List<String> sentences = new ArrayList<String>();
        final List<String> tags = new ArrayList<String>();

        public void onDisplay(String t) { display.append(t); }
        public void onSentence(String s) { sentences.add(s); }
        public void onTag(String n, String a) { tags.add(n + "=" + a); }
    }

    private Rec run(String... deltas) {
        Rec r = new Rec();
        StreamProcessor p = new StreamProcessor(r);
        for (String d : deltas) p.feed(d);
        p.finish();
        return r;
    }

    @Test
    public void splitsSentencesAsTheyArrive() {
        Rec r = new Rec();
        StreamProcessor p = new StreamProcessor(r);
        p.feed("Hello there, senpai. ");
        assertEquals(1, r.sentences.size()); // emitted before the stream ended
        p.feed("That is a rather interesting question! Let me think");
        assertEquals(2, r.sentences.size());
        p.finish();
        assertEquals(3, r.sentences.size());
        assertEquals("Hello there, senpai.", r.sentences.get(0));
        assertEquals("Let me think", r.sentences.get(2));
    }

    @Test
    public void stripsTagsAcrossDeltaBoundaries() {
        Rec r = run("[[mo", "od:ha", "ppy]]Nice to see you. [", "[task:Buy milk|2026-10-10 18:00]]Done.");
        assertEquals("Nice to see you. Done.", r.display.toString());
        assertEquals(2, r.tags.size());
        assertEquals("mood=happy", r.tags.get(0));
        assertEquals("task=Buy milk|2026-10-10 18:00", r.tags.get(1));
        for (String s : r.sentences) assertTrue(s, !s.contains("[") && !s.contains("]"));
    }

    @Test
    public void singleBracketsSurvive() {
        Rec r = run("Use a[0] and [x] here.");
        assertEquals("Use a[0] and [x] here.", r.display.toString());
        assertEquals(0, r.tags.size());
    }

    @Test
    public void unterminatedTagIsReturnedAsText() {
        Rec r = run("Oops [[mood:hap");
        assertEquals("Oops [[mood:hap", r.display.toString());
        assertEquals(0, r.tags.size());
    }

    @Test
    public void doesNotSplitDecimalsAbbreviationsOrInitials() {
        Rec r = run("The value is 3.14 which is e.g. pi, said Dr. Okabe and J. K. Rowling today. Next one here.");
        assertEquals(2, r.sentences.size());
        assertTrue(r.sentences.get(0).startsWith("The value is 3.14"));
        assertTrue(r.sentences.get(0).endsWith("today."));
    }

    @Test
    public void shortFragmentsAreMergedButFirstOneIsFast() {
        Rec r = run("Hmm. Okay. Right. This is the longer sentence that follows them all.");
        // "Hmm." is shorter than the first minimum so it is merged forward
        assertTrue(r.sentences.size() <= 2);
        StringBuilder all = new StringBuilder();
        for (String s : r.sentences) all.append(s).append(' ');
        assertTrue(all.toString().contains("longer sentence"));
    }

    @Test
    public void cjkPunctuationEndsSentenceImmediately() {
        Rec r = new Rec();
        StreamProcessor p = new StreamProcessor(r);
        p.feed("こんにちは、先輩。");
        assertEquals(1, r.sentences.size());
    }

    @Test
    public void markdownIsCleanedForSpeechButKeptForDisplay() {
        Rec r = run("**Bold** point with a [link](https://example.com/x) and `code`.\n- item one is here\n");
        assertTrue(r.display.toString().contains("**Bold**"));
        String spoken = r.sentences.get(0);
        assertTrue(spoken, !spoken.contains("*") && !spoken.contains("http") && !spoken.contains("`"));
        assertTrue(spoken, spoken.contains("link"));
    }

    @Test
    public void emojiAreNotSpoken() {
        Rec r = run("That was fun 😄 really ❤️ fun, senpai.");
        assertEquals("That was fun really fun, senpai.", r.sentences.get(0));
    }

    @Test
    public void earlyCommaFlushReducesLatencyForLongFirstSentence() {
        Rec r = new Rec();
        StreamProcessor p = new StreamProcessor(r);
        p.feed("Well, since you are asking about the thalamus and its many relay functions in the brain, ");
        assertEquals(1, r.sentences.size());
        p.feed("I should say it is more than a relay.");
        p.finish();
        assertEquals(2, r.sentences.size());
    }

    @Test
    public void chunkRespectsLimit() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 100; i++) sb.append("This is sentence number ").append(i).append(". ");
        List<String> chunks = Speech.chunk(sb.toString(), 500);
        for (String c : chunks) assertTrue(c.length() <= 500);
        assertTrue(chunks.size() > 5);
    }
}
