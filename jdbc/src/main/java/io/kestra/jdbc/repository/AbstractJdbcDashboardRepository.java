package io.kestra.jdbc.repository;

import io.kestra.core.events.CrudEvent;
import io.kestra.core.events.CrudEventType;
import io.kestra.core.models.dashboards.Dashboard;
import io.kestra.core.models.dashboards.DashboardWithSource;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.repositories.DashboardRepositoryInterface;
import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.data.model.Pageable;
import jakarta.validation.ConstraintViolationException;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.*;
import org.jooq.impl.DSL;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

@Slf4j
@AllArgsConstructor
public abstract class AbstractJdbcDashboardRepository extends AbstractJdbcRepository implements DashboardRepositoryInterface {
    protected io.kestra.jdbc.AbstractJdbcRepository<Dashboard> jdbcRepository;
    private final ApplicationEventPublisher<CrudEvent<Dashboard>> eventPublisher;

    @Override
    public Optional<DashboardWithSource> get(String tenantId, String id) {
        return jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                DSLContext context = DSL.using(configuration);
                Select<Record2<String, String>> from;

                from = context
                        .select(
                            field("source_code", String.class),
                            field("value", String.class)
                        )
                        .from(jdbcRepository.getTable())
                        .where(this.defaultFilter(tenantId))
                        .and(field("id", String.class).eq(id));
                Record2<String, String> fetched = from.fetchAny();

                if (fetched == null) {
                    return Optional.empty();
                }

                Dashboard dashboard = jdbcRepository.map(fetched);
                String source = fetched.get("source_code", String.class);
                return Optional.of(DashboardWithSource.of(dashboard, source));
            });
    }

    abstract protected Condition findCondition(String query);

    @Override
    public ArrayListTotal<Dashboard> list(Pageable pageable, String tenantId, String query) {
        return this.jdbcRepository
            .getDslContextWrapper()
            .transactionResult(configuration -> {
                DSLContext context = DSL.using(configuration);

                SelectConditionStep<Record1<Object>> select = context
                    .select(
                        field("value")
                    )
                    .hint(context.configuration().dialect().supports(SQLDialect.MYSQL) ? "SQL_CALC_FOUND_ROWS" : null)
                    .from(jdbcRepository.getTable())
                    .where(this.defaultFilter(tenantId));

                select = select.and(this.findCondition(query));

                return this.jdbcRepository.fetchPage(context, select, pageable);
            });
    }

    @Override
    public DashboardWithSource save(DashboardWithSource previousDashboard, Dashboard dashboard, String source) throws ConstraintViolationException {
        if (dashboard instanceof DashboardWithSource dashboardWithSource) {
            dashboard = dashboardWithSource.toDashboard();
        }

        if (previousDashboard != null && previousDashboard.equals(DashboardWithSource.of(dashboard, source))) {
            return previousDashboard;
        }

        if (previousDashboard == null) {
            dashboard = dashboard.toBuilder().created(Instant.now()).updated(Instant.now()).build();
        } else {
            dashboard = dashboard.toBuilder().id(previousDashboard.getId()).created(previousDashboard.getCreated()).updated(Instant.now()).build();
        }

        Map<Field<Object>, Object> fields = this.jdbcRepository.persistFields(dashboard);
        fields.put(field("source_code"), source);

        this.jdbcRepository.persist(dashboard, fields);

        if (previousDashboard == null) {
            eventPublisher.publishEvent(new CrudEvent<>(dashboard, CrudEventType.CREATE));
        } else {
            eventPublisher.publishEvent(new CrudEvent<>(dashboard, previousDashboard, CrudEventType.UPDATE));
        }

        return DashboardWithSource.of(dashboard, source);
    }

    @Override
    public DashboardWithSource delete(String tenantId, String id) {
        Optional<DashboardWithSource> dashboard = this.get(tenantId, id);
        if (dashboard.isEmpty()) {
            throw new IllegalStateException("Dashboard " + id + " doesn't exists");
        }

        Dashboard deleted = dashboard.get().toDashboard().toDeleted();

        Map<Field<Object>, Object> fields = this.jdbcRepository.persistFields(deleted);
        fields.put(field("source_code"), dashboard.get().getSourceCode());

        this.jdbcRepository.persist(deleted, fields);

        eventPublisher.publishEvent(new CrudEvent<>(dashboard.get(), CrudEventType.DELETE));

        return (DashboardWithSource) dashboard.get().toDeleted();
    }
}
