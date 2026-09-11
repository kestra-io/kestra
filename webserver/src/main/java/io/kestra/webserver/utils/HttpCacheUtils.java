package io.kestra.webserver.utils;

import io.micronaut.core.annotation.Nullable;

/**
 * Conditional-request helpers for static UI resources.
 */
public final class HttpCacheUtils {
    private HttpCacheUtils() {
    }

    /**
     * @return the given content-identifying base wrapped as a strong entity tag.
     */
    public static String etag(String etagBase) {
        return "\"" + etagBase + "\"";
    }

    /**
     * Returns true when the given {@code If-None-Match} header value matches the given entity tag,
     * using the weak comparison mandated for {@code If-None-Match}: the weak indicator is ignored
     * on both sides (RFC 9110 §8.8.3.2), so a weak server tag still matches its echoed value.
     */
    public static boolean anyEtagMatches(@Nullable String ifNoneMatch, String etag) {
        if (ifNoneMatch == null || ifNoneMatch.isBlank()) {
            return false;
        }

        String target = stripWeakIndicator(etag);
        for (String part : ifNoneMatch.split(",")) {
            String candidate = part.trim();
            if ("*".equals(candidate)) {
                return true;
            }
            if (stripWeakIndicator(candidate).equals(target)) {
                return true;
            }
        }
        return false;
    }

    private static String stripWeakIndicator(String etag) {
        return etag.startsWith("W/") ? etag.substring(2) : etag;
    }
}
