package io.kestra.repository.mysql;

import io.kestra.core.events.CrudEvent;
import io.kestra.core.models.dashboards.Dashboard;
import io.kestra.jdbc.repository.AbstractJdbcDashboardRepository;
import io.micronaut.context.event.ApplicationEventPublisher;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.jooq.Condition;

@Singleton
@MysqlRepositoryEnabled
public class MysqlDashboardRepository extends AbstractJdbcDashboardRepository {
    @Inject
    public MysqlDashboardRepository(@Named("dashboards") MysqlRepository<Dashboard> repository,
                                    ApplicationEventPublisher<CrudEvent<Dashboard>> eventPublisher) {
        super(repository, eventPublisher);
    }

    @Override
    protected Condition findCondition(String query) {
        return MysqlDashboardRepositoryService.findCondition(this.jdbcRepository, query);
    }
}
