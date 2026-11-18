package com.fastclick.autoclicker;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

/** The draggable crosshair that marks where taps land (its exact center). */
final class TargetView extends View {
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cross = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint badge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint badgeText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean doubleMode;

    TargetView(Context c) {
        super(c);
        fill.setColor(0x334F8CFF);
        ring.setStyle(Paint.Style.STROKE);
        ring.setColor(Ui.ACCENT);
        ring.setStrokeWidth(Ui.dp(c, 3));
        cross.setColor(0xCCFFFFFF);
        cross.setStrokeWidth(Ui.dp(c, 1.5f));
        dot.setColor(Ui.RED);
        badge.setColor(Ui.ACCENT);
        badgeText.setColor(0xFFFFFFFF);
        badgeText.setTextAlign(Paint.Align.CENTER);
        badgeText.setFakeBoldText(true);
        badgeText.setTextSize(Ui.dp(c, 10));
    }

    void setDoubleMode(boolean d) {
        doubleMode = d;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        float cx = w / 2f, cy = h / 2f;
        float r = Math.min(w, h) / 2f - Ui.dp(getContext(), 8);
        c.drawCircle(cx, cy, r, fill);
        c.drawCircle(cx, cy, r, ring);
        if (doubleMode) c.drawCircle(cx, cy, r * 0.62f, ring);
        float gap = Ui.dp(getContext(), 5);
        c.drawLine(cx - r, cy, cx - gap, cy, cross);
        c.drawLine(cx + gap, cy, cx + r, cy, cross);
        c.drawLine(cx, cy - r, cx, cy - gap, cross);
        c.drawLine(cx, cy + gap, cx, cy + r, cross);
        c.drawCircle(cx, cy, Ui.dp(getContext(), 3.5f), dot);

        // Mode badge in the top-right corner.
        float br = Ui.dp(getContext(), 9);
        float bx = w - br, by = br;
        c.drawCircle(bx, by, br, badge);
        c.drawText(doubleMode ? "2×" : "1×", bx,
                by - (badgeText.descent() + badgeText.ascent()) / 2f, badgeText);
    }
}
