package io.kestra.webserver.responses;

import java.util.List;

import io.kestra.core.repositories.PaginationType;

import io.micronaut.data.model.CursoredPage;
import io.micronaut.data.model.Page;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Paged response for stores that may paginate by cursor instead of offset (e.g. an external log store with no
 * exact row count). Kept as a separate type from {@link PagedResults} rather than a shared {@code total} field:
 * only this cursor-capable path can legitimately omit {@code total}, so the OpenAPI schema (and generated SDK
 * types) can still mark {@code total} as required on every other, offset-only, endpoint.
 */
@Getter
@NoArgsConstructor
public class CursorPagedResults<T> {
    @NotNull
    private List<T> results;

    /**
     * Exact total row count — present in offset mode, omitted in cursor mode (a cursor store has no total).
     */
    private Long total;

    /**
     * Pagination mode of this response: {@link PaginationType#OFFSET} (with an exact {@code total}) or
     * {@link PaginationType#CURSOR} (forward-only, no {@code total}).
     */
    @NotNull
    private PaginationType type;

    /**
     * Opaque token to fetch the page that continues after this one; only present in cursor mode. Cursor pagination
     * is forward-only (there is no "previous" — external log stores don't offer one). A non-empty page always carries
     * a {@code nextCursor}, so the client keeps paging until it gets back an empty page (not until {@code nextCursor}
     * is null).
     */
    private String nextCursor;

    /**
     * Wrap a Micronaut {@link Page}: an offset page (with a total) serializes as {@code {results, total, type:"OFFSET"}};
     * a {@link CursoredPage} (no total) serializes as {@code {results, type:"CURSOR", nextCursor}} (forward-only).
     * <p>
     * The cursor is a single opaque token the store defines and interprets (e.g. an upstream {@code nextPageToken}),
     * carried verbatim as a lone {@code String} element. We read it from {@link CursoredPage#nextPageable()} — the
     * position to resume after this page — and leave it null on an empty (last) page.
     */
    public static <T> CursorPagedResults<T> of(Page<T> page) {
        CursorPagedResults<T> pagedResults = new CursorPagedResults<>();
        pagedResults.results = page.getContent();

        if (page.hasTotalSize()) {
            pagedResults.total = page.getTotalSize();
            pagedResults.type = PaginationType.OFFSET;
        } else {
            pagedResults.type = PaginationType.CURSOR;
            if (page instanceof CursoredPage<T> cursoredPage && !cursoredPage.isEmpty()) {
                pagedResults.nextCursor = cursoredPage.nextPageable().cursor()
                    .map(cursor -> (String) cursor.get(0))
                    .orElse(null);
            }
        }

        return pagedResults;
    }
}
