package io.kestra.core.models.hierarchies;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.exceptions.InternalException;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.tasks.*;
import io.kestra.core.runners.FlowExecutorInterface;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.SubflowExecution;
import io.kestra.core.runners.SubflowExecutionResult;
import lombok.Getter;

import java.util.List;
import java.util.Optional;

@Getter
public class SubflowGraphTask extends AbstractGraphTask {
    public SubflowGraphTask(String uid, ExecutableTask<?> task, TaskRun taskRun, List<String> values, RelationType relationType) {
        super(uid, (TaskInterface) task, taskRun, values, relationType);
    }

    public SubflowGraphTask(ExecutableTask<?> task, TaskRun taskRun, List<String> values, RelationType relationType) {
        super((TaskInterface) task, taskRun, values, relationType);
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
        SubflowGraphTask previous = this;
        return new SubflowGraphTask(this.getUid(), new SubflowTaskWrapper<>(runContext, this.executableTask()), this.getTaskRun(), this.getValues(), this.getRelationType()) {
            @Override
            public int hashCode() {
                // Since edges are handled by a hashmap, we need to keep the same hash and uid is not a good candidate as it changes whenever a node is moved to a cluster
                return previous.hashCode();
            }
        };
    }

    public record SubflowTaskWrapper<T extends Output>(RunContext runContext, ExecutableTask<T> subflowTask) implements TaskInterface, ExecutableTask<T> {
        @Override
        public List<SubflowExecution<?>> createSubflowExecutions(RunContext runContext, FlowExecutorInterface flowExecutorInterface, Flow currentFlow, Execution currentExecution, TaskRun currentTaskRun) throws InternalException {
            return subflowTask.createSubflowExecutions(runContext, flowExecutorInterface, currentFlow, currentExecution, currentTaskRun);
        }

        @Override
        public Optional<SubflowExecutionResult> createSubflowExecutionResult(RunContext runContext, TaskRun taskRun, Flow flow, Execution execution) {
            return subflowTask.createSubflowExecutionResult(runContext, taskRun, flow, execution);
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
    }
}
