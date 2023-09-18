package io.kestra.core.models.executions;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.kestra.core.models.DeletedInterface;
import io.kestra.core.models.executions.metrics.Counter;
import io.kestra.core.models.executions.metrics.Timer;
import io.micronaut.core.annotation.Nullable;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.Map;
import javax.validation.constraints.NotNull;

@Value
@Builder(toBuilder = true)
public class MetricEntry implements DeletedInterface {
    String tenantId;

    @NotNull
    String namespace;

    @NotNull
    String flowId;

    @Nullable
    String taskId;

    @Nullable
    String executionId;

    @Nullable
    String taskRunId;

    @NotNull
    String type;

    @NotNull
    String name;

    @NotNull
    @JsonInclude
    Double value;

    @NotNull
    Instant timestamp;

    @Nullable
    Map<String, String> tags;

    @NotNull
    @Builder.Default
    boolean deleted = false;

    public static MetricEntry of(TaskRun taskRun, AbstractMetricEntry<?> metricEntry) {
        return MetricEntry.builder()
            .tenantId(taskRun.getTenantId())
            .namespace(taskRun.getNamespace())
            .flowId(taskRun.getFlowId())
            .executionId(taskRun.getExecutionId())
            .taskId(taskRun.getTaskId())
            .taskRunId(taskRun.getId())
            .type(metricEntry.getType())
            .name(metricEntry.name)
            .tags(metricEntry.getTags())
            .value(computeValue(metricEntry))
            .timestamp(metricEntry.getTimestamp())
            .build();
    }

    private static Double computeValue(AbstractMetricEntry<?> metricEntry) {
        if (metricEntry instanceof Counter) {
            return ((Counter) metricEntry).getValue();
        }

        if (metricEntry instanceof Timer) {
            return (double) ((Timer) metricEntry).getValue().toMillis();
        }

        throw new IllegalArgumentException("Unknown metric type: " + metricEntry.getClass());
    }
}
