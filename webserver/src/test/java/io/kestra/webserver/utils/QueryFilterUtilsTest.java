package io.kestra.webserver.utils;

import io.kestra.core.models.QueryFilter;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;

public class QueryFilterUtilsTest {
    ZonedDateTime date = ZonedDateTime.parse("2024-05-27T15:00:00+02:00[Europe/Paris]");

    @Test
    void validateTimeline_ok() {
        var filters = getFiltersWithStartAndEndDate(date, date.plus(10, ChronoUnit.DAYS));
        assertThatCode(() -> QueryFilterUtils.validateTimeline(filters)).doesNotThrowAnyException();
    }

    @Test
    void validateTimeline_invalid_forEndBeforeStart() {
        var filters = getFiltersWithStartAndEndDate(date, date.minus(10, ChronoUnit.DAYS));
        assertThatCode(() -> QueryFilterUtils.validateTimeline(filters))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining(
                "Start date must be before End Date"
            );
    }

    private List<QueryFilter> getFiltersWithStartAndEndDate(ZonedDateTime start, ZonedDateTime end) {
        return RequestUtils.mapLegacyParamsToFilters(
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
