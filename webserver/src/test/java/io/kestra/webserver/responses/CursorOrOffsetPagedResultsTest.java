package io.kestra.webserver.responses;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.kestra.core.repositories.PaginationType;

import io.micronaut.data.model.CursoredPage;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;

import static org.assertj.core.api.Assertions.assertThat;

class CursorOrOffsetPagedResultsTest {

    @Test
    void offsetPageExposesTotalAndNoCursor() {
        Page<String> page = Page.of(List.of("a", "b"), Pageable.from(1, 2), 42L);

        CursorOrOffsetPagedResults<String> results = CursorOrOffsetPagedResults.of(page);

        assertThat(results.getResults()).containsExactly("a", "b");
        assertThat(results.getType()).isEqualTo(PaginationType.OFFSET);
        assertThat(results.getTotal()).isEqualTo(42L);
        assertThat(results.getNextCursor()).isNull();
    }

    @Test
    void cursorPageExposesCursorsAndNoTotal() {
        List<Pageable.Cursor> cursors = List.of(Pageable.Cursor.of("cursor-a"), Pageable.Cursor.of("cursor-b"));
        CursoredPage<String> page = CursoredPage.of(List.of("a", "b"), Pageable.from(1, 2), cursors, null);

        CursorOrOffsetPagedResults<String> results = CursorOrOffsetPagedResults.of(page);

        assertThat(results.getResults()).containsExactly("a", "b");
        assertThat(results.getType()).isEqualTo(PaginationType.CURSOR);
        // No total in cursor mode — it must be omitted from the response, not serialized as 0.
        assertThat(results.getTotal()).isNull();
        // Forward-only: the opaque token to resume after this page is the last cursor, carried verbatim.
        assertThat(results.getNextCursor()).isEqualTo("cursor-b");
    }
}
