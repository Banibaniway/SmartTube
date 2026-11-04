package com.liskovsoft.smartyoutubetv2.tv.ui.common;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.BitmapDrawable;
import android.os.Environment;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build.VERSION;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.leanback.app.BackgroundManager;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.SimpleTarget;
import com.bumptech.glide.request.transition.Transition;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.playback.PlaybackActivity;
import com.liskovsoft.smartyoutubetv2.tv.util.ViewUtil;

import java.io.File;

public class UriBackgroundManager {
    private static final String TAG = UriBackgroundManager.class.getSimpleName();
    private static final int BACKGROUND_UPDATE_DELAY_MS = 300;
    private static final String CUSTOM_BACKGROUND_PATH = "data/smarttube_background.jpg";
    // Multiplied with the background image (Kodi scheme)
    private static final int BACKGROUND_DARKEN_COLOR = 0xFF5A5A5A;
    private static final String PREFS_NAME = "kodi_theme_prefs";
    private static final String KEY_STORAGE_ASKED = "storage_permission_asked";
    private static final int STORAGE_PERMISSION_REQUEST_CODE = 9731;
    // Kodi scheme: backgrounds are prepared once per process and shared by all the screens (main thread only)
    private static Bitmap sCustomBackground;
    private static String sCustomBackgroundKey;
    private static Bitmap sEmbeddedSource;
    private static Bitmap sEmbeddedDarkened;
    private Uri mBackgroundURI;
    private Drawable mDefaultBackground;
    // Custom background (file copied by Kodi) used by mDefaultBackground, null if none
    private Bitmap mCustomBackground;
    private DisplayMetrics mMetrics;
    private Runnable mBackgroundTask;
    private BackgroundManager mBackgroundManager;
    private final Activity mActivity;
    private final Handler mHandler;
    private int mBackgroundColor = -1;

    public UriBackgroundManager(Activity activity) {
        mActivity = activity;
        mHandler = new Handler();
        prepareBackgroundManager();
        setDefaultBackground();
    }

    private void prepareBackgroundManager() {
        mBackgroundManager = BackgroundManager.getInstance(mActivity);
        mBackgroundManager.attach(mActivity.getWindow());
        mDefaultBackground = createDefaultBackground();
        mBackgroundTask = new UpdateBackgroundTask();
        mMetrics = new DisplayMetrics();
        mActivity.getWindowManager().getDefaultDisplay().getMetrics(mMetrics);
    }

    private Drawable createDefaultBackground() {
        Drawable result = ContextCompat.getDrawable(mActivity, Helpers.getThemeAttr(mActivity, R.attr.shelfBackground));
        mCustomBackground = null;
        // Kodi scheme, except the player (keeps the original background)
        if (isKodiScheme() && !(mActivity instanceof PlaybackActivity)) {
            Bitmap custom = getCustomBackground();
            if (custom != null) {
                mCustomBackground = custom;
                result = new BitmapDrawable(mActivity.getResources(), custom);
            } else if (result instanceof BitmapDrawable) {
                // Embedded background of the Kodi scheme
                Bitmap darkened = getEmbeddedBackground(((BitmapDrawable) result).getBitmap());
                if (darkened != null) {
                    result = new BitmapDrawable(mActivity.getResources(), darkened);
                }
            }
        }
        return result;
    }

    /**
     * Kodi theme only: the app may stay alive between two launches, so the background file
     * (copied by Kodi) can change while the screen already exists.<br/>
     * Unchanged file (path, date, size): nothing is decoded again.
     */
    private void refreshCustomBackground() {
        if (!isKodiScheme() || mActivity instanceof PlaybackActivity) {
            return;
        }

        try {
            Bitmap custom = getCustomBackground();

            if (custom != mCustomBackground) {
                mDefaultBackground = createDefaultBackground();
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    /**
     * Kodi theme only: user supplied background (e.g. copied there by Kodi).<br/>
     * Falls back to the theme background when the file is missing or unreadable.
     */
    private Bitmap getCustomBackground() {
        try {
            requestStoragePermissionOnce();

            File file = new File(Environment.getExternalStorageDirectory(), CUSTOM_BACKGROUND_PATH);

            if (!file.isFile() || !file.canRead()) {
                return null;
            }

            // Same file (path, date, size): reuse the prepared background
            String key = file.getAbsolutePath() + ":" + file.lastModified() + ":" + file.length();

            if (sCustomBackground != null && key.equals(sCustomBackgroundKey)) {
                return sCustomBackground;
            }

            Bitmap result = decodeCustomBackground(file);

            if (result != null) {
                sCustomBackground = result;
                sCustomBackgroundKey = key;
            }

            return result;
        } catch (Throwable e) { // incl. OutOfMemoryError
            return null;
        }
    }

    /**
     * Decode at (about) the screen size, then crop and darken.
     */
    private Bitmap decodeCustomBackground(File file) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);

        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return null;
        }

        DisplayMetrics metrics = new DisplayMetrics();
        mActivity.getWindowManager().getDefaultDisplay().getMetrics(metrics);
        int screenWidth = Math.max(metrics.widthPixels, metrics.heightPixels);
        int screenHeight = Math.min(metrics.widthPixels, metrics.heightPixels);

        // Skip the useless pixels of the big images (keeps the image at least as big as the screen)
        int sampleSize = 1;
        while (screenWidth > 0 && screenHeight > 0 &&
                bounds.outWidth / (sampleSize * 2) >= screenWidth && bounds.outHeight / (sampleSize * 2) >= screenHeight) {
            sampleSize *= 2;
        }

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        options.inSampleSize = sampleSize;
        Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), options);

        if (bitmap == null) {
            return null;
        }

        Bitmap cropped = scaleAndCropToScreen(bitmap);
        Bitmap darkened = darken(cropped);

        if (darkened != cropped) {
            cropped.recycle();
        }

        return darkened;
    }

    private Bitmap getEmbeddedBackground(Bitmap source) {
        if (source == null) {
            return null;
        }

        if (source != sEmbeddedSource || sEmbeddedDarkened == null) {
            sEmbeddedDarkened = darken(source);
            sEmbeddedSource = source;
        }

        return sEmbeddedDarkened;
    }

    private boolean isKodiScheme() {
        return MainUIData.instance(mActivity).isKodiScheme();
    }

    /**
     * Darken the background: multiply every pixel by the color (same as colordiffuse in Kodi).
     */
    private Bitmap darken(Bitmap src) {
        try {
            Bitmap result = Bitmap.createBitmap(src.getWidth(), src.getHeight(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(result);
            Paint paint = new Paint();
            paint.setColorFilter(new PorterDuffColorFilter(BACKGROUND_DARKEN_COLOR, PorterDuff.Mode.MULTIPLY));
            canvas.drawBitmap(src, 0, 0, paint);
            return result;
        } catch (Throwable e) { // incl. OutOfMemoryError
            return src;
        }
    }

    /**
     * Scale (keeping the aspect ratio) so the image covers the whole screen, then crop the excess in the center.<br/>
     * The source file is never modified.
     */
    private Bitmap scaleAndCropToScreen(Bitmap src) {
        DisplayMetrics metrics = new DisplayMetrics();
        mActivity.getWindowManager().getDefaultDisplay().getMetrics(metrics);
        int screenWidth = Math.max(metrics.widthPixels, metrics.heightPixels);
        int screenHeight = Math.min(metrics.widthPixels, metrics.heightPixels);

        if (src.getWidth() <= 0 || src.getHeight() <= 0 || screenWidth <= 0 || screenHeight <= 0) {
            return src;
        }

        float scale = Math.max((float) screenWidth / src.getWidth(), (float) screenHeight / src.getHeight());
        int scaledWidth = Math.max(screenWidth, Math.round(src.getWidth() * scale));
        int scaledHeight = Math.max(screenHeight, Math.round(src.getHeight() * scale));

        Bitmap scaled = Bitmap.createScaledBitmap(src, scaledWidth, scaledHeight, true);

        if (scaled != src) {
            src.recycle();
        }

        int x = (scaled.getWidth() - screenWidth) / 2;
        int y = (scaled.getHeight() - screenHeight) / 2;

        Bitmap cropped = Bitmap.createBitmap(scaled, x, y, screenWidth, screenHeight);

        if (cropped != scaled) {
            scaled.recycle();
        }

        return cropped;
    }

    /**
     * Ask for the storage access once (Kodi theme only). If refused, the embedded background is used.
     */
    private void requestStoragePermissionOnce() {
        if (VERSION.SDK_INT < 23 || mActivity.isFinishing()) {
            return;
        }

        if (ContextCompat.checkSelfPermission(mActivity, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            return;
        }

        SharedPreferences prefs = mActivity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        if (prefs.getBoolean(KEY_STORAGE_ASKED, false)) {
            return;
        }

        prefs.edit().putBoolean(KEY_STORAGE_ASKED, true).apply();
        ActivityCompat.requestPermissions(mActivity, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, STORAGE_PERMISSION_REQUEST_CODE);
    }

    private void startBackgroundTimer(Uri backgroundURI) {
        mBackgroundURI = backgroundURI;
        mHandler.removeCallbacks(mBackgroundTask);
        mHandler.postDelayed(mBackgroundTask, BACKGROUND_UPDATE_DELAY_MS);
    }

    public void startBackgroundTimer(String bgImageUrl) {
        if (bgImageUrl != null) {
            startBackgroundTimer(Uri.parse(bgImageUrl));
        }
    }

    public void setBackgroundFrom(Video item) {
        // Selectively change background picture
    }

    public void onStart() {
        refreshCustomBackground();

        if (mBackgroundURI != null) {
            showBackground(mBackgroundURI.toString());
        } else if (mBackgroundColor != -1) {
            setBackgroundColor(mBackgroundColor);
        } else {
            setDefaultBackground();
        }
    }

    public void onStop() {
        mBackgroundManager.release();
    }

    public void onDestroy() {
        mHandler.removeCallbacks(mBackgroundTask);
        mBackgroundManager = null;
    }

    public void removeBackground() {
        mBackgroundManager.setDrawable(null);
    }

    public void setDefaultBackground() {
        mBackgroundManager.setDrawable(mDefaultBackground);
    }

    public void setBackgroundColor(int color) {
        mBackgroundColor = color;
        mBackgroundManager.setColor(color);
    }

    private class UpdateBackgroundTask implements Runnable {
        @Override
        public void run() {
            if (mBackgroundURI != null) {
                showBackground(mBackgroundURI.toString());
            }
        }
    }

    public BackgroundManager getBackgroundManager() {
        return mBackgroundManager;
    }

    public void showBackground(String uri) {
        View videoView = mActivity.findViewById(R.id.video_surface);

        if (videoView != null) {
            videoView.setVisibility(uri == null ? View.VISIBLE : View.INVISIBLE);
        }

        if (uri == null) {
            removeBackground();
            return;
        }

        int width = mMetrics.widthPixels;
        int height = mMetrics.heightPixels;

        RequestOptions options = ViewUtil.glideOptions()
                .centerCrop()
                .error(mDefaultBackground);

        Glide.with(mActivity)
                .asBitmap()
                .load(uri)
                .apply(options)
                .into(new SimpleTarget<Bitmap>(width, height) {
                    @Override
                    public void onResourceReady(
                            @NonNull Bitmap resource,
                            Transition<? super Bitmap> transition) {
                        mBackgroundManager.setBitmap(resource);
                    }
                });
    }

    public void showBackgroundColor(int colorResId) {
        View videoView = mActivity.findViewById(R.id.video_surface);

        if (videoView != null) {
            videoView.setVisibility(colorResId == -1 ? View.VISIBLE : View.INVISIBLE);
        }

        if (colorResId == -1) {
            removeBackground();
            return;
        }

        if (mBackgroundManager != null) {
            mBackgroundManager.setColor(ContextCompat.getColor(mActivity, colorResId));
        }
    }
}
