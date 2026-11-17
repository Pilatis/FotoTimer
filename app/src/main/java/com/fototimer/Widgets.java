package com.fototimer;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Componentes visuais próprios (sem bibliotecas externas). */
final class Widgets {
    private Widgets() {}

    interface IntListener { void on(int v); }
}

/** Anel de progresso (contagem regressiva até a próxima foto). */
class RingView extends View {
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private float progress = 0f;

    RingView(Context c) {
        super(c);
        float w = Ui.dp(c, 12);
        track.setStyle(Paint.Style.STROKE);
        track.setStrokeWidth(w);
        track.setColor(Ui.CARD2);
        arc.setStyle(Paint.Style.STROKE);
        arc.setStrokeWidth(w);
        arc.setStrokeCap(Paint.Cap.ROUND);
        arc.setColor(Ui.ACCENT);
    }

    void set(float p, int color) {
        progress = Math.max(0f, Math.min(1f, p));
        arc.setColor(color);
        invalidate();
    }

    @Override protected void onMeasure(int w, int h) {
        int s = Math.min(MeasureSpec.getSize(w), MeasureSpec.getSize(h));
        setMeasuredDimension(s, s);
    }

    @Override protected void onDraw(Canvas c) {
        float half = track.getStrokeWidth() / 2f;
        rect.set(half, half, getWidth() - half, getHeight() - half);
        c.drawArc(rect, 0, 360, false, track);
        if (progress > 0.001f) c.drawArc(rect, -90, 360 * progress, false, arc);
    }
}

/** Controle segmentado (ex.: Traseira | Frontal). */
class Segmented extends LinearLayout {
    private final TextView[] items;
    private int selected = 0;
    private Widgets.IntListener listener;

    Segmented(Context c, String... labels) {
        super(c);
        setOrientation(HORIZONTAL);
        int p = Ui.dp(c, 4);
        setPadding(p, p, p, p);
        setBackground(Ui.round(Ui.CARD2, Ui.dp(c, 14)));
        items = new TextView[labels.length];
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            TextView t = Ui.text(c, labels[i], 14, Ui.TEXT2, true);
            t.setGravity(Gravity.CENTER);
            t.setLayoutParams(new LayoutParams(0, Ui.dp(c, 40), 1f));
            t.setOnClickListener(v -> {
                if (!isEnabled()) return;
                v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                select(idx);
                if (listener != null) listener.on(idx);
            });
            items[i] = t;
            addView(t);
        }
        select(0);
    }

    void setListener(Widgets.IntListener l) { listener = l; }
    int selected() { return selected; }

    void select(int idx) {
        selected = idx;
        for (int i = 0; i < items.length; i++) {
            boolean on = i == idx;
            items[i].setTextColor(on ? 0xFFFFFFFF : Ui.TEXT2);
            items[i].setBackground(on ? Ui.round(Ui.ACCENT, Ui.dp(getContext(), 11)) : null);
        }
    }
}

/** Seletor numérico: [ – ]  valor  [ + ], segurar repete. Tocar no valor permite digitar. */
class Stepper extends LinearLayout {
    interface Formatter { String fmt(int v); }
    interface StepFn { int next(int v, int dir); }

    private final TextView value;
    private int current, min, max;
    private final Formatter formatter;
    private final StepFn step;
    private Widgets.IntListener listener;
    private final Handler h = new Handler(Looper.getMainLooper());

    Stepper(Context c, int min, int max, Formatter f, StepFn step) {
        super(c);
        this.min = min; this.max = max; this.formatter = f; this.step = step;
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        addView(button(c, "−", -1));
        value = Ui.text(c, "", 20, Ui.TEXT, true);
        value.setGravity(Gravity.CENTER);
        value.setMinWidth(Ui.dp(c, 96));
        value.setPadding(Ui.dp(c, 8), Ui.dp(c, 8), Ui.dp(c, 8), Ui.dp(c, 8));
        value.setBackground(Ui.touch(Ui.round(0, Ui.dp(c, 10)), Ui.dp(c, 10)));
        addView(value);
        addView(button(c, "+", +1));
    }

    void setListener(Widgets.IntListener l) { listener = l; }
    void setOnValueClick(OnClickListener l) { value.setOnClickListener(v -> { if (isEnabled()) l.onClick(v); }); }
    int get() { return current; }

    void set(int v) {
        current = Math.max(min, Math.min(max, v));
        value.setText(formatter.fmt(current));
    }

    private void bump(int dir) {
        int before = current;
        set(step.next(current, dir));
        if (current != before) {
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            if (listener != null) listener.on(current);
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private TextView button(Context c, String label, int dir) {
        TextView b = Ui.text(c, label, 22, Ui.TEXT, false);
        b.setGravity(Gravity.CENTER);
        b.setBackground(Ui.touch(Ui.oval(Ui.CARD2), Ui.dp(c, 22)));
        b.setLayoutParams(new LayoutParams(Ui.dp(c, 44), Ui.dp(c, 44)));
        b.setContentDescription(dir > 0 ? "Aumentar" : "Diminuir");
        final Runnable[] repeat = new Runnable[1];
        repeat[0] = () -> { bump(dir); h.postDelayed(repeat[0], 70); };
        b.setOnTouchListener((v, e) -> {
            if (!isEnabled()) return true;
            switch (e.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    v.setPressed(true);
                    bump(dir);
                    h.postDelayed(repeat[0], 450);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.setPressed(false);
                    h.removeCallbacks(repeat[0]);
                    return true;
            }
            return true;
        });
        return b;
    }
}

/** Linha de atalhos rápidos (chips). */
class ChipRow extends HorizontalScrollView {
    private final LinearLayout inner;
    private final int[] values;
    private final TextView[] chips;

    ChipRow(Context c, int[] values, String[] labels, Widgets.IntListener l) {
        super(c);
        setHorizontalScrollBarEnabled(false);
        setOverScrollMode(OVER_SCROLL_NEVER);
        this.values = values;
        inner = Ui.row(c);
        addView(inner);
        chips = new TextView[values.length];
        for (int i = 0; i < values.length; i++) {
            final int v = values[i];
            TextView t = Ui.text(c, labels[i], 13, Ui.TEXT2, true);
            t.setGravity(Gravity.CENTER);
            int ph = Ui.dp(c, 14);
            t.setPadding(ph, 0, ph, 0);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, Ui.dp(c, 34));
            lp.rightMargin = Ui.dp(c, 8);
            t.setLayoutParams(lp);
            t.setOnClickListener(x -> {
                if (!isEnabled()) return;
                x.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                l.on(v);
            });
            chips[i] = t;
            inner.addView(t);
        }
    }

    void highlight(int current) {
        Context c = getContext();
        for (int i = 0; i < values.length; i++) {
            boolean on = values[i] == current;
            chips[i].setTextColor(on ? Ui.ACCENT : Ui.TEXT2);
            chips[i].setBackground(Ui.touch(on
                    ? Ui.roundStroke(Ui.ACCENT_SOFT, Ui.ACCENT, Ui.dp(c, 17), Ui.dp(c, 1))
                    : Ui.roundStroke(0, Ui.STROKE, Ui.dp(c, 17), Ui.dp(c, 1)), Ui.dp(c, 17)));
        }
    }

    @Override public void setEnabled(boolean e) {
        super.setEnabled(e);
        for (TextView t : chips) t.setEnabled(e);
    }
}

/** FrameLayout sempre quadrado (miniaturas da galeria). */
class SquareFrame extends FrameLayout {
    SquareFrame(Context c) { super(c); }
    @Override protected void onMeasure(int w, int h) { super.onMeasure(w, w); }
}
