package io.kestra.webserver.responses;

import lombok.Getter;
import lombok.NoArgsConstructor;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.repositories.ArrayListTotal;

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
