package com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.misc;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.view.animation.LinearInterpolator;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * Same busy spinner as the Kodi skin (DialogBusy.xml): two 8 segments rings (80 and 50 px on a 1080p screen)
 * turning in opposite directions by 45 degrees steps: 600 ms pause, then 200 ms rotation, looped.
 * The ring is symmetric every 45 degrees so a step looks continuous.
 */
public class KodiSpinnerDrawable extends Drawable implements Animatable {
    private static final long CYCLE_MS = 800;
    private static final long PAUSE_MS = 600;
    private static final long ROTATION_MS = CYCLE_MS - PAUSE_MS;
    private static final float STEP_DEGREES = 45;
    private static final float INNER_RATIO = 50f / 80f;
    private final Bitmap mBitmap;
    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF mDest = new RectF();
    private final ValueAnimator mAnimator;
    private float mAngle;

    /**
     * Spinner with the Kodi color (see R.color.kodi_spinner)
     */
    public static KodiSpinnerDrawable create(Context context) {
        return new KodiSpinnerDrawable(context, ContextCompat.getColor(context, R.color.kodi_spinner));
    }

    /**
     * Kodi size of the big ring: 80 px on a 1080p screen
     */
    public static int getDefaultSize(Context context) {
        return Math.round(80f * context.getResources().getDisplayMetrics().heightPixels / 1080f);
    }

    public KodiSpinnerDrawable(Context context, int color) {
        mBitmap = BitmapFactory.decodeResource(context.getResources(), R.drawable.kodi_spinner); // drawable-nodpi: no scaling
        // Same as Kodi colordiffuse: the color (with its alpha) replaces the white of the ring
        mPaint.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));

        mAnimator = ValueAnimator.ofFloat(0f, 1f);
        mAnimator.setDuration(CYCLE_MS);
        mAnimator.setInterpolator(new LinearInterpolator());
        mAnimator.setRepeatCount(ValueAnimator.INFINITE);
        mAnimator.addUpdateListener(animation -> {
            mAngle = getAngle(animation.getAnimatedFraction());
            invalidateSelf();
        });
    }

    private static float getAngle(float cycleFraction) {
        float timeMs = cycleFraction * CYCLE_MS;

        if (timeMs <= PAUSE_MS) {
            return 0;
        }

        return (timeMs - PAUSE_MS) / ROTATION_MS * STEP_DEGREES;
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        if (mBitmap == null) {
            return;
        }

        float centerX = getBounds().exactCenterX();
        float centerY = getBounds().exactCenterY();
        float outerSize = Math.min(getBounds().width(), getBounds().height());

        // Big ring: counterclockwise
        drawRing(canvas, centerX, centerY, outerSize, -mAngle);
        // Small ring: clockwise (the Kodi skin flips the picture, same look as the ring is symmetric)
        drawRing(canvas, centerX, centerY, outerSize * INNER_RATIO, mAngle);
    }

    private void drawRing(Canvas canvas, float centerX, float centerY, float size, float angle) {
        mDest.set(centerX - size / 2, centerY - size / 2, centerX + size / 2, centerY + size / 2);
        canvas.save();
        canvas.rotate(angle, centerX, centerY);
        canvas.drawBitmap(mBitmap, null, mDest, mPaint);
        canvas.restore();
    }

    @Override
    public void start() {
        if (!mAnimator.isStarted()) {
            mAnimator.start();
        }
    }

    @Override
    public void stop() {
        mAnimator.cancel();
        mAngle = 0;
    }

    @Override
    public boolean isRunning() {
        return mAnimator.isRunning();
    }

    @Override
    public void setAlpha(int alpha) {
        mPaint.setAlpha(alpha);
        invalidateSelf();
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        // Color is fixed (see constructor)
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
