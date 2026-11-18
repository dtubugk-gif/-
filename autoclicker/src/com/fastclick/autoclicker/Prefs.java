package com.fastclick.autoclicker;

import android.content.Context;
import android.content.SharedPreferences;

/** Shared settings between the app screen and the clicking service. */
final class Prefs {
    /** One tap needs at least 1 ms finger-down plus 1 ms finger-up, so 2 ms is the floor. */
    static final int MIN_INTERVAL_MS = 2;

    private static final String FILE = "settings";
    private static final String KEY_INTERVAL = "interval_ms";
    private static final String KEY_MAX = "max_clicks";

    private Prefs() {}

    private static SharedPreferences sp(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    static int interval(Context c) {
        return Math.max(MIN_INTERVAL_MS, sp(c).getInt(KEY_INTERVAL, MIN_INTERVAL_MS));
    }

    /** 0 means "no limit". */
    static long maxClicks(Context c) {
        return Math.max(0, sp(c).getLong(KEY_MAX, 0));
    }

    static void save(Context c, int intervalMs, long maxClicks) {
        sp(c).edit()
                .putInt(KEY_INTERVAL, Math.max(MIN_INTERVAL_MS, intervalMs))
                .putLong(KEY_MAX, Math.max(0, maxClicks))
                .apply();
    }
}
