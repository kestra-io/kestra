package io.kestra.webserver.errors;

import java.net.URI;
import java.util.regex.Pattern;

/**
 * A registered kind of problem: a stable slug, a stable title, and the HTTP status it always maps to.
 *
 * <p>The slug determines the {@code type} URI, which is the only part of an error response clients are
 * meant to branch on — so once published, a slug is permanent. Resolvable {@code https://} URIs are used
 * rather than URNs per RFC 9457 §3.1.1, which notes that moving from non-resolvable to resolvable later
 * would itself be a breaking change.
 *
 * <p>The title must never be parameterised: it has to stay identical across every occurrence of the same
 * type so clients can use it as a translation key. Per-occurrence text belongs in
 * the problem document's {@code detail} member.
 */
public record ProblemType(String slug, String title, int status) {
    /** Base of every {@code type} URI. Each slug appended to this resolves to that type's documentation. */
    public static final String BASE_URI = "https://kestra.io/docs/api-reference/problems/";

    private static final Pattern SLUG = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*");

    public ProblemType {
        if (slug == null || !SLUG.matcher(slug).matches()) {
            throw new IllegalArgumentException("Problem type slug must be lowercase kebab-case: '%s'.".formatted(slug));
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Problem type '%s' must declare a non-blank title.".formatted(slug));
        }
        if (400 > status || 599 < status) {
            throw new IllegalArgumentException("Problem type '%s' must declare a 4xx or 5xx status, got %d.".formatted(slug, status));
        }
    }

    public static ProblemType of(final String slug, final String title, final int status) {
        return new ProblemType(slug, title, status);
    }

    /** The {@code type} URI carried on the wire. */
    public URI uri() {
        return URI.create(BASE_URI + slug);
    }

    public boolean isServerError() {
        return 500 <= status;
    }
}
