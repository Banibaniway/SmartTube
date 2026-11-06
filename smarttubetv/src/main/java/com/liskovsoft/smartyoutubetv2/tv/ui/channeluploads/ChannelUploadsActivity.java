package com.liskovsoft.smartyoutubetv2.tv.ui.channeluploads;

import android.os.Bundle;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.common.LeanbackActivity;

public class ChannelUploadsActivity extends LeanbackActivity {
    private static final String TAG = ChannelUploadsActivity.class.getSimpleName();
    private ChannelUploadsFragment mFragment;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.fragment_channel_uploads);
        mFragment = (ChannelUploadsFragment) getSupportFragmentManager().findFragmentById(R.id.channel_uploads_fragment);
    }

    /**
     * Back key. A playlist opened from the search results: show the results again instead of closing.
     */
    @Override
    public void finish() {
        if (mFragment != null && mFragment.goBack()) {
            return;
        }

        super.finish();
    }

    @Override
    public void finishReally() {
        super.finishReally();

        mFragment.onFinish();
    }
}
