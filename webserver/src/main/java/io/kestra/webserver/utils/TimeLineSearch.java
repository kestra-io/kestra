package io.kestra.webserver.utils;

import java.time.ZonedDateTime;
import java.util.List;

import io.kestra.core.models.QueryFilter;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class TimeLineSearch {
    private ZonedDateTime startDate;
    private ZonedDateTime endDate;

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
        ZonedDateTime now = ZonedDateTime.now();
        ZonedDateTime startDate = null;
        ZonedDateTime endDate = null;

        for (QueryFilter filter : flatLeaves(filters)) {
            if (filter.field() == null) {
                continue;
            }
            switch (filter.field()) {
                case START_DATE -> startDate = QueryFilterUtils.resolveDateValue(QueryFilter.Field.START_DATE, filter.value(), now);
                case END_DATE -> endDate = QueryFilterUtils.resolveDateValue(QueryFilter.Field.END_DATE, filter.value(), now);
            }
        }

        if (startDate == null) {
            // this default startDate filter is there to avoid flooding the database in case of failure on our side
            startDate = now.minusDays(8);
        }

        return new TimeLineSearch(startDate, endDate);
    }

}
