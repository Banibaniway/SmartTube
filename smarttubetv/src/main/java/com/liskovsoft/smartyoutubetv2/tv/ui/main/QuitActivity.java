package com.liskovsoft.smartyoutubetv2.tv.ui.main;

import android.app.Activity;
import android.app.ActivityManager;
import android.os.Build.VERSION;
import android.os.Bundle;
import android.os.Process;

import com.liskovsoft.smartyoutubetv2.common.utils.Utils;

/**
 * Invisible activity. Started by an external app (e.g. Kodi, when it quits) to close the app process.<br/>
 * Saves the app data and exits the process (same routine as the long press on Back).<br/>
 * Intent action: org.smarttube.action.QUIT
 */
public class QuitActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Close the app screens first: otherwise the system may restore an inner screen on the next launch
        closeOtherAppTasks();

        try {
            Utils.forceFinishTheApp(this);
        } catch (Throwable e) {
            e.printStackTrace();
            // Fallback: nothing to save (e.g. the process has just been created by this call)
            finish();
            Process.killProcess(Process.myPid());
            return;
        }

        finish();
    }

    private void closeOtherAppTasks() {
        if (VERSION.SDK_INT < 21) {
            return;
        }

        try {
            ActivityManager manager = (ActivityManager) getSystemService(ACTIVITY_SERVICE);

            if (manager == null) {
                return;
            }

            for (ActivityManager.AppTask task : manager.getAppTasks()) {
                try {
                    if (task.getTaskInfo().id != getTaskId()) {
                        task.finishAndRemoveTask();
                    }
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }
}
