package io.kestra.repository.postgres;

import io.kestra.core.events.CrudEvent;
import io.kestra.core.models.dashboards.Dashboard;
import io.kestra.jdbc.repository.AbstractJdbcDashboardRepository;
import io.micronaut.context.event.ApplicationEventPublisher;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.jooq.Condition;

@Singleton
@PostgresRepositoryEnabled
public class PostgresDashboardRepository extends AbstractJdbcDashboardRepository {
    @Inject
    public PostgresDashboardRepository(@Named("dashboards") PostgresRepository<Dashboard> repository,
                                       ApplicationEventPublisher<CrudEvent<Dashboard>> eventPublisher) {
        super(repository, eventPublisher);
    }

    @Override
    protected Condition findCondition(String query) {
        return PostgresDashboardRepositoryService.findCondition(this.jdbcRepository, query);
    }
}
