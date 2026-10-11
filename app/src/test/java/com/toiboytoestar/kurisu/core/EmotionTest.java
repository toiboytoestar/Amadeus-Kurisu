package com.toiboytoestar.kurisu.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class EmotionTest {
    @Test public void userReactions() {
        assertEquals("pissed", Emotion.reactToUser("hey Christina"));
        assertEquals("blush", Emotion.reactToUser("Thank you so much"));
        assertEquals("annoyed", Emotion.reactToUser("you're useless"));
        assertEquals("worried", Emotion.reactToUser("I failed my exam"));
        assertEquals("surprised", Emotion.reactToUser("no way"));
        assertEquals("happy", Emotion.reactToUser("hello there"));
        assertNull(Emotion.reactToUser("set a timer for ten minutes"));
        assertNull(Emotion.reactToUser(null));
    }

    @Test public void replyGuesses() {
        assertEquals("sad", Emotion.guessFromReply("Sorry, I couldn't find that."));
        assertEquals("thinking", Emotion.guessFromReply("Hmm, let me check."));
        assertEquals("happy", Emotion.guessFromReply("Great, done!"));
        assertEquals("pleasant", Emotion.guessFromReply("Which subject first?"));
        assertNull(Emotion.guessFromReply("The mitochondria produce energy."));
    }

    @Test public void idle() {
        assertEquals("sleepy", Emotion.idleForHour(2));
        assertEquals("normal", Emotion.idleForHour(14));
    }

    @Test public void daysUntilCountsCalendarDays() {
        java.util.TimeZone tz = java.util.TimeZone.getTimeZone("Asia/Kolkata");
        long now = TimeParse.parse("2026-10-11 23:30", 0, tz);
        assertEquals(0, TimeParse.daysUntil(now, TimeParse.parse("2026-10-11", 0, tz), tz));
        assertEquals(1, TimeParse.daysUntil(now, TimeParse.parse("2026-10-12 08:00", 0, tz), tz));
        assertEquals(40, TimeParse.daysUntil(now, TimeParse.parse("2026-11-20", 0, tz), tz));
        assertEquals(-2, TimeParse.daysUntil(now, TimeParse.parse("2026-10-09", 0, tz), tz));
    }
}
