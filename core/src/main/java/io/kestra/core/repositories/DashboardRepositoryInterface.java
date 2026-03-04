package io.kestra.core.repositories;

import io.kestra.core.models.dashboards.ColumnDescriptor;
import io.kestra.core.models.dashboards.Dashboard;
import io.kestra.core.models.dashboards.DataFilter;
import io.kestra.core.models.dashboards.DataFilterKPI;
import io.kestra.core.models.dashboards.charts.DataChart;
import io.kestra.core.models.dashboards.charts.DataChartKPI;
import io.micronaut.data.model.Pageable;
import jakarta.annotation.Nullable;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface DashboardRepositoryInterface {

    /**
     * Gets the total number of Dashboards.
     *
     * @return the total number.
     */
    long countAllForAllTenants();

    Boolean isEnabled();

    Optional<Dashboard> get(String tenantId, String id);

    ArrayListTotal<Dashboard> list(Pageable pageable, String tenantId, String query);

    List<Dashboard> findAll(String tenantId);

    List<Dashboard> findAllWithNoAcl(String tenantId);

    default Dashboard save(Dashboard dashboard, String source) {
        return this.save(null, dashboard, source);
    }

    Dashboard save(@Nullable Dashboard previousDashboard, Dashboard dashboard, String source);

    Dashboard delete(String tenantId, String id);

    <F extends Enum<F>> ArrayListTotal<Map<String, Object>> generate(String tenantId, DataChart<?, DataFilter<F, ? extends ColumnDescriptor<F>>> dataChart, ZonedDateTime startDate, ZonedDateTime endDate, Pageable pageable) throws IOException;

    <F extends Enum<F>> List<Map<String, Object>> generateKPI(String tenantId, DataChartKPI<?, DataFilterKPI<F, ? extends ColumnDescriptor<F>>> dataChart, ZonedDateTime startDate, ZonedDateTime endDate) throws IOException;
}
