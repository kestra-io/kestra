package io.kestra.webserver.responses;

import java.util.List;

import io.kestra.core.repositories.ArrayListTotal;

import io.micronaut.data.model.Page;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Paged response for the offset-pagination endpoints (the vast majority of list APIs): a store that always knows
 * its row count, so both {@code results} and {@code total} are always present. A store that may not know its
 * total (e.g. an external log store) uses {@link CursorOrOffsetPagedResults} — see that class for why the two
 * are kept apart.
 */
@Getter
@NoArgsConstructor
public class PagedResults<T> {
    @NotNull
    private List<T> results;

    @NotNull
    private Long total;

    private PagedResults(ArrayListTotal<T> results) {
        this.results = results;
        this.total = results.getTotal();
    }

    public static <T> PagedResults<T> of(ArrayListTotal<T> results) {
        return new PagedResults<>(results);
    }

    /**
     * Wrap a Micronaut offset {@link Page} — always has a total, since {@code Page} (as opposed to
     * {@link io.micronaut.data.model.CursoredPage}) is only produced by stores that know their exact row count.
     */
    public static <T> PagedResults<T> of(Page<T> page) {
        PagedResults<T> pagedResults = new PagedResults<>();
        pagedResults.results = page.getContent();
        pagedResults.total = page.getTotalSize();
        return pagedResults;
    }
}
