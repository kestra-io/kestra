package org.floworc.core.tasks.flows;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.floworc.core.executions.Execution;
import org.floworc.core.executions.ExecutionService;
import org.floworc.core.executions.TaskRun;
import org.floworc.core.tasks.Task;

import java.util.List;
import java.util.Optional;

@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
public class Parallel extends Task {
    private Integer concurrent;

    private List<Task> tasks;

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
    public Optional<List<TaskRun>> getChildTaskRun(Execution execution) {
        return ExecutionService.getNexts(execution, this.tasks);
    }

    @Override
    public Void run() {
        log.info("Starting '{}'", this.tasks);

        return null;
    }
}
