package com.toiboytoestar.kurisu;

import android.app.Application;

import java.io.PrintWriter;
import java.io.StringWriter;

/** Remembers the last crash so the next launch can show it (helps when no debugger is attached). */
public class App extends Application {
    static final String PREF_CRASH = "last_crash";

    @Override
    public void onCreate() {
        super.onCreate();
        final Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                try {
                    StringWriter sw = new StringWriter();
                    e.printStackTrace(new PrintWriter(sw));
                    String s = sw.toString();
                    if (s.length() > 1800) s = s.substring(0, 1800);
                    new Prefs(App.this).raw().edit().putString(PREF_CRASH, s).commit();
                } catch (Throwable ignored) { }
                if (prev != null) prev.uncaughtException(t, e);
            }
        });
    }
}
