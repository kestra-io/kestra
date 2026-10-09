package io.kestra.worker.stores;

import java.util.Optional;

import io.kestra.core.models.ServerType;
import io.kestra.core.utils.Enums;

import io.micronaut.context.condition.Condition;
import io.micronaut.context.condition.ConditionContext;

/**
 * Matches when the server reaches a store through the controller: a worker, asked to by the {@link WorkerAccess}
 * set under {@link #configKey()}.
 * <p>
 * The configured value is parsed rather than pattern-matched, so a misspelt mode fails the context
 * instead of silently leaving the worker writing content to a storage no other server reads.
 */
public abstract class WorkerAccessFromControllerCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context) {
        return ServerType.WORKER == serverType(context)
            && WorkerAccess.CONTROLLER == workerAccess(context);
    }

    protected abstract String configKey();

    @SuppressWarnings("unchecked")
    private WorkerAccess workerAccess(ConditionContext context) {
        return ((Optional<String>) context.get(configKey(), String.class))
            .map(WorkerAccess::fromString)
            .orElse(WorkerAccess.STORAGE);
    }

    @SuppressWarnings("unchecked")
    private static ServerType serverType(ConditionContext context) {
        return ((Optional<String>) context.get("kestra.serverType", String.class))
            .map(value -> Enums.getForNameIgnoreCase(value, ServerType.class))
            .orElse(ServerType.STANDALONE);
    }
}
