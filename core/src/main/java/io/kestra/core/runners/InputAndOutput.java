package io.kestra.core.runners;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.FlowInterface;
import io.kestra.core.models.flows.Output;

import java.util.List;
import java.util.Map;

/**
 * InputAndOutput could be used to work with flow execution inputs and outputs.
 */
public interface InputAndOutput {
    /**
     * Reads the inputs of a flow execution.
     */
    Map<String, Object> readInputs(FlowInterface flow, Execution execution, Map<String, Object> inputs);

    /**
     * Processes the outputs of a flow execution (parse them based on their types).
     */
    Map<String, Object> typedOutputs(FlowInterface flow, Execution execution, Map<String, Object> rOutputs);

    /**
     * Render flow execution outputs.
     */
    Map<String, Object> renderOutputs(List<Output> outputs) throws IllegalVariableEvaluationException;
}
