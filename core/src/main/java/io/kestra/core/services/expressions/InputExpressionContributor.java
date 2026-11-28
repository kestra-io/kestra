package io.kestra.core.services.expressions;

import java.util.List;

import io.kestra.core.models.flows.Flow;
import io.kestra.core.services.ExpressionCategory;
import io.kestra.core.services.ExpressionContributor;
import io.kestra.core.services.ExpressionRequest;

import jakarta.inject.Singleton;

@Singleton
public class InputExpressionContributor implements ExpressionContributor {

    @Override
    public ExpressionCategory category() {
        return ExpressionCategory.INPUTS;
    }

    @Override
    public List<String> contribute(ExpressionRequest request) {
        if (request.flow() == null) {
            return List.of();
        }
        Flow flow = request.flow();
        if (flow.getInputs() == null || flow.getInputs().isEmpty()) {
            return List.of();
        }
        // Expand FORM inputs so suggestions are the resolvable nested paths (e.g. inputs.environment.region).
        return flow.resolvableInputs().stream()
            .map(input -> "inputs." + input.getId())
            .sorted()
            .toList();
    }
}
