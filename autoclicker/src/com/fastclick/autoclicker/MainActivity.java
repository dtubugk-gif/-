package com.fastclick.autoclicker;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    private TextView status;
    private EditText interval;
    private EditText maxClicks;

    private int dp(float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics());
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(20), dp(20), dp(20), dp(20));

        status = new TextView(this);
        status.setTextSize(16);
        status.setPadding(0, 0, 0, dp(12));
        col.addView(status);

        Button enable = new Button(this);
        enable.setText("Open Accessibility settings");
        enable.setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        col.addView(enable);

        col.addView(label("Time between clicks (milliseconds, minimum " + Prefs.MIN_INTERVAL_MS + ")"));
        interval = number();
        interval.setText(String.valueOf(Prefs.interval(this)));
        col.addView(interval);

        Button fastest = new Button(this);
        fastest.setText("Fastest possible (" + Prefs.MIN_INTERVAL_MS + " ms)");
        fastest.setOnClickListener(v -> interval.setText(String.valueOf(Prefs.MIN_INTERVAL_MS)));
        col.addView(fastest);

        col.addView(label("Stop after this many clicks (0 = never)"));
        maxClicks = number();
        maxClicks.setText(String.valueOf(Prefs.maxClicks(this)));
        col.addView(maxClicks);

        Button save = new Button(this);
        save.setText("Save");
        save.setOnClickListener(v -> save());
        col.addView(save);

        TextView help = new TextView(this);
        help.setPadding(0, dp(16), 0, 0);
        help.setText("How to use:\n"
                + "1. Tap \"Open Accessibility settings\", find Fast Clicker, and turn it on.\n"
                + "2. A floating panel and a blue target appear. Drag the target (and the panel, by ☰) where you want.\n"
                + "3. Press ▶ Start. Press ■ Stop to end.\n\n"
                + "About speed: Android needs at least 1 ms finger-down and 1 ms finger-up for each tap, "
                + "so the hard ceiling is 500 clicks per second. Real phones usually reach a few hundred. "
                + "The panel shows the actual rate your phone is achieving.");
        col.addView(help);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(col);
        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        boolean on = serviceEnabled();
        status.setText(on
                ? "✅ Clicker service is ON. Use the floating panel to start."
                : "⚠ Clicker service is OFF. Turn it on in Accessibility settings.");
    }

    private TextView label(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setPadding(0, dp(16), 0, dp(4));
        return t;
    }

    private EditText number() {
        EditText e = new EditText(this);
        e.setInputType(InputType.TYPE_CLASS_NUMBER);
        e.setSingleLine(true);
        e.setGravity(Gravity.START);
        return e;
    }

    private void save() {
        int ms;
        long max;
        try {
            ms = Integer.parseInt(interval.getText().toString().trim());
            String m = maxClicks.getText().toString().trim();
            max = m.isEmpty() ? 0 : Long.parseLong(m);
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Please enter whole numbers", Toast.LENGTH_SHORT).show();
            return;
        }
        if (ms < Prefs.MIN_INTERVAL_MS) {
            ms = Prefs.MIN_INTERVAL_MS;
            interval.setText(String.valueOf(ms));
            Toast.makeText(this, "Set to the minimum, " + ms + " ms", Toast.LENGTH_SHORT).show();
        }
        Prefs.save(this, ms, max);
        Toast.makeText(this, "Saved. Takes effect on next Start.", Toast.LENGTH_SHORT).show();
    }

    private boolean serviceEnabled() {
        String enabled = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabled == null) return false;
        String me = new ComponentName(this, ClickService.class).flattenToString();
        for (String s : enabled.split(":")) {
            if (s.equalsIgnoreCase(me)) return true;
        }
        return false;
    }
}
