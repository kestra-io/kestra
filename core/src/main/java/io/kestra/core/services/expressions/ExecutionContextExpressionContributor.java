package io.kestra.core.services.expressions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import io.kestra.core.models.Label;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.runners.RunContextCache;
import io.kestra.core.runners.RunVariables;
import io.kestra.core.services.ExpressionCategory;
import io.kestra.core.services.ExpressionContributor;
import io.kestra.core.services.ExpressionRequest;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * All structural paths from {@link RunVariables#EXECUTION_CONTEXT_PATHS}, plus the flow labels, envs, and globals.
 */
@Singleton
public class ExecutionContextExpressionContributor implements ExpressionContributor {
    private final RunContextCache runContextCache;

    @Inject
    public ExecutionContextExpressionContributor(RunContextCache runContextCache) {
        this.runContextCache = runContextCache;
    }

    @Override
    public ExpressionCategory category() {
        return ExpressionCategory.EXECUTION_CONTEXT;
    }

    @Override
    public List<String> contribute(ExpressionRequest request) {
        if (request.flow() == null) {
            return List.of();
        }
        List<String> expressions = new ArrayList<>(RunVariables.EXECUTION_CONTEXT_PATHS);
        addFlowLabels(request.flow(), expressions);
        addEnvsAndGlobals(expressions);
        Collections.sort(expressions);
        return expressions;
    }

    private static void addFlowLabels(Flow flow, List<String> expressions) {
        if (flow.getLabels() != null) {
            for (Label label : flow.getLabels()) {
                expressions.add("labels." + label.key());
            }
        }
    }

    private void addEnvsAndGlobals(List<String> expressions) {
        Map<String, String> envVars = runContextCache.getEnvVars();
        if (envVars != null) {
            for (String key : envVars.keySet()) {
                expressions.add("envs." + key);
            }
        }
        Map<?, ?> globalVars = runContextCache.getGlobalVars();
        if (globalVars != null) {
            for (Object key : globalVars.keySet()) {
                expressions.add("globals." + key);
            }
        }
    }
}
