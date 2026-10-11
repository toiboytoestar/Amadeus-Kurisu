package com.toiboytoestar.kurisu;

import android.animation.ValueAnimator;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.ImageView;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Kurisu's face: picks the sprite for a mood, flaps the mouth while she talks, blinks while idle
 * and breathes very slightly. Sprites are decoded off the UI thread and cached.
 */
final class Avatar {
    private final ImageView view;
    private final Resources res;
    private final String pkg;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final ExecutorService loader = Executors.newSingleThreadExecutor();
    private final LruCache<String, Bitmap[]> cache = new LruCache<String, Bitmap[]>(2);
    private final Map<String, Bitmap> blinkCache = new HashMap<String, Bitmap>();
    private final Random rnd = new Random();
    private final int sample;

    private Bitmap[] frames;
    private String sprite = "";
    private String wanted = "";
    private boolean talking;
    private boolean mouthOpen;
    private boolean running;
    private boolean blinking;
    private ValueAnimator breath;

    Avatar(ImageView view) {
        this.view = view;
        this.res = view.getResources();
        this.pkg = R.class.getPackage().getName(); // resource package != applicationId (renamed at build)
        this.sample = res.getDisplayMetrics().widthPixels < 720 ? 2 : 1;
    }

    /** Maps a mood word (from the model or a voice line) to a sprite set name. */
    static String spriteFor(String mood) {
        if (mood == null) return "kurisu_normal";
        switch (mood.trim().toLowerCase()) {
            case "happy": return "kurisu_happy";
            case "normal": case "neutral": return "kurisu_normal";
            case "annoyed": return "kurisu_annoyed";
            case "angry": return "kurisu_angry";
            case "pissed": return "kurisu_pissed";
            case "blush": case "embarrassed": return "kurisu_blush";
            case "sad": return "kurisu_sad";
            case "disappointed": return "kurisu_disappointed";
            case "indifferent": return "kurisu_indifferent";
            case "winking": case "wink": return "kurisu_winking";
            case "side": return "kurisu_side";
            case "sleepy": return "kurisu_eyes_closed";
            case "pleasant": return "kurisu_sided_pleasant";
            case "worried": return "kurisu_sided_worried";
            case "thinking": return "kurisu_sided_thinking";
            case "surprised": return "kurisu_sided_surprised";
            case "sided_angry": return "kurisu_sided_angry";
            case "sided_blush": return "kurisu_sided_blush";
            case "sided_sleepy": return "kurisu_sided_eyes_closed";
            default: return "kurisu_normal";
        }
    }

    static boolean isValidMood(String mood) {
        if (mood == null) return false;
        String m = mood.trim().toLowerCase();
        return !m.equals("") && (!spriteFor(m).equals("kurisu_normal") || m.equals("normal") || m.equals("neutral"));
    }

    private static boolean canBlink(String sprite) {
        return !(sprite.equals("kurisu_happy") || sprite.equals("kurisu_eyes_closed") || sprite.equals("kurisu_winking")
                || sprite.equals("kurisu_sided_eyes_closed"));
    }

    private Bitmap decode(String name) {
        int id = res.getIdentifier(name, "drawable", pkg);
        if (id == 0) return null;
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inSampleSize = sample;
        try {
            return BitmapFactory.decodeResource(res, id, o);
        } catch (OutOfMemoryError e) {
            cache.evictAll();
            o.inSampleSize = sample * 2;
            try {
                return BitmapFactory.decodeResource(res, id, o);
            } catch (OutOfMemoryError e2) {
                return null;
            }
        }
    }

    private String userMood = "normal";

    /** Sets the expression. Idle glances (see blink) are temporary and never replace this. */
    void setMood(String mood) {
        h.removeCallbacks(endQuirk);
        userMood = mood == null ? "normal" : mood;
        load(userMood);
    }

    private final Runnable endQuirk = new Runnable() {
        @Override
        public void run() {
            load(userMood);
        }
    };

    private static final String[] QUIRKS = {"side", "pleasant", "indifferent", "sided_sleepy"};

    private void load(String mood) {
        final String base = spriteFor(mood);
        wanted = base;
        if (base.equals(sprite) && frames != null) return;
        Bitmap[] cached = cache.get(base);
        if (cached != null) {
            apply(base, cached);
            return;
        }
        loader.execute(new Runnable() {
            @Override
            public void run() {
                final Bitmap[] f = new Bitmap[3];
                for (int i = 0; i < 3; i++) f[i] = decode(base + (i + 1));
                if (f[0] == null) return;
                for (int i = 1; i < 3; i++) if (f[i] == null) f[i] = f[0];
                h.post(new Runnable() {
                    @Override
                    public void run() {
                        cache.put(base, f);
                        if (base.equals(wanted)) apply(base, f);
                    }
                });
            }
        });
    }

    private void apply(String base, Bitmap[] f) {
        frames = f;
        sprite = base;
        show(talking && mouthOpen ? f[1] : f[0]);
    }

    private void show(Bitmap b) {
        if (b != null) view.setImageBitmap(b);
    }

    void setTalking(boolean t) {
        if (talking == t) return;
        talking = t;
        h.removeCallbacks(flap);
        mouthOpen = false;
        if (frames != null) show(frames[0]);
        if (t) h.post(flap);
    }

    private final Runnable flap = new Runnable() {
        @Override
        public void run() {
            if (!talking) return;
            if (frames != null) {
                mouthOpen = !mouthOpen || rnd.nextInt(5) == 0;
                show(mouthOpen ? frames[1 + rnd.nextInt(2)] : frames[0]);
            }
            h.postDelayed(this, 80 + rnd.nextInt(110));
        }
    };

    private final Runnable blink = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            // while idle on the neutral face, sometimes glance away for a moment
            if (!talking && userMood.equals("normal") && sprite.equals("kurisu_normal") && rnd.nextInt(6) == 0) {
                load(QUIRKS[rnd.nextInt(QUIRKS.length - 1)]);
                h.postDelayed(endQuirk, 1800);
            }
            if (!talking && frames != null && canBlink(sprite) && !blinking) {
                final String name = sprite.startsWith("kurisu_sided") ? "kurisu_sided_eyes_closed1" : "kurisu_eyes_closed1";
                Bitmap b = blinkCache.get(name);
                if (b == null) {
                    b = decode(name);
                    if (b != null) blinkCache.put(name, b);
                }
                if (b != null) {
                    blinking = true;
                    show(b);
                    h.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            blinking = false;
                            if (frames != null) show(talking && mouthOpen ? frames[1] : frames[0]);
                        }
                    }, 120);
                }
            }
            h.postDelayed(this, 2200 + rnd.nextInt(3800));
        }
    };

    /** Start idle animations (call from onResume). */
    void start() {
        if (running) return;
        running = true;
        h.postDelayed(blink, 2000);
        view.post(new Runnable() {
            @Override
            public void run() {
                view.setPivotX(view.getWidth() / 2f);
                view.setPivotY(view.getHeight());
            }
        });
        breath = ValueAnimator.ofFloat(0f, 1f);
        breath.setDuration(3400);
        breath.setRepeatCount(ValueAnimator.INFINITE);
        breath.setRepeatMode(ValueAnimator.REVERSE);
        breath.setInterpolator(new AccelerateDecelerateInterpolator());
        breath.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator a) {
                float v = (Float) a.getAnimatedValue();
                float s = 1f + 0.012f * v;
                view.setScaleX(s);
                view.setScaleY(s);
            }
        });
        breath.start();
    }

    /** Stop idle animations (call from onPause). */
    void stop() {
        running = false;
        h.removeCallbacks(blink);
        h.removeCallbacks(flap);
        h.removeCallbacks(endQuirk);
        if (breath != null) {
            breath.cancel();
            breath = null;
        }
        view.setScaleX(1f);
        view.setScaleY(1f);
    }

    void release() {
        stop();
        loader.shutdownNow();
        cache.evictAll();
        blinkCache.clear();
    }
}
