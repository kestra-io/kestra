package io.kestra.worker.stores;

import java.util.Optional;

import io.kestra.core.models.ServerType;
import io.kestra.core.utils.Enums;

import io.micronaut.context.condition.Condition;
import io.micronaut.context.condition.ConditionContext;

/**
 * Matches when the server reaches the KV store through the controller: a worker, asked to.
 * <p>
 * The configured value is parsed rather than pattern-matched, so a misspelt mode fails the context
 * instead of silently leaving the worker writing values to a storage no other server reads.
 */
public class KVStoreFromControllerCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context) {
        return ServerType.WORKER == serverType(context)
            && KVWorkerAccess.CONTROLLER == workerAccess(context);
    }

    @SuppressWarnings("unchecked")
    private static KVWorkerAccess workerAccess(ConditionContext context) {
        return ((Optional<String>) context.get(KVWorkerAccess.CONFIG_KEY, String.class))
            .map(KVWorkerAccess::fromString)
            .orElse(KVWorkerAccess.STORAGE);
    }

    @SuppressWarnings("unchecked")
    private static ServerType serverType(ConditionContext context) {
        return ((Optional<String>) context.get("kestra.server-type", String.class))
            .map(value -> Enums.getForNameIgnoreCase(value, ServerType.class))
            .orElse(ServerType.STANDALONE);
    }
}
