package org.kestra.core.models.listeners;

import io.micronaut.core.annotation.Introspected;
import lombok.Builder;
import lombok.Value;
import org.kestra.core.models.conditions.Condition;
import org.kestra.core.models.tasks.Task;

import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;

@Value
@Builder
@Introspected
public class Listener {
    private String description;

    @Valid
    private List<Condition> conditions;

    @Valid
    @NotEmpty
    private List<Task> tasks;
}
