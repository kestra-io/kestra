package io.kestra.plugin.core.dashboard.data;

import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.dashboards.filters.AbstractFilter;
import io.kestra.core.utils.ListUtils;

public interface IData<F extends Enum<F>> {
    /** Applied through the start and end dates rather than through {@link #whereWithGlobalFilters}. */
    Set<QueryFilter.Field> TIME_FILTER_FIELDS = Set.of(QueryFilter.Field.START_DATE, QueryFilter.Field.END_DATE, QueryFilter.Field.TIME_RANGE);

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

    /** The dashboard filter fields {@link #whereWithGlobalFilters} narrows on; a data source that does not declare them reports none as ignored. */
    default Set<QueryFilter.Field> globalFilterFields() {
        return EnumSet.allOf(QueryFilter.Field.class);
    }

    /** The dashboard filter fields this data source leaves out of its query, in the order they were requested. */
    default List<QueryFilter.Field> ignoredGlobalFilterFields(List<QueryFilter> filters) {
        Set<QueryFilter.Field> applied = globalFilterFields();

        return ListUtils.emptyOnNull(filters).stream()
            .map(QueryFilter::field)
            .filter(field -> !TIME_FILTER_FIELDS.contains(field) && !applied.contains(field))
            .distinct()
            .toList();
    }
}
