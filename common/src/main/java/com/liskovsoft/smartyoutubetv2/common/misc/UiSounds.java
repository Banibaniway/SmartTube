package com.liskovsoft.smartyoutubetv2.common.misc;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Build.VERSION;
import android.util.SparseBooleanArray;

import com.liskovsoft.smartyoutubetv2.common.R;

/**
 * Short UI sounds (Kodi color scheme only). Loaded once per process, played at full volume.
 */
public final class UiSounds {
    public static final int CLICK = 0;
    public static final int BACK = 1;
    public static final int OPEN = 2;

    private static final int[] RES_IDS = {R.raw.ui_click, R.raw.ui_back, R.raw.ui_open};
    private static SoundPool sPool;
    private static final int[] sSoundIds = new int[RES_IDS.length];
    private static final SparseBooleanArray sLoaded = new SparseBooleanArray();

    private UiSounds() {
    }

    public static synchronized void init(Context context) {
        if (sPool != null || context == null) {
            return;
        }

        try {
            Context appContext = context.getApplicationContext() != null ? context.getApplicationContext() : context;

            if (VERSION.SDK_INT >= 21) {
                AudioAttributes attributes = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build();
                sPool = new SoundPool.Builder().setMaxStreams(3).setAudioAttributes(attributes).build();
            } else {
                sPool = new SoundPool(3, AudioManager.STREAM_MUSIC, 0);
            }

            sPool.setOnLoadCompleteListener((pool, sampleId, status) -> {
                if (status == 0) {
                    synchronized (UiSounds.class) {
                        sLoaded.put(sampleId, true);
                    }
                }
            });

            for (int i = 0; i < RES_IDS.length; i++) {
                sSoundIds[i] = sPool.load(appContext, RES_IDS[i], 1);
            }
        } catch (Throwable e) {
            e.printStackTrace();
            sPool = null;
        }
    }

    public static synchronized void play(int sound) {
        if (sPool == null || sound < 0 || sound >= sSoundIds.length || !sLoaded.get(sSoundIds[sound])) {
            return;
        }

        try {
            sPool.play(sSoundIds[sound], 1f, 1f, 1, 0, 1f);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }
}
