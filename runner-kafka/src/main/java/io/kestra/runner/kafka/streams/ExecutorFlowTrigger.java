package io.kestra.runner.kafka.streams;

import lombok.AllArgsConstructor;
import lombok.Getter;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;

@Getter
@AllArgsConstructor
public class ExecutorFlowTrigger {
    Flow flowHavingTrigger;
    Execution execution;
}
