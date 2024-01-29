package io.kestra.core.models.listeners;

import io.micronaut.core.annotation.Introspected;
import lombok.Builder;
import lombok.Value;
import io.kestra.core.models.conditions.Condition;
import io.kestra.core.models.tasks.Task;

import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

@Value
@Builder
@Introspected
public class Listener {
    String description;

    @Valid
    List<Condition> conditions;

    @Valid
    @NotEmpty
    List<Task> tasks;
}
