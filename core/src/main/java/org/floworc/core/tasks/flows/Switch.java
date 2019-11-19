package org.floworc.core.tasks.flows;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.floworc.core.models.executions.Execution;
import org.floworc.core.models.executions.TaskRun;
import org.floworc.core.models.flows.State;
import org.floworc.core.models.tasks.FlowableTask;
import org.floworc.core.models.tasks.Task;
import org.floworc.core.runners.FlowableUtils;
import org.floworc.core.runners.RunContext;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public class Switch extends Task implements FlowableTask {
    private String value;

    private Map<String, List<Task>> cases;

    private List<Task> defaults;

    @Override
    public List<Task> childTasks() {
        List<Task> result = new ArrayList<>(defaults);
        cases.forEach((s, tasks) -> result.addAll(tasks));

        return result;
    }

    private List<Task> resolveTasks(RunContext runContext) {
        String renderValue;
        try {
            renderValue = runContext.render(this.value);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return cases
            .entrySet()
            .stream()
            .filter(entry -> entry.getKey().equals(renderValue))
            .map(Map.Entry::getValue)
            .findFirst()
            .orElse(this.defaults);
    }

    @Override
    public Optional<State.Type> resolveState(RunContext runContext, Execution execution) {
        return FlowableUtils.resolveState(runContext, execution, this.resolveTasks(runContext), this.getErrors());
    }

    @Override
    public List<TaskRun> resolveNexts(RunContext runContext, Execution execution) {
        return FlowableUtils.resolveSequentialNexts(runContext, execution, this.resolveTasks(runContext), this.errors);
    }
}
