package com.toiboytoestar.kurisu;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

/** Plays raw 16-bit mono PCM and blocks until it has finished or was cancelled. Call play() from a worker thread. */
final class PcmPlayer {
    interface Cancel {
        boolean cancelled();
    }

    private volatile AudioTrack track;

    @SuppressWarnings("deprecation")
    void play(byte[] pcm, int sampleRate, Cancel cancel) {
        if (pcm == null || pcm.length < 2) return;
        int min = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
        AudioTrack t;
        try {
            t = new AudioTrack(AudioManager.STREAM_MUSIC, sampleRate, AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, Math.max(min, 8192), AudioTrack.MODE_STREAM);
        } catch (RuntimeException e) {
            return; // no audio output available; the caller just moves on
        }
        track = t;
        try {
            t.play();
            int off = 0;
            while (off < pcm.length && !cancel.cancelled()) {
                int n = t.write(pcm, off, Math.min(4096, pcm.length - off));
                if (n <= 0) break;
                off += n;
            }
            // let the buffered tail play out
            long frames = pcm.length / 2;
            long deadline = System.currentTimeMillis() + frames * 1000L / sampleRate + 1500;
            while (!cancel.cancelled() && t.getPlaybackHeadPosition() < frames && System.currentTimeMillis() < deadline) {
                try {
                    Thread.sleep(20);
                } catch (InterruptedException e) {
                    break;
                }
            }
        } catch (RuntimeException ignored) {
            // released by stop() while writing, or the device refused to play
        } finally {
            track = null;
            try {
                t.stop();
            } catch (IllegalStateException ignored) { }
            t.release();
        }
    }

    /** Silences playback immediately (the blocked play() call returns). */
    void stop() {
        AudioTrack t = track;
        if (t != null) {
            try {
                t.pause();
                t.flush();
            } catch (IllegalStateException ignored) { }
        }
    }
}
