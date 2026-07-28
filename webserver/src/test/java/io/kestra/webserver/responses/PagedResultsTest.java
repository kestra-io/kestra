package io.kestra.webserver.responses;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;

import static org.assertj.core.api.Assertions.assertThat;

class PagedResultsTest {

    @Test
    void offsetPageExposesResultsAndTotal() {
        Page<String> page = Page.of(List.of("a", "b"), Pageable.from(1, 2), 42L);

        PagedResults<String> results = PagedResults.of(page);

        assertThat(results.getResults()).containsExactly("a", "b");
        assertThat(results.getTotal()).isEqualTo(42L);
    }
}
