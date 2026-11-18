package com.fastclick.autoclicker;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.Locale;

public class MainActivity extends Activity {

    private static final int SEEK_STEPS = 1000;

    private int mode;
    private boolean syncing;

    private View statusDot;
    private TextView statusTitle;
    private TextView statusBody;
    private TextView statusButton;

    private TextView segSingle;
    private TextView segDouble;
    private TextView modeHint;

    private TextView speedValue;
    private TextView speedRate;
    private SeekBar speedSeek;
    private LinearLayout speedChips;

    private EditText maxField;

    private int dp(float v) {
        return Ui.dp(this, v);
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Ui.BG);
        getWindow().setNavigationBarColor(Ui.BG);
        mode = Prefs.mode(this);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(16), dp(20), dp(16), dp(24));

        col.addView(header());
        col.addView(statusCard());
        col.addView(modeCard());
        col.addView(speedCard());
        col.addView(stopCard());
        col.addView(helpCard());
        col.addView(closeButton());

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Ui.BG);
        scroll.setFillViewport(true);
        scroll.addView(col);
        setContentView(scroll);

        refreshMode();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
        int m = Prefs.mode(this);
        if (m != mode) { mode = m; refreshMode(); }
    }

    // ------------------------------------------------------------ building blocks

    private TextView text(String s, float sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private LinearLayout card(String title) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(16), dp(14), dp(16), dp(16));
        c.setBackground(Ui.rounded(Ui.CARD, dp(18)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(12);
        c.setLayoutParams(lp);
        if (title != null) {
            TextView t = text(title.toUpperCase(Locale.US), 12, Ui.MUTED, true);
            t.setLetterSpacing(0.08f);
            t.setPadding(0, 0, 0, dp(10));
            c.addView(t);
        }
        return c;
    }

    private TextView pillButton(String label, int bg, int fg) {
        TextView b = text(label, 15, fg, true);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(18), dp(12), dp(18), dp(12));
        b.setBackground(Ui.pressable(Ui.rounded(bg, dp(24))));
        return b;
    }

    private TextView chip(String label, boolean selected) {
        TextView c = text(label, 13, selected ? 0xFFFFFFFF : Ui.TEXT, selected);
        c.setPadding(dp(14), dp(8), dp(14), dp(8));
        c.setBackground(Ui.pressable(Ui.rounded(selected ? Ui.ACCENT : Ui.CARD_2, dp(16))));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = dp(8);
        c.setLayoutParams(lp);
        return c;
    }

    private View fullWidth(View v, int topMarginDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(topMarginDp);
        v.setLayoutParams(lp);
        return v;
    }

    // ------------------------------------------------------------ sections

    private View header() {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(4), 0, 0, dp(4));

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_launcher);
        row.addView(icon, new LinearLayout.LayoutParams(dp(44), dp(44)));

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setPadding(dp(12), 0, 0, 0);
        titles.addView(text("Fast Clicker", 24, Ui.TEXT, true));
        titles.addView(text("Auto tap & double tap", 13, Ui.MUTED, false));
        row.addView(titles);
        return row;
    }

    private View statusCard() {
        LinearLayout c = card(null);

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        statusDot = new View(this);
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dp(12), dp(12));
        dotLp.rightMargin = dp(10);
        row.addView(statusDot, dotLp);
        statusTitle = text("", 17, Ui.TEXT, true);
        row.addView(statusTitle);
        c.addView(row);

        statusBody = text("", 14, Ui.MUTED, false);
        statusBody.setPadding(0, dp(6), 0, 0);
        c.addView(statusBody);

        statusButton = pillButton("", Ui.ACCENT, 0xFFFFFFFF);
        statusButton.setOnClickListener(v -> {
            ClickService s = ClickService.instance;
            if (s == null) {
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            } else {
                s.setOverlayVisible(!s.isOverlayVisible());
                refreshStatus();
            }
        });
        c.addView(fullWidth(statusButton, 14));
        return c;
    }

    private View modeCard() {
        LinearLayout c = card("Tap type");

        LinearLayout seg = new LinearLayout(this);
        seg.setPadding(dp(4), dp(4), dp(4), dp(4));
        seg.setBackground(Ui.rounded(Ui.CARD_2, dp(22)));
        segSingle = text("Single tap", 15, Ui.TEXT, true);
        segDouble = text("Double tap", 15, Ui.TEXT, true);
        for (TextView t : new TextView[] {segSingle, segDouble}) {
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, dp(10), 0, dp(10));
            seg.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        }
        segSingle.setOnClickListener(v -> setMode(Prefs.MODE_SINGLE));
        segDouble.setOnClickListener(v -> setMode(Prefs.MODE_DOUBLE));
        c.addView(seg);

        modeHint = text("", 13, Ui.MUTED, false);
        modeHint.setPadding(dp(2), dp(10), 0, 0);
        c.addView(modeHint);
        return c;
    }

    private View speedCard() {
        LinearLayout c = card("Speed");

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.BOTTOM);
        speedValue = text("", 30, Ui.TEXT, true);
        row.addView(speedValue);
        TextView between = text("  between taps", 14, Ui.MUTED, false);
        between.setPadding(0, 0, 0, dp(6));
        row.addView(between);
        c.addView(row);

        speedRate = text("", 13, Ui.ACCENT, true);
        c.addView(speedRate);

        speedSeek = new SeekBar(this);
        speedSeek.setMax(SEEK_STEPS);
        speedSeek.setPadding(dp(8), dp(16), dp(8), dp(12));
        speedSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                if (!fromUser || syncing) return;
                Prefs.setInterval(MainActivity.this, mode, seekToMs(p));
                refreshSpeed(false);
            }
            @Override public void onStartTrackingTouch(SeekBar s) { }
            @Override public void onStopTrackingTouch(SeekBar s) { }
        });
        c.addView(fullWidth(speedSeek, 0));

        LinearLayout labels = new LinearLayout(this);
        TextView fast = text("Faster", 12, Ui.MUTED, false);
        TextView slow = text("Slower", 12, Ui.MUTED, false);
        slow.setGravity(Gravity.END);
        labels.addView(fast, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        labels.addView(slow, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        c.addView(labels);

        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        speedChips = new LinearLayout(this);
        hs.addView(speedChips);
        c.addView(fullWidth(hs, 12));
        return c;
    }

    private View stopCard() {
        LinearLayout c = card("Stop after");

        maxField = new EditText(this);
        maxField.setInputType(InputType.TYPE_CLASS_NUMBER);
        maxField.setSingleLine(true);
        maxField.setTextColor(Ui.TEXT);
        maxField.setHintTextColor(Ui.MUTED);
        maxField.setTextSize(18);
        maxField.setHint("Never (keep going)");
        maxField.setPadding(dp(14), dp(12), dp(14), dp(12));
        maxField.setBackground(Ui.rounded(Ui.CARD_2, dp(12)));
        long max = Prefs.maxClicks(this);
        if (max > 0) maxField.setText(String.valueOf(max));
        maxField.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c2) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c2) { }
            @Override public void afterTextChanged(Editable e) {
                long v;
                try {
                    v = e.length() == 0 ? 0 : Long.parseLong(e.toString());
                } catch (NumberFormatException ex) {
                    v = 0;
                }
                Prefs.setMaxClicks(MainActivity.this, v);
            }
        });
        c.addView(maxField);

        LinearLayout chips = new LinearLayout(this);
        long[] presets = {0, 100, 1_000, 10_000, 100_000};
        for (long p : presets) {
            TextView ch = chip(p == 0 ? "Never" : String.format(Locale.US, "%,d", p), false);
            ch.setOnClickListener(v -> {
                maxField.setText(p == 0 ? "" : String.valueOf(p));
                maxField.setSelection(maxField.length());
            });
            chips.addView(ch);
        }
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.addView(chips);
        c.addView(fullWidth(hs, 12));
        return c;
    }

    private View helpCard() {
        LinearLayout c = card("How to use");
        String[] steps = {
                "Turn on Fast Clicker in Accessibility settings (button above).",
                "Drag the blue target to the spot you want tapped. Drag the panel by its ⋮⋮ handle.",
                "Tap 1× / 2× on the panel to switch between single and double tap.",
                "Press ▶ to start and ■ to stop. ✕ closes the floating controls.",
        };
        for (int i = 0; i < steps.length; i++) {
            LinearLayout row = new LinearLayout(this);
            row.setPadding(0, dp(4), 0, dp(4));
            TextView n = text(String.valueOf(i + 1), 13, 0xFFFFFFFF, true);
            n.setGravity(Gravity.CENTER);
            n.setBackground(Ui.oval(Ui.ACCENT));
            LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(dp(24), dp(24));
            nlp.rightMargin = dp(12);
            row.addView(n, nlp);
            TextView t = text(steps[i], 14, Ui.TEXT, false);
            row.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            c.addView(row);
        }

        if (Build.VERSION.SDK_INT >= 33) {
            TextView tip = text("Can't turn it on? On Android 13 and newer, open App info, tap ⋮ "
                    + "(top right) → “Allow restricted settings”, then try again.",
                    13, Ui.AMBER, false);
            tip.setPadding(0, dp(12), 0, 0);
            c.addView(tip);
            TextView info = pillButton("Open App info", Ui.CARD_2, Ui.TEXT);
            info.setOnClickListener(v -> startActivity(new Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", getPackageName(), null))));
            c.addView(fullWidth(info, 10));
        }

        TextView speed = text("About speed: each tap needs at least 1 ms down and 1 ms up, so the "
                + "ceiling is 500 taps per second. Most phones reach a few hundred. The floating "
                + "panel shows the real rate.", 12, Ui.MUTED, false);
        speed.setPadding(0, dp(12), 0, 0);
        c.addView(speed);
        return c;
    }

    private View closeButton() {
        TextView close = pillButton("✕  Close Fast Clicker", 0x22EF4444, 0xFFFF8A8A);
        close.setOnClickListener(v -> {
            ClickService s = ClickService.instance;
            if (s != null) s.setOverlayVisible(false);
            finishAndRemoveTask();
        });
        return fullWidth(close, 16);
    }

    // ------------------------------------------------------------ state

    private void setMode(int m) {
        if (m == mode) return;
        mode = m;
        Prefs.setMode(this, m);
        refreshMode();
    }

    private void refreshStatus() {
        boolean on = serviceEnabled() && ClickService.instance != null;
        statusDot.setBackground(Ui.oval(on ? Ui.GREEN : Ui.AMBER));
        if (!on) {
            statusTitle.setText("Clicker is off");
            statusBody.setText("Turn on Fast Clicker in Accessibility settings to show the floating controls.");
            statusButton.setText("Turn on");
            statusButton.setBackground(Ui.pressable(Ui.rounded(Ui.ACCENT, dp(24))));
            statusButton.setTextColor(0xFFFFFFFF);
            return;
        }
        boolean shown = ClickService.instance.isOverlayVisible();
        statusTitle.setText("Clicker is ready");
        statusBody.setText(shown
                ? "The floating controls are on screen. Switch to the app you want to tap."
                : "The floating controls are closed.");
        statusButton.setText(shown ? "Hide floating controls" : "Show floating controls");
        statusButton.setBackground(Ui.pressable(Ui.rounded(shown ? Ui.CARD_2 : Ui.ACCENT, dp(24))));
        statusButton.setTextColor(shown ? Ui.TEXT : 0xFFFFFFFF);
    }

    private void refreshMode() {
        boolean dbl = mode == Prefs.MODE_DOUBLE;
        styleSegment(segSingle, !dbl);
        styleSegment(segDouble, dbl);
        modeHint.setText(dbl
                ? "Each action is two quick taps on the same spot (like double-tapping to like a post or zoom)."
                : "Each action is one tap. Fastest option.");
        refreshSpeed(true);
    }

    private void styleSegment(TextView t, boolean selected) {
        t.setTextColor(selected ? 0xFFFFFFFF : Ui.MUTED);
        t.setBackground(selected ? Ui.pressable(Ui.rounded(Ui.ACCENT, dp(18))) : Ui.pressable(Ui.rounded(0, dp(18))));
    }

    private void refreshSpeed(boolean moveSeek) {
        int ms = Prefs.interval(this, mode);
        speedValue.setText(ClickService.formatMs(ms));
        double perSec = 1000.0 / ms;
        String unit = mode == Prefs.MODE_DOUBLE ? "double taps" : "taps";
        speedRate.setText(perSec >= 10
                ? String.format(Locale.US, "Up to %,d %s per second", Math.round(perSec), unit)
                : String.format(Locale.US, "Up to %.1f %s per second", perSec, unit));
        if (moveSeek) {
            syncing = true;
            speedSeek.setProgress(msToSeek(ms));
            syncing = false;
        }

        int[] presets = mode == Prefs.MODE_DOUBLE
                ? new int[] {150, 250, 500, 1000, 2000, 5000}
                : new int[] {2, 10, 50, 100, 500, 1000};
        speedChips.removeAllViews();
        for (int p : presets) {
            String label = p == Prefs.minInterval(mode) ? "Fastest" : ClickService.formatMs(p);
            TextView ch = chip(label, p == ms);
            ch.setOnClickListener(v -> {
                Prefs.setInterval(this, mode, p);
                refreshSpeed(true);
            });
            speedChips.addView(ch);
        }
    }

    /** Logarithmic slider: equal steps feel natural from 2 ms up to 10 s. */
    private int seekToMs(int p) {
        double min = Prefs.minInterval(mode), max = Prefs.MAX_INTERVAL_MS;
        int ms = (int) Math.round(min * Math.pow(max / min, p / (double) SEEK_STEPS));
        if (ms > 1000) ms = Math.round(ms / 100f) * 100;
        else if (ms > 100) ms = Math.round(ms / 10f) * 10;
        return Prefs.clamp(mode, ms);
    }

    private int msToSeek(int ms) {
        double min = Prefs.minInterval(mode), max = Prefs.MAX_INTERVAL_MS;
        return (int) Math.round(SEEK_STEPS * Math.log(ms / min) / Math.log(max / min));
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
