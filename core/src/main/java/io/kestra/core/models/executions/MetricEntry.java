package io.kestra.core.models.executions;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.kestra.core.models.DeletedInterface;
import io.kestra.core.models.TenantInterface;
import io.kestra.core.models.executions.metrics.Counter;
import io.kestra.core.models.executions.metrics.Timer;
import io.micronaut.core.annotation.Nullable;
import io.swagger.v3.oas.annotations.Hidden;
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
