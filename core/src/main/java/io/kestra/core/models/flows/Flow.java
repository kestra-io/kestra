package io.kestra.core.models.flows;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.AnnotatedMember;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;
import io.kestra.core.exceptions.InternalException;
import io.kestra.core.models.DeletedInterface;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.listeners.Listener;
import io.kestra.core.models.tasks.FlowableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.tasks.TaskValidationInterface;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.validations.ManualConstraintViolation;
import io.kestra.core.serializers.JacksonMapper;
import io.micronaut.core.annotation.Introspected;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import javax.validation.Valid;
import javax.validation.constraints.*;

@SuperBuilder
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Introspected
@ToString
@EqualsAndHashCode
public class Flow implements DeletedInterface {
    private static final ObjectMapper jsonMapper = JacksonMapper.ofJson().copy()
        .setAnnotationIntrospector(new JacksonAnnotationIntrospector() {
            @Override
            public boolean hasIgnoreMarker(final AnnotatedMember m) {
                List<String> exclusions = Arrays.asList("revision", "deleted");
                return exclusions.contains(m.getName()) || super.hasIgnoreMarker(m);
            }
        });

    @NotNull
    @NotBlank
    @Pattern(regexp="[a-zA-Z0-9._-]+")
    String id;

    @NotNull
    @Pattern(regexp="[a-z0-9._-]+")
    String namespace;

    @With
    @Min(value = 1)
    Integer revision;

    String description;

    Map<String, String> labels;

    @Valid
    List<Input> inputs;

    Map<String, Object> variables;

    @Valid
    @NotEmpty
    List<Task> tasks;

    @Valid
    List<Task> errors;

    @Valid
    List<Listener> listeners;

    @Valid
    List<AbstractTrigger> triggers;

    List<TaskDefault> taskDefaults;

    @NotNull
    @Builder.Default
    boolean disabled = false;

    @NotNull
    @Builder.Default
    boolean deleted = false;

    public Logger logger() {
        return LoggerFactory.getLogger("flow." + this.id);
    }

    @JsonIgnore
    public String uid() {
        return Flow.uid(this.getNamespace(), this.getId(), Optional.ofNullable(this.revision));
    }

    @JsonIgnore
    public String uidWithoutRevision() {
        return String.join("_", Arrays.asList(
            this.getNamespace(),
            this.getId()
        ));
    }

    public static String uid(Execution execution) {
        return String.join("_", Arrays.asList(
            execution.getNamespace(),
            execution.getFlowId(),
            String.valueOf(execution.getFlowRevision())
        ));
    }

    public static String uid(String namespace, String id, Optional<Integer> revision) {
        return String.join("_", Arrays.asList(
            namespace,
            id,
            String.valueOf(revision.orElse(-1))
        ));
    }

    public static String uidWithoutRevision(String namespace, String id) {
        return String.join("_", Arrays.asList(
            namespace,
            id
        ));
    }

    public static String uidWithoutRevision(Execution execution) {
        return String.join("_", Arrays.asList(
            execution.getNamespace(),
            execution.getFlowId()
        ));
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
        if (task.isFlowable()) {
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

    public Task findTaskByTaskId(String taskId) throws InternalException {
        return allTasks()
            .flatMap(t -> t.findById(taskId).stream())
            .findFirst()
            .orElseThrow(() -> new InternalException("Can't find task with id '" + id + "' on flow '" + this.id + "'"));
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

    public Optional<ConstraintViolationException> validate() {
        Set<ConstraintViolation<?>> violations = new HashSet<>();

        List<Task> allTasks = allTasksWithChilds();

        // unique id
        List<String> ids = allTasks
            .stream()
            .map(Task::getId)
            .collect(Collectors.toList());

        List<String> duplicates = ids
            .stream()
            .distinct()
            .filter(entry -> Collections.frequency(ids, entry) > 1).collect(Collectors.toList());

        if (duplicates.size() > 0) {
            violations.add(ManualConstraintViolation.of(
                "Duplicate task id with name [" +   String.join(", ", duplicates) + "]",
                this,
                Flow.class,
                "flow.tasks",
                String.join(", ", duplicates)
            ));
        }

        // validate tasks
        allTasks
            .forEach(task -> {
                if (task instanceof TaskValidationInterface) {
                    violations.addAll(((TaskValidationInterface<?>) task).failedConstraints());
                }
            });

        if (violations.size() > 0) {
            return Optional.of(new ConstraintViolationException(violations));
        } else {
            return Optional.empty();
        }
    }

    public Optional<ConstraintViolationException> validateUpdate(Flow updated) {
        Set<ConstraintViolation<?>> violations = new HashSet<>();

        // validate flow
        updated.validate()
            .ifPresent(e -> violations.addAll(e.getConstraintViolations()));

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

    public Flow toDeleted() {
        return new Flow(
            this.id,
            this.namespace,
            this.revision + 1,
            this.description,
            this.labels,
            this.inputs,
            this.variables,
            this.tasks,
            this.errors,
            this.listeners,
            this.triggers,
            this.taskDefaults,
            this.disabled,
            true
        );
    }
}
