package com.google.android.exoplayer2.util;

import android.util.Base64;
import java.net.URLDecoder;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * YouTube "xtags" of a format: describes the variant of a stream that has the same itag as another one
 * (original / DRC "stable volume" / voice boost, dubbed language...).<br/>
 * Two forms: base64url protobuf in the format info (e.g. CggKA2RyYxIBMQ = drc=1)
 * and plain text in the stream url (e.g. xtags=drc%3D1).<br/>
 * Decoded form used everywhere here: "key=value:key=value".
 */
public final class XtagsUtil {
    private XtagsUtil() {
    }

    /**
     * @param infoXtags xtags of the format info (base64 protobuf or plain text), may be null
     * @param url stream url (may contain the xtags param), may be null
     * @return decoded tags "key=value:key=value" or empty string
     */
    public static String decode(String infoXtags, String url) {
        Map<String, String> tags = new LinkedHashMap<>();
        tags.putAll(parseRaw(infoXtags));
        tags.putAll(parseRaw(extractUrlParam(url, "xtags")));
        return join(tags);
    }

    /**
     * @param decoded decoded tags "key=value:key=value"
     */
    public static Map<String, String> parse(String decoded) {
        return parsePlain(decoded);
    }

    public static boolean isDrc(String decoded) {
        return "1".equals(parse(decoded).get("drc"));
    }

    public static boolean isVoiceBoost(String decoded) {
        return "1".equals(parse(decoded).get("vb"));
    }

    private static Map<String, String> parseRaw(String raw) {
        if (raw == null || raw.isEmpty()) {
            return new LinkedHashMap<>();
        }

        String value = raw;

        try {
            if (value.contains("%")) {
                value = URLDecoder.decode(value, "UTF-8");
            }
        } catch (Exception e) {
            // keep raw value
        }

        if (value.contains("=") && !value.endsWith("=") || value.contains(":")) {
            return parsePlain(value);
        }

        Map<String, String> result = parseProto(value);

        if (result.isEmpty()) {
            result.put("?", value); // unknown format: show it as is
        }

        return result;
    }

    private static Map<String, String> parsePlain(String value) {
        Map<String, String> result = new LinkedHashMap<>();

        if (value == null || value.isEmpty()) {
            return result;
        }

        for (String pair : value.split("[:&,;]")) {
            if (pair.isEmpty()) {
                continue;
            }

            int pos = pair.indexOf('=');

            if (pos > 0) {
                result.put(pair.substring(0, pos), pair.substring(pos + 1));
            } else {
                result.put(pair, "");
            }
        }

        return result;
    }

    /**
     * Protobuf: repeated (field 1) { name (field 1) string, value (field 2) string }
     */
    private static Map<String, String> parseProto(String base64) {
        Map<String, String> result = new LinkedHashMap<>();

        try {
            byte[] data = Base64.decode(base64, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
            int[] pos = {0};

            while (pos[0] < data.length) {
                long tag = readVarint(data, pos);
                int field = (int) (tag >>> 3);
                int wireType = (int) (tag & 7);

                if (wireType != 2) {
                    skip(data, pos, wireType);
                    continue;
                }

                int length = (int) readVarint(data, pos);
                int end = pos[0] + length;

                if (length < 0 || end > data.length) {
                    break;
                }

                if (field == 1) {
                    String name = null;
                    String value = "";

                    while (pos[0] < end) {
                        long innerTag = readVarint(data, pos);
                        int innerField = (int) (innerTag >>> 3);
                        int innerWireType = (int) (innerTag & 7);

                        if (innerWireType != 2) {
                            skip(data, pos, innerWireType);
                            continue;
                        }

                        int innerLength = (int) readVarint(data, pos);

                        if (innerLength < 0 || pos[0] + innerLength > end) {
                            break;
                        }

                        String text = new String(data, pos[0], innerLength, "UTF-8");
                        pos[0] += innerLength;

                        if (innerField == 1) {
                            name = text;
                        } else if (innerField == 2) {
                            value = text;
                        }
                    }

                    if (name != null) {
                        result.put(name, value);
                    }
                }

                pos[0] = end;
            }
        } catch (Exception e) {
            // Not a protobuf
        }

        return result;
    }

    private static long readVarint(byte[] data, int[] pos) {
        long result = 0;
        int shift = 0;

        while (pos[0] < data.length && shift < 64) {
            byte b = data[pos[0]++];
            result |= (long) (b & 0x7F) << shift;

            if ((b & 0x80) == 0) {
                return result;
            }

            shift += 7;
        }

        throw new IllegalStateException("Bad varint");
    }

    private static void skip(byte[] data, int[] pos, int wireType) {
        switch (wireType) {
            case 0:
                readVarint(data, pos);
                break;
            case 1:
                pos[0] += 8;
                break;
            case 5:
                pos[0] += 4;
                break;
            default:
                throw new IllegalStateException("Unsupported wire type");
        }
    }

    private static String extractUrlParam(String url, String name) {
        if (url == null) {
            return null;
        }

        int start = url.indexOf('?');

        if (start == -1) {
            return null;
        }

        for (String param : url.substring(start + 1).split("&")) {
            if (param.startsWith(name + "=")) {
                return param.substring(name.length() + 1);
            }
        }

        return null;
    }

    private static String join(Map<String, String> tags) {
        StringBuilder result = new StringBuilder();

        for (Map.Entry<String, String> entry : tags.entrySet()) {
            if (result.length() > 0) {
                result.append(':');
            }

            result.append(entry.getKey());

            if (!entry.getValue().isEmpty()) {
                result.append('=').append(entry.getValue());
            }
        }

        return result.toString();
    }
}
