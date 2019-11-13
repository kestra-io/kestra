package org.floworc.core.tasks.flows;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.floworc.core.models.executions.Execution;
import org.floworc.core.models.tasks.FlowableResult;
import org.floworc.core.models.tasks.FlowableTask;
import org.floworc.core.models.tasks.Task;
import org.floworc.core.runners.FlowableUtils;
import org.floworc.core.runners.RunContext;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

    @Override
    public FlowableResult nexts(RunContext runContext, Execution execution) {
        String renderValue;
        try {
            renderValue = runContext.render(this.value);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return FlowableUtils.getNexts(
            runContext,
            execution,
            cases
                .entrySet()
                .stream()
                .filter(entry -> entry.getKey().equals(renderValue))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(this.defaults),
            this.errors
        );
    }
}
