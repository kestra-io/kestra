package org.floworc.webserver.responses;

import org.floworc.core.models.flows.Flow;
import org.floworc.core.repositories.ArrayListTotal;

import javax.validation.constraints.NotNull;

public class PagedResults<T> {
    @NotNull
    private ArrayListTotal<T> results;
    @NotNull
    private long total;

    private PagedResults(ArrayListTotal<T> results) {
        this.results = results;
        this.total = results.getTotal();
    }

    public static PagedResults of(ArrayListTotal<Flow> results) {
        return new PagedResults((results));
    }
}
