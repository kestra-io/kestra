package org.kestra.core.models.listeners;

import io.micronaut.core.annotation.Introspected;
import lombok.Builder;
import lombok.Value;
import org.kestra.core.models.tasks.Task;

import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import java.util.List;

@Value
@Builder
@Introspected
public class Listener {
    @Valid
    private List<Condition> conditions;

    @Valid
    @NotEmpty
    private List<Task> tasks;
}
