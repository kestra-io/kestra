package io.kestra.core.repositories;

import io.kestra.core.models.dashboards.Dashboard;
import io.kestra.core.models.dashboards.DashboardWithSource;
import io.micronaut.data.model.Pageable;
import jakarta.annotation.Nullable;

import java.util.Optional;

public interface DashboardRepositoryInterface {
    Optional<DashboardWithSource> get(String tenantId, String id);

    ArrayListTotal<Dashboard> list(Pageable pageable, String tenantId, String query);

    default DashboardWithSource save(Dashboard dashboard, String source) {
        return this.save(null, dashboard, source);
    }

    DashboardWithSource save(@Nullable DashboardWithSource previousDashboard, Dashboard dashboard, String source);

    DashboardWithSource delete(String tenantId, String id);
}
