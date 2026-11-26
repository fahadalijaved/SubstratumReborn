/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */

package projekt.substratum.activities.launch;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.SweepGradient;
import android.view.View;
import android.view.animation.OvershootInterpolator;

import androidx.annotation.NonNull;

import com.google.android.material.color.MaterialColors;

/**
 * One accent choice: a filled circle (struck through for "none", a color wheel for a custom color
 * not picked yet), with a ring that springs in when it is the chosen one.
 */
public class AmoledSwatch extends View {

    public enum Kind {NONE, COLOR, PICKER}

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float dp;
    private Kind kind = Kind.COLOR;
    private int color;
    private boolean chosen;
    private float ring; // 0 to 1
    private ValueAnimator animator;
    private SweepGradient wheel;

    public AmoledSwatch(Context context) {
        super(context);
        dp = getResources().getDisplayMetrics().density;
        setClickable(true);
        setFocusable(true);
    }

    public void setSwatch(Kind kind, int color) {
        this.kind = kind;
        this.color = color;
        invalidate();
    }

    public void setChosen(boolean chosen, boolean animate) {
        if (this.chosen == chosen && animator == null) return;
        this.chosen = chosen;
        setSelected(chosen);
        if (animator != null) animator.cancel();
        float target = chosen ? 1f : 0f;
        if (!animate) {
            ring = target;
            invalidate();
            return;
        }
        animator = ValueAnimator.ofFloat(ring, target);
        animator.setDuration(chosen ? 360 : 160);
        animator.setInterpolator(new OvershootInterpolator(chosen ? 2.2f : 0f));
        animator.addUpdateListener(a -> {
            ring = (float) a.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int size = Math.round(46 * dp);
        setMeasuredDimension(resolveSize(size, widthMeasureSpec), resolveSize(size, heightMeasureSpec));
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        float outer = Math.min(cx, cy) - dp;
        // The chosen one: the swatch shrinks inside a ring
        float inner = outer - (3 * dp + 2.5f * dp) * Math.max(0f, Math.min(1.2f, ring));
        if (ring > 0.01f) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2.5f * dp);
            paint.setShader(null);
            int onSurface = MaterialColors.getColor(this,
                    com.google.android.material.R.attr.colorOnSurface, Color.WHITE);
            paint.setColor(onSurface);
            paint.setAlpha(Math.round(255 * Math.min(1f, ring)));
            canvas.drawCircle(cx, cy, outer - 1.25f * dp, paint);
        }
        paint.setStyle(Paint.Style.FILL);
        paint.setAlpha(255);
        switch (kind) {
            case NONE:
                paint.setShader(null);
                paint.setColor(0xff1e1e1e);
                canvas.drawCircle(cx, cy, inner, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(2 * dp);
                paint.setColor(0xff8a8a8a);
                float d = inner * 0.62f;
                canvas.drawLine(cx - d, cy + d, cx + d, cy - d, paint);
                break;
            case PICKER:
                if (wheel == null) {
                    wheel = new SweepGradient(cx, cy, new int[]{0xffff3b30, 0xffffcc00,
                            0xff34c759, 0xff00c7be, 0xff007aff, 0xffaf52de, 0xffff3b30}, null);
                }
                paint.setShader(wheel);
                canvas.drawCircle(cx, cy, inner, paint);
                paint.setShader(null);
                break;
            case COLOR:
            default:
                paint.setShader(null);
                paint.setColor(color | 0xff000000);
                canvas.drawCircle(cx, cy, inner, paint);
                break;
        }
        // A hairline, so black and white swatches keep an edge on black and white cards
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp);
        paint.setColor(0x33808080);
        canvas.drawCircle(cx, cy, inner - dp / 2, paint);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        wheel = null;
    }

    @Override
    protected void onDetachedFromWindow() {
        if (animator != null) animator.cancel();
        super.onDetachedFromWindow();
    }
}
