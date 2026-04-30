package io.kestra.core.models.flows;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.introspect.AnnotatedMember;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;

import io.kestra.core.exceptions.InternalException;
import io.kestra.core.models.HasUID;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.flows.check.Check;
import io.kestra.core.models.flows.sla.SLA;
import io.kestra.core.models.tasks.FlowableTask;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.tasks.retrys.AbstractRetry;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.validations.ManualConstraintViolation;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.utils.ListUtils;
import io.kestra.core.validations.FlowValidation;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.*;
import lombok.experimental.SuperBuilder;

/**
 * A serializable flow with no source.
 * <p>
 * This class is planned for deprecation - use the {@link FlowWithSource}.
 */
@SuperBuilder(toBuilder = true)
@Getter
@NoArgsConstructor
@ToString
@EqualsAndHashCode
@FlowValidation
public class Flow extends AbstractFlow implements HasUID {
    private static final ObjectMapper NON_DEFAULT_OBJECT_MAPPER = JacksonMapper.ofYaml()
        .copy()
        .setDefaultPropertyInclusion(JsonInclude.Include.NON_DEFAULT);

    private static final ObjectMapper WITHOUT_REVISION_OBJECT_MAPPER = NON_DEFAULT_OBJECT_MAPPER.copy()
        .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
        .setAnnotationIntrospector(new JacksonAnnotationIntrospector() {
            @Override
            public boolean hasIgnoreMarker(final AnnotatedMember m) {
                List<String> exclusions = Arrays.asList("revision", "deleted", "source", "updated");
                return exclusions.contains(m.getName()) || super.hasIgnoreMarker(m);
            }
        });

    @Schema(
        type = "object",
        additionalProperties = Schema.AdditionalPropertiesValue.FALSE
    )
    Map<String, Object> variables;

    @Valid
    @NotEmpty
    @Schema(additionalProperties = Schema.AdditionalPropertiesValue.TRUE)
    List<Task> tasks;

    @Valid
    List<Task> errors;

    @Valid
    @JsonProperty("finally")
    @Getter(AccessLevel.NONE)
    protected List<Task> _finally;

    public List<Task> getFinally() {
        return this._finally;
    }

    @Valid
    List<Task> afterExecution;

    @Valid
    List<AbstractTrigger> triggers;

    @Valid
    List<FlowPluginDefault> pluginDefaults;

    @Valid
    Concurrency concurrency;

    @Schema(
        title = "Output values available and exposes to other flows.",
        description = "Output values make information about the execution of your Flow available and expose for other Kestra flows to use. Output values are similar to return values in programming languages."
    )
    @PluginProperty(dynamic = true)
    @Valid
    List<Output> outputs;

    // implementation = Object.class prevents the Micronaut OpenAPI annotation processor from following
    // the @JsonSubTypes on AbstractRetry, which causes a PostponeToNextRoundException at compile time
    // due to the Micronaut constraint validators on the concrete retry subtypes (Constant, Exponential, Random).
    @Schema(
        title = "Retry",
        description = "Retry policy applied when the flow fails.",
        implementation = Object.class
    )
    @Valid
    AbstractRetry retry;

    @Valid
    @PluginProperty
    List<SLA> sla;

    @Schema(
        title = "Conditions evaluated before the flow is executed.",
        description = "A list of conditions that are evaluated before the flow is executed.  If no checks are defined, the flow executes normally."
    )
    @Valid
    @PluginProperty
    List<Check> checks;

    public Stream<String> allTypes() {
        return Stream.of(
            Optional.ofNullable(triggers).orElse(Collections.emptyList()).stream().map(AbstractTrigger::getType),
            allTasks().map(Task::getType),
            Optional.ofNullable(pluginDefaults).orElse(Collections.emptyList()).stream().map(FlowPluginDefault::getType)
        ).reduce(Stream::concat).orElse(Stream.empty())
            .distinct();
    }

    public Stream<Task> allTasks() {
        return Stream.of(
            this.tasks != null ? this.tasks : Collections.<Task> emptyList(),
            this.errors != null ? this.errors : Collections.<Task> emptyList(),
            this._finally != null ? this._finally : Collections.<Task> emptyList(),
            this.afterExecution != null ? this.afterExecution : Collections.<Task> emptyList()
        )
            .flatMap(Collection::stream);
    }

    public List<Task> allTasksWithChilds() {
        return allTasks()
            .flatMap(this::allTasksWithChilds)
            .toList();
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
            .filter(trigger -> trigger != null && trigger.getId() != null) // this can happen when validating a flow under creation
            .map(AbstractTrigger::getId)
            .collect(Collectors.toList()) : Collections.emptyList();
    }

    public List<String> allTasksWithChildsAndTriggerIds() {
        return Stream.concat(
            this.allTasksWithChilds().stream()
                .map(Task::getId),
            this.allTriggerIds().stream()
        )
            .toList();
    }

    public List<Task> allErrorsWithChildren() {
        var allErrors = allTasksWithChilds().stream()
            .filter(task -> task.isFlowable() && ((FlowableTask<?>) task).getErrors() != null)
            .flatMap(task -> ((FlowableTask<?>) task).getErrors().stream())
            .collect(Collectors.toCollection(ArrayList::new));

        if (!ListUtils.isEmpty(this.getErrors())) {
            allErrors.addAll(this.getErrors());
        }

        return allErrors;
    }

    public List<Task> allFinallyWithChildren() {
        var allFinally = allTasksWithChilds().stream()
            .filter(task -> task.isFlowable() && ((FlowableTask<?>) task).getFinally() != null)
            .flatMap(task -> ((FlowableTask<?>) task).getFinally().stream())
            .collect(Collectors.toCollection(ArrayList::new));

        if (!ListUtils.isEmpty(this.getFinally())) {
            allFinally.addAll(this.getFinally());
        }

        return allFinally;
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

    public AbstractTrigger findTriggerByTriggerId(String triggerId) {
        return this.triggers
            .stream()
            .filter(trigger -> trigger.getId().equals(triggerId))
            .findFirst()
            .orElse(null);
    }

    public boolean equalsWithoutRevision(FlowInterface o) {
        try {
            return WITHOUT_REVISION_OBJECT_MAPPER.writeValueAsString(this).equals(WITHOUT_REVISION_OBJECT_MAPPER.writeValueAsString(o));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public Optional<ConstraintViolationException> validateUpdate(Flow updated) {
        Set<ConstraintViolation<?>> violations = new HashSet<>();

        // change flow id
        if (!updated.getId().equals(this.getId())) {
            violations.add(
                ManualConstraintViolation.of(
                    "Illegal flow id update",
                    updated,
                    Flow.class,
                    "flow.id",
                    updated.getId()
                )
            );
        }

        // change flow namespace
        if (!updated.getNamespace().equals(this.getNamespace())) {
            violations.add(
                ManualConstraintViolation.of(
                    "Illegal namespace update",
                    updated,
                    Flow.class,
                    "flow.namespace",
                    updated.getNamespace()
                )
            );
        }

        if (!violations.isEmpty()) {
            return Optional.of(new ConstraintViolationException(violations));
        } else {
            return Optional.empty();
        }
    }

    @Override
    public Flow toDeleted() {
        return this.toBuilder()
            .revision(this.revision + 1)
            .deleted(true)
            .build();
    }

    /**
     * {@inheritDoc}
     * To be conservative a flow MUST not return any source.
     */
    @Override
    @Schema(hidden = true)
    public String getSource() {
        return null;
    }
}
