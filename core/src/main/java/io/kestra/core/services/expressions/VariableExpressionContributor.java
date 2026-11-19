package io.kestra.core.services.expressions;

import java.util.List;

import io.kestra.core.models.flows.Flow;
import io.kestra.core.services.ExpressionCategory;
import io.kestra.core.services.ExpressionContributor;
import io.kestra.core.services.ExpressionRequest;

import jakarta.inject.Singleton;

@Singleton
public class VariableExpressionContributor implements ExpressionContributor {

    @Override
    public ExpressionCategory category() {
        return ExpressionCategory.VARIABLES;
    }

    @Override
    public List<String> contribute(ExpressionRequest request) {
        if (request.flow() == null) {
            return List.of();
        }
        Flow flow = request.flow();
        if (flow.getVariables() == null || flow.getVariables().isEmpty()) {
            return List.of();
        }
        return flow.getVariables().keySet().stream()
            .map(key -> "vars." + key)
            .sorted()
            .toList();
    }
}
