package com.liskovsoft.smartyoutubetv2.common.utils;

import android.content.Context;

import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.sharedutils.prefs.GlobalPreferences;
import com.liskovsoft.sharedutils.rx.RxHelper;
import com.liskovsoft.smartyoutubetv2.common.prefs.GeneralData;
import com.liskovsoft.youtubeapi.service.YouTubeServiceManager;

/**
 * Prepares the playback engine (tokens, player data) in the background, once per process.<br/>
 * The first real playback is then much faster.
 */
public final class PlaybackWarmUp {
    private static final String TAG = PlaybackWarmUp.class.getSimpleName();
    // The first video of YouTube: public and always available
    private static final String WARM_UP_VIDEO_ID = "jNQXAC9IVRw";
    private static boolean sWarmedUp;

    private PlaybackWarmUp() {
    }

    /**
     * Warm up after the app has been initialized (media service prefs are ready).
     */
    public static void start(Context context, long delayMs) {
        if (sWarmedUp) {
            return;
        }

        sWarmedUp = true;

        RxHelper.runAsync(() -> {
            try {
                YouTubeServiceManager.instance().getMediaItemService().getFormatInfo(WARM_UP_VIDEO_ID);
            } catch (Throwable e) {
                Log.e(TAG, "Playback warm-up failed: " + e.getMessage());
            }
        }, delayMs);
    }

    /**
     * Warm up on the app start requested by an external app (e.g. Kodi) before the app is opened.<br/>
     * The context should have the proper app language (same as the activities).
     */
    public static void startFromTrigger(Context context) {
        if (context == null || sWarmedUp) {
            return;
        }

        // Init media service language and context. Same as the splash screen does on the first start.
        GlobalPreferences.instance(context);

        // Proxy is configured by the splash screen: don't make requests without it
        if (GeneralData.instance(context).isProxyEnabled()) {
            Log.d(TAG, "Proxy is enabled. Skip the warm-up...");
            return;
        }

        start(context, 0);
    }
}
