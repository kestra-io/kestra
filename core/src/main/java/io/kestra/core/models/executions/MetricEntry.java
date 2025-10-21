package io.kestra.core.models.executions;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.kestra.core.models.DeletedInterface;
import io.kestra.core.models.TenantInterface;
import io.kestra.core.models.executions.metrics.Counter;
import io.kestra.core.models.executions.metrics.Gauge;
import io.kestra.core.models.executions.metrics.Timer;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.annotation.Nullable;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.Map;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

@Value
@Builder(toBuilder = true)
public class MetricEntry implements DeletedInterface, TenantInterface {
    @Hidden
    @Pattern(regexp = "^[a-z0-9][a-z0-9_-]*")
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

    @Nullable
    ExecutionKind executionKind;

    public static MetricEntry of(TaskRun taskRun, AbstractMetricEntry<?> metricEntry, ExecutionKind executionKind) {
        return MetricEntry.builder()
            .tenantId(taskRun.getTenantId())
            .namespace(taskRun.getNamespace())
            .flowId(taskRun.getFlowId())
            .executionId(taskRun.getExecutionId())
            .taskId(taskRun.getTaskId())
            .taskRunId(taskRun.getId())
            .type(metricEntry.getType())
            .name(metricEntry.getName())
            .tags(metricEntry.getTags())
            .value(computeValue(metricEntry))
            .timestamp(metricEntry.getTimestamp())
            .executionKind(executionKind)
            .build();
    }

    private static Double computeValue(AbstractMetricEntry<?> metricEntry) {
        if (metricEntry instanceof Counter counter) {
            return counter.getValue();
        }

        if (metricEntry instanceof Gauge gauge) {
            return gauge.getValue();
        }

        if (metricEntry instanceof Timer timer) {
            return (double) timer.getValue().toMillis();
        }

        throw new IllegalArgumentException("Unknown metric type: " + metricEntry.getClass());
    }
}
