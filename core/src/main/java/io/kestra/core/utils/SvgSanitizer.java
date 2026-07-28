package io.kestra.core.utils;

import java.util.regex.Pattern;

/**
 * Strips executable content from SVG markup before it is inlined into the browser DOM.
 * <p>
 * Plugin icons are read from third-party plugin JARs (see {@code RegisteredPlugin#icon}), so they
 * are not fully trusted content even though they are bundled by the instance operator rather than
 * submitted by end users.
 * <p>
 * {@code <foreignObject>} is deliberately left in place: Figma's SVG export uses it to fake
 * conic/angular gradients ({@code <foreignObject><div style="background:conic-gradient(...)">}),
 * and several real plugin icons have no visible content without it. It's still safe to keep,
 * because {@link #SCRIPT_TAG}, {@link #EVENT_HANDLER_ATTRIBUTE} and
 * {@link #JAVASCRIPT_URI_ATTRIBUTE} strip their dangerous payloads wherever they appear, including
 * inside a foreignObject. {@code <iframe>}, which has no legitimate purpose in an icon and could
 * otherwise navigate to arbitrary content from inside a preserved foreignObject, is removed instead.
 */
public final class SvgSanitizer {
    private static final Pattern SCRIPT_TAG = Pattern.compile("<script\\b.*?</script\\s*>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern IFRAME_TAG = Pattern.compile("<iframe\\b.*?(</iframe\\s*>|/>)", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern EVENT_HANDLER_ATTRIBUTE = Pattern.compile("\\s+on[a-z]+\\s*=\\s*(\"[^\"]*\"|'[^']*')", Pattern.CASE_INSENSITIVE);
    private static final Pattern JAVASCRIPT_URI_ATTRIBUTE = Pattern.compile("(href|xlink:href|src)\\s*=\\s*(\"\\s*javascript:[^\"]*\"|'\\s*javascript:[^']*')", Pattern.CASE_INSENSITIVE);

    private SvgSanitizer() {
    }

    public static String sanitize(String svg) {
        String sanitized = SCRIPT_TAG.matcher(svg).replaceAll("");
        sanitized = IFRAME_TAG.matcher(sanitized).replaceAll("");
        sanitized = EVENT_HANDLER_ATTRIBUTE.matcher(sanitized).replaceAll("");
        sanitized = JAVASCRIPT_URI_ATTRIBUTE.matcher(sanitized).replaceAll("$1=\"\"");
        return sanitized;
    }
}
