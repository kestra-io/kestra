package io.kestra.webserver.utils;


import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.QueryFilter.Field;
import io.kestra.core.models.QueryFilter.Op;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class TimeLineSearchTest {

    @Test
    void testExtractFrom() {
        // GIVEN
        ZonedDateTime startDate = ZonedDateTime.parse("2024-01-01T10:00:00Z");
        ZonedDateTime endDate = ZonedDateTime.parse("2024-01-02T10:00:00Z");
        Duration timeRange = Duration.ofHours(24);

        List<QueryFilter> filters = List.of(
            QueryFilter.builder().field(QueryFilter.Field.START_DATE).operation(QueryFilter.Op.EQUALS).value(startDate.toString()).build(),
            QueryFilter.builder().field(QueryFilter.Field.END_DATE).operation(QueryFilter.Op.EQUALS).value(endDate.toString()).build()
        );
        // WHEN
        TimeLineSearch result = TimeLineSearch.extractFrom(filters);

        // THEN
        assertThat(result).isNotNull();
        assertThat(result.getStartDate()).isEqualTo(startDate);
        assertThat(result.getEndDate()).isEqualTo(endDate);

        filters = List.of(
            QueryFilter.builder().field(QueryFilter.Field.TIME_RANGE).operation(QueryFilter.Op.EQUALS).value(timeRange.toString()).build()
        );
        // WHEN
        result = TimeLineSearch.extractFrom(filters);

        // THEN
        assertThat(result).isNotNull();
        assertThat(result.getTimeRange()).isEqualTo(timeRange);
    }

    @Test
    void testExtractFromWithInvalidDuration() {
        // GIVEN
        List<QueryFilter> filters = List.of(
            QueryFilter.builder().field(QueryFilter.Field.TIME_RANGE).operation(QueryFilter.Op.EQUALS).value("invalid-duration").build()
        );
        // WHEN
        Exception exception = assertThrows(IllegalArgumentException.class, () -> TimeLineSearch.extractFrom(filters));
        // THEN
        assertThat(exception.getMessage()).contains("Invalid duration");
    }

    @Test
    void testUpdateFiltersRemovesTimeRange() {
        // GIVEN
        ZonedDateTime startDate = ZonedDateTime.parse("2024-01-01T10:00:00Z");
        ZonedDateTime newStartDate = ZonedDateTime.parse("2024-01-02T10:00:00Z");

        List<QueryFilter> filters = List.of(
            QueryFilter.builder().field(Field.START_DATE).operation(Op.EQUALS).value(startDate.toString()).build(),
            QueryFilter.builder().field(Field.TIME_RANGE).operation(Op.EQUALS).value(Duration.ofHours(24).toString()).build()
        );
        // WHEN
        List<QueryFilter> updatedFilters = QueryFilterUtils.updateFilters(filters, newStartDate);
        // THEN
        assertThat(updatedFilters).hasSize(2)
            .satisfiesExactly(
                filter -> {
                    assertThat(filter.field()).isEqualTo(Field.START_DATE);
                    assertThat(filter.operation()).isEqualTo(Op.EQUALS);
                    assertThat(filter.value()).isEqualTo(newStartDate.toString());
                },
                filter -> {
                    assertThat(filter.field()).isEqualTo(Field.START_DATE);
                    assertThat(filter.operation()).isEqualTo(Op.GREATER_THAN_OR_EQUAL_TO);
                    assertThat(filter.value()).isEqualTo(newStartDate.toString());
                }
            );
    }

    @Test
    void testUpdateFiltersKeepsUnrelatedFilters() {
        // GIVEN
        ZonedDateTime startDate = ZonedDateTime.parse("2024-01-01T10:00:00Z");
        ZonedDateTime newStartDate = ZonedDateTime.parse("2024-01-02T10:00:00Z");
        ZonedDateTime endDate = ZonedDateTime.parse("2024-01-03T10:00:00Z");

        List<QueryFilter> filters = List.of(
            QueryFilter.builder().field(QueryFilter.Field.START_DATE).operation(QueryFilter.Op.EQUALS).value(startDate.toString()).build(),
            QueryFilter.builder().field(QueryFilter.Field.END_DATE).operation(QueryFilter.Op.EQUALS).value(endDate.toString()).build(),
            QueryFilter.builder().field(QueryFilter.Field.TIME_RANGE).operation(QueryFilter.Op.EQUALS).value(Duration.ofHours(24).toString()).build()
        );
        // WHEN
        List<QueryFilter> updatedFilters = QueryFilterUtils.updateFilters(filters, newStartDate);
        // THEN
        assertThat(updatedFilters).hasSize(3)
            .satisfiesExactly(
                filter -> {
                    assertThat(filter.field()).isEqualTo(Field.START_DATE);
                    assertThat(filter.operation()).isEqualTo(Op.EQUALS);
                    assertThat(filter.value()).isEqualTo(newStartDate.toString());
                },
                filter -> {
                    assertThat(filter.field()).isEqualTo(Field.END_DATE);
                    assertThat(filter.operation()).isEqualTo(Op.EQUALS);
                    assertThat(filter.value()).isEqualTo(endDate.toString());
                },
                filter -> {
                    assertThat(filter.field()).isEqualTo(Field.START_DATE);
                    assertThat(filter.operation()).isEqualTo(Op.GREATER_THAN_OR_EQUAL_TO);
                    assertThat(filter.value()).isEqualTo(newStartDate.toString());
                }
            );
    }
}