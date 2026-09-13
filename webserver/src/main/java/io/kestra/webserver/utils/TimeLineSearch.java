package io.kestra.webserver.utils;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;

import io.kestra.core.models.QueryFilter;
import io.kestra.core.utils.TypeConverter;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TimeLineSearch {
    private ZonedDateTime startDate;
    private ZonedDateTime endDate;
    private Duration timeRange;

    /**
     * Returns a flat list of all leaf {@link QueryFilter}s by recursing into node children.
     * This ensures date boundary fields nested inside conditional groups (AND/OR nodes) are found.
     */
    private static List<QueryFilter> flatLeaves(List<QueryFilter> filters) {
        return filters.stream()
            .flatMap(f -> f.isNode() ? flatLeaves(f.children()).stream() : java.util.stream.Stream.of(f))
            .toList();
    }

    public static TimeLineSearch extractFrom(List<QueryFilter> filters) {
        ZonedDateTime startDate = null;
        ZonedDateTime endDate = null;
        Duration timeRange = null;

        for (QueryFilter filter : flatLeaves(filters)) {
            if (filter.field() == null) {
                continue;
            }
            switch (filter.field()) {
                case START_DATE -> startDate = TypeConverter.toZonedDateTime(filter.value());
                case END_DATE -> endDate = TypeConverter.toZonedDateTime(filter.value());
                case TIME_RANGE -> timeRange = TypeConverter.toDuration(filter.value());
            }
        }

        if ((startDate != null || endDate != null) && timeRange != null) {
            throw new IllegalArgumentException("Parameters 'startDate'/'endDate' and 'timeRange' are mutually exclusive");
        }

        if (timeRange != null) {
            startDate = ZonedDateTime.now().minus(timeRange);
        }

        if (startDate == null) {
            // this default startDate filter is there to avoid flooding the database in case of failure on our side
            startDate = ZonedDateTime.now().minusDays(8);
        }

        return new TimeLineSearch(startDate, endDate, timeRange);
    }

}