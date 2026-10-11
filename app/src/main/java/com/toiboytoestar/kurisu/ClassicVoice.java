package com.toiboytoestar.kurisu;

import android.app.Activity;
import android.content.Context;
import android.media.MediaPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.regex.Pattern;

/**
 * The original app's behaviour: pre-recorded Steins;Gate voice lines triggered by keywords.
 * Kept for easter eggs ("Christina", "nullpo", "@channel"...), for greetings, short acknowledgements
 * and as the offline fallback when no AI back-end is configured.
 */
final class ClassicVoice {

    interface Events {
        void onClassicStart(String mood, String subtitle);

        void onClassicDone();
    }

    private static final class Group {
        final int[] keywords;
        final VoiceLine[] lines;
        final boolean egg;

        Group(boolean egg, int[] keywords, VoiceLine... lines) {
            this.egg = egg;
            this.keywords = keywords;
            this.lines = lines;
        }
    }

    private final Activity act;
    private final Events events;
    private final VoiceLine[] v = VoiceLine.Line.getLines();
    private final List<Group> groups = new ArrayList<Group>();
    private final Random rnd = new Random();
    private MediaPlayer mp;
    private boolean playing;
    private int shamanGirls = -1;

    ClassicVoice(Activity act, Events events) {
        this.act = act;
        this.events = events;
        groups.add(new Group(true, new int[]{R.string.christina},
                v[VoiceLine.Line.CHRISTINA], v[VoiceLine.Line.WHY_CHRISTINA], v[VoiceLine.Line.SHOULD_CHRISTINA], v[VoiceLine.Line.NO_TINA]));
        groups.add(new Group(true, new int[]{R.string.the_zombie, R.string.the_zombie2, R.string.celeb17},
                v[VoiceLine.Line.DONT_CALL_ME_LIKE_THAT]));
        groups.add(new Group(true, new int[]{R.string.atchannel, R.string.kurigohan, R.string.kamehameha},
                v[VoiceLine.Line.SENPAI_DONT_TELL], v[VoiceLine.Line.STILL_NOT_HAPPY]));
        groups.add(new Group(true, new int[]{R.string.salieri, R.string.maho, R.string.hiyajo},
                v[VoiceLine.Line.SENPAI_QUESTION], v[VoiceLine.Line.SENPAI_WHAT_WE_TALKING],
                v[VoiceLine.Line.SENPAI_QUESTIONMARK], v[VoiceLine.Line.SENPAI_WHO_IS_THIS]));
        groups.add(new Group(true, new int[]{R.string.nice_body, R.string.hot, R.string.sexy, R.string.boobies, R.string.oppai},
                v[VoiceLine.Line.DEVILISH_PERVERT], v[VoiceLine.Line.PERVERT_CONFIRMED], v[VoiceLine.Line.PERVERT_IDIOT]));
        groups.add(new Group(true, new int[]{R.string.robotics_notes, R.string.antimatter},
                v[VoiceLine.Line.HEHEHE]));
        // topic groups: only used when no AI is configured
        groups.add(new Group(false, new int[]{R.string.time_machine, R.string.time_travel2, R.string.cern, R.string.time_travel},
                v[VoiceLine.Line.TM_NONCENCE], v[VoiceLine.Line.TM_YOU_SAID], v[VoiceLine.Line.TM_NO_EVIDENCE],
                v[VoiceLine.Line.TM_DONT_KNOW], v[VoiceLine.Line.TM_NOT_POSSIBLE]));
        groups.add(new Group(false, new int[]{R.string.memory, R.string.amadeus, R.string.science},
                v[VoiceLine.Line.HUMANS_SOFTWARE], v[VoiceLine.Line.MEMORY_COMPLEXITY], v[VoiceLine.Line.SECRET_DIARY],
                v[VoiceLine.Line.MODIFIYING_MEMORIES], v[VoiceLine.Line.MEMORIES_CHRISTINA]));
        groups.add(new Group(false, new int[]{R.string.hello, R.string.good_morning, R.string.konnichiwa, R.string.good_evening},
                v[VoiceLine.Line.HELLO], v[VoiceLine.Line.NICE_TO_MEET_OKABE], v[VoiceLine.Line.PLEASED_TO_MEET],
                v[VoiceLine.Line.LOOKING_FORWARD_TO_WORKING]));
    }

    VoiceLine line(int index) {
        return v[index];
    }

    boolean isPlaying() {
        return playing;
    }

    /** Matches input against keyword groups. eggsOnly limits to the gag lines. Returns null if nothing matches. */
    VoiceLine[] match(String input, boolean eggsOnly) {
        Context lc = LangContext.load(act.getApplicationContext(), new Prefs(act).langCode());
        String s = input.toLowerCase(Locale.ROOT);
        if (containsKeyword(s, lc.getString(R.string.nullpo))) {
            shamanGirls++;
            if (shamanGirls < 5) return new VoiceLine[]{v[VoiceLine.Line.GAH], v[VoiceLine.Line.GAH_EXTENDED]};
            VoiceLine single;
            switch (shamanGirls) {
                case 5:
                    single = new VoiceLine(R.raw.leskinen_awesome, VoiceLine.Mood.WINKING, R.string.line_Leskinen_awesome);
                    break;
                case 6:
                    single = new VoiceLine(R.raw.leskinen_nice, VoiceLine.Mood.WINKING, R.string.line_Leskinen_nice);
                    break;
                case 7:
                    single = new VoiceLine(R.raw.leskinen_oh_no, VoiceLine.Mood.WINKING, R.string.line_Leskinen_oh_no);
                    break;
                case 8:
                    single = new VoiceLine(R.raw.leskinen_shaman, VoiceLine.Mood.WINKING, R.string.line_Leskinen_shaman);
                    break;
                default:
                    single = new VoiceLine(R.raw.leskinen_holy_cow, VoiceLine.Mood.WINKING, R.string.line_Leskinen_holy_cow);
                    shamanGirls = 0;
                    break;
            }
            return new VoiceLine[]{single};
        }
        for (Group g : groups) {
            if (eggsOnly && !g.egg) continue;
            for (int res : g.keywords) {
                if (containsKeyword(s, lc.getString(res))) return g.lines;
            }
        }
        return null;
    }

    VoiceLine[] genericLines() {
        return new VoiceLine[]{v[VoiceLine.Line.ASK_ME], v[VoiceLine.Line.WHAT_DO_YOU_WANT], v[VoiceLine.Line.WHAT_IS_IT],
                v[VoiceLine.Line.HEHEHE], v[VoiceLine.Line.WHY_SAY_THAT], v[VoiceLine.Line.YOU_SURE]};
    }

    /** Keyword match: whole-word for alphabetic scripts, substring for scripts written without spaces. */
    static boolean containsKeyword(String input, String keyword) {
        String k = keyword.toLowerCase(Locale.ROOT).trim();
        if (k.length() == 0) return false;
        boolean unspaced = false;
        for (int i = 0; i < k.length(); i++) {
            if (k.charAt(i) >= 0x2E80) {
                unspaced = true;
                break;
            }
        }
        if (unspaced) return input.contains(k);
        return Pattern.compile("(?<![\\p{L}\\p{N}@])" + Pattern.quote(k) + "(?![\\p{L}\\p{N}])").matcher(input).find();
    }

    VoiceLine pick(VoiceLine[] lines) {
        return lines[lines.length > 1 ? rnd.nextInt(lines.length) : 0];
    }

    void play(VoiceLine line) {
        stop();
        final String subtitle = LangContext.load(act.getApplicationContext(), new Prefs(act).langCode()).getString(line.getSubtitle());
        final MediaPlayer m = MediaPlayer.create(act, line.getId());
        if (m == null) {
            events.onClassicStart(line.getMood(), subtitle);
            events.onClassicDone();
            return;
        }
        mp = m;
        playing = true;
        m.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer p) {
                if (mp != p) return;
                finish();
            }
        });
        m.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer p, int what, int extra) {
                if (mp == p) finish();
                return true;
            }
        });
        events.onClassicStart(line.getMood(), subtitle);
        m.start();
    }

    private void finish() {
        release();
        events.onClassicDone();
    }

    private void release() {
        playing = false;
        MediaPlayer m = mp;
        mp = null;
        if (m != null) {
            try {
                m.release();
            } catch (Exception ignored) { }
        }
    }

    /** Stops playback without notifying. */
    void stop() {
        release();
    }
}
