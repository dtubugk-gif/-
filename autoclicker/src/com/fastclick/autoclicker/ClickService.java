package com.fastclick.autoclicker;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.SharedPreferences;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

/**
 * Accessibility service that shows a floating control panel and a draggable
 * target, and taps (or double-taps) the target while running.
 */
public class ClickService extends AccessibilityService
        implements SharedPreferences.OnSharedPreferenceChangeListener {

    /** Pack several single taps into one gesture when they fit in this window. */
    private static final int BURST_WINDOW_MS = 100;

    // Double tap timing. Android's tap detector needs the gap between the
    // first finger-up and the second finger-down to be 40..300 ms.
    private static final int DOUBLE_PRESS_MS = 30;
    private static final int DOUBLE_GAP_MS = 60;

    /** Running instance, so the app screen can show or hide the overlay. */
    static ClickService instance;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private WindowManager wm;
    private LinearLayout panel;
    private WindowManager.LayoutParams panelParams;
    private TargetView target;
    private WindowManager.LayoutParams targetParams;
    private TextView playButton;
    private TextView modeButton;
    private TextView statsCount;
    private TextView statsRate;
    private boolean overlayVisible;

    private boolean running;
    private int mode;
    private long clicks;
    private long maxClicks;
    private int intervalMs;
    private long startedAt;
    private float tapX, tapY;

    private final Runnable statsTick = new Runnable() {
        @Override public void run() {
            updateStats();
            if (running) handler.postDelayed(this, 250);
        }
    };

    private final Runnable nextTap = this::dispatchNext;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        mode = Prefs.mode(this);
        buildTarget();
        buildPanel();
        overlayVisible = true;
        Prefs.listen(this, this);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) { }

    @Override public void onInterrupt() { stop(); }

    @Override
    public void onDestroy() {
        stop();
        Prefs.unlisten(this, this);
        setOverlayVisible(false);
        if (instance == this) instance = null;
        super.onDestroy();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sp, String key) {
        if (running) return;
        mode = Prefs.mode(this);
        refreshModeViews();
    }

    boolean isOverlayVisible() {
        return overlayVisible;
    }

    void setOverlayVisible(boolean show) {
        if (wm == null || show == overlayVisible) return;
        if (show) {
            wm.addView(target, targetParams);
            wm.addView(panel, panelParams);
        } else {
            stop();
            wm.removeView(panel);
            wm.removeView(target);
        }
        overlayVisible = show;
    }

    // ---------------------------------------------------------------- overlays

    private int dp(float v) {
        return Ui.dp(this, v);
    }

    private WindowManager.LayoutParams overlayParams(int w, int h) {
        WindowManager.LayoutParams p = new WindowManager.LayoutParams(
                w, h,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        p.gravity = Gravity.TOP | Gravity.START;
        return p;
    }

    private void buildTarget() {
        int size = dp(72);
        target = new TargetView(this);
        target.setDoubleMode(mode == Prefs.MODE_DOUBLE);
        targetParams = overlayParams(size, size);
        targetParams.x = getResources().getDisplayMetrics().widthPixels / 2 - size / 2;
        targetParams.y = getResources().getDisplayMetrics().heightPixels / 2 - size / 2;
        target.setOnTouchListener(new DragListener(targetParams, target));
        wm.addView(target, targetParams);
    }

    private TextView roundButton(String text, int color, int sizeDp, float textSp) {
        TextView b = new TextView(this);
        b.setText(text);
        b.setTextColor(0xFFFFFFFF);
        b.setTextSize(textSp);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setGravity(Gravity.CENTER);
        b.setBackground(Ui.pressable(Ui.oval(color)));
        b.setLayoutParams(new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)));
        return b;
    }

    private View spacer(int widthDp) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(widthDp), 1));
        return v;
    }

    private void buildPanel() {
        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.HORIZONTAL);
        panel.setGravity(Gravity.CENTER_VERTICAL);
        panel.setPadding(dp(4), dp(6), dp(10), dp(6));
        panel.setBackground(Ui.rounded(0xF0181D27, dp(32)));
        panel.setElevation(dp(6));

        TextView handle = new TextView(this);
        handle.setText("⋮⋮");
        handle.setTextColor(Ui.MUTED);
        handle.setTextSize(16);
        handle.setGravity(Gravity.CENTER);
        handle.setLayoutParams(new LinearLayout.LayoutParams(dp(28), dp(48)));
        panel.addView(handle);

        playButton = roundButton("▶", Ui.GREEN, 48, 18);
        playButton.setOnClickListener(v -> { if (running) stop(); else start(); });
        panel.addView(playButton);
        panel.addView(spacer(8));

        modeButton = roundButton("1×", Ui.CARD_2, 40, 14);
        modeButton.setOnClickListener(v -> {
            if (running) return;
            Prefs.setMode(this, mode == Prefs.MODE_DOUBLE ? Prefs.MODE_SINGLE : Prefs.MODE_DOUBLE);
        });
        panel.addView(modeButton);
        panel.addView(spacer(10));

        LinearLayout statsCol = new LinearLayout(this);
        statsCol.setOrientation(LinearLayout.VERTICAL);
        statsCount = new TextView(this);
        statsCount.setTextColor(Ui.TEXT);
        statsCount.setTextSize(14);
        statsCount.setTypeface(Typeface.DEFAULT_BOLD);
        statsRate = new TextView(this);
        statsRate.setTextColor(Ui.MUTED);
        statsRate.setTextSize(11);
        statsCol.addView(statsCount);
        statsCol.addView(statsRate);
        statsCol.setMinimumWidth(dp(84));
        panel.addView(statsCol);

        // Close: stops clicking and hides the panel and target.
        // Bring them back with the app's "Show floating controls" button.
        TextView close = roundButton("✕", 0x33EF4444, 34, 14);
        close.setTextColor(0xFFFF8A8A);
        close.setContentDescription("Close");
        close.setOnClickListener(v -> setOverlayVisible(false));
        panel.addView(close);

        panelParams = overlayParams(WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT);
        panelParams.x = dp(12);
        panelParams.y = dp(100);
        handle.setOnTouchListener(new DragListener(panelParams, panel));
        refreshModeViews();
        wm.addView(panel, panelParams);
    }

    private void refreshModeViews() {
        if (target == null) return;
        boolean dbl = mode == Prefs.MODE_DOUBLE;
        target.setDoubleMode(dbl);
        modeButton.setText(dbl ? "2×" : "1×");
        modeButton.setBackground(Ui.pressable(Ui.oval(dbl ? Ui.ACCENT : Ui.CARD_2)));
        statsCount.setText(dbl ? "Double tap" : "Single tap");
        int ms = Prefs.interval(this, mode);
        statsRate.setText("every " + formatMs(ms));
    }

    static String formatMs(int ms) {
        return ms >= 1000 && ms % 100 == 0
                ? String.format(Locale.US, "%.1f s", ms / 1000f).replace(".0 s", " s")
                : ms + " ms";
    }

    /** Moves an overlay window with the finger. */
    private final class DragListener implements View.OnTouchListener {
        private final WindowManager.LayoutParams params;
        private final View window;
        private int startX, startY;
        private float downX, downY;

        DragListener(WindowManager.LayoutParams params, View window) {
            this.params = params;
            this.window = window;
        }

        @Override
        public boolean onTouch(View v, MotionEvent e) {
            if (running) return false;
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    startX = params.x;
                    startY = params.y;
                    downX = e.getRawX();
                    downY = e.getRawY();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    params.x = startX + (int) (e.getRawX() - downX);
                    params.y = startY + (int) (e.getRawY() - downY);
                    wm.updateViewLayout(window, params);
                    return true;
                default:
                    return true;
            }
        }
    }

    // ---------------------------------------------------------------- clicking

    private void start() {
        mode = Prefs.mode(this);
        intervalMs = Prefs.interval(this, mode);
        maxClicks = Prefs.maxClicks(this);

        int[] loc = new int[2];
        target.getLocationOnScreen(loc);
        tapX = loc[0] + target.getWidth() / 2f;
        tapY = loc[1] + target.getHeight() / 2f;

        // Let taps pass through the target to the app underneath.
        targetParams.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        target.setAlpha(0.4f);
        wm.updateViewLayout(target, targetParams);

        clicks = 0;
        startedAt = SystemClock.elapsedRealtime();
        running = true;
        playButton.setText("■");
        playButton.setBackground(Ui.pressable(Ui.oval(Ui.RED)));
        modeButton.setAlpha(0.4f);
        handler.post(statsTick);
        dispatchNext();
    }

    private void stop() {
        if (!running) return;
        running = false;
        handler.removeCallbacks(nextTap);
        handler.removeCallbacks(statsTick);
        targetParams.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        target.setAlpha(1f);
        if (overlayVisible) wm.updateViewLayout(target, targetParams);
        playButton.setText("▶");
        playButton.setBackground(Ui.pressable(Ui.oval(Ui.GREEN)));
        modeButton.setAlpha(1f);
        updateStats();
    }

    private void dispatchNext() {
        if (!running) return;

        long remaining = maxClicks > 0 ? maxClicks - clicks : Long.MAX_VALUE;
        if (remaining <= 0) { stop(); return; }

        Path point = new Path();
        point.moveTo(tapX, tapY);
        GestureDescription.Builder builder = new GestureDescription.Builder();
        int count;

        if (mode == Prefs.MODE_DOUBLE) {
            // Two quick taps on the same spot: down 0-30 ms, up, down 90-120 ms.
            builder.addStroke(new GestureDescription.StrokeDescription(point, 0, DOUBLE_PRESS_MS));
            builder.addStroke(new GestureDescription.StrokeDescription(point,
                    DOUBLE_PRESS_MS + DOUBLE_GAP_MS, DOUBLE_PRESS_MS));
            count = 1;
        } else {
            // Short intervals: several sequential taps in one gesture to cut
            // per-gesture overhead. Long intervals: one tap, then wait.
            count = 1;
            if (intervalMs * 2 <= BURST_WINDOW_MS) {
                count = Math.min(GestureDescription.getMaxStrokeCount(), BURST_WINDOW_MS / intervalMs);
                count = (int) Math.max(1, Math.min(count, remaining));
            }
            for (int i = 0; i < count; i++) {
                builder.addStroke(new GestureDescription.StrokeDescription(point, (long) i * intervalMs, 1));
            }
        }

        final int batch = count;
        final long dispatchedAt = SystemClock.uptimeMillis();
        boolean ok = dispatchGesture(builder.build(), new GestureResultCallback() {
            @Override public void onCompleted(GestureDescription g) {
                clicks += batch;
                scheduleAfter(dispatchedAt, batch);
            }
            @Override public void onCancelled(GestureDescription g) {
                // Usually a real finger touched the screen; keep going.
                scheduleAfter(dispatchedAt, batch);
            }
        }, handler);
        if (!ok) stop();
    }

    private void scheduleAfter(long dispatchedAt, int batch) {
        if (!running) return;
        if (batch > 1) {
            dispatchNext();
        } else {
            handler.postAtTime(nextTap, dispatchedAt + intervalMs);
        }
    }

    private void updateStats() {
        if (statsCount == null) return;
        double secs = Math.max(0.001, (SystemClock.elapsedRealtime() - startedAt) / 1000.0);
        String limit = maxClicks > 0 ? " / " + maxClicks : "";
        String unit = mode == Prefs.MODE_DOUBLE ? "double taps" : "clicks";
        statsCount.setText(String.format(Locale.US, "%,d%s", clicks, limit));
        statsRate.setText(String.format(Locale.US, "%.1f %s/s", clicks / secs, unit));
    }
}
