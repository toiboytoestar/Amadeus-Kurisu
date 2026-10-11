package com.toiboytoestar.kurisu;

import android.content.Context;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;

import com.toiboytoestar.kurisu.core.GeminiTts;
import com.toiboytoestar.kurisu.core.Speech;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Text-to-speech that is fed sentence by sentence while the model is still generating,
 * so Kurisu starts talking after the first sentence. All callbacks arrive on the main thread.
 */
final class Speaker {

    interface Events {
        /** An utterance started playing; apply its mood. */
        void onUtteranceStart(String mood);

        /** Everything queued has been spoken (or the engine is unavailable and the text was "read" silently). */
        void onIdle();

        /** A short message for the user (e.g. the natural voice is unavailable). */
        void onNotice(String message);
    }

    private static final class Pending {
        final String text;
        final String mood;

        Pending(String text, String mood) {
            this.text = text;
            this.mood = mood;
        }
    }

    private final Context app;
    private final Prefs prefs;
    private final Events events;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AudioManager audio;
    private final List<Pending> beforeReady = new ArrayList<Pending>();
    private final Map<String, String> moods = new HashMap<String, String>();

    private TextToSpeech tts;
    private boolean ready;
    private boolean failed;
    private boolean languageMissing;
    private int outstanding;
    private volatile int gen;
    private long seq;
    private boolean hasFocus;

    Speaker(Context app, Prefs prefs, Events events) {
        this.app = app.getApplicationContext();
        this.prefs = prefs;
        this.events = events;
        this.audio = (AudioManager) this.app.getSystemService(Context.AUDIO_SERVICE);
    }

    void init() {
        try {
            tts = new TextToSpeech(app, new TextToSpeech.OnInitListener() {
                @Override
                public void onInit(final int status) {
                    main.post(new Runnable() {
                        @Override
                        public void run() {
                            onEngineReady(status);
                        }
                    });
                }
            });
        } catch (Exception e) {
            failed = true;
        }
    }

    private void onEngineReady(int status) {
        if (status != TextToSpeech.SUCCESS || tts == null) {
            failed = true;
            flushAsSilent();
            return;
        }
        ready = true;
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override
            public void onStart(final String id) {
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        if (!current(id)) return;
                        String mood = moods.get(id);
                        events.onUtteranceStart(mood == null ? "normal" : mood);
                    }
                });
            }

            @Override
            public void onDone(final String id) {
                finished(id);
            }

            @Override
            public void onError(final String id) {
                finished(id);
            }

            @Override
            public void onStop(final String id, boolean interrupted) {
                finished(id);
            }
        });
        applySettings();
        List<Pending> q = new ArrayList<Pending>(beforeReady);
        beforeReady.clear();
        for (Pending p : q) speakSystem(p.text, p.mood);
    }

    private boolean current(String id) {
        return id != null && id.startsWith(gen + ":");
    }

    private void finished(final String id) {
        main.post(new Runnable() {
            @Override
            public void run() {
                if (!current(id)) return;
                moods.remove(id);
                if (outstanding > 0) outstanding--;
                if (outstanding == 0) {
                    abandonFocus();
                    events.onIdle();
                }
            }
        });
    }

    /** Re-reads language, voice, rate and pitch from preferences. */
    void applySettings() {
        if (!ready || tts == null) return;
        Locale loc = Locale.forLanguageTag(prefs.speechLang());
        int r = tts.setLanguage(loc);
        languageMissing = r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED;
        if (languageMissing) tts.setLanguage(Locale.getDefault());
        String wanted = prefs.ttsVoice();
        if (wanted.length() > 0 && !languageMissing) {
            try {
                for (Voice v : tts.getVoices()) {
                    if (v.getName().equals(wanted) && v.getLocale().getLanguage().equals(loc.getLanguage())) {
                        tts.setVoice(v);
                        break;
                    }
                }
            } catch (Exception ignored) { }
        }
        tts.setSpeechRate(prefs.ttsRate());
        tts.setPitch(prefs.ttsPitch());
    }

    boolean languageMissing() {
        return languageMissing;
    }

    boolean unavailable() {
        return failed;
    }

    boolean isSpeaking() {
        return outstanding > 0 || (!ready && !failed && !beforeReady.isEmpty());
    }

    void speak(String text, String mood) {
        if (text == null || text.trim().length() == 0) return;
        if (cloudUsable()) {
            for (String chunk : Speech.chunk(text, 700)) enqueueCloud(chunk, mood);
            return;
        }
        speakSystem(text, mood);
    }

    // ------------------------------------------------------------ natural cloud voice

    private static final class CloudItem {
        final int gen;
        final String text;
        final String mood;
        final Future<byte[]> audio;

        CloudItem(int gen, String text, String mood, Future<byte[]> audio) {
            this.gen = gen;
            this.text = text;
            this.mood = mood;
            this.audio = audio;
        }
    }

    private final ExecutorService fetchExec = Executors.newFixedThreadPool(2);
    private final LinkedBlockingQueue<CloudItem> cloudQ = new LinkedBlockingQueue<CloudItem>();
    private final PcmPlayer player = new PcmPlayer();
    private Thread cloudThread;
    private volatile long cloudBlockedUntil;

    private boolean cloudUsable() {
        return prefs.cloudTts() && prefs.cloudKey().length() > 0 && System.currentTimeMillis() >= cloudBlockedUntil;
    }

    private void enqueueCloud(final String chunk, final String mood) {
        if (outstanding == 0) requestFocus();
        outstanding++;
        final String key = prefs.cloudKey();
        final String model = prefs.cloudModel();
        final String voice = prefs.cloudVoice();
        Future<byte[]> f = fetchExec.submit(new Callable<byte[]>() {
            @Override
            public byte[] call() throws Exception {
                return new GeminiTts("", key, model, voice).synthesize(chunk, mood);
            }
        });
        cloudQ.add(new CloudItem(gen, chunk, mood, f));
        ensureCloudThread();
    }

    private synchronized void ensureCloudThread() {
        if (cloudThread != null && cloudThread.isAlive()) return;
        cloudThread = new Thread(new Runnable() {
            @Override
            public void run() {
                cloudLoop();
            }
        }, "kurisu-voice");
        cloudThread.setDaemon(true);
        cloudThread.start();
    }

    /** Called with the lock held by the loop when it wants to stop: only stops if nothing was queued meanwhile. */
    private synchronized boolean retireIfIdle() {
        if (!cloudQ.isEmpty()) return false;
        cloudThread = null;
        return true;
    }

    private void cloudLoop() {
        while (true) {
            final CloudItem it;
            try {
                it = cloudQ.poll(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                synchronized (this) {
                    cloudThread = null;
                }
                return;
            }
            if (it == null) {
                if (retireIfIdle()) return;
                continue;
            }
            try {
                playOne(it);
            } catch (RuntimeException e) {
                // never let an unexpected error kill the process or leave the turn waiting for audio
                final String why = e.getClass().getSimpleName();
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        cloudFailed(it, why);
                    }
                });
            }
        }
    }

    private void playOne(final CloudItem it) {
        if (it.gen != gen) {
            it.audio.cancel(true);
            return;
        }
        byte[] pcm = null;
        String error = null;
        if (System.currentTimeMillis() < cloudBlockedUntil) {
            it.audio.cancel(true);
            error = "";
        } else {
            try {
                pcm = it.audio.get();
            } catch (Exception e) {
                Throwable c = e.getCause() != null ? e.getCause() : e;
                error = c.getMessage() != null ? c.getMessage() : c.getClass().getSimpleName();
            }
        }
        if (it.gen != gen) return;
        if (pcm == null) {
            final String why = error;
            main.post(new Runnable() {
                @Override
                public void run() {
                    cloudFailed(it, why);
                }
            });
            return;
        }
        main.post(new Runnable() {
            @Override
            public void run() {
                if (it.gen == gen) events.onUtteranceStart(it.mood == null ? "normal" : it.mood);
            }
        });
        final int g = it.gen;
        player.play(pcm, GeminiTts.SAMPLE_RATE, new PcmPlayer.Cancel() {
            @Override
            public boolean cancelled() {
                return g != gen;
            }
        });
        main.post(new Runnable() {
            @Override
            public void run() {
                if (it.gen != gen) return;
                if (outstanding > 0) outstanding--;
                if (outstanding == 0) {
                    abandonFocus();
                    events.onIdle();
                }
            }
        });
    }

    /** Main thread: the natural voice failed for this line, so say it with the phone's voice and pause cloud for a while. */
    private void cloudFailed(CloudItem it, String why) {
        if (it.gen != gen) return;
        if (why != null && why.length() > 0 && System.currentTimeMillis() >= cloudBlockedUntil) {
            events.onNotice("Natural voice unavailable (" + why + "). Using the phone's voice for a bit.");
        }
        if (System.currentTimeMillis() >= cloudBlockedUntil) cloudBlockedUntil = System.currentTimeMillis() + 90000;
        speakSystem(it.text, it.mood); // counts itself as outstanding
        if (outstanding > 0) outstanding--;
        if (outstanding == 0) {
            abandonFocus();
            events.onIdle();
        }
    }

    private void speakSystem(String text, String mood) {
        if (failed) {
            simulate(text, mood);
            return;
        }
        if (!ready) {
            beforeReady.add(new Pending(text, mood));
            return;
        }
        for (String chunk : Speech.chunk(text, 3000)) {
            if (outstanding == 0) requestFocus();
            String id = gen + ":" + (seq++);
            moods.put(id, mood);
            outstanding++;
            int res = tts.speak(chunk, TextToSpeech.QUEUE_ADD, new Bundle(), id);
            if (res != TextToSpeech.SUCCESS) {
                moods.remove(id);
                outstanding--;
                if (outstanding == 0) events.onIdle();
            }
        }
    }

    /** No TTS engine: pretend to speak for a plausible duration so the UI still behaves. */
    private void simulate(String text, final String mood) {
        final int myGen = gen;
        outstanding++;
        events.onUtteranceStart(mood);
        main.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (myGen != gen) return;
                if (outstanding > 0) outstanding--;
                if (outstanding == 0) events.onIdle();
            }
        }, Math.min(15000, 400 + text.length() * 45L));
    }

    private void flushAsSilent() {
        List<Pending> q = new ArrayList<Pending>(beforeReady);
        beforeReady.clear();
        for (Pending p : q) simulate(p.text, p.mood);
    }

    /** Stops speaking immediately and discards everything queued. No callbacks are delivered. */
    void stop() {
        gen++;
        outstanding = 0;
        CloudItem dropped;
        while ((dropped = cloudQ.poll()) != null) dropped.audio.cancel(true);
        player.stop();
        moods.clear();
        beforeReady.clear();
        if (tts != null && ready) {
            try {
                tts.stop();
            } catch (Exception ignored) { }
        }
        abandonFocus();
    }

    void shutdown() {
        stop();
        fetchExec.shutdownNow();
        if (tts != null) {
            try {
                tts.shutdown();
            } catch (Exception ignored) { }
            tts = null;
        }
        ready = false;
    }

    @SuppressWarnings("deprecation")
    private void requestFocus() {
        if (hasFocus || audio == null) return;
        hasFocus = audio.requestAudioFocus(null, AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
    }

    @SuppressWarnings("deprecation")
    private void abandonFocus() {
        if (!hasFocus || audio == null) return;
        audio.abandonAudioFocus(null);
        hasFocus = false;
    }
}
