package io.kestra.core.utils;

import java.util.regex.Pattern;

/**
 * Strips executable content from SVG markup before it is inlined into the browser DOM.
 * <p>
 * Plugin icons are read from third-party plugin JARs (see {@code RegisteredPlugin#icon}), so they
 * are not fully trusted content even though they are bundled by the instance operator rather than
 * submitted by end users.
 */
public final class SvgSanitizer {
    private static final Pattern SCRIPT_TAG = Pattern.compile("<script\\b.*?</script\\s*>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern FOREIGN_OBJECT_TAG = Pattern.compile("<foreignObject\\b.*?</foreignObject\\s*>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern EVENT_HANDLER_ATTRIBUTE = Pattern.compile("\\s+on[a-z]+\\s*=\\s*(\"[^\"]*\"|'[^']*')", Pattern.CASE_INSENSITIVE);
    private static final Pattern JAVASCRIPT_URI_ATTRIBUTE = Pattern.compile("(href|xlink:href)\\s*=\\s*(\"\\s*javascript:[^\"]*\"|'\\s*javascript:[^']*')", Pattern.CASE_INSENSITIVE);

    private SvgSanitizer() {
    }

    public static String sanitize(String svg) {
        String sanitized = SCRIPT_TAG.matcher(svg).replaceAll("");
        sanitized = FOREIGN_OBJECT_TAG.matcher(sanitized).replaceAll("");
        sanitized = EVENT_HANDLER_ATTRIBUTE.matcher(sanitized).replaceAll("");
        sanitized = JAVASCRIPT_URI_ATTRIBUTE.matcher(sanitized).replaceAll("$1=\"\"");
        return sanitized;
    }
}
