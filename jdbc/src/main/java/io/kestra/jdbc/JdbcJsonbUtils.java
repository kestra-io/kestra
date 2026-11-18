package io.kestra.jdbc;

import org.jooq.JSONB;

/**
 * Removes the null characters and the lone UTF-16 surrogate escapes that PostgreSQL refuses in JSONB values.
 * Raw lone surrogates are left alone, as the PostgreSQL driver already encodes them as {@code ?}.
 */
public final class JdbcJsonbUtils {
    private static final String ESCAPE = "\\u";
    private static final String ESCAPED_REPLACEMENT = "\\uFFFD";

    private JdbcJsonbUtils() {
    }

    public static JSONB valueOf(String json) {
        return json == null ? null : JSONB.valueOf(sanitize(json));
    }

    /**
     * Removes null characters, raw or escaped, and replaces lone surrogate escapes by the escaped U+FFFD.
     * Returns the same instance when there is nothing to fix.
     */
    static String sanitize(String json) {
        String text = json.indexOf('\u0000') < 0 ? json : json.replace("\u0000", "");

        StringBuilder out = null;
        int copied = 0;
        int escape = text.indexOf(ESCAPE);
        while (escape >= 0) {
            int next = escape + 2;
            int unit = isEscaped(text, escape) ? -1 : escapedUnit(text, escape);
            if (unit >= 0) {
                next = escape + 6;
                String replacement = null;
                if (unit == 0) {
                    replacement = "";
                } else if (Character.isHighSurrogate((char) unit)) {
                    int low = escapedUnit(text, next);
                    if (low >= 0 && Character.isLowSurrogate((char) low)) {
                        next += 6;
                    } else {
                        replacement = ESCAPED_REPLACEMENT;
                    }
                } else if (Character.isLowSurrogate((char) unit)) {
                    replacement = ESCAPED_REPLACEMENT;
                }

                if (replacement != null) {
                    if (out == null) {
                        out = new StringBuilder(text.length());
                    }
                    out.append(text, copied, escape).append(replacement);
                    copied = next;
                }
            }
            escape = text.indexOf(ESCAPE, next);
        }

        return out == null ? text : out.append(text, copied, text.length()).toString();
    }

    private static boolean isEscaped(String json, int index) {
        int previous = index - 1;
        while (previous >= 0 && json.charAt(previous) == '\\') {
            previous--;
        }
        return ((index - 1 - previous) & 1) == 1;
    }

    private static int escapedUnit(String json, int start) {
        if (start + 6 > json.length() || json.charAt(start) != '\\' || json.charAt(start + 1) != 'u') {
            return -1;
        }

        int unit = 0;
        for (int i = start + 2; i < start + 6; i++) {
            char c = json.charAt(i);
            int digit = c < 128 ? Character.digit(c, 16) : -1;
            if (digit < 0) {
                return -1;
            }
            unit = unit * 16 + digit;
        }

        return unit;
    }
}
