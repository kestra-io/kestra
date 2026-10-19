package io.kestra.core.http.client;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import jakarta.annotation.Nullable;

/**
 * Masks credentials in HTTP headers and URIs before they are exposed in task outputs or logs.
 *
 * <p>
 * A value is redacted when its name is on the deny-list or matches a sensitive pattern, or when it is exactly equal
 * to a resolved secret. Secrets are only compared by equality, never by substring, and secrets shorter than
 * {@link #MIN_SECRET_LENGTH} are ignored so that unrelated data is not masked.
 */
public final class HttpRedactor {
    public static final String REDACTED = "***";
    public static final int MIN_SECRET_LENGTH = 4;

    private static final Set<String> SENSITIVE_HEADERS = Set.of(
        "authorization",
        "proxy-authorization",
        "cookie",
        "set-cookie",
        "x-api-key",
        "x-auth-token",
        "x-amz-security-token",
        "x-amz-signature",
        "ocp-apim-subscription-key"
    );

    private static final Pattern SENSITIVE_NAME = Pattern.compile("(?i)(token|secret|password|credential|signature|api[-_]?key)");

    private HttpRedactor() {
    }

    public static boolean isSensitiveName(@Nullable String name) {
        if (name == null) {
            return false;
        }
        return SENSITIVE_HEADERS.contains(name.toLowerCase(Locale.ROOT)) || SENSITIVE_NAME.matcher(name).find();
    }

    public static boolean isSecretValue(@Nullable String value, Set<String> secretValues) {
        return value != null && value.length() >= MIN_SECRET_LENGTH && secretValues.contains(value);
    }

    public static Map<String, List<String>> redactHeaders(@Nullable Map<String, List<String>> headers, Set<String> secretValues) {
        Map<String, List<String>> redacted = new LinkedHashMap<>();
        if (headers == null) {
            return redacted;
        }

        headers.forEach((name, values) ->
        {
            boolean sensitive = isSensitiveName(name);
            List<String> out = new ArrayList<>(values.size());
            for (String value : values) {
                out.add(sensitive || isSecretValue(value, secretValues) ? REDACTED : value);
            }
            redacted.put(name, out);
        });

        return redacted;
    }

    /**
     * Strips the user-info and masks query values whose parameter name is sensitive or whose decoded value is a secret.
     */
    public static URI redactUri(@Nullable URI uri, Set<String> secretValues) {
        if (uri == null) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        if (uri.getScheme() != null) {
            sb.append(uri.getScheme()).append(':');
        }

        String authority = uri.getRawAuthority();
        if (authority != null) {
            sb.append("//").append(authority.substring(authority.lastIndexOf('@') + 1));
        } else if (uri.isOpaque()) {
            return uri;
        }

        if (uri.getRawPath() != null) {
            sb.append(uri.getRawPath());
        }
        if (uri.getRawQuery() != null) {
            sb.append('?').append(redactQuery(uri.getRawQuery(), secretValues));
        }
        if (uri.getRawFragment() != null) {
            sb.append('#').append(uri.getRawFragment());
        }

        return URI.create(sb.toString());
    }

    /**
     * Same as {@link #redactUri(URI, Set)} for a raw string such as a {@code Location} header, which may be relative or malformed.
     */
    public static String redactUri(@Nullable String uri, Set<String> secretValues) {
        if (uri == null) {
            return null;
        }

        try {
            return redactUri(URI.create(uri), secretValues).toString();
        } catch (IllegalArgumentException e) {
            return REDACTED;
        }
    }

    private static String redactQuery(String rawQuery, Set<String> secretValues) {
        List<String> params = new ArrayList<>();
        for (String param : rawQuery.split("&", -1)) {
            int eq = param.indexOf('=');
            if (eq < 0) {
                params.add(param);
                continue;
            }

            String rawName = param.substring(0, eq);
            String rawValue = param.substring(eq + 1);
            boolean redact = isSensitiveName(decode(rawName)) || isSecretValue(decode(rawValue), secretValues);
            params.add(redact ? rawName + "=" + REDACTED : param);
        }

        return String.join("&", params);
    }

    private static String decode(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return value;
        }
    }
}
