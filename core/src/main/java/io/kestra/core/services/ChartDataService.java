package io.kestra.core.services;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import io.kestra.core.models.dashboards.ColumnDescriptor;
import io.kestra.core.models.dashboards.DataFilter;
import io.kestra.core.models.dashboards.DataFilterKPI;
import io.kestra.core.models.dashboards.charts.DataChart;
import io.kestra.core.models.dashboards.charts.DataChartKPI;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.repositories.QueryBuilderInterface;
import io.kestra.plugin.core.dashboard.chart.kpis.KpiOption;

import io.micronaut.data.model.Pageable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import static io.kestra.core.utils.MathUtils.roundDouble;

/**
 * Computes the data behind a dashboard chart by dispatching to the {@link QueryBuilderInterface}
 * that handles the chart's underlying entity, and is therefore independent of the backend those
 * query builders are implemented against.
 */
@Singleton
public class ChartDataService {
    private final List<QueryBuilderInterface<?>> queryBuilders;
    private final Map<Class<? extends QueryBuilderInterface<?>>, QueryBuilderInterface<?>> queryBuilderByRepositoryClass = new ConcurrentHashMap<>();

    @Inject
    public ChartDataService(List<QueryBuilderInterface<?>> queryBuilders) {
        this.queryBuilders = Objects.requireNonNull(queryBuilders, "queryBuilders must not be null");
    }

    public <F extends Enum<F>> ArrayListTotal<Map<String, Object>> generate(
        String tenantId,
        DataChart<?, DataFilter<F, ? extends ColumnDescriptor<F>>> dataChart,
        ZonedDateTime startDate,
        ZonedDateTime endDate,
        Pageable pageable
    ) throws IOException {
        return this.<F>queryBuilder(dataChart.getData().repositoryClass())
            .fetchData(tenantId, dataChart.getData(), startDate, endDate, pageable);
    }

    public <F extends Enum<F>> List<Map<String, Object>> generateKPI(
        String tenantId,
        DataChartKPI<?, DataFilterKPI<F, ? extends ColumnDescriptor<F>>> dataChart,
        ZonedDateTime startDate,
        ZonedDateTime endDate
    ) throws IOException {
        QueryBuilderInterface<F> queryBuilder = this.queryBuilder(dataChart.getData().repositoryClass());

        Double filteredValue = queryBuilder.fetchValue(tenantId, dataChart.getData(), startDate, endDate, dataChart.getData().getNumerator() != null);

        if (dataChart.getChartOptions() != null && dataChart.getChartOptions().getNumberType().equals(KpiOption.NumberType.PERCENTAGE)) {
            Double totalValue = queryBuilder.fetchValue(tenantId, dataChart.getData(), startDate, endDate, false);
            if (totalValue == null || totalValue == 0) {
                return List.of(Map.of("value", 0.0));
            }
            double percentageValue = (filteredValue / totalValue) * 100;
            return List.of(Map.of("value", roundDouble(percentageValue, 2)));
        }

        return List.of(Map.of("value", roundDouble(filteredValue, 2)));
    }

    @SuppressWarnings("unchecked")
    private <F extends Enum<F>> QueryBuilderInterface<F> queryBuilder(Class<? extends QueryBuilderInterface<F>> repositoryClass) {
        return (QueryBuilderInterface<F>) queryBuilderByRepositoryClass.computeIfAbsent(
            repositoryClass,
            clazz -> queryBuilders
                .stream()
                .filter(builder -> clazz.isAssignableFrom(builder.getClass()))
                .findFirst()
                .orElseThrow(() -> new UnsupportedOperationException("Cannot compute the chart data: no query builder is registered for '%s'.".formatted(clazz.getName())))
        );
    }
}
