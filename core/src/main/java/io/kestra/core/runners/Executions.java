package io.kestra.core.runners;

import io.kestra.core.models.Label;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.utils.IdUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * Class for constructing and manipulating {@link Execution} objects.
 */
public final class Executions {

    /**
     * Factory method for constructing a new {@link Execution} object for the given {@link Flow} and inputs.
     *
     * @param flow   The Flow.
     * @param inputs The Flow's inputs.
     * @param labels The Flow labels.
     * @return a new {@link Execution}.
     */
    public static Execution newExecution(final Flow flow,
                                         final BiFunction<Flow, Execution, Map<String, Object>> inputs,
                                         final List<Label> labels) {
        Execution execution = Execution.builder()
            .id(IdUtils.create())
            .tenantId(flow.getTenantId())
            .namespace(flow.getNamespace())
            .flowId(flow.getId())
            .flowRevision(flow.getRevision())
            .state(new State())
            .build();

        if (inputs != null) {
            execution = execution.withInputs(inputs.apply(flow, execution));
        }

        List<Label> executionLabels = new ArrayList<>();
        if (flow.getLabels() != null) {
            executionLabels.addAll(flow.getLabels());
        }
        if (labels != null) {
            executionLabels.addAll(labels);
        }
        if (!executionLabels.isEmpty()) {
            execution = execution.withLabels(executionLabels);
        }

        return execution;
    }
}
