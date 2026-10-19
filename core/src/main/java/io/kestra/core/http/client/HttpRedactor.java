package io.kestra.core.http.client;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.annotation.Nullable;

/**
 * Masks credentials in HTTP headers and URIs before they are exposed in task outputs or logs.
 *
 * <p>
 * A value is redacted when its name is on the deny-list or matches a sensitive pattern, or when it is exactly equal
 * to a resolved secret. Secrets are only compared by equality (against a whole value or a segment-aligned path span), never by substring, and secrets shorter than
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

    // Matched by the name regex through "credential" but carrying only a boolean or a list of names.
    private static final Set<String> NON_SENSITIVE_NAMES = Set.of(
        "access-control-allow-credentials",
        "access-control-allow-headers",
        "access-control-expose-headers",
        "access-control-request-headers",
        "idempotency-key",
        "sec-websocket-key"
    );

    private static final Pattern SENSITIVE_NAME = Pattern.compile("(?i)(token|secret|password|credential|signature|api[-_]?key)");

    // Matched on whole tokens only, so that names such as "page", "monkey" or "keyword" are left alone.
    private static final Set<String> SENSITIVE_NAME_TOKENS = Set.of("key", "sig", "auth", "pass", "pwd");
    private static final Pattern NAME_TOKEN_SEPARATOR = Pattern.compile("[^a-z0-9]+");
    private static final Pattern CAMEL_CASE_BOUNDARY = Pattern.compile("([a-z0-9])([A-Z])");

    // URL-valued headers often carry pagination cursors, so only well-known credential parameters are masked there.
    private static final Set<String> URI_HEADER_CREDENTIAL_PARAMS = Set.of(
        "access_token", "id_token", "refresh_token", "token", "api_key", "apikey", "key", "sig", "signature", "password",
        "secret", "auth", "client_secret", "code", "x-amz-signature", "x-amz-credential", "x-amz-security-token"
    );

    private static final Set<String> URI_HEADERS = Set.of("location", "content-location");
    private static final Pattern LINK_TARGET = Pattern.compile("<([^>]*)>");

    private HttpRedactor() {
    }

    public static boolean isSensitiveName(@Nullable String name) {
        if (name == null) {
            return false;
        }
        var lower = name.toLowerCase(Locale.ROOT);
        if (SENSITIVE_HEADERS.contains(lower)) {
            return true;
        }
        if (NON_SENSITIVE_NAMES.contains(lower)) {
            return false;
        }
        return SENSITIVE_NAME.matcher(name).find() || hasSensitiveToken(name);
    }

    private static boolean hasSensitiveToken(String name) {
        var separated = CAMEL_CASE_BOUNDARY.matcher(name).replaceAll("$1 $2").toLowerCase(Locale.ROOT);
        return NAME_TOKEN_SEPARATOR.splitAsStream(separated).anyMatch(SENSITIVE_NAME_TOKENS::contains);
    }

    public static boolean isSecretValue(@Nullable String value, Set<String> secretValues) {
        return value != null && value.length() >= MIN_SECRET_LENGTH && secretValues.contains(value);
    }

    public static Map<String, List<String>> redactHeaders(@Nullable Map<String, List<String>> headers, Set<String> secretValues) {
        var redacted = new LinkedHashMap<String, List<String>>();
        if (headers == null) {
            return redacted;
        }

        headers.forEach((name, values) ->
        {
            var sensitive = isSensitiveName(name);
            var lowerName = name.toLowerCase(Locale.ROOT);
            var out = new ArrayList<String>(values.size());
            for (var value : values) {
                if (sensitive) {
                    out.add(REDACTED);
                } else if (URI_HEADERS.contains(lowerName)) {
                    out.add(redactUri(value, secretValues, HttpRedactor::isUriHeaderCredentialParam));
                } else if ("link".equals(lowerName)) {
                    out.add(redactLink(value, secretValues));
                } else {
                    out.add(isSecretValue(value, secretValues) ? REDACTED : value);
                }
            }
            redacted.put(name, out);
        });

        return redacted;
    }

    /**
     * Strips the user-info, masks query values whose parameter name is sensitive or whose raw or decoded value is a secret,
     * and masks the path segments, alone or as a segment-aligned run, that are a secret. The fragment is redacted like the query. A URI that is entirely a secret keeps only its scheme and
     * host.
     */
    public static URI redactUri(@Nullable URI uri, Set<String> secretValues) {
        return redactUri(uri, secretValues, HttpRedactor::isSensitiveName);
    }

    /**
     * Same as {@link #redactUri(String, Set)} but masks only credential query parameters, like the {@code Location} header, so pagination cursors are kept.
     */
    public static String redactRedirectLocation(@Nullable String location, Set<String> secretValues) {
        return redactUri(location, secretValues, HttpRedactor::isUriHeaderCredentialParam);
    }

    private static URI redactUri(@Nullable URI uri, Set<String> secretValues, Predicate<String> sensitiveParam) {
        if (uri == null) {
            return null;
        }

        if (isSecretValue(uri.toString(), secretValues) || isSecretValue(decode(uri.toString()), secretValues)) {
            var host = uri.getRawAuthority() == null ? null : uri.getRawAuthority().substring(uri.getRawAuthority().lastIndexOf('@') + 1);
            return URI.create(host == null ? REDACTED : (uri.getScheme() == null ? "" : uri.getScheme() + ":") + "//" + host + "/" + REDACTED);
        }

        var sb = new StringBuilder();
        if (uri.getScheme() != null) {
            sb.append(uri.getScheme()).append(':');
        }

        var authority = uri.getRawAuthority();
        if (authority != null) {
            sb.append("//").append(authority.substring(authority.lastIndexOf('@') + 1));
        } else if (uri.isOpaque()) {
            return uri;
        }

        if (uri.getRawPath() != null) {
            sb.append(redactPath(uri.getRawPath(), secretValues));
        }
        if (uri.getRawQuery() != null) {
            sb.append('?').append(redactQuery(uri.getRawQuery(), secretValues, sensitiveParam));
        }
        if (uri.getRawFragment() != null) {
            sb.append('#').append(redactFragment(uri.getRawFragment(), secretValues, sensitiveParam));
        }

        return URI.create(sb.toString());
    }

    /**
     * Same as {@link #redactUri(URI, Set)} for a raw string such as a {@code Location} header, which may be relative or malformed.
     */
    public static String redactUri(@Nullable String uri, Set<String> secretValues) {
        return redactUri(uri, secretValues, HttpRedactor::isSensitiveName);
    }

    private static String redactUri(@Nullable String uri, Set<String> secretValues, Predicate<String> sensitiveParam) {
        if (uri == null) {
            return null;
        }

        try {
            return redactUri(URI.create(uri), secretValues, sensitiveParam).toString();
        } catch (IllegalArgumentException e) {
            return REDACTED;
        }
    }

    /**
     * Redacts the target of each {@code <uri>} entry of a {@code Link} header and keeps the parameters that follow it.
     */
    private static String redactLink(String value, Set<String> secretValues) {
        if (isSecretValue(value, secretValues)) {
            return REDACTED;
        }

        var matcher = LINK_TARGET.matcher(value);
        var sb = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(sb, Matcher.quoteReplacement("<" + redactUri(matcher.group(1), secretValues, HttpRedactor::isUriHeaderCredentialParam) + ">"));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static String redactPath(String rawPath, Set<String> secretValues) {
        if (rawPath.isEmpty()) {
            return rawPath;
        }

        var withoutLeadingSlash = rawPath.startsWith("/") ? rawPath.substring(1) : rawPath;
        if (isSecretValue(withoutLeadingSlash, secretValues) || isSecretValue(decode(withoutLeadingSlash), secretValues)) {
            return "/" + REDACTED;
        }

        var segments = rawPath.split("/", -1);
        var decodedSegments = Arrays.stream(segments).map(HttpRedactor::decode).toArray(String[]::new);
        var redacted = new ArrayList<String>(segments.length);
        var i = 0;
        while (i < segments.length) {
            var end = longestSecretSpanEnd(segments, decodedSegments, i, secretValues);
            if (end < 0) {
                redacted.add(segments[i]);
                i++;
            } else {
                redacted.add(REDACTED);
                i = end + 1;
            }
        }
        return String.join("/", redacted);
    }

    // Segment-aligned only, so a secret that is a substring inside a normal word is never masked.
    private static int longestSecretSpanEnd(String[] segments, String[] decodedSegments, int start, Set<String> secretValues) {
        for (var end = segments.length - 1; end >= start; end--) {
            var raw = String.join("/", Arrays.copyOfRange(segments, start, end + 1));
            var decoded = String.join("/", Arrays.copyOfRange(decodedSegments, start, end + 1));
            if (isSecretValue(raw, secretValues) || isSecretValue(decoded, secretValues)) {
                return end;
            }
        }
        return -1;
    }

    private static String redactFragment(String rawFragment, Set<String> secretValues, Predicate<String> sensitiveParam) {
        if (rawFragment.indexOf('=') < 0) {
            return isSecretValue(rawFragment, secretValues) || isSecretValue(decode(rawFragment), secretValues) ? REDACTED : rawFragment;
        }
        return redactQuery(rawFragment, secretValues, sensitiveParam);
    }

    private static String redactQuery(String rawQuery, Set<String> secretValues, Predicate<String> sensitiveParam) {
        var params = new ArrayList<String>();
        for (var param : rawQuery.split("&", -1)) {
            var eq = param.indexOf('=');
            if (eq < 0) {
                params.add(isSecretValue(param, secretValues) || isSecretValue(decode(param), secretValues) ? REDACTED : param);
                continue;
            }

            var rawName = param.substring(0, eq);
            var rawValue = param.substring(eq + 1);
            var redact = sensitiveParam.test(decode(rawName)) || isSecretValue(rawValue, secretValues) || isSecretValue(decode(rawValue), secretValues);
            params.add(redact ? rawName + "=" + REDACTED : param);
        }

        return String.join("&", params);
    }

    private static boolean isUriHeaderCredentialParam(String name) {
        return URI_HEADER_CREDENTIAL_PARAMS.contains(name.toLowerCase(Locale.ROOT));
    }

    private static String decode(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return value;
        }
    }
}
