package io.kestra.core.models.flows;

import io.kestra.core.models.tasks.TaskForExecution;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.micronaut.core.annotation.Introspected;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.List;

@SuperBuilder(toBuilder = true)
@Getter
@NoArgsConstructor
@Introspected
@ToString
@EqualsAndHashCode
public class FlowForExecution {
    @NotNull
    @NotBlank
    @Pattern(regexp = "^[a-zA-Z0-9][a-zA-Z0-9._-]*")
    String id;

    @NotNull
    @Pattern(regexp = "^[a-z0-9][a-z0-9._-]*")
    String namespace;

    @Min(value = 1)
    Integer revision;

    @Valid
    List<Input<?>> inputs;

    @Valid
    @NotEmpty
    List<TaskForExecution> tasks;

    @Valid
    List<AbstractTrigger> triggers;

    @NotNull
    @Builder.Default
    boolean disabled = false;

    @NotNull
    @Builder.Default
    boolean deleted = false;

    public static FlowForExecution of(Flow flow) {
        return FlowForExecution.builder()
            .id(flow.getId())
            .namespace(flow.getNamespace())
            .revision(flow.getRevision())
            .inputs(flow.getInputs())
            .tasks(flow.getTasks().stream().map(TaskForExecution::of).toList())
            .disabled(flow.isDisabled())
            .deleted(flow.isDeleted())
            .build();
    }
}
