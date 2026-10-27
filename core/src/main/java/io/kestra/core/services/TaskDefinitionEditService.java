package io.kestra.core.services;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import io.kestra.core.exceptions.FlowProcessingException;
import io.kestra.core.exceptions.InternalException;
import io.kestra.core.exceptions.KestraRuntimeException;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.validations.ModelValidator;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.utils.ListUtils;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.validation.ConstraintViolationException;

/**
 * Runs a replayed task run on a definition edited for that replay only. The edit keeps the task id, its type and
 * the tasks nested in it, so the execution still matches the graph of its flow revision.
 */
@Singleton
public class TaskDefinitionEditService {
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {
    };
    private static final List<String> TASK_SECTIONS = List.of("tasks", "errors", "finally", "afterExecution");

    private final FlowParsingService flowParsingService;
    private final TaskOutputService taskOutputService;
    private final ModelValidator modelValidator;
    // an edit is immutable once saved, so the ids of the edits are a safe cache key
    private final Cache<String, FlowWithSource> runtimeFlows = Caffeine.newBuilder()
        .maximumSize(100)
        .expireAfterAccess(Duration.ofMinutes(10))
        .build();

    @Inject
    public TaskDefinitionEditService(FlowParsingService flowParsingService, TaskOutputService taskOutputService, ModelValidator modelValidator) {
        this.flowParsingService = Objects.requireNonNull(flowParsingService);
        this.taskOutputService = Objects.requireNonNull(taskOutputService);
        this.modelValidator = Objects.requireNonNull(modelValidator);
    }

    /**
     * @throws IllegalArgumentException if the definition is not valid YAML, changes the task id, its type or the
     *         tasks nested in it, or does not make a valid flow
     */
    public void validate(FlowWithSource flow, Execution execution, String taskRunId, String definition) throws InternalException {
        TaskRun taskRun = execution.findTaskRunByTaskRunIdIfPresent(taskRunId).orElseThrow(
            () -> new IllegalArgumentException(
                "Task run '%s' does not exist in execution '%s'.".formatted(taskRunId, execution.getId())
            )
        );
        Task task = flow.findTaskByTaskId(taskRun.getTaskId());
        Map<String, Object> edited = parse(definition, "the edited definition");

        if (!task.getId().equals(edited.get("id")) || !task.getType().equals(edited.get("type"))) {
            throw new IllegalArgumentException(
                "Cannot replay task '%s' on an edited definition that changes its id or its type: change them with a new revision.".formatted(task.getId())
            );
        }

        Map<String, Object> source = parse(flow.getSource(), "the flow source");
        Map<String, Object> original = findTask(taskSections(source), task.getId()).orElseThrow(
            () -> new IllegalArgumentException(
                "Cannot find task '%s' in the source of revision %s of flow '%s'.".formatted(task.getId(), flow.getRevision(), flow.getId())
            )
        );
        if (!nestedTasks(original).equals(nestedTasks(edited))) {
            throw new IllegalArgumentException(
                "Cannot replay task '%s' on an edited definition that changes the tasks nested in it: change them with a new revision.".formatted(task.getId())
            );
        }

        try {
            modelValidator.validate(flowParsingService.parse(flow.getTenantId(), toYaml(replaceInTaskSections(source, task.getId(), edited)), true));
        } catch (FlowProcessingException | ConstraintViolationException e) {
            throw new IllegalArgumentException("The edited definition of task '%s' is not valid: %s".formatted(task.getId(), e.getMessage()), e);
        }
    }

    /**
     * The runtime flow with each edited task of the execution replaced by its edited definition.
     */
    public FlowWithSource applyEdits(FlowWithSource flow, Execution execution) {
        List<TaskRun> edited = ListUtils.emptyOnNull(execution.getTaskRunList()).stream()
            .filter(taskRun -> taskRun.getDefinitionEdit() != null)
            .toList();
        if (edited.isEmpty()) {
            return flow;
        }

        String key = flow.uid() + edited.stream().map(taskRun -> taskRun.getDefinitionEdit().id()).collect(Collectors.joining(",", "|", ""));
        return runtimeFlows.get(key, _ -> patchForRuntime(flow, edited));
    }

    private FlowWithSource patchForRuntime(FlowWithSource flow, List<TaskRun> edited) {
        try {
            Map<String, Object> source = parse(flow.getSource(), "the flow source");
            for (TaskRun taskRun : edited) {
                String definition = taskOutputService.getEditedDefinition(taskRun).orElseThrow(
                    () -> new IllegalStateException(
                        "The edited definition of task run '%s' is missing.".formatted(taskRun.getId())
                    )
                );
                source = replaceInTaskSections(source, taskRun.getTaskId(), parse(definition, "the edited definition"));
            }
            return flowParsingService.parseForRuntime(flow.toBuilder().source(toYaml(source)).build()).flow();
        } catch (InternalException | FlowProcessingException e) {
            throw new KestraRuntimeException("Unable to apply the edited task definitions to flow '%s'.".formatted(flow.uid()), e);
        }
    }

    private static List<Object> taskSections(Map<String, Object> source) {
        return TASK_SECTIONS.stream().map(source::get).filter(Objects::nonNull).toList();
    }

    private static Map<String, Object> replaceInTaskSections(Map<String, Object> source, String taskId, Map<String, Object> definition) {
        Map<String, Object> copy = new LinkedHashMap<>(source);
        TASK_SECTIONS.stream().filter(copy::containsKey).forEach(section -> copy.put(section, replaceTask(copy.get(section), taskId, definition)));
        return copy;
    }

    private static Optional<Map<String, Object>> findTask(Object node, String taskId) {
        if (node instanceof Map<?, ?> map) {
            if (isTask(map) && taskId.equals(map.get("id"))) {
                @SuppressWarnings("unchecked")
                Map<String, Object> task = (Map<String, Object>) map;
                return Optional.of(task);
            }
            return map.values().stream().map(child -> findTask(child, taskId)).flatMap(Optional::stream).findFirst();
        }
        if (node instanceof List<?> list) {
            return list.stream().map(child -> findTask(child, taskId)).flatMap(Optional::stream).findFirst();
        }
        return Optional.empty();
    }

    @SuppressWarnings("unchecked")
    private static <T> T replaceTask(T node, String taskId, Map<String, Object> definition) {
        if (node instanceof Map<?, ?> map) {
            if (isTask(map) && taskId.equals(map.get("id"))) {
                return (T) definition;
            }
            Map<Object, Object> copy = new LinkedHashMap<>(map);
            copy.replaceAll((_, child) -> replaceTask(child, taskId, definition));
            return (T) copy;
        }
        if (node instanceof List<?> list) {
            return (T) list.stream().map(child -> replaceTask(child, taskId, definition)).toList();
        }
        return node;
    }

    private static List<Object> nestedTasks(Map<String, Object> task) {
        List<Object> nested = new ArrayList<>();
        task.values().forEach(child -> collectTasks(child, nested));
        return nested;
    }

    private static void collectTasks(Object node, List<Object> tasks) {
        if (node instanceof Map<?, ?> map && isTask(map)) {
            tasks.add(map);
        } else if (node instanceof Map<?, ?> map) {
            map.values().forEach(child -> collectTasks(child, tasks));
        } else if (node instanceof List<?> list) {
            list.forEach(child -> collectTasks(child, tasks));
        }
    }

    private static boolean isTask(Map<?, ?> map) {
        return map.get("id") instanceof String && map.get("type") instanceof String;
    }

    private static Map<String, Object> parse(String yaml, String what) {
        try {
            Map<String, Object> map = JacksonMapper.ofYaml().readValue(yaml, MAP);
            if (map == null) {
                throw new IllegalArgumentException("Unable to read %s: it is empty.".formatted(what));
            }
            return map;
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Unable to read %s as YAML: %s".formatted(what, e.getOriginalMessage()), e);
        }
    }

    private static String toYaml(Map<String, Object> source) {
        try {
            return JacksonMapper.ofYaml().writeValueAsString(source);
        } catch (JsonProcessingException e) {
            throw new KestraRuntimeException("Unable to write the edited flow source as YAML.", e);
        }
    }
}
