package com.google.android.exoplayer2.util;

import android.util.Base64;
import java.net.URLDecoder;

/**
 * YouTube describes some audio variants in the "xtags" field only (base64 of a small protobuf,
 * e.g. CggKA2RyYxIBMQ = "drc" "1"), without the isDrc flag.
 */
public final class XtagsUtil {
    private XtagsUtil() {
    }

    /**
     * DRC audio ("stable volume"): compressed dynamic range, loud effects are lowered.
     */
    public static boolean isDrc(String xtags) {
        if (xtags == null || xtags.isEmpty()) {
            return false;
        }

        try {
            String encoded = xtags.contains("%") ? URLDecoder.decode(xtags, "UTF-8") : xtags;
            byte[] data = Base64.decode(encoded, Base64.URL_SAFE | Base64.NO_WRAP);
            return new String(data, "ISO-8859-1").contains("drc");
        } catch (Exception e) {
            return xtags.contains("drc");
        }
    }
}
