package com.fastclick.autoclicker;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;

/** Colors and small drawing helpers shared by the app screen and the overlay. */
final class Ui {
    static final int BG = 0xFF0E1117;
    static final int CARD = 0xFF181D27;
    static final int CARD_2 = 0xFF222937;
    static final int ACCENT = 0xFF4F8CFF;
    static final int GREEN = 0xFF22C55E;
    static final int RED = 0xFFEF4444;
    static final int AMBER = 0xFFF59E0B;
    static final int TEXT = 0xFFE8ECF3;
    static final int MUTED = 0xFF9AA3B2;

    private Ui() {}

    static int dp(Context c, float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                c.getResources().getDisplayMetrics());
    }

    static GradientDrawable rounded(int color, float radiusPx) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radiusPx);
        return d;
    }

    static GradientDrawable oval(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(color);
        return d;
    }

    /** Background with a touch ripple. */
    static Drawable pressable(Drawable base) {
        return new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), base, null);
    }
}
