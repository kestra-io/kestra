package io.kestra.webserver.utils;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class PageableUtilsTest {

    @Test
    void testFrom() {
        final Function<String, String> toUpper = (String key) -> key.toUpperCase(Locale.ROOT);

        final Pageable pagedSortedMapped = PageableUtils.from(1, 42, List.of("key:asc"), toUpper);
        final Pageable pagedSorted = PageableUtils.from(1, 42, List.of("key:asc"));
        final Pageable paged = PageableUtils.from(1, 42);

        assertFalse(pagedSortedMapped.isUnpaged());
        assertTrue(pagedSortedMapped.isSorted());
        assertThat(pagedSortedMapped.getSort().getOrderBy().getFirst()).isEqualTo(Sort.Order.asc("KEY"));

        assertFalse(pagedSorted.isUnpaged());
        assertTrue(pagedSorted.isSorted());
        assertThat(pagedSorted.getSort().getOrderBy().getFirst()).isEqualTo(Sort.Order.asc("key"));

        assertFalse(paged.isUnpaged());
        assertFalse(paged.isSorted());

        assertThrows(IllegalArgumentException.class, () -> PageableUtils.from(1, -1, List.of("key:asc"), toUpper));
        assertThrows(IllegalArgumentException.class, () -> PageableUtils.from(1, -1, List.of("key:asc")));
        assertThrows(IllegalArgumentException.class, () -> PageableUtils.from(1, -1));
    }

    @Test
    void shouldThrowWhenSortFieldIsUnknown() {
        // Given a mapper that only knows "id" — any other field returns null
        Function<String, String> mapper = Map.of("id", "id")::get;

        // When an unknown sort field is supplied
        HttpStatusException e = assertThrows(HttpStatusException.class,
            () -> PageableUtils.from(1, 10, List.of("unknownColumn:asc"), mapper));

        // Then a 422 is returned with the unknown field name
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, e.getStatus());
        assertThat(e.getMessage()).contains("unknownColumn");
    }
}