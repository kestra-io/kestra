package io.kestra.jdbc;

import org.jooq.JSONB;

/**
 * Makes JSON payloads acceptable to PostgreSQL JSONB before they are stored.
 *
 * <p>
 * PostgreSQL rejects two things that Java strings and Jackson can produce:
 * <ul>
 * <li>null bytes, raw or JSON-escaped, which are stripped;</li>
 * <li>lone UTF-16 surrogates, raw or JSON-escaped (for example the high surrogate D835 not followed by a low
 * surrogate), which are replaced by the replacement character U+FFFD, as any UTF-8 encoder does.</li>
 * </ul>
 * A single invalid character in a task output or a webhook body would otherwise make the whole insert fail.
 */
public final class JdbcJsonbUtils {
    private static final char REPLACEMENT = '\uFFFD';

    private JdbcJsonbUtils() {
    }

    public static JSONB valueOf(String json) {
        if (json == null) {
            return null;
        }

        return JSONB.valueOf(replaceLoneSurrogates(json.replace("\u0000", "").replace("\\u0000", "")));
    }

    /**
     * Replaces lone UTF-16 surrogates, both raw characters and JSON unicode escapes, by U+FFFD.
     * Valid surrogate pairs and escaped backslashes are kept as they are.
     */
    static String replaceLoneSurrogates(String json) {
        if (!mayContainSurrogate(json)) {
            return json;
        }

        StringBuilder out = new StringBuilder(json.length());
        int length = json.length();
        int i = 0;
        while (i < length) {
            char c = json.charAt(i);

            if (c == '\\' && i + 1 < length) {
                if (json.charAt(i + 1) != 'u') {
                    // any other escape, including an escaped backslash: copy both characters so the next one is not read as an escape
                    out.append(c).append(json.charAt(i + 1));
                    i += 2;
                    continue;
                }

                int unit = escapedUnit(json, i);
                if (unit < 0 || !Character.isSurrogate((char) unit)) {
                    out.append(c);
                    i++;
                    continue;
                }

                if (Character.isHighSurrogate((char) unit)) {
                    int next = escapedUnit(json, i + 6);
                    if (next >= 0 && Character.isLowSurrogate((char) next)) {
                        out.append(json, i, i + 12);
                        i += 12;
                        continue;
                    }
                }

                out.append("\\uFFFD");
                i += 6;
                continue;
            }

            if (Character.isHighSurrogate(c) && i + 1 < length && Character.isLowSurrogate(json.charAt(i + 1))) {
                out.append(c).append(json.charAt(i + 1));
                i += 2;
                continue;
            }

            out.append(Character.isSurrogate(c) ? REPLACEMENT : c);
            i++;
        }

        return out.toString();
    }

    private static boolean mayContainSurrogate(String json) {
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (Character.isSurrogate(c)) {
                return true;
            }
            if (c == '\\' && i + 2 < json.length() && json.charAt(i + 1) == 'u' && (json.charAt(i + 2) == 'd' || json.charAt(i + 2) == 'D')) {
                return true;
            }
        }

        return false;
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
