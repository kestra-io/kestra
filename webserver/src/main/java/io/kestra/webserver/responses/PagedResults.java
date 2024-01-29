package io.kestra.webserver.responses;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.kestra.core.repositories.ArrayListTotal;
import lombok.Getter;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotNull;

@Getter
@NoArgsConstructor
public class PagedResults<T> {
    @NotNull
    private ArrayListTotal<T> results;

    @JsonInclude
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
