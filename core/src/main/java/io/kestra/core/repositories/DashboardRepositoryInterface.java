package io.kestra.core.repositories;

import io.kestra.core.models.dashboards.ColumnDescriptor;
import io.kestra.core.models.dashboards.Dashboard;
import io.kestra.core.models.dashboards.DataFilter;
import io.kestra.core.models.dashboards.charts.DataChart;
import io.micronaut.data.model.Pageable;
import jakarta.annotation.Nullable;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface DashboardRepositoryInterface {
    Boolean isEnabled();

    Optional<Dashboard> get(String tenantId, String id);

    ArrayListTotal<Dashboard> list(Pageable pageable, String tenantId, String query);

    default Dashboard save(Dashboard dashboard, String source) {
        return this.save(null, dashboard, source);
    }

    Dashboard save(@Nullable Dashboard previousDashboard, Dashboard dashboard, String source);

    Dashboard delete(String tenantId, String id);

    <F extends Enum<F>> List<Map<String, Object>> generate(String tenantId, DataChart<?, DataFilter<F, ? extends ColumnDescriptor<F>>> dataChart, ZonedDateTime startDate, ZonedDateTime endDate) throws IOException;
}
