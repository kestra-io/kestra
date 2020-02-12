package org.kestra.core.models.executions.metrics;

import lombok.Builder;
import lombok.Value;
import org.kestra.core.models.flows.State;

import javax.validation.constraints.NotNull;
import java.time.LocalDate;

@Value
@Builder
public class ExecutionMetrics {
    @NotNull
    private String namespace;
    @NotNull
    private String flowId;
    @NotNull
    private State.Type state;
    @NotNull
    private LocalDate startDate;
    @NotNull
    private Long count;

    private Stats durationStats;
}
