package org.kestra.core.models.listeners;

import lombok.Builder;
import lombok.Value;
import org.kestra.core.models.tasks.Task;

import javax.validation.Valid;
import java.util.List;

@Value
@Builder
public class Listener {
    @Valid
    private List<Condition> conditions;

    @Valid
    private List<Task> tasks;
}
