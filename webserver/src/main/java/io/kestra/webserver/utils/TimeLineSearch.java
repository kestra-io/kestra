package io.kestra.webserver.utils;

import io.kestra.core.models.QueryFilter;
import lombok.Builder;
import lombok.Data;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
@Data
@Builder
public class TimeLineSearch {
    private ZonedDateTime startDate;
    private ZonedDateTime endDate;
    private Duration timeRange;
    public static TimeLineSearch extractFrom(List<QueryFilter> filters) {
        ZonedDateTime startDate = null;
        ZonedDateTime endDate = null;
        Duration timeRange = null;

        for (QueryFilter filter : filters) {
            if (filter.field() == null) {
                continue;
            }
            switch (filter.field()) {
                case START_DATE -> startDate = ZonedDateTime.parse(filter.value().toString());
                case END_DATE -> endDate = ZonedDateTime.parse(filter.value().toString());
                case TIME_RANGE -> timeRange = parseDuration(filter.value().toString());
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
    private static Duration parseDuration(String duration) {
        try {
           return Duration.parse(duration);
        } catch (DateTimeParseException e){
            throw new IllegalArgumentException("Invalid duration: " + duration);
        }
    }


}