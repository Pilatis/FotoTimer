package com.fototimer;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Cores, medidas e pequenos construtores de views usados em todas as telas. */
final class Ui {
    private Ui() {}

    static final int BG = 0xFF0D0F13;
    static final int CARD = 0xFF171A21;
    static final int CARD2 = 0xFF222632;
    static final int STROKE = 0xFF2A2F3B;
    static final int TEXT = 0xFFF3F5F9;
    static final int TEXT2 = 0xFF98A1B3;
    static final int TEXT3 = 0xFF666F80;
    static final int ACCENT = 0xFF5B8CFF;
    static final int ACCENT_SOFT = 0x295B8CFF;
    static final int RED = 0xFFFF5D62;
    static final int GREEN = 0xFF3DDC97;
    static final int AMBER = 0xFFFFB547;
    static final int AMBER_SOFT = 0x22FFB547;

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    static GradientDrawable round(int color, float radiusPx) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radiusPx);
        return g;
    }

    static GradientDrawable roundStroke(int fill, int stroke, float radiusPx, int strokePx) {
        GradientDrawable g = round(fill, radiusPx);
        g.setStroke(strokePx, stroke);
        return g;
    }

    static GradientDrawable oval(int color) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(color);
        return g;
    }

    /** Fundo com efeito de toque (ripple). */
    static Drawable touch(Drawable content, float radiusPx) {
        return new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), content, round(0xFFFFFFFF, radiusPx));
    }

    static TextView text(Context c, String s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(Typeface.create(bold ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
        t.setIncludeFontPadding(false);
        return t;
    }

    static ImageView icon(Context c, int res, int tint, int sizeDp) {
        ImageView i = new ImageView(c);
        i.setImageResource(res);
        i.setImageTintList(ColorStateList.valueOf(tint));
        i.setLayoutParams(new LinearLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp)));
        return i;
    }

    /** Botão redondo só com ícone (barra superior). */
    static ImageView iconButton(Context c, int res, String description, View.OnClickListener l) {
        ImageView i = new ImageView(c);
        i.setImageResource(res);
        i.setImageTintList(ColorStateList.valueOf(TEXT));
        int p = dp(c, 10);
        i.setPadding(p, p, p, p);
        i.setBackground(touch(oval(CARD2), dp(c, 22)));
        i.setContentDescription(description);
        i.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(c, 44), dp(c, 44));
        lp.leftMargin = dp(c, 8);
        i.setLayoutParams(lp);
        return i;
    }

    static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    static LinearLayout col(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    static LinearLayout card(Context c) {
        LinearLayout l = col(c);
        l.setBackground(round(CARD, dp(c, 20)));
        int p = dp(c, 18);
        l.setPadding(p, p, p, p);
        return l;
    }

    static View divider(Context c) {
        View v = new View(c);
        v.setBackgroundColor(STROKE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, Math.max(1, dp(c, 1)));
        lp.topMargin = dp(c, 16);
        lp.bottomMargin = dp(c, 16);
        v.setLayoutParams(lp);
        return v;
    }

    static View spacer(Context c) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(0, 0, 1f));
        return v;
    }

    static LinearLayout.LayoutParams lp(int w, int h) { return new LinearLayout.LayoutParams(w, h); }

    static LinearLayout.LayoutParams lpTop(Context c, int w, int h, int topDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, h);
        lp.topMargin = dp(c, topDp);
        return lp;
    }

    /** Desenha atrás das barras do sistema e aplica o espaço delas como padding. */
    @SuppressWarnings("deprecation")
    static void edgeToEdge(Activity a, View root) {
        Window w = a.getWindow();
        w.setStatusBarColor(0);
        w.setNavigationBarColor(0);
        if (Build.VERSION.SDK_INT >= 30) {
            w.setDecorFitsSystemWindows(false);
        } else {
            root.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
        final int l = root.getPaddingLeft(), t = root.getPaddingTop(),
                r = root.getPaddingRight(), b = root.getPaddingBottom();
        root.setOnApplyWindowInsetsListener((v, in) -> {
            int it, ib, il, ir;
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets s = in.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                it = s.top; ib = s.bottom; il = s.left; ir = s.right;
            } else {
                it = in.getSystemWindowInsetTop(); ib = in.getSystemWindowInsetBottom();
                il = in.getSystemWindowInsetLeft(); ir = in.getSystemWindowInsetRight();
            }
            v.setPadding(l + il, t + it, r + ir, b + ib);
            return in;
        });
    }

    static String interval(int s) {
        if (s < 60) return s + " s";
        int m = s / 60, r = s % 60;
        if (m < 60) return r == 0 ? m + " min" : m + " min " + r + " s";
        int h = m / 60, rm = m % 60;
        return rm == 0 ? h + " h" : h + " h " + rm + " min";
    }

    static String duration(long sec) {
        long h = sec / 3600, m = (sec % 3600) / 60, s = sec % 60;
        if (h > 0) return String.format(java.util.Locale.US, "%d:%02d:%02d", h, m, s);
        return String.format(java.util.Locale.US, "%02d:%02d", m, s);
    }

    static String durationWords(long sec) {
        if (sec < 60) return sec + " s";
        long h = sec / 3600, m = (sec % 3600) / 60;
        if (h == 0) return m + " min";
        return m == 0 ? h + " h" : h + " h " + m + " min";
    }

    static String size(long mb) {
        if (mb < 1024) return mb + " MB";
        return String.format(java.util.Locale.US, "%.1f GB", mb / 1024.0);
    }
}
