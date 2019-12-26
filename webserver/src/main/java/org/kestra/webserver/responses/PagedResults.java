package org.kestra.webserver.responses;

import lombok.Getter;
import lombok.NoArgsConstructor;
import org.kestra.core.models.flows.Flow;
import org.kestra.core.repositories.ArrayListTotal;

import javax.validation.constraints.NotNull;

@Getter
@NoArgsConstructor
public class PagedResults<T> {
    @NotNull
    private ArrayListTotal<T> results;
    @NotNull
    private long total;

    private PagedResults(ArrayListTotal<T> results) {
        this.results = results;
        this.total = results.getTotal();
    }

    public static <T> PagedResults<T> of(ArrayListTotal<T> results) {
        return new PagedResults<>(results);
    }
}
