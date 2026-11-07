package com.liskovsoft.smartyoutubetv2.tv.ui.playback.actions;

import android.content.Context;
import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * Zoom 133% of the current video only (toggle 100% / 133%). Same icon as the video zoom.
 */
public class VideoZoom133Action extends TwoStateAction {
    public VideoZoom133Action(Context context) {
        super(context, R.id.action_video_zoom_133, R.drawable.action_video_zoom);

        String label = context.getString(R.string.action_video_zoom_133);
        String[] labels = new String[2];
        // Note, labels denote the action taken when clicked
        labels[INDEX_OFF] = label;
        labels[INDEX_ON] = label;
        setLabels(labels);
    }
}
