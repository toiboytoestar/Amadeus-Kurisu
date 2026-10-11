package com.toiboytoestar.kurisu.core;

/** Builds the prompt Kurisu receives for the evening check-in (with a weekly summary on Sundays). */
public final class CheckIn {
    private CheckIn() {}

    public static final class Stats {
        public int streak;
        public int tasksDoneWeek;
        public int notesWeek;
        public int cardsWeek;
        public int openTasks;
        public int dueCards;
        public int focusMinutesToday;
        public String nearestExam = "";
    }

    public static String prompt(Stats s, boolean weekly) {
        StringBuilder b = new StringBuilder();
        b.append("(It is the user's evening check-in. Do it in character, warm but brief, spoken style: ")
                .append("ask how their day went and what they got done, and ask ONE question at a time. ");
        if (s.streak >= 2) b.append("Mention their ").append(s.streak).append("-day streak of talking to you. ");
        if (s.nearestExam.length() > 0) b.append("Remind them gently about: ").append(s.nearestExam).append(". ");
        if (s.openTasks > 0) b.append("They still have ").append(s.openTasks).append(" open tasks; offer to pick what matters tomorrow. ");
        if (s.dueCards > 0) b.append(s.dueCards).append(" flashcards are due for review. ");
        if (s.focusMinutesToday > 0) b.append("They focused for ").append(s.focusMinutesToday).append(" minutes today. ");
        if (weekly) {
            b.append("It is also Sunday, so give a short WEEKLY SUMMARY from these real numbers (do not invent others): ")
                    .append(s.tasksDoneWeek).append(" tasks completed, ").append(s.notesWeek).append(" notes written, ")
                    .append(s.cardsWeek).append(" flashcards created. Praise what is true, name one thing to improve, and suggest one goal for next week. ");
        }
        b.append("If they say they are tired or stressed, be kind and suggest rest.)");
        return b.toString();
    }
}
