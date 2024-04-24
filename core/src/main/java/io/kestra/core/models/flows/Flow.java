package io.kestra.core.models.flows;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.introspect.AnnotatedMember;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;
import io.kestra.core.exceptions.InternalException;
import io.kestra.core.models.Label;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.listeners.Listener;
import io.kestra.core.models.tasks.FlowableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.tasks.retrys.AbstractRetry;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.validations.ManualConstraintViolation;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.serializers.ListOrMapOfLabelDeserializer;
import io.kestra.core.serializers.ListOrMapOfLabelSerializer;
import io.kestra.core.services.FlowService;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.validations.FlowValidation;
import io.micronaut.core.annotation.Introspected;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@SuperBuilder(toBuilder = true)
@Getter
@NoArgsConstructor
@Introspected
@ToString
@EqualsAndHashCode
@FlowValidation
public class Flow extends AbstractFlow {
    private static final ObjectMapper jsonMapper = JacksonMapper.ofJson().copy()
        .setAnnotationIntrospector(new JacksonAnnotationIntrospector() {
            @Override
            public boolean hasIgnoreMarker(final AnnotatedMember m) {
                List<String> exclusions = Arrays.asList("revision", "deleted", "source");
                return exclusions.contains(m.getName()) || super.hasIgnoreMarker(m);
            }
        });

    String description;

    @JsonSerialize(using = ListOrMapOfLabelSerializer.class)
    @JsonDeserialize(using = ListOrMapOfLabelDeserializer.class)
    @Schema(implementation = Object.class, oneOf = {List.class, Map.class})
    List<Label> labels;

    Map<String, Object> variables;

    @Valid
    @NotEmpty
    List<Task> tasks;

    @Valid
    List<Task> errors;

    @Valid
    @Deprecated
    List<Listener> listeners;

    @Valid
    List<AbstractTrigger> triggers;

    @Valid
    List<TaskDefault> taskDefaults;

    @Valid
    Concurrency concurrency;

    @Schema(
        title = "Output values available and exposes to other flows.",
        description = "Output values make information about the execution of your Flow available and expose for other Kestra flows to use. Output values are similar to return values in programming languages."
    )
    @PluginProperty(dynamic = true)
    @Valid
    List<Output> outputs;

    @Valid
    protected AbstractRetry retry;

    public Logger logger() {
        return LoggerFactory.getLogger("flow." + this.id);
    }

    @JsonIgnore
    public String uid() {
        return Flow.uid(this.getTenantId(), this.getNamespace(), this.getId(), Optional.ofNullable(this.revision));
    }

    @JsonIgnore
    public String uidWithoutRevision() {
        return Flow.uidWithoutRevision(this.getTenantId(), this.getNamespace(), this.getId());
    }

    public static String uid(Execution execution) {
        return IdUtils.fromParts(
            execution.getTenantId(),
            execution.getNamespace(),
            execution.getFlowId(),
            String.valueOf(execution.getFlowRevision())
        );
    }

    public static String uid(String tenantId, String namespace, String id, Optional<Integer> revision) {
        return IdUtils.fromParts(
            tenantId,
            namespace,
            id,
            String.valueOf(revision.orElse(-1))
        );
    }

    public static String uidWithoutRevision(String tenantId, String namespace, String id) {
        return IdUtils.fromParts(
            tenantId,
            namespace,
            id
        );
    }

    public static String uidWithoutRevision(Execution execution) {
        return IdUtils.fromParts(
            execution.getTenantId(),
            execution.getNamespace(),
            execution.getFlowId()
        );
    }

    public Stream<String> allTypes() {
        return Stream.of(
                Optional.ofNullable(triggers).orElse(Collections.emptyList()).stream().map(AbstractTrigger::getType),
                allTasks().map(Task::getType),
                Optional.ofNullable(taskDefaults).orElse(Collections.emptyList()).stream().map(TaskDefault::getType)
            ).reduce(Stream::concat).orElse(Stream.empty())
            .distinct();
    }

    public Stream<Task> allTasks() {
        return Stream.of(
                this.tasks != null ? this.tasks : new ArrayList<Task>(),
                this.errors != null ? this.errors : new ArrayList<Task>(),
                this.listenersTasks()
            )
            .flatMap(Collection::stream);
    }

    public List<Task> allTasksWithChilds() {
        return allTasks()
            .flatMap(this::allTasksWithChilds)
            .collect(Collectors.toList());
    }

    private Stream<Task> allTasksWithChilds(Task task) {
        if (task == null) {
            return Stream.empty();
        } else if (task.isFlowable()) {
            Stream<Task> taskStream = ((FlowableTask<?>) task).allChildTasks()
                .stream()
                .flatMap(this::allTasksWithChilds);

            return Stream.concat(
                Stream.of(task),
                taskStream
            );
        } else {
            return Stream.of(task);
        }
    }

    public List<String> allTriggerIds() {
        return this.triggers != null ? this.triggers.stream()
            .map(AbstractTrigger::getId)
            .collect(Collectors.toList()) : new ArrayList<>();
    }

    public List<String> allTasksWithChildsAndTriggerIds() {
        return Stream.concat(
            this.allTasksWithChilds().stream()
                .map(Task::getId),
            this.allTriggerIds().stream()
        )
            .collect(Collectors.toList());
    }

    public List<Task> allErrorsWithChilds() {
        var allErrors = allTasksWithChilds().stream()
            .filter(task -> task.isFlowable() && ((FlowableTask<?>) task).getErrors() != null)
            .flatMap(task -> ((FlowableTask<?>) task).getErrors().stream())
            .collect(Collectors.toCollection(ArrayList::new));

        if (this.getErrors() != null && !this.getErrors().isEmpty()) {
            allErrors.addAll(this.getErrors());
        }

        return allErrors;
    }

    public Task findParentTasksByTaskId(String taskId) {
        return allTasksWithChilds()
            .stream()
            .filter(Task::isFlowable)
            .filter(task -> ((FlowableTask<?>) task).allChildTasks().stream().anyMatch(t -> t.getId().equals(taskId)))
            .findFirst()
            .orElse(null);
    }

    public Task findTaskByTaskId(String taskId) throws InternalException {
        return allTasks()
            .flatMap(t -> t.findById(taskId).stream())
            .findFirst()
            .orElseThrow(() -> new InternalException("Can't find task with id '" + taskId + "' on flow '" + this.id + "'"));
    }

    public Task findTaskByTaskIdOrNull(String taskId) {
        return allTasks()
            .flatMap(t -> t.findById(taskId).stream())
            .findFirst()
            .orElse(null);
    }

    public Flow updateTask(String taskId, Task newValue) throws InternalException {
        Task task = this.findTaskByTaskId(taskId);
        Map<String, Object> map = JacksonMapper.toMap(this);

        return JacksonMapper.toMap(
            recursiveUpdate(map, task, newValue),
            Flow.class
        );
    }

    private static Object recursiveUpdate(Object object, Task previous, Task newValue) {
        if (object instanceof Map) {
            Map<?, ?> value = (Map<?, ?>) object;
            if (value.containsKey("id") && value.get("id").equals(previous.getId()) &&
                value.containsKey("type") && value.get("type").equals(previous.getType())
            ) {
                return JacksonMapper.toMap(newValue);
            } else {
                return value
                    .entrySet()
                    .stream()
                    .map(e -> new AbstractMap.SimpleEntry<>(
                        e.getKey(),
                        recursiveUpdate(e.getValue(), previous, newValue)
                    ))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            }
        } else if (object instanceof Collection) {
            Collection<?> value = (Collection<?>) object;
            return value
                .stream()
                .map(r -> recursiveUpdate(r, previous, newValue))
                .collect(Collectors.toList());
        } else {
            return object;
        }
    }

    private List<Task> listenersTasks() {
        if (this.getListeners() == null) {
            return new ArrayList<>();
        }

        return this.getListeners()
            .stream()
            .flatMap(listener -> listener.getTasks().stream())
            .collect(Collectors.toList());
    }

    public boolean equalsWithoutRevision(Flow o) {
        try {
            return jsonMapper.writeValueAsString(this).equals(jsonMapper.writeValueAsString(o));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public Optional<ConstraintViolationException> validateUpdate(Flow updated) {
        Set<ConstraintViolation<?>> violations = new HashSet<>();

        // change flow id
        if (!updated.getId().equals(this.getId())) {
            violations.add(ManualConstraintViolation.of(
                "Illegal flow id update",
                updated,
                Flow.class,
                "flow.id",
                updated.getId()
            ));
        }

        // change flow namespace
        if (!updated.getNamespace().equals(this.getNamespace())) {
            violations.add(ManualConstraintViolation.of(
                "Illegal namespace update",
                updated,
                Flow.class,
                "flow.namespace",
                updated.getNamespace()
            ));
        }

        if (violations.size() > 0) {
            return Optional.of(new ConstraintViolationException(violations));
        } else {
            return Optional.empty();
        }
    }

    public String generateSource() {
        return FlowService.generateSource(this, null);
    }

    public Flow toDeleted() {
        return this.toBuilder()
            .revision(this.revision + 1)
            .deleted(true)
            .build();
    }
}
