package io.kestra.core.runners;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.FlowInterface;
import io.kestra.core.models.flows.Output;
import io.micronaut.context.ApplicationContext;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

class InputAndOutputImpl implements InputAndOutput {
    private final FlowInputOutput flowInputOutput;
    private final RunContext runContext;

    InputAndOutputImpl(ApplicationContext applicationContext, RunContext runContext) {
        this.flowInputOutput = applicationContext.getBean(FlowInputOutput.class);
        this.runContext = runContext;
    }

    @Override
    public Map<String, Object> readInputs(FlowInterface flow, Execution execution, Map<String, Object> inputs) {
        return flowInputOutput.readExecutionInputs(flow, execution, inputs);
    }

    @Override
    public Map<String, Object> typedOutputs(FlowInterface flow, Execution execution, Map<String, Object> rOutputs) {
        return flowInputOutput.typedOutputs(flow, execution, rOutputs);
    }

    @Override
    public Map<String, Object> renderOutputs(List<Output> outputs) throws IllegalVariableEvaluationException {
        if (outputs == null) return Map.of();

        // render required outputs
        Map<String, Object> outputsById = outputs
            .stream()
            .filter(output -> output.getRequired() == null || output.getRequired())
            .collect(HashMap::new, (map, entry) -> map.put(entry.getId(), entry.getValue()), Map::putAll);
        outputsById = runContext.render(outputsById);

        // render optional outputs one by one to catch, log, and skip any error.
        for (io.kestra.core.models.flows.Output output : outputs) {
            if (Boolean.FALSE.equals(output.getRequired())) {
                try {
                    outputsById.putAll(runContext.render(Map.of(output.getId(), output.getValue())));
                } catch (Exception e) {
                    runContext.logger().warn("Failed to render optional flow output '{}'. Output is ignored.", output.getId(), e);
                    outputsById.put(output.getId(), null);
                }
            }
        }
        return outputsById;
    }
}
