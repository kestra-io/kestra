package org.kestra.core.models.hierarchies;

import lombok.Builder;
import lombok.Value;
import org.kestra.core.exceptions.IllegalVariableEvaluationException;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.models.flows.Flow;
import org.kestra.core.services.TreeService;

import java.util.List;

@Value
@Builder
public class FlowTree {
    List<TaskTree> tasks;

    public static FlowTree of(Flow flow) throws IllegalVariableEvaluationException {
        return FlowTree.of(flow, null);
    }

    public static FlowTree of(Flow flow, Execution execution) throws IllegalVariableEvaluationException {
        return FlowTree.builder()
            .tasks(TreeService.sequential(
                flow.getTasks(),
                flow.getErrors(),
                null,
                execution
            ))
            .build();
    }
}
