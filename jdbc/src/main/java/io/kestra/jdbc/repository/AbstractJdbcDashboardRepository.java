package io.kestra.jdbc.repository;

import io.kestra.core.events.CrudEvent;
import io.kestra.core.models.dashboards.ColumnDescriptor;
import io.kestra.core.models.dashboards.Dashboard;
import io.kestra.core.models.dashboards.DataFilter;
import io.kestra.core.models.dashboards.DataFilterKPI;
import io.kestra.core.models.dashboards.charts.DataChart;
import io.kestra.core.models.dashboards.charts.DataChartKPI;
import io.kestra.core.queues.QueueService;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.repositories.DashboardRepositoryInterface;
import io.kestra.core.repositories.QueryBuilderInterface;
import io.kestra.plugin.core.dashboard.chart.kpis.KpiOption;
import io.micronaut.context.event.ApplicationEventPublisher;
import io.micronaut.data.model.Pageable;
import jakarta.validation.ConstraintViolationException;
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

import static io.kestra.core.utils.MathUtils.roundDouble;

@Slf4j
public abstract class AbstractJdbcDashboardRepository extends AbstractJdbcCrudRepository<Dashboard> implements DashboardRepositoryInterface {
    private final ApplicationEventPublisher<CrudEvent<Dashboard>> eventPublisher;
    private final List<QueryBuilderInterface<?>> queryBuilders;

    public AbstractJdbcDashboardRepository(io.kestra.jdbc.AbstractJdbcRepository<Dashboard> jdbcRepository,
                                           QueueService queueService,
                                           ApplicationEventPublisher<CrudEvent<Dashboard>> eventPublisher,
                                           List<QueryBuilderInterface<?>> queryBuilders) {
        super(jdbcRepository, queueService);
        this.eventPublisher = eventPublisher;
        this.queryBuilders = queryBuilders;
    }


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
        return findPage(pageable, tenantId, this.findCondition(query));
    }

    @Override
    public List<Dashboard> findAllWithNoAcl(String tenantId) {
        return findAll(this.defaultFilterWithNoACL(tenantId));
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
        this.eventPublisher.publishEvent(CrudEvent.of(previousDashboard, dashboard));

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
        fields.put(field("source_code"), deleted.getSourceCode());

        this.jdbcRepository.persist(deleted, fields);
        this.eventPublisher.publishEvent(CrudEvent.delete(dashboard.get()));

        return deleted;
    }

    @Override
    public <F extends Enum<F>> ArrayListTotal<Map<String, Object>> generate(String tenantId, DataChart<?, DataFilter<F, ? extends ColumnDescriptor<F>>> dataChart, ZonedDateTime startDate, ZonedDateTime endDate, Pageable pageable) throws IOException {
        Map<Class<? extends QueryBuilderInterface<?>>, QueryBuilderInterface<?>> queryBuilderByHandledFields = new HashMap<>();

        @SuppressWarnings("unchecked")
        QueryBuilderInterface<F> queryBuilder = (QueryBuilderInterface<F>) queryBuilderByHandledFields.computeIfAbsent(
            dataChart.getData().repositoryClass(),
            clazz -> queryBuilders
                .stream()
                .filter(b -> clazz.isAssignableFrom(b.getClass()))
                .findFirst()
                .orElseThrow(() -> new UnsupportedOperationException("No query builder found for " + clazz))
        );

        return queryBuilder.fetchData(tenantId, dataChart.getData(), startDate, endDate, pageable);
    }

    @Override
    public <F extends Enum<F>> List<Map<String, Object>> generateKPI(String tenantId, DataChartKPI<?, DataFilterKPI<F, ? extends ColumnDescriptor<F>>> dataChart, ZonedDateTime startDate, ZonedDateTime endDate) throws IOException {
        Map<Class<? extends QueryBuilderInterface<?>>, QueryBuilderInterface<?>> queryBuilderByHandledFields = new HashMap<>();

        @SuppressWarnings("unchecked")
        QueryBuilderInterface<F> queryBuilder = (QueryBuilderInterface<F>) queryBuilderByHandledFields.computeIfAbsent(
            dataChart.getData().repositoryClass(),
            clazz -> queryBuilders
                .stream()
                .filter(b -> clazz.isAssignableFrom(b.getClass()))
                .findFirst()
                .orElseThrow(() -> new UnsupportedOperationException("No query builder found for " + clazz))
        );

        Double filteredValue = queryBuilder.fetchValue(tenantId, dataChart.getData(), startDate, endDate, dataChart.getData().getNumerator() != null);

        if (dataChart.getChartOptions() != null && dataChart.getChartOptions().getNumberType().equals(KpiOption.NumberType.PERCENTAGE)) {
            Double totalValue = queryBuilder.fetchValue(tenantId, dataChart.getData(), startDate, endDate, false);
            if (totalValue == null || totalValue == 0) return List.of(Map.of("value", 0.0));
            Double percentageValue = (filteredValue / totalValue) * 100;
            return List.of(Map.of("value", roundDouble(percentageValue, 2)));
        }

        return List.of(Map.of("value", roundDouble(filteredValue, 2)));
    }

    @Override
    public Boolean isEnabled() {
        return true;
    }
}
