package io.kestra.plugin.core.dashboard.data;

import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.dashboards.filters.AbstractFilter;

import java.time.ZonedDateTime;
import java.util.List;

public interface IData<F extends Enum<F>> {
    List<AbstractFilter<F>> whereWithGlobalFilters(List<QueryFilter> queryFilterList, ZonedDateTime startDate, ZonedDateTime endDate, List<AbstractFilter<F>> where);
}
