package com.fastclick.autoclicker;

import android.content.Context;
import android.content.SharedPreferences;

/** Shared settings between the app screen and the clicking service. */
final class Prefs {
    static final int MODE_SINGLE = 1;
    static final int MODE_DOUBLE = 2;

    /** A single tap needs at least 1 ms finger-down plus 1 ms finger-up. */
    static final int MIN_SINGLE_MS = 2;
    /** A double tap takes 120 ms itself (see ClickService), plus a short pause. */
    static final int MIN_DOUBLE_MS = 150;
    static final int MAX_INTERVAL_MS = 10_000;

    private static final String FILE = "settings";
    private static final String KEY_MODE = "mode";
    private static final String KEY_SINGLE = "interval_ms";
    private static final String KEY_DOUBLE = "double_interval_ms";
    private static final String KEY_MAX = "max_clicks";

    private Prefs() {}

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    static int mode(Context c) {
        return sp(c).getInt(KEY_MODE, MODE_SINGLE) == MODE_DOUBLE ? MODE_DOUBLE : MODE_SINGLE;
    }

    static void setMode(Context c, int mode) {
        sp(c).edit().putInt(KEY_MODE, mode).apply();
    }

    static int minInterval(int mode) {
        return mode == MODE_DOUBLE ? MIN_DOUBLE_MS : MIN_SINGLE_MS;
    }

    static int clamp(int mode, int ms) {
        return Math.max(minInterval(mode), Math.min(MAX_INTERVAL_MS, ms));
    }

    /** Time from the start of one click (or double tap) to the start of the next. */
    static int interval(Context c, int mode) {
        int ms = mode == MODE_DOUBLE
                ? sp(c).getInt(KEY_DOUBLE, 400)
                : sp(c).getInt(KEY_SINGLE, MIN_SINGLE_MS);
        return clamp(mode, ms);
    }

    static void setInterval(Context c, int mode, int ms) {
        sp(c).edit().putInt(mode == MODE_DOUBLE ? KEY_DOUBLE : KEY_SINGLE, clamp(mode, ms)).apply();
    }

    /** 0 means "no limit". */
    static long maxClicks(Context c) {
        return Math.max(0, sp(c).getLong(KEY_MAX, 0));
    }

    static void setMaxClicks(Context c, long max) {
        sp(c).edit().putLong(KEY_MAX, Math.max(0, max)).apply();
    }

    static void listen(Context c, SharedPreferences.OnSharedPreferenceChangeListener l) {
        sp(c).registerOnSharedPreferenceChangeListener(l);
    }

    static void unlisten(Context c, SharedPreferences.OnSharedPreferenceChangeListener l) {
        sp(c).unregisterOnSharedPreferenceChangeListener(l);
    }
}
