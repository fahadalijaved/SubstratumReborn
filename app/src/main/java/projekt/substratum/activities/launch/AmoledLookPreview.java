/*
 * Copyright (c) 2026 SubstratumReborn
 * SPDX-License-Identifier: GPL-3.0-Or-Later
 */

package projekt.substratum.activities.launch;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.view.animation.PathInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.Map;

import projekt.substratum.R;
import projekt.substratum.common.platform.AmoledPreview;
import projekt.substratum.common.platform.RebornPlatform;

/**
 * A small Settings and quick settings mock, stock on the left and AMOLED on the right, drawn
 * from {@link AmoledPreview#roles}. A new look morphs in.
 */
public class AmoledLookPreview extends View {

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip = new Path();
    private final RectF rect = new RectF();
    private final float dp;
    private Map<String, int[]> from = Collections.emptyMap();
    private Map<String, int[]> to = Collections.emptyMap();
    private float progress = 1f;
    private ValueAnimator animator;
    private final String stockLabel;
    private final String amoledLabel;

    public AmoledLookPreview(Context context) {
        this(context, null);
    }

    public AmoledLookPreview(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        dp = getResources().getDisplayMetrics().density;
        stroke.setStyle(Paint.Style.STROKE);
        text.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        text.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 11,
                getResources().getDisplayMetrics()));
        stockLabel = context.getString(R.string.amoled_look_stock);
        amoledLabel = context.getString(R.string.amoled_look_amoled);
        setContentDescription(context.getString(R.string.amoled_look_preview_description,
                RebornPlatform.stockLook(context)));
    }

    /**
     * Show these colors; with {@code animate}, morph from what is shown.
     */
    public void setRoles(Map<String, int[]> roles, boolean animate) {
        if (animator != null) animator.cancel();
        boolean first = to.isEmpty();
        from = first || !animate ? roles : snapshot();
        to = roles;
        if (first || !animate) {
            progress = 1f;
            invalidate();
            return;
        }
        progress = 0f;
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(420);
        animator.setInterpolator(new PathInterpolator(0.2f, 0f, 0f, 1f));
        animator.addUpdateListener(a -> {
            progress = (float) a.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    private Map<String, int[]> snapshot() {
        Map<String, int[]> now = new java.util.HashMap<>();
        for (String role : to.keySet()) {
            now.put(role, new int[]{color(role, AmoledPreview.STOCK),
                    color(role, AmoledPreview.AMOLED)});
        }
        return now;
    }

    private int color(String role, int which) {
        int[] end = to.get(role);
        if (end == null) return 0;
        int[] start = from.get(role);
        if (start == null || progress >= 1f) return end[which];
        return lerp(start[which], end[which], progress);
    }

    private static int lerp(int a, int b, float t) {
        int aa = a >>> 24, ar = (a >> 16) & 0xff, ag = (a >> 8) & 0xff, ab = a & 0xff;
        int ba = b >>> 24, br = (b >> 16) & 0xff, bg = (b >> 8) & 0xff, bb = b & 0xff;
        return Math.round(aa + (ba - aa) * t) << 24 | Math.round(ar + (br - ar) * t) << 16 |
                Math.round(ag + (bg - ag) * t) << 8 | Math.round(ab + (bb - ab) * t);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        setMeasuredDimension(width, resolveSize(Math.round(204 * dp), heightMeasureSpec));
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        if (to.isEmpty()) return;
        float w = getWidth(), h = getHeight();
        clip.reset();
        rect.set(0, 0, w, h);
        clip.addRoundRect(rect, 22 * dp, 22 * dp, Path.Direction.CW);
        canvas.save();
        canvas.clipPath(clip);
        float half = w / 2f;
        half(canvas, 0, half, h, AmoledPreview.STOCK, stockLabel);
        half(canvas, half, w, h, AmoledPreview.AMOLED, amoledLabel);
        // The divider between before and after
        fill.setColor(0x66ffffff);
        canvas.drawRect(half - dp / 2, 0, half + dp / 2, h, fill);
        canvas.restore();
        // A hairline edge, so a black preview still reads as a frame on a black card
        stroke.setStrokeWidth(dp);
        stroke.setColor(0x33ffffff);
        rect.set(dp / 2, dp / 2, w - dp / 2, h - dp / 2);
        canvas.drawRoundRect(rect, 22 * dp, 22 * dp, stroke);
    }

    private void half(Canvas canvas, float left, float right, float h, int which, String label) {
        float pad = 12 * dp;
        fill.setColor(color(AmoledPreview.SETTINGS_PAGE, which));
        canvas.drawRect(left, 0, right, h, fill);
        float x0 = left + pad, x1 = right - pad;

        text.setColor(color(AmoledPreview.SETTINGS_SUMMARY, which));
        canvas.drawText(label, x0 + 2 * dp, pad + 10 * dp, text);

        // A Settings card: two rows, a switch, a divider, accent text and a popup
        float top = pad + 20 * dp, cardH = 78 * dp, cardW = x1 - x0;
        rect.set(x0, top, x1, top + cardH);
        fill.setColor(color(AmoledPreview.SETTINGS_CARD, which));
        canvas.drawRoundRect(rect, 14 * dp, 14 * dp, fill);
        float rowX = x0 + 12 * dp;
        float row1 = top + 14 * dp;
        bar(canvas, rowX, row1, cardW * 0.40f, 7 * dp, color(AmoledPreview.SETTINGS_TITLE, which));
        bar(canvas, rowX, row1 + 12 * dp, cardW * 0.28f, 5 * dp,
                color(AmoledPreview.SETTINGS_SUMMARY, which));
        float trackW = 28 * dp, trackH = 16 * dp, trackX = x1 - 12 * dp - trackW,
                trackY = row1 + 1 * dp;
        rect.set(trackX, trackY, trackX + trackW, trackY + trackH);
        fill.setColor(color(AmoledPreview.SETTINGS_SWITCH_ON, which));
        canvas.drawRoundRect(rect, trackH / 2, trackH / 2, fill);
        fill.setColor(0xfffcfcff);
        canvas.drawCircle(trackX + trackW - trackH / 2, trackY + trackH / 2, trackH / 2 - 3 * dp,
                fill);
        fill.setColor(color(AmoledPreview.SETTINGS_DIVIDER, which));
        canvas.drawRect(rowX, top + cardH / 2, x1 - 12 * dp, top + cardH / 2 + dp, fill);
        float row2 = top + cardH / 2 + 13 * dp;
        bar(canvas, rowX, row2, cardW * 0.36f, 7 * dp, color(AmoledPreview.SETTINGS_TITLE, which));
        bar(canvas, rowX, row2 + 12 * dp, cardW * 0.22f, 5 * dp,
                color(AmoledPreview.SETTINGS_ACCENT, which));
        // A popup menu over the card's corner
        float popW = Math.min(52 * dp, cardW * 0.34f), popH = 34 * dp;
        float popX = x1 - 8 * dp - popW, popY = top + cardH - popH - 6 * dp;
        rect.set(popX, popY, popX + popW, popY + popH);
        fill.setColor(color(AmoledPreview.POPUP_BG, which));
        canvas.drawRoundRect(rect, 10 * dp, 10 * dp, fill);
        stroke.setStrokeWidth(dp);
        stroke.setColor(color(AmoledPreview.POPUP_OUTLINE, which));
        canvas.drawRoundRect(rect, 10 * dp, 10 * dp, stroke);
        bar(canvas, popX + 8 * dp, popY + 9 * dp, popW * 0.55f, 5 * dp,
                color(AmoledPreview.SETTINGS_TITLE, which));
        bar(canvas, popX + 8 * dp, popY + 20 * dp, popW * 0.40f, 5 * dp,
                color(AmoledPreview.SETTINGS_SUMMARY, which));

        // Quick settings: a panel with an active and an inactive tile and the brightness bar
        float qsTop = top + cardH + 10 * dp, qsBottom = h - pad;
        rect.set(x0, qsTop, x1, qsBottom);
        fill.setColor(color(AmoledPreview.QS_PANEL, which));
        canvas.drawRoundRect(rect, 18 * dp, 18 * dp, fill);
        float cy = (qsTop + qsBottom) / 2f, r = Math.min(13 * dp, (qsBottom - qsTop) / 2 - 6 * dp);
        float cx = x0 + 10 * dp + r;
        tile(canvas, cx, cy, r, color(AmoledPreview.QS_TILE_ON, which),
                color(AmoledPreview.QS_TILE_ICON_ON, which));
        float cx2 = cx + 2 * r + 8 * dp;
        tile(canvas, cx2, cy, r, color(AmoledPreview.QS_TILE_OFF, which),
                color(AmoledPreview.QS_TILE_ICON_OFF, which));
        float barX = cx2 + r + 10 * dp, barX1 = x1 - 10 * dp, barH = 12 * dp;
        if (barX1 - barX > 16 * dp) {
            rect.set(barX, cy - barH / 2, barX1, cy + barH / 2);
            fill.setColor(color(AmoledPreview.QS_TRACK, which));
            canvas.drawRoundRect(rect, barH / 2, barH / 2, fill);
            rect.set(barX, cy - barH / 2, barX + (barX1 - barX) * 0.62f, cy + barH / 2);
            fill.setColor(color(AmoledPreview.QS_FILL, which));
            canvas.drawRoundRect(rect, barH / 2, barH / 2, fill);
        }
    }

    private void tile(Canvas canvas, float cx, float cy, float r, int background, int icon) {
        fill.setColor(background);
        canvas.drawCircle(cx, cy, r, fill);
        fill.setColor(icon);
        canvas.drawCircle(cx, cy, r * 0.36f, fill);
    }

    private void bar(Canvas canvas, float x, float y, float width, float height, int color) {
        fill.setColor(color);
        rect.set(x, y, x + width, y + height);
        canvas.drawRoundRect(rect, height / 2, height / 2, fill);
    }

    @Override
    protected void onDetachedFromWindow() {
        if (animator != null) animator.cancel();
        super.onDetachedFromWindow();
    }
}
