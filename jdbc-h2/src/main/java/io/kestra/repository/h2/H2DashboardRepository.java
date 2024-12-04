package io.kestra.repository.h2;

import io.kestra.core.events.CrudEvent;
import io.kestra.core.models.dashboards.Dashboard;
import io.kestra.jdbc.repository.AbstractJdbcDashboardRepository;
import io.micronaut.context.event.ApplicationEventPublisher;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.jooq.Condition;

@Singleton
@H2RepositoryEnabled
public class H2DashboardRepository extends AbstractJdbcDashboardRepository {
    @Inject
    public H2DashboardRepository(@Named("dashboards") H2Repository<Dashboard> repository,
                                 ApplicationEventPublisher<CrudEvent<Dashboard>> eventPublisher) {
        super(repository, eventPublisher);
    }

    @Override
    protected Condition findCondition(String query) {
        return H2DashboardRepositoryService.findCondition(this.jdbcRepository, query);
    }
}
