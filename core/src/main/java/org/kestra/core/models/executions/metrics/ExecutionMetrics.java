package org.kestra.core.models.executions.metrics;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Builder;
import lombok.Value;
import org.kestra.core.models.flows.State;

import javax.validation.constraints.NotNull;
import java.time.LocalDate;

@Value
@Builder
public class ExecutionMetrics {
    @NotNull
    @JsonIgnore
    private transient State.Type state;

    @NotNull
    private LocalDate startDate;

    @NotNull
    @JsonIgnore
    private transient Long count;

    private Long success;
    private Long failed;
    private Long created;
    private Long running;

    public static class ExecutionMetricsBuilder {
        public ExecutionMetrics build() {
            this.computeStateCount();
            return new ExecutionMetrics(
                this.state,
                this.startDate,
                this.count,
                this.success,
                this.failed,
                this.created,
                this.running
            );
        }

        private void computeStateCount() {
            switch (this.state) {
                case FAILED:
                    this.failed = this.count;
                    break;
                case SUCCESS:
                    this.success = this.count;
                    break;
                case RUNNING:
                    this.running = this.count;
                    break;
                case CREATED:
                    this.created = this.count;
                    break;
            }
        }
    }

}
