package io.kestra.webserver.utils;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.kestra.core.models.QueryFilter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TimeLineSearchTest {

    @Test
    void testExtractFrom_absoluteStartAndEndDate() {
        // GIVEN
        ZonedDateTime startDate = ZonedDateTime.parse("2024-01-01T10:00:00Z");
        ZonedDateTime endDate = ZonedDateTime.parse("2024-01-02T10:00:00Z");

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
    }

    @Test
    void testExtractFrom_relativeStartDate_computesNowMinusDuration() {
        // GIVEN — a relative duration on the (past-oriented) START_DATE field
        Duration range = Duration.ofHours(24);
        List<QueryFilter> filters = List.of(
            QueryFilter.builder().field(QueryFilter.Field.START_DATE).operation(QueryFilter.Op.GREATER_THAN_OR_EQUAL_TO).value(range.toString()).build()
        );

        // WHEN
        ZonedDateTime before = ZonedDateTime.now();
        TimeLineSearch result = TimeLineSearch.extractFrom(filters);
        ZonedDateTime after = ZonedDateTime.now();

        // THEN — startDate resolves to ~ now - 24h
        assertThat(result.getStartDate()).isBetween(before.minus(range), after.minus(range));
    }

    @Test
    void testExtractFrom_noDateFilter_defaultsToEightDaysAgo() {
        // GIVEN — no date boundary at all
        List<QueryFilter> filters = List.of(
            QueryFilter.builder().field(QueryFilter.Field.LABELS).operation(QueryFilter.Op.EQUALS).value("foo:bar").build()
        );

        // WHEN
        ZonedDateTime before = ZonedDateTime.now();
        TimeLineSearch result = TimeLineSearch.extractFrom(filters);
        ZonedDateTime after = ZonedDateTime.now();

        // THEN — flood-guard default of now - 8 days is applied
        assertThat(result.getStartDate()).isBetween(before.minusDays(8), after.minusDays(8));
        assertThat(result.getEndDate()).isNull();
    }

    @Test
    void testExtractFrom_nestedInsideOrNode_findsDateBoundaries() {
        // Given — START_DATE leaf nested inside an OR node
        ZonedDateTime startDate = ZonedDateTime.parse("2024-01-01T10:00:00Z");
        var startDateLeaf = QueryFilter.builder()
            .field(QueryFilter.Field.START_DATE)
            .operation(QueryFilter.Op.EQUALS)
            .value(startDate.toString())
            .build();
        var labelsLeaf = QueryFilter.builder()
            .field(QueryFilter.Field.LABELS)
            .operation(QueryFilter.Op.EQUALS)
            .value("foo:bar")
            .build();
        var orNode = QueryFilter.builder()
            .logical(QueryFilter.Logical.OR)
            .children(List.of(startDateLeaf, labelsLeaf))
            .build();

        // When
        TimeLineSearch result = TimeLineSearch.extractFrom(List.of(orNode));

        // Then — the nested START_DATE is found
        assertThat(result.getStartDate()).isEqualTo(startDate);
    }

    @Test
    void testExtractFrom_invalidValue_throws() {
        // GIVEN — a value that is neither a duration nor an absolute date
        List<QueryFilter> filters = List.of(
            QueryFilter.builder().field(QueryFilter.Field.START_DATE).operation(QueryFilter.Op.EQUALS).value("invalid-value").build()
        );

        // WHEN / THEN
        assertThrows(IllegalArgumentException.class, () -> TimeLineSearch.extractFrom(filters));
    }
}
