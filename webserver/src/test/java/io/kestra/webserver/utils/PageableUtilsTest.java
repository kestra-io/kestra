package io.kestra.webserver.utils;

import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.function.Function;

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
}