package io.kestra.jdbc;

import org.jooq.JSONB;

/**
 * Makes JSON payloads acceptable to PostgreSQL JSONB before they are stored.
 *
 * <p>
 * PostgreSQL rejects two things that Java strings and Jackson can produce:
 * <ul>
 * <li>null characters, raw or as the JSON escape for code point 0, which are removed;</li>
 * <li>lone UTF-16 surrogates, raw or JSON-escaped (for example the high surrogate D835 not followed by a low
 * surrogate), which are replaced by the replacement character U+FFFD, as any UTF-8 encoder does.</li>
 * </ul>
 * A single invalid character in a task output or a webhook body would otherwise make the whole insert fail.
 *
 * <p>
 * Both are fixed in one pass over the payload. A clean payload is returned as is, without copying it.
 */
public final class JdbcJsonbUtils {
    private static final char REPLACEMENT = '\uFFFD';
    private static final String ESCAPED_REPLACEMENT = "\\uFFFD";

    private JdbcJsonbUtils() {
    }

    public static JSONB valueOf(String json) {
        return json == null ? null : JSONB.valueOf(sanitize(json));
    }

    /**
     * Removes null characters and replaces lone surrogates, both as raw characters and as JSON unicode escapes, in
     * a single pass. Valid surrogate pairs are kept. An escaped backslash is copied with the character that
     * follows it, so the text it starts is never read as an escape.
     */
    static String sanitize(String json) {
        StringBuilder out = null;
        int length = json.length();
        int copied = 0; // json[copied, i) is clean and not yet appended to out
        int i = 0;
        while (i < length) {
            char c = json.charAt(i);
            int consumed;
            String replacement;

            if (c == '\\' && i + 1 < length) {
                if (json.charAt(i + 1) != 'u') {
                    i += 2;
                    continue;
                }

                int unit = escapedUnit(json, i);
                if (unit < 0 || (unit != 0 && !Character.isSurrogate((char) unit))) {
                    i += unit < 0 ? 1 : 6;
                    continue;
                }

                if (Character.isHighSurrogate((char) unit)) {
                    int next = escapedUnit(json, i + 6);
                    if (next >= 0 && Character.isLowSurrogate((char) next)) {
                        i += 12;
                        continue;
                    }
                }

                consumed = 6;
                replacement = unit == 0 ? "" : ESCAPED_REPLACEMENT;
            } else if (c == '\u0000') {
                consumed = 1;
                replacement = "";
            } else if (Character.isSurrogate(c)) {
                if (Character.isHighSurrogate(c) && i + 1 < length && Character.isLowSurrogate(json.charAt(i + 1))) {
                    i += 2;
                    continue;
                }

                consumed = 1;
                replacement = String.valueOf(REPLACEMENT);
            } else {
                i++;
                continue;
            }

            if (out == null) {
                out = new StringBuilder(length);
            }
            out.append(json, copied, i).append(replacement);
            i += consumed;
            copied = i;
        }

        if (out == null) {
            return json;
        }

        return out.append(json, copied, length).toString();
    }

    /**
     * @return the UTF-16 code unit of the JSON unicode escape starting at {@code start}, or -1 if there is none.
     */
    private static int escapedUnit(String json, int start) {
        if (start + 6 > json.length() || json.charAt(start) != '\\' || json.charAt(start + 1) != 'u') {
            return -1;
        }

        int unit = 0;
        for (int k = start + 2; k < start + 6; k++) {
            int digit = Character.digit(json.charAt(k), 16);
            if (digit < 0) {
                return -1;
            }
            unit = unit * 16 + digit;
        }

        return unit;
    }
}
