package io.kestra.core.repositories;

/**
 * The pagination model a repository exposes for its {@link io.micronaut.data.model.Page}-returning
 * finds — and, correspondingly, the shape of a paginated API response.
 * <p>
 * This is distinct from Micronaut's {@link io.micronaut.data.model.Pageable.Mode} (OFFSET /
 * CURSOR_NEXT / CURSOR_PREVIOUS), which describes the direction of a single request; this enum
 * describes the capability/response model, so a stateless external store advertises it once and the
 * client (and tests) can adapt regardless of which page is requested first.
 */
public enum PaginationType {
    /** Random-access pages with an exact total ({@code Page.getTotalSize()}); the default (JDBC/Elasticsearch). */
    OFFSET,

    /**
     * Forward-only cursor with no total (a {@link io.micronaut.data.model.CursoredPage}); used by stateless
     * external stores (e.g. GCP Cloud Logging, S3, Datadog) that expose only a next-page token.
     */
    CURSOR
}
