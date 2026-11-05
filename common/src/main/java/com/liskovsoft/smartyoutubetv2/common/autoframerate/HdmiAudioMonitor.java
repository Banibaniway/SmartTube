package com.liskovsoft.smartyoutubetv2.common.autoframerate;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;

/**
 * Detects the HDMI audio reset caused by a display mode switch (AFR).<br/>
 * An audio output created before this reset and resumed after it is rerouted with a wrong latency (audio early).
 */
public final class HdmiAudioMonitor {
    private static boolean sInitDone;
    private static boolean sUnplugged;
    private static boolean sReplugged;

    private HdmiAudioMonitor() {
    }

    public static synchronized void init(Context context) {
        if (sInitDone || context == null) {
            return;
        }

        sInitDone = true;

        Context appContext = context.getApplicationContext() != null ? context.getApplicationContext() : context;

        try {
            appContext.registerReceiver(new BroadcastReceiver() {
                @Override
                public void onReceive(Context ctx, Intent intent) {
                    if (isInitialStickyBroadcast()) {
                        return; // current state on registration, not an event
                    }

                    int state = intent.getIntExtra(AudioManager.EXTRA_AUDIO_PLUG_STATE, -1);

                    if (state == 0) {
                        sUnplugged = true;
                    } else if (state == 1 && sUnplugged) {
                        sReplugged = true;
                    }
                }
            }, new IntentFilter(AudioManager.ACTION_HDMI_AUDIO_PLUG));
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    /**
     * Display mode switch started: forget the previous HDMI audio events.
     */
    public static void reset() {
        sUnplugged = false;
        sReplugged = false;
    }

    /**
     * The HDMI audio has been unplugged then plugged again since the last {@link #reset()}.
     */
    public static boolean isReplugged() {
        return sReplugged;
    }
}
