package io.kestra.core.runners;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.micronaut.core.annotation.Nullable;

/**
 * Common wire-format fields for {@link RunContext} reconstruction on the worker side.
 * <p>
 * Both {@link WorkerTaskData} and {@link WorkerTriggerData} carry these fields
 * to allow the worker to rebuild a {@link RunContext}.
 */
public sealed interface WorkerRunContextData permits WorkerTaskData, WorkerTriggerData {

    /** The variables map, stripped of worker-reconstructed keys. */
    Map<String, Object> variables();

    /** OpenTelemetry trace parent for distributed tracing. */
    @Nullable
    String traceParent();

    /**
     * Keys that every worker-side reconstruction strips — environment, globals,
     * kestra config, and the non-serializable secret consumer.
     */
    Set<String> COMMON_RECONSTRUCTED_KEYS = Set.of(
        "envs",
        "globals",
        "kestra",
        RunVariables.SECRET_CONSUMER_VARIABLE_NAME
    );

    /**
     * Filters a {@link RunContext}'s variables by removing the given reconstructed keys.
     * <p>
     * Reads the variables in their pre-decryption form, so a secret is never serialized in cleartext onto a queue;
     * the receiving side decrypts it when it reads the variables back.
     *
     * @param runContext the RunContext whose variables to filter
     * @param keysToRemove keys to strip from the variables map
     * @return a mutable copy of the variables with the specified keys removed
     */
    static Map<String, Object> filterVariables(RunContext runContext, Set<String> keysToRemove) {
        Map<String, Object> variables = runContext instanceof DefaultRunContext defaultRunContext
            ? defaultRunContext.rawVariables()
            : runContext.getVariables();
        Map<String, Object> filtered = new HashMap<>(variables);
        filtered.keySet().removeAll(keysToRemove);
        return filtered;
    }
}
