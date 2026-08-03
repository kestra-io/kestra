package io.kestra.core.models;

import java.util.List;

/**
 * Backend-agnostic result of an access-control check, translated by each backend into its own query
 * language (a jOOQ condition, an Elasticsearch query, …).
 * <p>
 * Generic on purpose: it carries only <em>what</em> the caller may see, not how to enforce it, so it
 * can be reused by any namespace-scoped resource (logs, audit logs, secrets, KV, …).
 *
 * <ul>
 * <li>{@link Kind#GLOBAL} — no restriction (the caller may read every namespace);</li>
 * <li>{@link Kind#NAMESPACES} — restrict to the given namespaces (and their children);</li>
 * <li>{@link Kind#DENY_ALL} — no access, the query must return nothing.</li>
 * </ul>
 *
 * @param kind the kind of restriction.
 * @param namespaces the allowed namespaces (only meaningful for {@link Kind#NAMESPACES}).
 */
public record AccessScope(Kind kind, List<String> namespaces) {

    public enum Kind {
        GLOBAL,
        NAMESPACES,
        DENY_ALL
    }

    public AccessScope {
        namespaces = namespaces == null ? List.of() : List.copyOf(namespaces);
    }

    public static AccessScope global() {
        return new AccessScope(Kind.GLOBAL, List.of());
    }

    public static AccessScope denyAll() {
        return new AccessScope(Kind.DENY_ALL, List.of());
    }

    /**
     * @return a {@link Kind#NAMESPACES} scope, or {@link #denyAll()} when the list is empty (no
     *         accessible namespace means no access).
     */
    public static AccessScope namespaces(List<String> namespaces) {
        return (namespaces == null || namespaces.isEmpty()) ? denyAll() : new AccessScope(Kind.NAMESPACES, namespaces);
    }
}
