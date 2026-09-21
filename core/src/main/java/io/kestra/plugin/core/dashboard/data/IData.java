package io.kestra.plugin.core.dashboard.data;

import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.dashboards.filters.AbstractFilter;

public interface IData<F extends Enum<F>> {
    List<AbstractFilter<F>> whereWithGlobalFilters(List<QueryFilter> queryFilterList, ZonedDateTime startDate, ZonedDateTime endDate, List<AbstractFilter<F>> where);

    /**
     * Fields filtered by a duration — an ISO-8601 duration such as {@code PT1S}, or a number of seconds — rather than
     * by the raw number the store persists for them.
     *
     * @see io.kestra.core.models.dashboards.filters.DurationFilters
     */
    default Set<F> durationFields() {
        return Collections.emptySet();
    }
}
