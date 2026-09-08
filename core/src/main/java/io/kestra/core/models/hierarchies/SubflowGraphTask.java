package io.kestra.core.models.hierarchies;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.exceptions.InternalException;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.FlowInterface;
import io.kestra.core.models.tasks.*;
import io.kestra.core.runners.FlowMetaStoreInterface;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.SubflowExecution;
import io.kestra.core.runners.SubflowExecutionResult;

import lombok.Getter;

@Getter
public class SubflowGraphTask extends AbstractGraphTask {
    private final boolean disabled;

    public SubflowGraphTask(String uid, ExecutableTask<?> task, TaskRun taskRun, List<String> values, RelationType relationType, boolean disabled) {
        super(uid, (TaskInterface) task, taskRun, values, relationType);
        this.disabled = disabled;
    }

    public SubflowGraphTask(ExecutableTask<?> task, TaskRun taskRun, List<String> values, RelationType relationType) {
        this(task, taskRun, values, relationType, false);
    }

    public SubflowGraphTask(ExecutableTask<?> task, TaskRun taskRun, List<String> values, RelationType relationType, boolean disabled) {
        super((TaskInterface) task, taskRun, values, relationType);
        this.disabled = disabled;
    }

    public ExecutableTask<?> executableTask() {
        TaskInterface task = super.getTask();
        if (task instanceof ExecutableTask<?> executableTask) {
            return executableTask;
        } else {
            return null;
        }
    }

    public SubflowGraphTask withRenderedSubflowId(RunContext runContext) {
        return withRenderedSubflowId(runContext, this.disabled);
    }

    public SubflowGraphTask withRenderedSubflowId(RunContext runContext, boolean disabled) {
        return copy(new SubflowTaskWrapper<>(runContext, this.executableTask()), disabled);
    }

    public SubflowGraphTask withDisabled(boolean disabled) {
        return copy(this.executableTask(), disabled);
    }

    private SubflowGraphTask copy(ExecutableTask<?> task, boolean disabled) {
        SubflowGraphTask copy = new SubflowGraphTask(this.getUid(), task, this.getTaskRun(), this.getValues(), this.getRelationType(), disabled);
        if (this.getBranchType() != null) {
            copy.updateWithChildren(this.getBranchType());
        }
        return copy;
    }

    public record SubflowTaskWrapper<T extends Output>(RunContext runContext, ExecutableTask<T> subflowTask) implements TaskInterface, ExecutableTask<T> {
        @Override
        public List<SubflowExecution<?>> createSubflowExecutions(RunContext runContext, FlowMetaStoreInterface flowExecutorInterface, FlowInterface currentFlow, Execution currentExecution,
            TaskRun currentTaskRun) throws InternalException {
            return subflowTask.createSubflowExecutions(runContext, flowExecutorInterface, currentFlow, currentExecution, currentTaskRun);
        }

        @Override
        public Optional<SubflowExecutionResult> createSubflowExecutionResult(RunContext runContext, TaskRun taskRun, FlowInterface flow, Execution execution, Map<String, Object> outputs) {
            return subflowTask.createSubflowExecutionResult(runContext, taskRun, flow, execution, outputs);
        }

        @Override
        public boolean waitForExecution() {
            return subflowTask.waitForExecution();
        }

        @Override
        public SubflowId subflowId() {
            String namespace = subflowTask.subflowId().namespace();
            String flowId = subflowTask.subflowId().flowId();
            if (runContext != null) {
                try {
                    namespace = runContext.render(namespace);
                    flowId = runContext.render(flowId);
                } catch (IllegalVariableEvaluationException e) {
                    throw new IllegalArgumentException(e);
                }
            }
            return new SubflowId(namespace, flowId, subflowTask.subflowId().revision());
        }

        @Override
        public RestartBehavior getRestartBehavior() {
            return subflowTask.getRestartBehavior();
        }

        @Override
        public String getId() {
            return ((TaskInterface) subflowTask).getId();
        }

        @Override
        public String getType() {
            return ((TaskInterface) subflowTask).getType();
        }

        @Override
        public String getVersion() {
            return ((TaskInterface) subflowTask).getVersion();
        }
    }
}
