package com.reya.hvylia;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.widget.TextView;

import java.util.HashMap;
import java.util.Map;

/** The player's look for native screens: colours, fonts and rounded shapes. */
final class Ui {
    static final int BG = 0xFF101211;
    static final int SHEET = 0xFF1B1E1C;
    static final int RAISED = 0xFF252927;
    static final int FG = 0xFFFFFFFF;
    static final int FG2 = 0xFFB2B8B4;
    static final int FG3 = 0xFF8A918D;
    static final int ACCENT = 0xFF1ED760;
    static final int ACCENT_INK = 0xFF06140B;
    static final int DANGER = 0xFFFF5C6C;

    private static final Map<String, Typeface> FONTS = new HashMap<>();

    private Ui() { }

    static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics()));
    }

    /** "manrope-600", "manrope-800" or "unbounded-700"; falls back to the system font. */
    static Typeface font(Context c, String name) {
        synchronized (FONTS) {
            Typeface t = FONTS.get(name);
            if (t == null) {
                try { t = Typeface.createFromAsset(c.getAssets(), "fonts/" + name + ".ttf"); }
                catch (RuntimeException e) { t = name.endsWith("800") || name.endsWith("700") ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT; }
                FONTS.put(name, t);
            }
            return t;
        }
    }

    static GradientDrawable round(int color, float radiusPx) {
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

    static TextView text(Context c, String s, float sp, int color, String font) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(font(c, font));
        t.setIncludeFontPadding(false);
        return t;
    }

    /** "≈ 7,4 МБ" for a duration at a constant bitrate. */
    static String size(long seconds, int kbps) {
        if (seconds <= 0) return "";
        double mb = seconds * kbps * 1000.0 / 8 / 1024 / 1024 + 0.06; // + ID3 cover
        return "≈ " + String.format(java.util.Locale.ROOT, mb < 10 ? "%.1f" : "%.0f", mb).replace('.', ',') + " МБ";
    }

    static String duration(long seconds) {
        if (seconds <= 0) return "";
        long h = seconds / 3600, m = seconds / 60 % 60, s = seconds % 60;
        return h > 0 ? String.format(java.util.Locale.ROOT, "%d:%02d:%02d", h, m, s) : String.format(java.util.Locale.ROOT, "%d:%02d", m, s);
    }
}
