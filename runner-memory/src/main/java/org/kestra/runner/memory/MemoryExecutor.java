package org.kestra.runner.memory;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Prototype;
import lombok.extern.slf4j.Slf4j;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.models.executions.TaskRun;
import org.kestra.core.models.flows.Flow;
import org.kestra.core.models.flows.State;
import org.kestra.core.models.tasks.ResolvedTask;
import org.kestra.core.queues.QueueFactoryInterface;
import org.kestra.core.queues.QueueInterface;
import org.kestra.core.repositories.FlowRepositoryInterface;
import org.kestra.core.runners.*;

import javax.inject.Named;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Prototype
@MemoryQueueEnabled
public class MemoryExecutor extends AbstractExecutor {
    private FlowRepositoryInterface flowRepository;
    private final QueueInterface<Execution> executionQueue;
    private final QueueInterface<WorkerTask> workerTaskQueue;
    private final QueueInterface<WorkerTaskResult> workerTaskResultQueue;
    private static ConcurrentHashMap<String, Execution> executions = new ConcurrentHashMap<>();
    private static final Object lock = new Object();

    public MemoryExecutor(
        ApplicationContext applicationContext,
        FlowRepositoryInterface flowRepository,
        @Named(QueueFactoryInterface.EXECUTION_NAMED) QueueInterface<Execution> executionQueue,
        @Named(QueueFactoryInterface.WORKERTASK_NAMED) QueueInterface<WorkerTask> workerTaskQueue,
        @Named(QueueFactoryInterface.WORKERTASKRESULT_NAMED) QueueInterface<WorkerTaskResult> workerTaskResultQueue
    ) {
        super(applicationContext);
        this.flowRepository = flowRepository;
        this.executionQueue = executionQueue;
        this.workerTaskQueue = workerTaskQueue;
        this.workerTaskResultQueue = workerTaskResultQueue;
    }

    @Override
    public void run() {
        this.executionQueue.receive(MemoryExecutor.class, execution -> {
            Flow flow = this.flowRepository
                .findByExecution(execution)
                .orElseThrow(() -> new IllegalArgumentException("Invalid flow id '" + execution.getFlowId() + "'"));

            synchronized (lock) {
                if (execution.isTerminatedWithListeners(flow)) {
                    executions.remove(execution.getId());
                } else {
                    executions.put(execution.getId(), execution);
                }
            }

            this.handlChild(execution, flow);
            this.handleListeners(execution, flow);
            this.handleWorkerTask(execution, flow);
            this.handleEnd(execution, flow);
            this.handleNext(execution, flow);
        });

        this.workerTaskResultQueue.receive(MemoryExecutor.class, message -> {
            TaskRun taskRun = message.getTaskRun();

            synchronized (lock) {
                if (!executions.containsKey(taskRun.getExecutionId())) {
                    throw new RuntimeException("Unable to find execution '" + taskRun.getExecutionId() + "' on ExecutionState");
                }

                Execution execution = executions.get(taskRun.getExecutionId());
                Execution newExecution = execution.withTaskRun(taskRun);

                this.executionQueue.emit(newExecution);
            }
        });
    }

    private void handleWorkerTask(Execution execution, Flow flow) {
        if (execution.getTaskRunList() == null) {
            return;
        }

        // submit TaskRun when receiving created, must be done after the state execution store
        List<TaskRun> nexts = execution
            .getTaskRunList()
            .stream()
            .filter(taskRun -> taskRun.getState().getCurrent() == State.Type.CREATED)
            .collect(Collectors.toList());

        for (TaskRun taskRun: nexts) {
            ResolvedTask resolvedTask = flow.findTaskByTaskRun(
                taskRun,
                new RunContext(this.applicationContext, flow, execution)
            );

            this.workerTaskQueue.emit(
                WorkerTask.builder()
                    .runContext(new RunContext(this.applicationContext, flow, resolvedTask, execution, taskRun))
                    .taskRun(taskRun)
                    .task(resolvedTask.getTask())
                    .build()
            );
        }
    }

    private void handleNext(Execution execution, Flow flow) {
        List<TaskRun> next = FlowableUtils.resolveSequentialNexts(
            execution,
            ResolvedTask.of(flow.getTasks()),
            ResolvedTask.of(flow.getErrors())
        );

        if (next.size() > 0) {
            Execution newExecution = this.onNexts(flow, execution, next);
            this.executionQueue.emit(newExecution);
        }
    }

    private void handlChild(Execution execution, Flow flow) {
        if (execution.getTaskRunList() == null) {
            return;
        }

        execution
            .getTaskRunList()
            .stream()
            .filter(taskRun -> taskRun.getState().getCurrent() == State.Type.RUNNING)
            .peek(taskRun -> {
                this.childWorkerTaskResult(flow, execution, taskRun)
                    .ifPresent(this.workerTaskResultQueue::emit);
            })
            .forEach(taskRun -> {
                this.childNexts(flow, execution, taskRun)
                    .ifPresent(this.executionQueue::emit);
            });
    }

    private void handleListeners(Execution execution, Flow flow) {
        if (!execution.getState().isTerninated()) {
            return;
        }

        List<ResolvedTask> currentTasks = execution.findValidListeners(flow);

        List<TaskRun> next = FlowableUtils.resolveSequentialNexts(
            execution,
            currentTasks,
            new ArrayList<>()
        );

        if (next.size() > 0) {
            Execution newExecution = this.onNexts(flow, execution, next);
            this.executionQueue.emit(newExecution);
        }
    }

    private void handleEnd(Execution execution, Flow flow) {
        if (execution.getState().isTerninated()) {
            return;
        }

        List<ResolvedTask> currentTasks = execution.findTaskDependingFlowState(
            ResolvedTask.of(flow.getTasks()),
            ResolvedTask.of(flow.getErrors())
        );

        if (execution.isTerminated(currentTasks)) {
            Execution newExecution = this.onEnd(flow, execution);
            this.executionQueue.emit(newExecution);
        }
    }
}
