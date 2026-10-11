package com.toiboytoestar.kurisu.core;

import java.util.Locale;

/** Picks Kurisu's facial expression for a situation. Pure Java so it can be unit tested. */
public final class Emotion {
    private Emotion() {}

    private static boolean has(String l, String... words) {
        for (String w : words) if (l.contains(w)) return true;
        return false;
    }

    /** Instant reaction to what the user said (shown while she is "thinking"). Null = no strong reaction. */
    public static String reactToUser(String text) {
        if (text == null) return null;
        String l = " " + text.toLowerCase(Locale.ROOT) + " ";
        if (has(l, "christina", "celeb seven", "zombie", "assistant")) return "pissed";          // classic tsundere trigger
        if (has(l, "love you", "you're cute", "you are cute", "so pretty", "beautiful", "i like you", "marry me")) return "blush";
        if (has(l, "stupid", "idiot", "shut up", "useless", "hate you", "dumb")) return "annoyed";
        if (has(l, "thank", "you're the best", "you are the best", "great job", "well done", "awesome", "good job")) return "blush";
        if (has(l, "good night", "goodnight", "sleepy", "so tired", "going to sleep")) return "sleepy";
        if (has(l, "i failed", "failed my", "i'm sad", "i am sad", "lonely", "stressed", "depressed", "anxious", "i can't do", "give up", "crying"))
            return "worried";
        if (has(l, "no way", "really?", "seriously?", "what?!", "wow", "oh my", "are you kidding", "can't believe")) return "surprised";
        if (has(l, "haha", "lol", "joke", "just kidding", "funny", "jk")) return "winking";
        if (has(l, " hello", " hi ", " hey", "good morning", "good evening", "good afternoon")) return "happy";
        if (has(l, "explain", "why ", "how does", "how do", "what is", "what's the", "research", "paper", "theory")) return "thinking";
        return null;
    }

    /** Guess an expression from her own sentence when the model forgot its [[mood:]] tag. Null = keep current. */
    public static String guessFromReply(String sentence) {
        if (sentence == null) return null;
        String l = sentence.toLowerCase(Locale.ROOT);
        if (has(l, "hmph", "idiot", "stupid", "baka", "seriously?", "how many times")) return "annoyed";
        if (has(l, "not christina", "stop calling")) return "pissed";
        if (has(l, "sorry", "unfortunately", "i couldn't", "i can't", "i cannot", "too bad")) return "sad";
        if (has(l, "careful", "be careful", "are you okay", "worried", "take a break", "get some rest", "don't push")) return "worried";
        if (has(l, "it's not like", "don't get the wrong idea", "t-thanks", "i-it's", "embarrass")) return "blush";
        if (has(l, "whoa", "oh!", "wait, what", "no way", "surprising", "unexpected", "incredible")) return "surprised";
        if (has(l, "just kidding", "heh", "hehe", "nice try", "got you")) return "winking";
        if (has(l, "hmm", "let me think", "let me check", "let me see", "one moment", "good question")) return "thinking";
        if (has(l, "great", "glad", "nice", "welcome", "thank", "congrat", "well done", "good morning", "hello", "let's", "!")) return "happy";
        if (sentence.contains("?")) return "pleasant";
        if (has(l, "good night", "sleep", "rest well", "late")) return "sleepy";
        return null;
    }

    /** Expression to rest on when nothing is happening, by hour of day (0-23). */
    public static String idleForHour(int hour) {
        return (hour >= 0 && hour < 5) ? "sleepy" : "normal";
    }
}
