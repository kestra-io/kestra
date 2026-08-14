package io.kestra.webserver.utils;

import java.util.Locale;

import io.micronaut.core.annotation.Nullable;

/**
 * HTTP content negotiation and conditional-request helpers for cached static assets.
 */
public final class HttpCacheUtils {
    private HttpCacheUtils() {
    }

    /**
     * Returns true when the given {@code Accept-Encoding} header value accepts the given content coding,
     * honouring explicit {@code q=0} refusals and the {@code *} wildcard.
     */
    public static boolean accepts(@Nullable String acceptEncoding, String encoding) {
        if (acceptEncoding == null || acceptEncoding.isBlank()) {
            return false;
        }

        Double exact = null;
        Double wildcard = null;
        for (String part : acceptEncoding.split(",")) {
            String[] tokens = part.trim().split(";");
            String name = tokens[0].trim().toLowerCase(Locale.ROOT);
            double quality = 1.0d;
            for (int i = 1; i < tokens.length; i++) {
                String parameter = tokens[i].trim();
                if (parameter.startsWith("q=")) {
                    try {
                        quality = Double.parseDouble(parameter.substring(2).trim());
                    } catch (NumberFormatException e) {
                        quality = 0.0d;
                    }
                }
            }
            if (name.equals(encoding)) {
                exact = quality;
            } else if ("*".equals(name)) {
                wildcard = quality;
            }
        }

        Double effective = exact != null ? exact : wildcard;
        return effective != null && effective > 0.0d;
    }

    /**
     * Returns true when the given {@code If-None-Match} header value matches the given entity tag.
     */
    public static boolean anyEtagMatches(@Nullable String ifNoneMatch, String etag) {
        if (ifNoneMatch == null || ifNoneMatch.isBlank()) {
            return false;
        }

        for (String part : ifNoneMatch.split(",")) {
            String candidate = part.trim();
            if ("*".equals(candidate)) {
                return true;
            }
            if (candidate.startsWith("W/")) {
                candidate = candidate.substring(2);
            }
            if (candidate.equals(etag)) {
                return true;
            }
        }
        return false;
    }
}
