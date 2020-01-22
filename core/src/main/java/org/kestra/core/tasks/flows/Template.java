package org.kestra.core.tasks.flows;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.kestra.core.exceptions.InvalidFlowStateException;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.models.executions.TaskRun;
import org.kestra.core.models.flows.Flow;
import org.kestra.core.models.tasks.FlowableTask;
import org.kestra.core.models.tasks.ResolvedTask;
import org.kestra.core.models.tasks.RunContextVariableProvider;
import org.kestra.core.models.tasks.Task;
import org.kestra.core.repositories.FlowRepositoryInterface;
import org.kestra.core.runners.FlowableUtils;
import org.kestra.core.runners.RunContext;

import javax.annotation.Nullable;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public class Template extends Task implements FlowableTask, RunContextVariableProvider {
    @Valid
    @NotNull
    private String namespace;

    @Valid
    @NotNull
    private String flowId;

    @Valid
    @Nullable
    private Integer version;

    @Override
    public List<ResolvedTask> childTasks(RunContext runContext, TaskRun parentTaskRun) {
        Flow flow = this.findFlow(runContext);

        return FlowableUtils.resolveTasks(flow.getTasks(), parentTaskRun);
    }

    @Override
    public List<TaskRun> resolveNexts(RunContext runContext, Execution execution, TaskRun parentTaskRun) {
        Flow flow = this.findFlow(runContext);

        return FlowableUtils.resolveSequentialNexts(
            execution,
            this.childTasks(runContext, parentTaskRun),
            FlowableUtils.resolveTasks(flow.getErrors(), parentTaskRun),
            parentTaskRun
        );
    }

    private org.kestra.core.models.flows.Flow findFlow(RunContext runContext) {
        FlowRepositoryInterface flowRepository = runContext.getApplicationContext().getBean(FlowRepositoryInterface.class);

        return flowRepository
            .findById(
                this.namespace,
                this.flowId,
                this.version != null ? Optional.of(version) : Optional.empty()
            )
            .orElseThrow(() -> new InvalidFlowStateException("Can't find flow template '" + this.namespace + "/" + this.flowId + "'"));
    }

    @Override
    public Map<String, Object> getVariables(RunContext runContext) {
        Flow flow = this.findFlow(runContext);

        return flow.getVariables();
    }
}
