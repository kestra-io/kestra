package io.kestra.core.repositories;

import io.kestra.core.models.dashboards.ColumnDescriptor;
import io.kestra.core.models.dashboards.DataFilter;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

public interface QueryBuilderInterface<F extends Enum<F>> {
    List<Map<String, Object>> fetchData(String tenantId, DataFilter<F, ? extends ColumnDescriptor<F>> filter, ZonedDateTime startDate, ZonedDateTime endDate) throws IOException;
}
