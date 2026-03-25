package io.kestra.core.runners;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.micronaut.core.annotation.Nullable;

/**
 * Wire-format for the execution context sent with a {@link WorkerTask}.
 * <p>
 * Carries the variables map (minus keys the worker reconstructs locally),
 * plus metadata needed to initialize a {@link RunContext} on the worker side.
 *
 * @param variables The variables map, stripped of worker-reconstructed keys.
 * @param secretInputs List of input keys that are secrets (for log masking).
 * @param traceParent OpenTelemetry trace parent for distributed tracing.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record WorkerTaskData(
    @JsonInclude(value = JsonInclude.Include.ALWAYS, content = JsonInclude.Include.ALWAYS) Map<String, Object> variables,
    List<String> secretInputs,
    @Nullable String traceParent) implements WorkerRunContextData {

    /**
     * Keys excluded from the wire format — the worker reconstructs them locally.
     */
    static final Set<String> WORKER_RECONSTRUCTED_KEYS = Stream.concat(
        Stream.of("task", "taskrun"),
        COMMON_RECONSTRUCTED_KEYS.stream()
    ).collect(Collectors.toUnmodifiableSet());

    /**
     * Creates a {@link WorkerTaskData} from a full {@link RunContext}, stripping
     * keys that the worker can reconstruct locally.
     *
     * @param runContext the RunContext to extract wire data from
     * @return a new WorkerTaskData suitable for serialization
     */
    public static WorkerTaskData from(RunContext runContext) {
        return new WorkerTaskData(
            WorkerRunContextData.filterVariables(runContext, WORKER_RECONSTRUCTED_KEYS),
            runContext.getSecretInputs(),
            runContext.getTraceParent()
        );
    }
}
