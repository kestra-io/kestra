package org.kestra.core.tasks.flows;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.models.executions.TaskRun;
import org.kestra.core.models.tasks.FlowableTask;
import org.kestra.core.models.tasks.ResolvedTask;
import org.kestra.core.runners.FlowableUtils;
import org.kestra.core.runners.RunContext;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public class EachSequential extends Sequential implements FlowableTask {
    private String value;

    @Override
    public List<ResolvedTask> childTasks(RunContext runContext, TaskRun parentTaskRun) {
        return this.resolveTasks(runContext, parentTaskRun);
    }

    private List<ResolvedTask> resolveTasks(RunContext runContext, TaskRun parentTaskRun) {
        ObjectMapper mapper = new ObjectMapper();

        String[] values;
        try {
            String renderValue = runContext.render(this.value);
            values = mapper.readValue(renderValue, String[].class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return Arrays
            .stream(values)
            .flatMap(value -> this.getTasks()
                .stream()
                .map(task -> ResolvedTask.builder()
                    .task(task)
                    .value(value)
                    .parentId(parentTaskRun.getId())
                    .build()
                )
            )
            .collect(Collectors.toList());
    }

    @Override
    public List<TaskRun> resolveNexts(RunContext runContext, Execution execution, TaskRun parentTaskRun) {
        return FlowableUtils.resolveSequentialNexts(
            execution,
            this.resolveTasks(runContext, parentTaskRun),
            FlowableUtils.resolveTasks(this.errors, parentTaskRun),
            parentTaskRun
        );
    }
}
