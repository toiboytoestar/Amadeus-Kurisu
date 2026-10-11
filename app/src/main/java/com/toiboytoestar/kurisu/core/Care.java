package com.toiboytoestar.kurisu.core;

import java.util.Locale;

/** Decides when Kurisu should respond with extra care (distress, stress, exhaustion). */
public final class Care {
    private Care() {}

    private static boolean has(String l, String... w) {
        for (String s : w) if (l.contains(s)) return true;
        return false;
    }

    public static boolean isCrisis(String text) {
        if (text == null) return false;
        String l = text.toLowerCase(Locale.ROOT);
        return has(l, "kill myself", "want to die", "end my life", "end it all", "suicid", "hurt myself", "self harm", "self-harm",
                "don't want to live", "do not want to live", "better off without me");
    }

    /** Extra instruction for this reply, or "" when no special care is needed. hour is the local hour 0-23. */
    public static String hint(String userText, int hour) {
        if (userText == null) return "";
        String l = userText.toLowerCase(Locale.ROOT);
        if (isCrisis(l)) {
            return "The user may be in serious distress or thinking about harming themselves. Drop the teasing and jokes completely. "
                    + "Respond with real warmth, take them seriously, ask gently how they are, and encourage them to reach out right now to someone they trust "
                    + "or to local emergency services or a crisis line in their country. Stay with them; keep it short and kind. Use [[mood:worried]].";
        }
        boolean stressed = has(l, "stressed", "overwhelmed", "anxious", "panic", "burnt out", "burned out", "burnout", "can't cope",
                "i give up", "give up", "too much", "exhausted", "so tired", "can't sleep", "cant sleep", "all-nighter", "all nighter", "crying", "depressed");
        boolean late = hour >= 23 || hour < 5;
        if (stressed && late) {
            return "The user sounds stressed or worn out and it is very late. Comfort them first in one or two short sentences, "
                    + "then gently suggest sleep; offer to move their tasks to tomorrow and to set a morning reminder. Do not pile on advice or new work. Use [[mood:worried]].";
        }
        if (stressed) {
            return "The user sounds stressed or overwhelmed. Acknowledge the feeling briefly, then offer ONE small step: "
                    + "a 5-minute break or breathing pause, or splitting the problem into the smallest next action. Offer a short timer. Use [[mood:worried]].";
        }
        if (late && has(l, "study", "revise", "homework", "assignment", "exam", "project", "work on")) {
            return "It is very late at night and the user wants to work. Help, but gently point out that sleep helps memory and "
                    + "offer a quick plan so they can stop at a sensible time.";
        }
        return "";
    }
}
