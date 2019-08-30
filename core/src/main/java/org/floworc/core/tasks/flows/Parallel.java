package org.floworc.core.tasks.flows;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.floworc.core.executions.Execution;
import org.floworc.core.tasks.FlowableTask;
import org.floworc.core.tasks.Task;

import javax.validation.Valid;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
public class Parallel extends Task implements FlowableTask {
    private Integer concurrent;

    @Valid
    private List<Task> tasks;

    @Valid
    private List<Task> errors;

    @Override
    public Optional<Task> findById(String id) {
        Optional<Task> superFind = super.findById(id);
        if (superFind.isPresent()) {
            return superFind;
        }

        return this.tasks
            .stream()
            .map(task -> task.findById(id))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .findFirst();
    }

    @Override
    public boolean hasChildTasks() {
        return true;
    }

    @Override
    public Optional<List<Task>> getChildTasks(Execution execution) {
        List<Task> notFind = this.tasks
            .stream()
            .filter(task -> execution
                .getTaskRunList()
                .stream()
                .anyMatch(taskRun -> !taskRun.getTaskId().equals(task.getId()))
            )
            .collect(Collectors.toList());

        if (notFind.size() == 0) {
            return Optional.empty();
        }

        return Optional.of(notFind);
    }
}
