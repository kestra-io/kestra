package io.kestra.core.runners;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.micronaut.core.annotation.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Wire-format for the execution context sent with a {@link WorkerTask}.
 * <p>
 * Carries the variables map (minus keys the worker reconstructs locally),
 * plus metadata needed to initialize a {@link RunContext} on the worker side.
 *
 * @param variables    The variables map, stripped of worker-reconstructed keys.
 * @param secretInputs List of input keys that are secrets (for log masking).
 * @param traceParent  OpenTelemetry trace parent for distributed tracing.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record WorkerTaskData(
    Map<String, Object> variables,
    List<String> secretInputs,
    @Nullable String traceParent
) {

    /**
     * Keys excluded from the wire format — the worker reconstructs them locally.
     * <p>
     * <ul>
     *   <li>{@code task} — from {@code WorkerTask.task} via {@link RunVariables#of(io.kestra.core.models.tasks.Task)}</li>
     *   <li>{@code taskrun} — from {@code WorkerTask.taskRun} via {@link RunVariables#of(io.kestra.core.models.executions.TaskRun)}</li>
     *   <li>{@code envs} — from {@link RunContextCache#getEnvVars()}</li>
     *   <li>{@code globals} — from {@link RunContextCache#getGlobalVars()}</li>
     *   <li>{@code kestra} — from worker config ({@code kestra.environment.name}, {@code kestra.url})</li>
     *   <li>{@code addSecretConsumer} — non-serializable lambda, re-created on worker</li>
     * </ul>
     */
    static final Set<String> WORKER_RECONSTRUCTED_KEYS = Set.of(
        "task",
        "taskrun",
        "envs",
        "globals",
        "kestra",
        RunVariables.SECRET_CONSUMER_VARIABLE_NAME
    );

    /**
     * Creates a {@link WorkerTaskData} from a full {@link RunContext}, stripping
     * keys that the worker can reconstruct locally.
     *
     * @param runContext the RunContext to extract wire data from
     * @return a new WorkerTaskData suitable for serialization
     */
    public static WorkerTaskData from(RunContext runContext) {
        Map<String, Object> filtered = new HashMap<>(runContext.getVariables());
        WORKER_RECONSTRUCTED_KEYS.forEach(filtered::remove);
        return new WorkerTaskData(
            filtered,
            runContext.getSecretInputs(),
            runContext.getTraceParent()
        );
    }
}
