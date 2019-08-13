package org.floworc.core.executions;

import lombok.*;
import lombok.experimental.Wither;
import org.floworc.core.flows.State;

import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Value
@Builder
public class Execution {
    @NotNull
    private String id;

    @NotNull
    private String flowId;

    @Wither
    private List<TaskRun> taskRunList;

    @Wither
    private Context context;

    @NotNull
    private State state;

    public Execution withState(State.Type state) {
        return new Execution(
            this.id,
            this.flowId,
            this.taskRunList,
            this.context,
            this.state.withState(state)
        );
    }

    public TaskRun findTaskRunById(String id) {
        Optional<TaskRun> find = this.taskRunList
            .stream()
            .filter(task -> task.getId().equals(id))
            .findFirst();

        if (!find.isPresent()) {
            throw new IllegalArgumentException("Can't find taskrun with id '" + id + "' on execution '" + this.id + "'");
        }

        return find.get();
    }

    public Execution withTaskRun(TaskRun taskRun) {
        ArrayList<TaskRun> newTaskRunList = new ArrayList<>(this.taskRunList);

        //noinspection ResultOfMethodCallIgnored
        Collections.replaceAll(
            newTaskRunList,
            this.findTaskRunById(taskRun.getId()),
            taskRun
        );

        return new Execution(
            this.id,
            this.flowId,
            newTaskRunList,
            this.context,
            this.state
        );
    }
}
