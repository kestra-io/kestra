package io.kestra.core.models.listeners;

import java.util.List;

import io.kestra.core.models.conditions.Condition;
import io.kestra.core.models.tasks.Task;

import io.micronaut.core.annotation.Introspected;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Builder;
import lombok.Value;

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
