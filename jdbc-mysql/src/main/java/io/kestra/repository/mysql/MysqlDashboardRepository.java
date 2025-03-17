package io.kestra.repository.mysql;

import io.kestra.core.events.CrudEvent;
import io.kestra.core.models.dashboards.Dashboard;
import io.kestra.core.repositories.QueryBuilderInterface;
import io.kestra.jdbc.repository.AbstractJdbcDashboardRepository;
import io.micronaut.context.event.ApplicationEventPublisher;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.jooq.Condition;

import java.util.List;

@Singleton
@MysqlRepositoryEnabled
public class MysqlDashboardRepository extends AbstractJdbcDashboardRepository {
    @Inject
    public MysqlDashboardRepository(@Named("dashboards") MysqlRepository<Dashboard> repository,
                                    ApplicationEventPublisher<CrudEvent<Dashboard>> eventPublisher,
                                    List<QueryBuilderInterface<?>> queryBuilders) {
        super(repository, eventPublisher, queryBuilders);
    }

    @Override
    protected Condition findCondition(String query) {
        return MysqlDashboardRepositoryService.findCondition(this.jdbcRepository, query);
    }
}
