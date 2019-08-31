package org.floworc.core.flows;

import lombok.*;
import org.floworc.core.tasks.Task;
import org.floworc.core.triggers.Trigger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.List;
import java.util.Optional;

@Value
@Builder
public class Flow {
    @NotNull
    private String id;

    @NotNull
    private String namespace;

    @Valid
    private List<Inputs> inputs;

    @Valid
    private List<Task> tasks;

    @Valid
    private List<Task> errors;

    @Valid
    private List<Trigger> triggers;

    public Logger logger() {
        return LoggerFactory.getLogger("flow." + this.id);
    }

    public Task findTaskById(String id) {
        Optional<Task> find = this.tasks
            .stream()
            .flatMap(task -> task.findById(id).stream())
            .findFirst();

        if (find.isPresent()) {
            return find.get();
        }

        if (this.errors != null) {
            Optional<Task> errors = this.errors
                .stream()
                .flatMap(task -> task.findById(id).stream())
                .findFirst();

            if (errors.isPresent()) {
                return errors.get();
            }
        }

        throw new IllegalArgumentException("Can't find task with id '" + id + "' on flow '" + this.id + "'");
    }
}
