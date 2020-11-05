package org.kestra.core.models.flows;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.AnnotatedMember;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;
import io.micronaut.core.annotation.Introspected;
import lombok.Builder;
import lombok.Value;
import lombok.With;
import org.kestra.core.exceptions.InternalException;
import org.kestra.core.models.DeletedInterface;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.models.listeners.Listener;
import org.kestra.core.models.tasks.Task;
import org.kestra.core.models.triggers.AbstractTrigger;
import org.kestra.core.models.validations.ManualConstraintViolation;
import org.kestra.core.serializers.JacksonMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import javax.validation.Valid;
import javax.validation.constraints.*;

@Value
@Builder
@Introspected
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
    private String id;

    @NotNull
    @Pattern(regexp="[a-z0-9._-]+")
    private String namespace;

    @With
    @Min(value = 1)
    private Integer revision;

    private String description;

    @Valid
    private List<Input> inputs;

    private Map<String, Object> variables;

    @Valid
    @NotEmpty
    private List<Task> tasks;

    @Valid
    private List<Task> errors;

    @Valid
    private List<Listener> listeners;

    @Valid
    private List<AbstractTrigger> triggers;

    @Builder.Default
    @NotNull
    private boolean deleted = false;

    public Logger logger() {
        return LoggerFactory.getLogger("flow." + this.id);
    }

    @JsonIgnore
    public String uid() {
        return String.join("_", Arrays.asList(
            this.getNamespace(),
            this.getId(),
            this.getRevision() != null ? String.valueOf(this.getRevision()) : "-1"
        ));
    }

    @JsonIgnore
    public String uidWithRevision() {
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

    public Task findTaskByTaskId(String taskId) throws InternalException {
        return Stream.of(
            this.tasks != null ? this.tasks : new ArrayList<Task>(),
            this.errors != null ? this.errors : new ArrayList<Task>(),
            this.listenersTasks()
        )
            .flatMap(Collection::stream)
            .flatMap(t -> t.findById(taskId).stream())
            .findFirst()
            .orElseThrow(() -> new InternalException("Can't find task with id '" + id + "' on flow '" + this.id + "'"));
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

    public boolean isListenerTask(String id) {
        return this.listenersTasks()
            .stream()
            .anyMatch(task -> task.getId().equals(id));
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

        if (!updated.getId().equals(this.getId())) {
            violations.add(ManualConstraintViolation.of(
                "Illegal flow id update",
                updated,
                Flow.class,
                "flow.id",
                updated.getId()
            ));
        }

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
            this.revision,
            this.description,
            this.inputs,
            this.variables,
            this.tasks,
            this.errors,
            this.listeners,
            this.triggers,
            true
        );
    }
}
