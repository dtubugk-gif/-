package com.fastclick.autoclicker;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

/**
 * Accessibility service that shows a floating control panel and a draggable
 * target, and taps the target as fast as Android allows while running.
 */
public class ClickService extends AccessibilityService {

    /** Pack several taps into one gesture when they all fit in this window. */
    private static final int BURST_WINDOW_MS = 100;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private WindowManager wm;
    private View panel;
    private WindowManager.LayoutParams panelParams;
    private View target;
    private WindowManager.LayoutParams targetParams;
    private Button startStop;
    private TextView stats;

    private boolean running;
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

    private final Runnable singleTap = this::dispatchNext;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        buildTarget();
        buildPanel();
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) { }

    @Override public void onInterrupt() { stop(); }

    @Override
    public void onDestroy() {
        stop();
        if (wm != null) {
            if (panel != null) wm.removeView(panel);
            if (target != null) wm.removeView(target);
        }
        super.onDestroy();
    }

    // ---------------------------------------------------------------- overlays

    private int dp(float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics());
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
        int size = dp(56);
        FrameLayout box = new FrameLayout(this);
        GradientDrawable ring = new GradientDrawable();
        ring.setShape(GradientDrawable.OVAL);
        ring.setColor(0x551E88E5);
        ring.setStroke(dp(3), 0xFF1E88E5);
        box.setBackground(ring);

        View dot = new View(this);
        GradientDrawable dotBg = new GradientDrawable();
        dotBg.setShape(GradientDrawable.OVAL);
        dotBg.setColor(0xFFE53935);
        dot.setBackground(dotBg);
        box.addView(dot, new FrameLayout.LayoutParams(dp(10), dp(10), Gravity.CENTER));

        targetParams = overlayParams(size, size);
        targetParams.x = getResources().getDisplayMetrics().widthPixels / 2 - size / 2;
        targetParams.y = getResources().getDisplayMetrics().heightPixels / 2 - size / 2;
        box.setOnTouchListener(new DragListener(targetParams, box));
        target = box;
        wm.addView(target, targetParams);
    }

    private void buildPanel() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(6), dp(4), dp(6), dp(4));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(12));
        bg.setColor(0xE0212121);
        row.setBackground(bg);

        TextView handle = new TextView(this);
        handle.setText("☰");
        handle.setTextColor(Color.WHITE);
        handle.setTextSize(20);
        handle.setPadding(dp(6), 0, dp(8), 0);
        row.addView(handle);

        startStop = new Button(this);
        startStop.setText("▶ Start");
        startStop.setAllCaps(false);
        startStop.setOnClickListener(v -> { if (running) stop(); else start(); });
        row.addView(startStop);

        stats = new TextView(this);
        stats.setTextColor(Color.WHITE);
        stats.setTextSize(12);
        stats.setPadding(dp(8), 0, dp(4), 0);
        stats.setText("Ready");
        row.addView(stats);

        panelParams = overlayParams(WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT);
        panelParams.x = dp(16);
        panelParams.y = dp(120);
        handle.setOnTouchListener(new DragListener(panelParams, row));
        panel = row;
        wm.addView(panel, panelParams);
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
        intervalMs = Prefs.interval(this);
        maxClicks = Prefs.maxClicks(this);

        int[] loc = new int[2];
        target.getLocationOnScreen(loc);
        tapX = loc[0] + target.getWidth() / 2f;
        tapY = loc[1] + target.getHeight() / 2f;

        // Let taps pass through the target circle to the app underneath.
        targetParams.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        target.setAlpha(0.35f);
        wm.updateViewLayout(target, targetParams);

        clicks = 0;
        startedAt = SystemClock.elapsedRealtime();
        running = true;
        startStop.setText("■ Stop");
        handler.post(statsTick);
        dispatchNext();
    }

    private void stop() {
        if (!running) return;
        running = false;
        handler.removeCallbacks(singleTap);
        if (target != null) {
            targetParams.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            target.setAlpha(1f);
            wm.updateViewLayout(target, targetParams);
        }
        if (startStop != null) startStop.setText("▶ Start");
        updateStats();
    }

    private void dispatchNext() {
        if (!running) return;

        long remaining = maxClicks > 0 ? maxClicks - clicks : Long.MAX_VALUE;
        if (remaining <= 0) { stop(); return; }

        // Short intervals: several sequential taps in one gesture to cut the
        // per-gesture overhead. Long intervals: one tap, then wait.
        int taps = 1;
        if (intervalMs * 2 <= BURST_WINDOW_MS) {
            taps = Math.min(GestureDescription.getMaxStrokeCount(), BURST_WINDOW_MS / intervalMs);
            taps = (int) Math.max(1, Math.min(taps, remaining));
        }

        Path point = new Path();
        point.moveTo(tapX, tapY);
        GestureDescription.Builder builder = new GestureDescription.Builder();
        for (int i = 0; i < taps; i++) {
            builder.addStroke(new GestureDescription.StrokeDescription(point, (long) i * intervalMs, 1));
        }

        final int batch = taps;
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
            handler.postAtTime(singleTap, dispatchedAt + intervalMs);
        }
    }

    private void updateStats() {
        if (stats == null) return;
        double secs = Math.max(0.001, (SystemClock.elapsedRealtime() - startedAt) / 1000.0);
        String limit = maxClicks > 0 ? " / " + maxClicks : "";
        stats.setText(String.format(Locale.US, "%d%s clicks\n%.0f clicks/s", clicks, limit, clicks / secs));
    }
}
