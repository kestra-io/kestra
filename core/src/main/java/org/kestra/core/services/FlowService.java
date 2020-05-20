package org.kestra.core.services;

import io.micronaut.data.model.Pageable;
import org.kestra.core.models.executions.metrics.ExecutionMetricsAggregation;
import org.kestra.core.models.executions.metrics.Stats;
import org.kestra.core.models.flows.Flow;
import org.kestra.core.repositories.ArrayListTotal;
import org.kestra.core.repositories.ExecutionRepositoryInterface;
import org.kestra.core.repositories.FlowRepositoryInterface;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FlowService {
    @Inject
    FlowRepositoryInterface flowRepositoryInterface;

    @Inject
    ExecutionRepositoryInterface executionRepositoryInterface;

    public static final double TREND_VARIATION_PERCENTAGE_THRESHOLD = 30;

    /**
     * Find and aggregate executions for all existing flows
     *
     * @param query
     * @param pageable
     * @return
     */
    public ArrayListTotal<ExecutionMetricsAggregation> findAndAggregate(String query, String startDate, Pageable pageable) {
        final String execQuery = "state.startDate:[" + startDate + " TO *]";
        Map<String, ExecutionMetricsAggregation> periodAggregationMap =
            executionRepositoryInterface.aggregateByStateWithDurationStats(execQuery, pageable);

        Map<String, Stats> last24hStatsMap =
            executionRepositoryInterface.findLast24hDurationStats(execQuery, pageable);

        ArrayListTotal<Flow> flows = flowRepositoryInterface.find(query, pageable);

        // We build a map of all the flows (since some flows do no have executions but we want them)
        Map<String, ExecutionMetricsAggregation> result = new HashMap<>();
        flows.stream()
            .forEach(flow -> {

                final String mapKey = Flow.uniqueIdWithoutRevision(flow.getNamespace(), flow.getId());

                ExecutionMetricsAggregation periodAggregation = periodAggregationMap.get(mapKey);
                Stats last24hStats = last24hStatsMap.get(mapKey);

                result.put(mapKey, ExecutionMetricsAggregation.builder()
                    .id(flow.getId())
                    .namespace(flow.getNamespace())
                    .metrics(periodAggregation != null && periodAggregation.getMetrics() != null ? periodAggregation.getMetrics() : null)
                    .periodDurationStats(periodAggregation != null && periodAggregation.getPeriodDurationStats() != null ? periodAggregation.getPeriodDurationStats() : null)
                    .lastDayDurationStats(last24hStats)
                    .trend(computeTrendOnAvgDuration(periodAggregation != null ? periodAggregation.getPeriodDurationStats() : null, last24hStats))
                    .build());
            });


        return new ArrayListTotal<ExecutionMetricsAggregation>(new ArrayList(result.values()),
            flows.getTotal());
    }

    /**
     * Indicates whether avg time taken by an execution is trending up, down or neutral
     * <p>
     * The used algorithm is really simple.
     * An improved version should take into account the standard deviation.
     *
     * @param periodDurationStats
     * @param lastDurationStats
     * @return
     */
    private ExecutionMetricsAggregation.Trend computeTrendOnAvgDuration(Stats periodDurationStats, Stats lastDurationStats) {
        if (periodDurationStats == null || lastDurationStats == null) {
            return null;
        }

        double originalValue = periodDurationStats.getAvg();
        double lastValue = lastDurationStats.getAvg();
        double toleratedDiff = originalValue * (TREND_VARIATION_PERCENTAGE_THRESHOLD / 100);

        if (lastValue < (originalValue - toleratedDiff)) {
            return ExecutionMetricsAggregation.Trend.DOWN;
        } else if (lastValue > (originalValue + toleratedDiff)) {
            return ExecutionMetricsAggregation.Trend.UP;
        }
        return ExecutionMetricsAggregation.Trend.NEUTRAL;
    }
}
