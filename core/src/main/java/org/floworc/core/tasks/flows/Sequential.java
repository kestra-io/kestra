package org.floworc.core.tasks.flows;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.floworc.core.models.executions.Execution;
import org.floworc.core.models.executions.TaskRun;
import org.floworc.core.models.tasks.FlowableTask;
import org.floworc.core.runners.FlowableUtils;
import org.floworc.core.models.tasks.Task;
import org.floworc.core.runners.RunContext;

import javax.validation.Valid;
import java.util.List;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public class Sequential extends Task implements FlowableTask {
    @Valid
    private List<Task> tasks;

    @Valid
    private List<Task> errors;

    @Override
    public List<Task> childTasks() {
        return this.tasks;
    }

    @Override
    public List<TaskRun> resolveNexts(RunContext runContext, Execution execution) {
        return FlowableUtils.resolveSequentialNexts(runContext, execution, this.tasks, this.errors);
    }
}
