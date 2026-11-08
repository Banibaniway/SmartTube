package com.google.android.exoplayer2.util;

import android.os.Parcel;
import android.os.Parcelable;
import com.google.android.exoplayer2.metadata.Metadata;

/**
 * Decoded YouTube xtags of a format ("key=value:key=value"), see {@link XtagsUtil}.
 */
public final class XtagsMetadata implements Metadata.Entry {
    public final String xtags;

    public XtagsMetadata(String xtags) {
        this.xtags = xtags;
    }

    private XtagsMetadata(Parcel in) {
        this.xtags = in.readString();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(xtags);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj || obj instanceof XtagsMetadata && Util.areEqual(xtags, ((XtagsMetadata) obj).xtags);
    }

    @Override
    public int hashCode() {
        return xtags != null ? xtags.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "XTAGS: " + xtags;
    }

    public static final Parcelable.Creator<XtagsMetadata> CREATOR =
            new Parcelable.Creator<XtagsMetadata>() {
                @Override
                public XtagsMetadata createFromParcel(Parcel in) {
                    return new XtagsMetadata(in);
                }

                @Override
                public XtagsMetadata[] newArray(int size) {
                    return new XtagsMetadata[size];
                }
            };

    /**
     * Decoded xtags of the format or empty string
     */
    public static String get(com.google.android.exoplayer2.Format format) {
        if (format == null || format.metadata == null) {
            return "";
        }

        for (int i = 0; i < format.metadata.length(); i++) {
            Metadata.Entry entry = format.metadata.get(i);

            if (entry instanceof XtagsMetadata && ((XtagsMetadata) entry).xtags != null) {
                return ((XtagsMetadata) entry).xtags;
            }
        }

        return "";
    }
}
