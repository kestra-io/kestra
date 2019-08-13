package org.floworc.core.executions;

import lombok.extern.slf4j.Slf4j;
import org.floworc.core.flows.State;
import org.floworc.core.tasks.Task;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Slf4j
public class ExecutionService {
    public static Optional<List<TaskRun>> getNexts(Execution execution, List<Task> tasks) {
        if (tasks.size() == 0) {
            throw new IllegalStateException("Invalid execution " + execution.getId() + " on flow " +
                execution.getFlowId() + " with 0 task"
            );
        }

        // first one
        if (execution.getTaskRunList() == null) {
            return Optional.of(tasks.get(0).toTaskRun(execution));
        }

        // all done
        long terminatedCount = execution
            .getTaskRunList()
            .stream()
            .filter(taskRun -> taskRun.getState().isTerninated())
            .count();

        if (terminatedCount == tasks.size()) {
            return Optional.empty();
        }

        // find first running
        Optional<TaskRun> firstRunning = execution
            .getTaskRunList()
            .stream()
            .filter(taskRun -> taskRun.getState().isRunning())
            .findFirst();

        if (firstRunning.isPresent()) {
            return tasks
                .get(execution.getTaskRunList().indexOf(firstRunning.get()))
                .getChildTaskRun(execution);
        }

        // reverse
        ArrayList<TaskRun> reverse = new ArrayList<>(execution.getTaskRunList());
        Collections.reverse(reverse);

        // find last created

        Optional<TaskRun> lastCreated = reverse
            .stream()
            .filter(taskRun -> taskRun.getState().getCurrent() == State.Type.CREATED)
            .findFirst();

        if (lastCreated.isPresent()) {
            return Optional.of(new ArrayList<>());
        }

        // find last termintated
        Optional<TaskRun> lastTerminated = reverse
            .stream()
            .filter(taskRun -> taskRun.getState().isTerninated())
            .findFirst();

        if (lastTerminated.isPresent()) {
            if (lastTerminated.get().getState().getCurrent() == State.Type.FAILED) {
                log.warn("Must find errors path");
                return Optional.of(new ArrayList<>());
            } else {
                int index = execution.getTaskRunList().indexOf(lastTerminated.get());

                if (tasks.size() > index - 1) {
                    return Optional.of(tasks
                        .get(index + 1)
                        .toTaskRun(execution)
                    );
                }
            }
        }

        return Optional.of(new ArrayList<>());
    }
}
