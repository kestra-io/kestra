package io.kestra.webserver.utils;

import java.util.Locale;

import io.micronaut.core.annotation.Nullable;

/**
 * HTTP content negotiation and conditional-request helpers for static UI resources.
 */
public final class HttpCacheUtils {
    private HttpCacheUtils() {
    }

    /**
     * @return the strong entity tag for a content-coding variant of a resource.
     */
    public static String etagFor(String etagBase, @Nullable String contentEncoding) {
        String suffix = contentEncoding == null ? "" : "-" + ("br".equals(contentEncoding) ? "br" : "gz");
        return "\"" + etagBase + suffix + "\"";
    }

    /**
     * @return the hex-encoded SHA-256 digest of the given bytes.
     */
    public static String sha256Hex(byte[] bytes) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
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
