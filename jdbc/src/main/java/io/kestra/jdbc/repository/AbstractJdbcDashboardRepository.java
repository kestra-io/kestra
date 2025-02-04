package io.kestra.jdbc.repository;

import io.kestra.core.events.CrudEvent;
import io.kestra.core.events.CrudEventType;
import io.kestra.core.models.dashboards.ColumnDescriptor;
import io.kestra.core.models.dashboards.Dashboard;
import io.kestra.core.models.dashboards.DataFilter;
import io.kestra.core.models.dashboards.charts.DataChart;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.repositories.DashboardRepositoryInterface;
import io.kestra.core.repositories.QueryBuilderInterface;
import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.data.model.Pageable;
import jakarta.validation.ConstraintViolationException;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.*;
import org.jooq.impl.DSL;

import java.io.IOException;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@AllArgsConstructor
public abstract class AbstractJdbcDashboardRepository extends AbstractJdbcRepository implements DashboardRepositoryInterface {
    protected io.kestra.jdbc.AbstractJdbcRepository<Dashboard> jdbcRepository;
    private final ApplicationEventPublisher<CrudEvent<Dashboard>> eventPublisher;

    List<QueryBuilderInterface<?>> queryBuilders;

    @Override
    public Optional<Dashboard> get(String tenantId, String id) {
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
                return Optional.of(dashboard.toBuilder().sourceCode(fetched.get("source_code", String.class)).build());
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
    public Dashboard save(Dashboard previousDashboard, Dashboard dashboard, String source) throws ConstraintViolationException {
        dashboard = dashboard.toBuilder().sourceCode(source).build();
        if (previousDashboard != null && previousDashboard.equals(dashboard)) {
            return previousDashboard;
        }

        if (previousDashboard == null) {
            dashboard = dashboard.toBuilder().created(Instant.now()).updated(Instant.now()).build();
        } else {
            dashboard = dashboard.toBuilder().id(previousDashboard.getId()).created(previousDashboard.getCreated()).updated(Instant.now()).build();
        }

        Map<Field<Object>, Object> fields = this.jdbcRepository.persistFields(dashboard);
        fields.remove(field("sourceCode"));
        fields.put(field("source_code"), source);

        this.jdbcRepository.persist(dashboard, fields);

        if (previousDashboard == null) {
            eventPublisher.publishEvent(new CrudEvent<>(dashboard, CrudEventType.CREATE));
        } else {
            eventPublisher.publishEvent(new CrudEvent<>(dashboard, previousDashboard, CrudEventType.UPDATE));
        }

        return dashboard;
    }

    @Override
    public Dashboard delete(String tenantId, String id) {
        Optional<Dashboard> dashboard = this.get(tenantId, id);
        if (dashboard.isEmpty()) {
            throw new IllegalStateException("Dashboard " + id + " doesn't exists");
        }

        Dashboard deleted = dashboard.get().toDeleted();

        Map<Field<Object>, Object> fields = this.jdbcRepository.persistFields(deleted);
        fields.remove(field("sourceCode"));
        fields.put(field("source_code"), dashboard.get().getSourceCode());

        this.jdbcRepository.persist(deleted, fields);

        eventPublisher.publishEvent(new CrudEvent<>(dashboard.get(), CrudEventType.DELETE));

        return dashboard.get().toDeleted();
    }

    @Override
    public <F extends Enum<F>> ArrayListTotal<Map<String, Object>> generate(String tenantId, DataChart<?, DataFilter<F, ? extends ColumnDescriptor<F>>> dataChart, ZonedDateTime startDate, ZonedDateTime endDate, Pageable pageable) throws IOException {
        Map<Class<? extends QueryBuilderInterface<?>>, QueryBuilderInterface<?>> queryBuilderByHandledFields = new HashMap<>();

        QueryBuilderInterface<F> queryBuilder = (QueryBuilderInterface<F>) queryBuilderByHandledFields.computeIfAbsent(
            dataChart.getData().repositoryClass(),
            clazz -> queryBuilders.stream().filter(b -> clazz.isAssignableFrom(b.getClass())).findFirst().orElseThrow(() -> new UnsupportedOperationException("No query builder found for " + clazz))
        );

        return queryBuilder.fetchData(tenantId, dataChart.getData(), startDate, endDate, pageable);
    }

    @Override
    public Boolean isEnabled() {
        return true;
    }
}
