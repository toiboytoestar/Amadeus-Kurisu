package com.toiboytoestar.kurisu;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;

import java.util.ArrayList;

/** Speech-to-text with live partial results and a mic level for the UI. Main thread only. */
final class SpeechInput implements RecognitionListener {

    interface Events {
        void onListening();

        void onLevel(float level);

        void onPartial(String text);

        void onFinal(String text);

        /** code is a SpeechRecognizer.ERROR_* constant. */
        void onError(int code);
    }

    private final Context ctx;
    private final Events events;
    private SpeechRecognizer sr;
    private boolean active;
    private boolean swallowClientError;
    private long silenceMs = 1300;

    /** How long a pause ends an utterance (recognizers may ignore this). Longer values suit dictation. */
    void setSilenceMs(long ms) {
        silenceMs = ms;
    }

    SpeechInput(Context ctx, Events events) {
        this.ctx = ctx;
        this.events = events;
    }

    static boolean available(Context c) {
        try {
            return SpeechRecognizer.isRecognitionAvailable(c);
        } catch (Exception e) {
            return false;
        }
    }

    boolean isActive() {
        return active;
    }

    void start(String langTag, boolean preferOffline) {
        if (active) {
            cancel();
        }
        if (sr == null) {
            sr = SpeechRecognizer.createSpeechRecognizer(ctx);
            sr.setRecognitionListener(this);
        }
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, langTag);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, langTag);
        i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        i.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, ctx.getPackageName());
        i.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, preferOffline);
        i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, silenceMs);
        i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, silenceMs);
        active = true;
        try {
            sr.startListening(i);
        } catch (Exception e) {
            active = false;
            events.onError(SpeechRecognizer.ERROR_CLIENT);
        }
    }

    /** Ends the utterance early; a final result still arrives. */
    void stopListening() {
        if (active && sr != null) {
            try {
                sr.stopListening();
            } catch (Exception ignored) { }
        }
    }

    /** Aborts without a result. */
    void cancel() {
        if (sr != null && active) {
            swallowClientError = true;
            try {
                sr.cancel();
            } catch (Exception ignored) { }
        }
        active = false;
    }

    void destroy() {
        active = false;
        if (sr != null) {
            try {
                sr.destroy();
            } catch (Exception ignored) { }
            sr = null;
        }
    }

    private static String first(Bundle b) {
        if (b == null) return "";
        ArrayList<String> l = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        return l == null || l.isEmpty() || l.get(0) == null ? "" : l.get(0);
    }

    @Override
    public void onReadyForSpeech(Bundle params) {
        events.onListening();
    }

    @Override
    public void onBeginningOfSpeech() { }

    @Override
    public void onRmsChanged(float rmsdB) {
        float v = (rmsdB + 2f) / 12f;
        events.onLevel(v < 0 ? 0 : v > 1 ? 1 : v);
    }

    @Override
    public void onBufferReceived(byte[] buffer) { }

    @Override
    public void onEndOfSpeech() { }

    @Override
    public void onError(int error) {
        if (error == SpeechRecognizer.ERROR_CLIENT && swallowClientError) {
            swallowClientError = false;
            return;
        }
        swallowClientError = false;
        if (!active) return;
        active = false;
        events.onError(error);
    }

    @Override
    public void onResults(Bundle results) {
        swallowClientError = false;
        if (!active) return;
        active = false;
        events.onFinal(first(results));
    }

    @Override
    public void onPartialResults(Bundle partialResults) {
        if (!active) return;
        String t = first(partialResults);
        if (t.length() > 0) events.onPartial(t);
    }

    @Override
    public void onEvent(int eventType, Bundle params) { }
}
