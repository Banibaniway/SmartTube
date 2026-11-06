package com.liskovsoft.smartyoutubetv2.tv.presenter;

import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build.VERSION;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import androidx.leanback.widget.ListRowPresenter;
import androidx.leanback.widget.Presenter;
import androidx.leanback.widget.RowHeaderPresenter;
import androidx.leanback.widget.RowPresenter;
import com.liskovsoft.smartyoutubetv2.common.prefs.MainUIData;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.util.ViewUtil;

public class CustomListRowPresenter extends ListRowPresenter {
    public CustomListRowPresenter() {
        super(ViewUtil.FOCUS_ZOOM_FACTOR, ViewUtil.FOCUS_DIMMER_ENABLED);
        setSelectEffectEnabled(ViewUtil.ROW_SELECT_EFFECT_ENABLED);
        setHeaderPresenter(new CustomRowHeaderPresenter());
    }

    @Override
    protected RowPresenter.ViewHolder createRowViewHolder(ViewGroup parent) {
        RowPresenter.ViewHolder viewHolder = super.createRowViewHolder(parent);

        if (MainUIData.instance(parent.getContext()).isKodiScheme() && viewHolder instanceof ListRowPresenter.ViewHolder) {
            // The focus shadow shows through the translucent cards: keep it (same layout as the original) but invisible
            makeShadowsTransparent(((ListRowPresenter.ViewHolder) viewHolder).getGridView());
        }

        return viewHolder;
    }

    /**
     * Keep the Leanback shadow (elevation, draw order) but make it fully transparent.<br/>
     * The shadow is drawn by the card itself (Android 9: dynamic shadow on the item view).
     */
    static void makeShadowsTransparent(RecyclerView gridView) {
        if (gridView == null || VERSION.SDK_INT < 28) {
            return;
        }

        gridView.addOnChildAttachStateChangeListener(new RecyclerView.OnChildAttachStateChangeListener() {
            @Override
            public void onChildViewAttachedToWindow(View view) {
                if (VERSION.SDK_INT >= 28) {
                    view.setOutlineAmbientShadowColor(Color.TRANSPARENT);
                    view.setOutlineSpotShadowColor(Color.TRANSPARENT);
                }
            }

            @Override
            public void onChildViewDetachedFromWindow(View view) {
                // NOP
            }
        });
    }

    /**
     * Kodi color scheme: row titles are bold and not dimmed when the row isn't selected.
     */
    private static class CustomRowHeaderPresenter extends RowHeaderPresenter {
        @Override
        public Presenter.ViewHolder onCreateViewHolder(ViewGroup parent) {
            Presenter.ViewHolder viewHolder = super.onCreateViewHolder(parent);

            if (MainUIData.instance(parent.getContext()).isKodiScheme()) {
                TextView title = viewHolder.view.findViewById(R.id.row_header);

                if (title != null) {
                    title.setTypeface(title.getTypeface(), Typeface.BOLD);
                }
            }

            return viewHolder;
        }

        @Override
        protected void onSelectLevelChanged(RowHeaderPresenter.ViewHolder holder) {
            if (MainUIData.instance(holder.view.getContext()).isKodiScheme()) {
                holder.view.setAlpha(1f);
            } else {
                super.onSelectLevelChanged(holder);
            }
        }
    }
}
