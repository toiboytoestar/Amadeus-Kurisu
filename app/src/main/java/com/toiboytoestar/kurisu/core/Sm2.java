package com.toiboytoestar.kurisu.core;

/** Small spaced-repetition scheduler (SM-2 flavoured). Grades: 0 again, 1 hard, 2 good, 3 easy. */
public final class Sm2 {
    private Sm2() {}

    public static final long DAY_MS = 24L * 60 * 60 * 1000;
    public static final long AGAIN_MS = 10L * 60 * 1000;

    public static final class State {
        public final double ease;
        public final int intervalDays;
        public final int reps;

        public State(double ease, int intervalDays, int reps) {
            this.ease = ease;
            this.intervalDays = intervalDays;
            this.reps = reps;
        }
    }

    public static State next(double ease, int intervalDays, int reps, int grade) {
        double e = ease <= 0 ? 2.5 : ease;
        switch (grade) {
            case 0:
                return new State(Math.max(1.3, e - 0.2), 0, 0);
            case 1: {
                int i = reps == 0 ? 1 : Math.max(1, (int) Math.round(intervalDays * 1.2));
                return new State(Math.max(1.3, e - 0.15), i, reps + 1);
            }
            case 3: {
                int i = reps == 0 ? 3 : Math.max(intervalDays + 1, (int) Math.round(intervalDays * e * 1.3));
                return new State(e + 0.15, i, reps + 1);
            }
            default: {
                int i = reps == 0 ? 1 : reps == 1 ? 3 : Math.max(intervalDays + 1, (int) Math.round(intervalDays * e));
                return new State(e, i, reps + 1);
            }
        }
    }

    public static long dueAt(long now, State s) {
        return s.intervalDays == 0 ? now + AGAIN_MS : now + s.intervalDays * DAY_MS;
    }
}
