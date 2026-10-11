package com.toiboytoestar.kurisu;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

/** The "phone call" screen. Also shows Kurisu's wake-up call when it rings. */
public class LaunchActivity extends Activity {

    static final String EXTRA_INCOMING = "incoming_call";

    private ImageView connect, cancel, logo;
    private TextView status;
    private boolean pressed;
    private boolean incoming;
    private MediaPlayer tone;
    private MediaPlayer ring;
    private final Handler h = new Handler(Looper.getMainLooper());
    private int frame = 0;

    private final Runnable animate = new Runnable() {
        @Override
        public void run() {
            if (frame < 39) {
                frame++;
                int id = getResources().getIdentifier("logo" + frame, "drawable", R.class.getPackage().getName());
                if (id != 0) logo.setImageDrawable(getResources().getDrawable(id, getTheme()));
                h.postDelayed(this, 20);
            }
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_launch);
        connect = (ImageView) findViewById(R.id.imageView_connect);
        cancel = (ImageView) findViewById(R.id.imageView_cancel);
        status = (TextView) findViewById(R.id.textView_call);
        logo = (ImageView) findViewById(R.id.imageView_logo);
        Notifier.ensureChannels(this);

        h.post(animate);
        applyIntent(getIntent());

        connect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onConnect();
            }
        });
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cancel.setImageResource(R.drawable.cancel_select);
                stopRinging();
                Notifier.cancel(LaunchActivity.this, Notifier.ID_CALL);
                finish();
            }
        });
        logo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(LaunchActivity.this, SettingsActivity.class));
            }
        });
        if (new Prefs(this).showXpIcon()) Notifier.showXp(this);
        showLastCrash();
    }

    private void showLastCrash() {
        final Prefs p = new Prefs(this);
        String crash = p.raw().getString(App.PREF_CRASH, "");
        if (crash.length() == 0) return;
        p.raw().edit().remove(App.PREF_CRASH).apply();
        android.widget.ScrollView sv = new android.widget.ScrollView(this);
        TextView tv = new TextView(this);
        tv.setText(crash);
        tv.setTextSize(10);
        tv.setPadding(24, 24, 24, 24);
        tv.setTextIsSelectable(true);
        sv.addView(tv);
        new android.app.AlertDialog.Builder(this)
                .setTitle("The app crashed last time")
                .setMessage("Please screenshot this and send it to the developer.")
                .setView(sv)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        applyIntent(intent);
    }

    private void applyIntent(Intent in) {
        incoming = in != null && in.getBooleanExtra(EXTRA_INCOMING, false);
        if (incoming) {
            if (Build.VERSION.SDK_INT >= 27) {
                setShowWhenLocked(true);
                setTurnScreenOn(true);
            } else {
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                        | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
            }
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            status.setText(R.string.incoming_call);
            startRinging();
        }
    }

    private void startRinging() {
        if (ring != null) return;
        Notifier.cancel(this, Notifier.ID_CALL); // the in-app ringtone takes over
        try {
            Prefs p = new Prefs(this);
            int id = getResources().getIdentifier(p.ringtone(), "raw", R.class.getPackage().getName());
            AudioAttributes attrs = new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build();
            ring = MediaPlayer.create(this, id != 0 ? id : R.raw.tone, attrs, android.media.AudioManager.AUDIO_SESSION_ID_GENERATE);
            if (ring == null) return;
            ring.setLooping(true);
            ring.start();
        } catch (Exception ignored) {
            ring = null;
        }
        // stop on its own after a minute so it can't ring forever
        h.postDelayed(new Runnable() {
            @Override
            public void run() {
                stopRinging();
            }
        }, 60000);
    }

    private void stopRinging() {
        if (ring != null) {
            try {
                ring.stop();
            } catch (Exception ignored) { }
            ring.release();
            ring = null;
        }
    }

    private void onConnect() {
        if (pressed) return;
        pressed = true;
        connect.setImageResource(R.drawable.connect_select);
        if (incoming) {
            stopRinging();
            Notifier.cancel(this, Notifier.ID_CALL);
            incoming = false;
            getIntent().removeExtra(EXTRA_INCOMING);
            new Prefs(this).raw().edit().putLong(MainActivity.PREF_BRIEFING_TOKEN, System.currentTimeMillis()).apply();
            Intent i = new Intent(this, MainActivity.class);
            i.putExtra(MainActivity.EXTRA_BRIEFING, true);
            startActivity(i);
            return;
        }
        tone = MediaPlayer.create(this, R.raw.tone);
        if (tone == null) {
            startActivity(new Intent(this, MainActivity.class));
            return;
        }
        tone.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                mp.release();
                tone = null;
                startActivity(new Intent(LaunchActivity.this, MainActivity.class));
            }
        });
        status.setText(R.string.connecting);
        tone.start();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (pressed) status.setText(R.string.disconnected);
        else if (incoming) status.setText(R.string.incoming_call);
        else if (!SpeechInput.available(this)) status.setText(R.string.google_app_error);
        else status.setText(R.string.call);
        pressed = false;
        connect.setImageResource(R.drawable.connect_unselect);
        cancel.setImageResource(R.drawable.cancel_unselect);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        h.removeCallbacksAndMessages(null);
        stopRinging();
        if (tone != null) {
            tone.release();
            tone = null;
        }
    }
}
