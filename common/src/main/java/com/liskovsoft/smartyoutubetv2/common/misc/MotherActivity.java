package com.liskovsoft.smartyoutubetv2.common.misc;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.os.Build.VERSION;
import android.os.Bundle;
import android.graphics.Rect;
import android.util.DisplayMetrics;
import android.view.KeyCharacterMap.UnavailableException;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.MotionEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import com.liskovsoft.sharedutils.helpers.Helpers;
import com.liskovsoft.sharedutils.helpers.KeyHelpers;
import com.liskovsoft.sharedutils.locale.LocaleContextWrapper;
import com.liskovsoft.sharedutils.locale.LocaleUpdater;
import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.R;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerTweaksData;
import com.liskovsoft.smartyoutubetv2.common.utils.Utils;
import com.liskovsoft.youtubeapi.service.internal.MediaServiceData;
import com.r0adkll.slidr.Slidr;
import com.r0adkll.slidr.model.SlidrConfig;
import com.r0adkll.slidr.model.SlidrListener;
import com.r0adkll.slidr.model.SlidrPosition;

import java.util.ArrayList;
import java.util.List;

public class MotherActivity extends FragmentActivity {
    private static final String TAG = MotherActivity.class.getSimpleName();
    private static final float DEFAULT_DENSITY = 2.0f; // xhdpi
    private static final float DEFAULT_WIDTH = 1920f; // xhdpi
    private static DisplayMetrics sCachedDisplayMetrics;
    // Caches computed from the scale (e.g. card sizes): cleared every time the scale is forgotten (app exit)
    private static final java.util.Set<Runnable> sOnMetricsInvalidated = new java.util.concurrent.CopyOnWriteArraySet<>();
    protected static boolean sIsInPipMode;
    private ScreensaverManager mScreensaverManager;
    // Make static in case Don't keep activities enabled in Developer settings
    private static List<OnPermissions> mOnPermissions;
    private static List<OnResult> mOnResults;
    private long mLastKeyDownTime;
    private boolean mEnableThrottleKeyDown;
    private boolean mIsOculusQuestFixEnabled;
    private boolean mIsFullscreenModeEnabled;
    private boolean mIsBackPressed;

    public interface OnPermissions {
        void onPermissions(int requestCode, String[] permissions, int[] grantResults);
    }

    public interface OnResult {
        void onResult(int requestCode, int resultCode, Intent data);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        // Fixing: Only fullscreen opaque activities can request orientation (api 26)
        // NOTE: You should remove 'screenOrientation' from the manifest.
        // NOTE: Possible side effect: initDpi() won't work: "When you setRequestedOrientation() the view may be restarted"
        //if (VERSION.SDK_INT != 26) {
        //    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        //}
        super.onCreate(savedInstanceState);

        Log.d(TAG, "Starting %s...", this.getClass().getSimpleName());

        mIsOculusQuestFixEnabled = PlayerTweaksData.instance(this).isOculusQuestFixEnabled();
        mIsFullscreenModeEnabled = GeneralData.instance(this).isFullscreenModeEnabled();

        initDpi();

        if (isUiSoundsEnabled()) {
            UiSounds.init(this);
        }
        initTheme();

        // Search Fullscreen routine inside onPause() method
        if (!mIsFullscreenModeEnabled) {
            // There's no way to do this programmatically!
            setTheme(R.style.FitSystemWindows);

            // totally disabling the translucency or any color placed on the status bar and navigation bar
            //if (Build.VERSION.SDK_INT >= 19) {
            //    Window w = getWindow();
            //    w.setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
            //}
        }

        if (mIsOculusQuestFixEnabled && VERSION.SDK_INT != 26) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        }

        mScreensaverManager = new ScreensaverManager(this); // moved below the theme to fix side effects

        //Helpers.addFullscreenListener(this);

        initEdgeSlide();
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            mScreensaverManager.enable();
        }

        return super.dispatchGenericMotionEvent(event);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            mScreensaverManager.enable();
        }

        try {
            return super.dispatchTouchEvent(event);
        } catch (NullPointerException | SecurityException | IllegalStateException | ArrayIndexOutOfBoundsException e) {
            // Attempt to invoke interface method 'boolean android.app.trust.ITrustManager.isDeviceLocked(int)' on a null object reference
            // Permission Denial: starting Intent
            // IllegalStateException: exitFreeformMode: You can only go fullscreen from freeform.
            e.printStackTrace();
            return false;
        }
    }

    @SuppressLint("RestrictedApi")
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event == null) { // handled
            return true;
        }

        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            boolean isKeepScreenOff = mScreensaverManager.isScreenOff() && Helpers.equalsAny(event.getKeyCode(),
                    new int[]{KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN});
            if (!isKeepScreenOff) {
                mScreensaverManager.enable();
            }
        }

        playKeySound(event);

        try {
            boolean handled = super.dispatchKeyEvent(event);

            // Kodi scheme: do the focus move ourselves (the system would play its navigation sound)
            return handled || moveFocusSilently(event);
        } catch (NullPointerException | IllegalArgumentException | IllegalStateException | SecurityException | UnavailableException e) {
            // NullPointerException: 'android.view.Window androidx.core.app.ComponentActivity.getWindow()' on a null object reference
            // IllegalArgumentException: View is not a direct child of HorizontalGridView
            // Fatal Exception: java.lang.IllegalStateException
            // android.permission.RECORD_AUDIO required for search (Android 5 mostly)
            // Fatal Exception: java.lang.SecurityException
            // Not allowed to bind to service Intent { act=android.speech.RecognitionService cmp=com.xgimi.duertts/com.baidu.duer.services.tvser
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MEDIA_STOP) { // shortcut for closing PIP
            PlaybackPresenter.instance(this).forceFinish();
            return true;
        }

        boolean result = super.onKeyDown(keyCode, event);

        // Fix buggy G20s menu key (focus lost on key press)
        return KeyHelpers.isMenuKey(keyCode) || throttleKeyDown(keyCode) || result;
    }

    /**
     * Kodi scheme: no Android navigation sound.<br/>
     * The system plays it when it moves the focus by a D-pad key that no view has handled.
     * Do the same steps as the system here (focus search, request focus, else unhandled move), but without the sound.<br/>
     * The key is consumed once the focus search is done: the system must not search again
     * (the Leanback grids count a pending move on each search: one key press would move twice).
     * @return true if the key has been handled
     */
    private boolean moveFocusSilently(KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN || !event.hasNoModifiers() || !MainUIData.instance(this).isKodiScheme()) {
            return false;
        }

        int direction;

        switch (event.getKeyCode()) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                direction = View.FOCUS_LEFT;
                break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                direction = View.FOCUS_RIGHT;
                break;
            case KeyEvent.KEYCODE_DPAD_UP:
                direction = View.FOCUS_UP;
                break;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                direction = View.FOCUS_DOWN;
                break;
            default:
                return false;
        }

        View decorView = getWindow().getDecorView();
        View focused = decorView.findFocus();

        if (focused == null) {
            return false; // the system restores the default focus (no sound, no focus search)
        }

        View next = focused.focusSearch(direction);

        if (next != null && next != focused) {
            Rect rect = new Rect();
            focused.getFocusedRect(rect);

            if (decorView instanceof ViewGroup) {
                ((ViewGroup) decorView).offsetDescendantRectToMyCoords(focused, rect);
                ((ViewGroup) decorView).offsetRectIntoDescendantCoords(next, rect);
            }

            if (next.requestFocus(direction, rect)) {
                return true;
            }
        }

        // Same as the system: last chance for the focused view to handle the key
        decorView.dispatchUnhandledMove(focused, direction);

        return true;
    }

    /**
     * Kodi color scheme only: click sound on OK, back sound on a short Back press (not in the player).
     */
    private void playKeySound(KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_UP || event.isCanceled() || !isUiSoundsEnabled()) {
            return;
        }

        switch (event.getKeyCode()) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
            case KeyEvent.KEYCODE_BUTTON_A:
                UiSounds.play(UiSounds.CLICK);
                break;
            case KeyEvent.KEYCODE_BACK:
                UiSounds.play(UiSounds.BACK);
                break;
        }
    }

    @Override
    protected void onPostCreate(@Nullable Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        if (MainUIData.instance(this).isKodiScheme()) {
            disableSystemSounds();
        }
    }

    /**
     * Kodi scheme: no Android navigation/click sounds (the app has its own).<br/>
     * Decor view: focus navigation. Focused view: click.
     */
    private void disableSystemSounds() {
        try {
            View decorView = getWindow().getDecorView();
            decorView.setSoundEffectsEnabled(false);
            decorView.getViewTreeObserver().addOnGlobalFocusChangeListener((oldFocus, newFocus) -> {
                if (newFocus != null) {
                    newFocus.setSoundEffectsEnabled(false);
                }
            });
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    private boolean isUiSoundsEnabled() {
        // The "white card text" flag identifies the Kodi color scheme
        return isUiSoundsAllowed() && MainUIData.instance(this).isKodiScheme();
    }

    /**
     * Screens that must stay silent (e.g. the player) override this.
     */
    protected boolean isUiSoundsAllowed() {
        return true;
    }

    public void finishReally() {
        try {
            //if (VERSION.SDK_INT >= 21 && getViewManager().getTopView() != null) { // remain root activity in recents
            //    super.finishAndRemoveTask();
            //} else {
            //    super.finish();
            //}

            // finishAndRemoveTask() must be used carefully for intermediate activities:
            // calling it right after starting a parent activity races with that
            // startActivity() call. On slower devices, task removal can complete
            // first, killing the process instead of navigating back. Use finish()
            // when returning to an existing parent — the task cleans up automatically
            // for singleInstance activities. Reserve finishAndRemoveTask() for actual
            // app exit.
            // The issue: https://github.com/yuliskov/SmartTube/issues/6010
            super.finish();
        } catch (Exception e) {
            // TextView not attached to window manager (IllegalArgumentException)
        }
    }

    @Override
    protected void attachBaseContext(Context context) {
        Context contextWrapper = null;

        if (context != null) {
            // NOTE: Use only cached metrics. Because metrics creation involves using WindowManager, which isn't available at this stage.
            contextWrapper = LocaleContextWrapper.wrap(context, LocaleUpdater.getSavedLocale(context), sCachedDisplayMetrics);
        }

        super.attachBaseContext(contextWrapper);
    }

    @Override
    protected void onResume() {
        mIsBackPressed = false;
        try {
            super.onResume();
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        }

        // 4K fix with AFR
        applyCustomConfig();

        applyFullscreenModeIfNeeded();

        // Restore this activity's screensaver policy after returning to the foreground.
        mScreensaverManager.resume();
    }

    @Override
    protected void onPause() {
        super.onPause();

        // Stop managing the screensaver so a paused activity cannot keep the display awake.
        mScreensaverManager.suspend();
    }

    @Override
    protected void onDestroy() {
        if (mScreensaverManager != null) {
            mScreensaverManager.cleanup();
        }

        super.onDestroy();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        applyCustomConfig();
    }

    public ScreensaverManager getScreensaverManager() {
        return mScreensaverManager;
    }

    protected void initTheme() {
        int rootThemeResId = MainUIData.instance(this).getColorScheme().browseThemeResId;
        if (rootThemeResId > 0) {
            setTheme(rootThemeResId);
        }
    }

    private void initDpi() {
        getResources().getDisplayMetrics().setTo(getDisplayMetrics(this));
    }

    private DisplayMetrics getDisplayMetrics(Context context) {
        // BUG: adapt to resolution change (e.g. on AFR)
        // Don't disable caching or you will experience weird sizes on cards in video suggestions (e.g. after exit from PIP)!
        if (sCachedDisplayMetrics == null) {
            // NOTE: Don't replace with getResources().getDisplayMetrics(). Shows wrong metrics here!
            DisplayMetrics displayMetrics = new DisplayMetrics();
            getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
            float uiScale = MainUIData.instance(context).getUIScale();
            // Take into the account screen orientation (e.g. when running on phone)
            int widthPixels = Math.max(displayMetrics.widthPixels, displayMetrics.heightPixels);
            float widthRatio = DEFAULT_WIDTH / widthPixels;
            float density = DEFAULT_DENSITY / widthRatio * uiScale;
            displayMetrics.density = density;
            displayMetrics.scaledDensity = density;
            sCachedDisplayMetrics = displayMetrics;
        }

        return sCachedDisplayMetrics;
    }

    private void applyCustomConfig() {
        // NOTE: dpi should come after locale update to prevent resources overriding.

        // Fix sudden language change.
        // Could happen when screen goes off or after PIP mode.
        LocaleUpdater.applySavedLocale(this);

        // Fix sudden dpi change.
        // Could happen when screen goes off or after PIP mode.
        initDpi();
    }

    private void applyFullscreenModeIfNeeded() {
        if (mIsFullscreenModeEnabled) {
            // Most of the fullscreen tweaks could be performed in styles but not all.
            // E.g. Hide bottom navigation bar (couldn't be done in styles).
            Helpers.makeActivityFullscreen2(this);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (mOnPermissions != null) {
            for (OnPermissions callback : mOnPermissions) {
                callback.onPermissions(requestCode, permissions, grantResults);
            }
            mOnPermissions.clear();
            mOnPermissions = null;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (mOnResults != null) {
            for (OnResult callback : mOnResults) {
                callback.onResult(requestCode, resultCode, data);
            }
            mOnResults.clear();
            mOnResults = null;
        }
    }

    @Override
    public void onBackPressed() {
        mIsBackPressed = true;
        super.onBackPressed();
        // Oculus Quest fix: back button not closing the activity
        if (mIsOculusQuestFixEnabled) {
            finish();
        }
    }

    public void addOnPermissions(OnPermissions onPermissions) {
        if (mOnPermissions == null) {
            mOnPermissions = new ArrayList<>();
        }

        mOnPermissions.remove(onPermissions);
        mOnPermissions.add(onPermissions);
    }

    public void addOnResult(OnResult onResult) {
        if (mOnResults == null) {
            mOnResults = new ArrayList<>();
        }

        mOnResults.remove(onResult);
        mOnResults.add(onResult);
    }

    /**
     * Use this method only upon exiting from the app.<br/>
     * Big troubles with AFR resolution switch!
     */
    public static void invalidate() {
        sCachedDisplayMetrics = null;
        sIsInPipMode = false;

        // Sizes computed from the old scale must be recomputed too
        for (Runnable callback : sOnMetricsInvalidated) {
            try {
                callback.run();
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Callback is run every time the cached scale is forgotten, i.e. on app exit (keep a strong ref, e.g. a static field).
     */
    public static void addOnMetricsInvalidated(Runnable callback) {
        if (callback != null) {
            sOnMetricsInvalidated.add(callback);
        }
    }

    public static DisplayMetrics getCachedDisplayMetrics() {
        return sCachedDisplayMetrics;
    }

    /**
     * Comments focus fix<br/>
     * https://stackoverflow.com/questions/34277425/recyclerview-items-lose-focus
     */
    private boolean throttleKeyDown(int keyCode) {
        if (mEnableThrottleKeyDown && keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            long current = System.currentTimeMillis();
            if (current - mLastKeyDownTime < 100) {
                return true;
            }

            mLastKeyDownTime = current;
        }

        return false;
    }

    /**
     * Comments focus fix<br/>
     * https://stackoverflow.com/questions/34277425/recyclerview-items-lose-focus
     */
    public void enableThrottleKeyDown(boolean enable) {
        mEnableThrottleKeyDown = enable;
    }

    //@Override
    //public void setTheme(int resid) {
    //    super.setTheme(resid);
    //
    //    // No way to do this programmatically!
    //    if (!GeneralData.instance(this).isFullscreenModeEnabled()) {
    //        super.setTheme(R.style.FitSystemWindows);
    //    }
    //}

    protected ViewManager getViewManager() {
        return ViewManager.instance(this);
    }

    protected GeneralData getGeneralData() {
        return GeneralData.instance(this);
    }

    protected PlayerTweaksData getPlayerTweaksData() {
        return PlayerTweaksData.instance(this);
    }

    protected PlayerData getPlayerData() {
        return PlayerData.instance(this);
    }

    protected MainUIData getMainUIData() {
        return MainUIData.instance(this);
    }

    protected MediaServiceData getMediaServiceData() {
        return MediaServiceData.instance();
    }

    protected final boolean isBackPressed() {
        return mIsBackPressed;
    }

    protected final void resetBackState() {
        mIsBackPressed = false;
    }

    private void initEdgeSlide() {
        if (VERSION.SDK_INT < 21 || !Helpers.isTouchSupported(this) || Utils.isSystemGestureArrowEnabled(this)) {
            return;
        }

        SlidrConfig config = new SlidrConfig.Builder()
                .position(SlidrPosition.LEFT) // Swipe from the left
                .edge(true)              // Only trigger from the screen edge
                .edgeSize(0.18f)              // Grab 18% of the screen (good for cars)
                .scrimStartAlpha(0f)          // Don't dim the background screen
                .scrimEndAlpha(0f)            // Background clear when finished
                .distanceThreshold(0.1f)      // Set drag distance to minimum
                .partial(true)           // Don't do full slide animation
                .listener(new SlidrListener() {
                    @Override
                    public void onSlideStateChanged(int state) {}

                    @Override
                    public void onSlideChange(float percent) {}

                    @Override
                    public void onSlideOpened() {}

                    @Override
                    public boolean onSlideClosed() {
                        // This replaces the default finish() with your back logic
                        onBackPressed();
                        return true; // Tells the library we handled the close
                    }
                })
                .build();

        // Attach to this activity
        Slidr.attach(this, config);
    }
}
