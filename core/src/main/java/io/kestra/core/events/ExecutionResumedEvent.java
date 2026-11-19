package io.kestra.core.events;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.Input;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.flows.Type;
import io.kestra.core.models.tasks.common.EncryptedString;
import io.kestra.core.utils.ListUtils;
import io.kestra.plugin.core.flow.PausableTask;

import io.micronaut.core.annotation.Nullable;
import lombok.Getter;

/**
 * The update of a paused execution that was resumed, or whose Approval was decided or cancelled, with what the
 * execution diff does not carry: the task run, the decision, the target state and the resume inputs.
 * SECRET inputs are replaced by {@link #MASK}; FILE inputs are kept as their storage URI.
 */
@Getter
public class ExecutionResumedEvent extends CrudEvent<Execution> {
    public static final String MASK = "******";

    @Nullable
    private final String taskRunId;
    @Nullable
    private final String taskId;
    @Nullable
    private final PausableTask.Decision.Type decision;
    private final State.Type state;
    private final Map<String, Object> inputs;

    public ExecutionResumedEvent(
        Execution before,
        Execution after,
        @Nullable TaskRun taskRun,
        @Nullable PausableTask.Decision decision,
        State.Type state,
        @Nullable Map<String, Object> inputs,
        @Nullable List<Input<?>> inputDefinitions) {
        super(after, before, CrudEventType.UPDATE);
        this.taskRunId = taskRun == null ? null : taskRun.getId();
        this.taskId = taskRun == null ? null : taskRun.getTaskId();
        this.decision = decision == null ? null : decision.type();
        this.state = state;
        Set<String> secretIds = ListUtils.emptyOnNull(inputDefinitions).stream()
            .filter(input -> Type.SECRET == input.getType())
            .map(Input::getId)
            .collect(Collectors.toSet());
        this.inputs = inputs == null ? Map.of() : mask(inputs, secretIds);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mask(Map<String, Object> values, Set<String> secretIds) {
        Map<String, Object> masked = new LinkedHashMap<>();
        values.forEach((key, value) ->
        {
            if (secretIds.contains(key) || isEncrypted(value)) {
                masked.put(key, MASK);
            } else if (value instanceof Map<?, ?> nested) {
                masked.put(key, mask((Map<String, Object>) nested, Set.of()));
            } else {
                masked.put(key, value);
            }
        });
        return masked;
    }

    private static boolean isEncrypted(@Nullable Object value) {
        return value instanceof EncryptedString
            || value instanceof Map<?, ?> map && EncryptedString.TYPE.equals(map.get("type"));
    }
}
