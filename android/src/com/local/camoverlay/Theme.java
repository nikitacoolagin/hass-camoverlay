package com.local.camoverlay;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;

/**
 * A tiny Material-style design system built entirely in code (the build links only
 * the manifest, so there are no XML themes or resources). Resolves a light or dark
 * colour palette from the current system night mode and hands out ready-made
 * drawables: rounded cards, filled / tonal buttons, chips and text fields.
 */
public class Theme {

    public final boolean dark;

    // Core palette (resolved in the constructor).
    public final int bg;                 // window background
    public final int surface;            // cards
    public final int surfaceVariant;     // text fields, tonal fills
    public final int onBg;               // primary text
    public final int onSurfaceVariant;   // secondary / muted text
    public final int primary;
    public final int onPrimary;
    public final int primaryContainer;
    public final int onPrimaryContainer;
    public final int outline;
    public final int success;
    public final int error;

    private final float density;

    public Theme(Context c) {
        density = c.getResources().getDisplayMetrics().density;
        int mode = c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        dark = mode == Configuration.UI_MODE_NIGHT_YES;

        if (dark) {
            bg                 = 0xFF0E1116;
            surface            = 0xFF171B22;
            surfaceVariant     = 0xFF222A35;
            onBg               = 0xFFE7EBF2;
            onSurfaceVariant   = 0xFF9BA6B5;
            primary            = 0xFF6E9BFF;
            onPrimary          = 0xFF06245C;
            primaryContainer   = 0xFF1E3A6E;
            onPrimaryContainer = 0xFFD9E4FF;
            outline            = 0xFF2C3643;
            success            = 0xFF5AD699;
            error              = 0xFFFF7A6E;
        } else {
            bg                 = 0xFFF4F6FB;
            surface            = 0xFFFFFFFF;
            surfaceVariant     = 0xFFEDF1F8;
            onBg               = 0xFF1A1C20;
            onSurfaceVariant   = 0xFF5B6573;
            primary            = 0xFF2D6CDF;
            onPrimary          = 0xFFFFFFFF;
            primaryContainer   = 0xFFDCE8FF;
            onPrimaryContainer = 0xFF0B2E68;
            outline            = 0xFFD6DDE8;
            success            = 0xFF18794E;
            error              = 0xFFC23A2B;
        }
    }

    public int dp(float v) { return Math.round(v * density); }

    private int ripple() {
        return dark ? 0x33FFFFFF : 0x1F000000;
    }

    private static Drawable withRipple(int rippleColor, GradientDrawable content) {
        if (Build.VERSION.SDK_INT >= 21) {
            return new RippleDrawable(ColorStateList.valueOf(rippleColor), content, content);
        }
        return content;
    }

    /** Rounded card surface with a hairline outline. */
    public GradientDrawable card() {
        GradientDrawable g = new GradientDrawable();
        g.setColor(surface);
        g.setCornerRadius(dp(20));
        g.setStroke(dp(1), outline);
        return g;
    }

    /** Filled primary button background (with a ripple on API 21+). */
    public Drawable filledButton() {
        GradientDrawable g = new GradientDrawable();
        g.setColor(primary);
        g.setCornerRadius(dp(14));
        return withRipple(0x33FFFFFF, g);
    }

    /** Tonal (secondary) button background. */
    public Drawable tonalButton() {
        GradientDrawable g = new GradientDrawable();
        g.setColor(primaryContainer);
        g.setCornerRadius(dp(12));
        return withRipple(ripple(), g);
    }

    /** Filled text-field background. */
    public GradientDrawable field() {
        GradientDrawable g = new GradientDrawable();
        g.setColor(surfaceVariant);
        g.setCornerRadius(dp(12));
        g.setStroke(dp(1), outline);
        return g;
    }

    /** Soft pill behind a status chip, tinted from a base colour. */
    public GradientDrawable chip(int baseColor) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(blend(baseColor, surface, dark ? 0.80f : 0.86f));
        g.setCornerRadius(dp(999));
        return g;
    }

    /** Circular translucent control button used on the overlay / fullscreen. */
    public GradientDrawable circle(int fill) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(fill);
        return g;
    }

    /** Mix {@code a} over {@code b} by {@code t} (0 = a, 1 = b). */
    public static int blend(int a, int b, float t) {
        int ar = Color.red(a), ag = Color.green(a), ab = Color.blue(a);
        int br = Color.red(b), bg2 = Color.green(b), bb = Color.blue(b);
        int r = Math.round(ar + (br - ar) * t);
        int g = Math.round(ag + (bg2 - ag) * t);
        int bl = Math.round(ab + (bb - ab) * t);
        return Color.rgb(r, g, bl);
    }
}
