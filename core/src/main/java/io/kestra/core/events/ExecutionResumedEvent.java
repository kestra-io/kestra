package io.kestra.core.events;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.Input;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.flows.Type;
import io.kestra.core.models.flows.input.FormInput;
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

    private final Resume resume;

    public ExecutionResumedEvent(
        Execution before,
        Execution after,
        @Nullable TaskRun taskRun,
        @Nullable PausableTask.Decision decision,
        State.Type state,
        @Nullable Map<String, Object> inputs,
        @Nullable List<Input<?>> inputDefinitions) {
        super(after, before, CrudEventType.UPDATE);
        this.resume = new Resume(
            taskRun == null ? null : taskRun.getId(),
            taskRun == null ? null : taskRun.getTaskId(),
            decision == null ? null : decision.type(),
            state,
            inputs == null || inputs.isEmpty() ? null : mask(inputs, inputDefinitions)
        );
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mask(Map<String, Object> values, @Nullable List<Input<?>> definitions) {
        Map<String, Input<?>> definitionsById = ListUtils.emptyOnNull(definitions).stream()
            .collect(Collectors.toMap(Input::getId, Function.identity(), (first, second) -> first));
        Map<String, Object> masked = new LinkedHashMap<>();
        values.forEach((key, value) ->
        {
            Input<?> definition = definitionsById.get(key);
            if (definition != null && Type.SECRET == definition.getType() || isEncrypted(value)) {
                masked.put(key, MASK);
            } else if (value instanceof Map<?, ?> nested) {
                masked.put(key, mask((Map<String, Object>) nested, definition instanceof FormInput form ? form.getInputs() : null));
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

    /**
     * @param state the state the paused task run was resumed to, which can differ from the execution's state when
     *              another task of the execution is still paused
     */
    public record Resume(
        @Nullable String taskRunId,
        @Nullable String taskId,
        @Nullable PausableTask.Decision.Type decision,
        State.Type state,
        @Nullable Map<String, Object> inputs
    ) {}
}
