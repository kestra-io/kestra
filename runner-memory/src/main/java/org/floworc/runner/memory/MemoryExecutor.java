package org.floworc.runner.memory;

import io.micronaut.context.annotation.Prototype;
import lombok.extern.slf4j.Slf4j;
import org.floworc.core.models.executions.Execution;
import org.floworc.core.models.executions.TaskRun;
import org.floworc.core.models.flows.Flow;
import org.floworc.core.models.flows.State;
import org.floworc.core.models.tasks.Task;
import org.floworc.core.queues.QueueFactoryInterface;
import org.floworc.core.queues.QueueInterface;
import org.floworc.core.repositories.FlowRepositoryInterface;
import org.floworc.core.runners.*;

import javax.inject.Named;
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
        FlowRepositoryInterface flowRepository,
        @Named(QueueFactoryInterface.EXECUTION_NAMED) QueueInterface<Execution> executionQueue,
        @Named(QueueFactoryInterface.WORKERTASK_NAMED) QueueInterface<WorkerTask> workerTaskQueue,
        @Named(QueueFactoryInterface.WORKERTASKRESULT_NAMED) QueueInterface<WorkerTaskResult> workerTaskResultQueue
    ) {
        this.flowRepository = flowRepository;
        this.executionQueue = executionQueue;
        this.workerTaskQueue = workerTaskQueue;
        this.workerTaskResultQueue = workerTaskResultQueue;
    }

    @Override
    public void run() {
        this.executionQueue.receive(MemoryExecutor.class, execution -> {
            synchronized (lock) {
                if (execution.getState().isTerninated()) {
                    executions.remove(execution.getId());
                } else {
                    executions.put(execution.getId(), execution);
                }
            }

            Flow flow = this.flowRepository
                .findByExecution(execution)
                .orElseThrow(() -> new IllegalArgumentException("Invalid flow id '" + execution.getFlowId() + "'"));

            this.handlChild(execution, flow);
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
        // submit TaskRun when receiving created, must be done after the state execution store
        List<TaskRun> nexts = execution
            .getTaskRunList()
            .stream()
            .filter(taskRun -> taskRun.getState().getCurrent() == State.Type.CREATED)
            .collect(Collectors.toList());

        for (TaskRun taskRun: nexts) {
            Task task = flow.findTaskById(taskRun.getTaskId());

            this.workerTaskQueue.emit(
                WorkerTask.builder()
                    .runContext(new RunContext(flow, task, execution, taskRun))
                    .taskRun(taskRun)
                    .task(task)
                    .build()
            );
        }
    }

    private void handleNext(Execution execution, Flow flow) {
        List<TaskRun> next = FlowableUtils.resolveSequentialNexts(
            new RunContext(flow, execution),
            execution,
            flow.getTasks(),
            flow.getErrors()
        );

        if (next.size() > 0) {
            Execution newExecution = this.onNexts(flow, execution, next);
            this.executionQueue.emit(newExecution);
        }
    }

    private void handlChild(Execution execution, Flow flow) {
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

    private void handleEnd(Execution execution, Flow flow) {
        if (execution.getState().isTerninated()) {
            return;
        }

        List<Task> currentTasks = execution.findTaskDependingFlowState(flow.getTasks(), flow.getErrors());

        if (execution.isTerminated(currentTasks)) {
            Execution newExecution = this.onEnd(flow, execution);
            this.executionQueue.emit(newExecution);
        }
    }
}
