package io.kestra.webserver.utils;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.kestra.core.models.QueryFilter;
import io.kestra.core.repositories.ExecutionRepositoryInterface.DateFilter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

public class QueryFilterUtilsTest {
    ZonedDateTime date = ZonedDateTime.parse("2024-05-27T15:00:00+02:00[Europe/Paris]");

    @Test
    void validateTimeline_ok() {
        assertThatCode(() -> getFiltersWithStartAndEndDate(date, date.plus(10, ChronoUnit.DAYS))).doesNotThrowAnyException();
    }

    @Test
    void validateTimeline_invalid_forEndBeforeStart() {
        assertThatCode(() -> getFiltersWithStartAndEndDate(date, date.minus(10, ChronoUnit.DAYS)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining(
                "Start date must be before End Date"
            );
    }

    @Test
    void replaceTimeRange_startDateMode_producesStartDateFilter() {
        var filters = timeRangeFilter();

        var result = QueryFilterUtils.replaceTimeRangeWithComputedDateFilter(filters, DateFilter.START_DATE);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).field()).isEqualTo(QueryFilter.Field.START_DATE);
        assertThat(result.get(0).operation()).isEqualTo(QueryFilter.Op.GREATER_THAN_OR_EQUAL_TO);
    }

    @Test
    void replaceTimeRange_endDateMode_producesEndDateFilter() {
        var filters = timeRangeFilter();

        var result = QueryFilterUtils.replaceTimeRangeWithComputedDateFilter(filters, DateFilter.END_DATE);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).field()).isEqualTo(QueryFilter.Field.END_DATE);
        assertThat(result.get(0).operation()).isEqualTo(QueryFilter.Op.GREATER_THAN_OR_EQUAL_TO);
    }

    @Test
    void replaceTimeRange_startOrEndDateMode_producesStartDateFilter() {
        var filters = timeRangeFilter();

        var result = QueryFilterUtils.replaceTimeRangeWithComputedDateFilter(filters, DateFilter.START_OR_END_DATE);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).field()).isEqualTo(QueryFilter.Field.START_DATE);
        assertThat(result.get(0).operation()).isEqualTo(QueryFilter.Op.GREATER_THAN_OR_EQUAL_TO);
    }

    @Test
    void replaceTimeRange_allTimeAllowed_dropsTimeRangeAndAddsNoStartDate() {
        var labelsLeaf = QueryFilter.builder()
            .field(QueryFilter.Field.LABELS)
            .operation(QueryFilter.Op.EQUALS)
            .value("foo:bar")
            .build();
        var filters = List.of(labelsLeaf, allTimeFilter());

        var result = QueryFilterUtils.replaceTimeRangeWithComputedDateFilter(filters, DateFilter.START_DATE, true);

        assertThat(result).containsExactly(labelsLeaf);
    }

    @Test
    void replaceTimeRange_allTimeNotAllowed_fallsBackToDefaultStartDate() {
        var result = QueryFilterUtils.replaceTimeRangeWithComputedDateFilter(List.of(allTimeFilter()), DateFilter.START_DATE);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().field()).isEqualTo(QueryFilter.Field.START_DATE);
        assertThat(ZonedDateTime.parse((String) result.getFirst().value()))
            .isBetween(ZonedDateTime.now().minusDays(8).minusMinutes(1), ZonedDateTime.now().minusDays(8).plusMinutes(1));
    }

    private static QueryFilter allTimeFilter() {
        return QueryFilter.builder()
            .field(QueryFilter.Field.TIME_RANGE)
            .operation(QueryFilter.Op.EQUALS)
            .value(TimeLineSearch.ALL_TIME)
            .build();
    }

    private static List<QueryFilter> timeRangeFilter() {
        return List.of(QueryFilter.builder()
            .field(QueryFilter.Field.TIME_RANGE)
            .operation(QueryFilter.Op.EQUALS)
            .value("PT24H")
            .build());
    }

    private List<QueryFilter> getFiltersWithStartAndEndDate(ZonedDateTime start, ZonedDateTime end) {
        return RequestUtils.getFiltersOrDefaultToLegacyMapping(
            null,
            null,
            null,
            null,
            null,
            null,
            start,
            end,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        );
    }
}
