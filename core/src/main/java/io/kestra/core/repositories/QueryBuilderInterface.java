package io.kestra.core.repositories;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import io.kestra.core.models.dashboards.ColumnDescriptor;
import io.kestra.core.models.dashboards.DataFilter;
import io.kestra.core.models.dashboards.DataFilterKPI;

import io.micronaut.data.model.Pageable;

public interface QueryBuilderInterface<F extends Enum<F>> {
    /**
     * Whether this query builder can compute dashboard aggregations ({@link #fetchData}/{@link #fetchValue}).
     * <p>
     * Backends that cannot aggregate (e.g. a cloud log store such as GCP Cloud Logging) return {@code false};
     * the dashboard engine then short-circuits to an empty result so charts render "No data" instead of erroring.
     *
     * @return {@code true} by default.
     */
    default boolean canAggregate() {
        return true;
    }

    default Set<F> dateFields() {
        return Collections.emptySet();
    }

    F dateFilterField();

    ArrayListTotal<Map<String, Object>> fetchData(String tenantId, DataFilter<F, ? extends ColumnDescriptor<F>> filter, ZonedDateTime startDate, ZonedDateTime endDate, Pageable pageable)
        throws IOException;

    Double fetchValue(String tenantId, DataFilterKPI<F, ? extends ColumnDescriptor<F>> descriptors, ZonedDateTime startDate, ZonedDateTime endDate, boolean numeratorFilter) throws IOException;
}
