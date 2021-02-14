package org.kestra.runner.kafka.streams;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.models.flows.Flow;

@Getter
@AllArgsConstructor
public class ExecutorFlowTrigger {
    Flow flowHavingTrigger;
    Execution execution;
}
