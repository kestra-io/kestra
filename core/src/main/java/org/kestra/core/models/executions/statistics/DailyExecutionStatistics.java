package org.kestra.core.models.executions.statistics;

import lombok.Builder;
import lombok.Data;
import lombok.Value;
import org.kestra.core.models.flows.State;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import javax.validation.constraints.NotNull;

@Data
@Builder
public class DailyExecutionStatistics {
    @NotNull
    protected LocalDate startDate;

    @NotNull
    private Duration duration;

    @Builder.Default
    private List<ExecutionCount> executionCounts = new ArrayList<>();

    @Value
    @Builder
    static public class ExecutionCount {
        @NotNull
        private State.Type state;

        @NotNull
        private Long count;
    }

    @Value
    @Builder
    static public class Duration {
        @NotNull
        private java.time.Duration min;

        @NotNull
        private java.time.Duration avg;

        @NotNull
        private java.time.Duration max;

        @NotNull
        private java.time.Duration sum;

        @NotNull
        private long count;
    }
}
