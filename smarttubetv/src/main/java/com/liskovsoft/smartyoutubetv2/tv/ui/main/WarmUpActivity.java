package com.liskovsoft.smartyoutubetv2.tv.ui.main;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;

import com.liskovsoft.sharedutils.locale.LocaleContextWrapper;
import com.liskovsoft.sharedutils.locale.LocaleUpdater;
import com.liskovsoft.smartyoutubetv2.common.utils.PlaybackWarmUp;

/**
 * Invisible activity. Started by an external app (e.g. Kodi) just before the real app is opened.<br/>
 * Starts the app process and prepares the playback engine in the background, then closes immediately.<br/>
 * Intent action: org.smarttube.action.WARMUP
 */
public class WarmUpActivity extends Activity {
    @Override
    protected void attachBaseContext(Context newBase) {
        Context context = newBase;

        if (newBase != null) {
            // Same language as the other activities: the media service language is set once on the first context
            context = LocaleContextWrapper.wrap(newBase, LocaleUpdater.getSavedLocale(newBase), null);
        }

        super.attachBaseContext(context);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            PlaybackWarmUp.startFromTrigger(this);
        } catch (Throwable e) {
            e.printStackTrace();
        }

        finish();
    }
}
