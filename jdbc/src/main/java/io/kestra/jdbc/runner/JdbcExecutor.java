package io.kestra.jdbc.runner;

import io.kestra.core.exceptions.InternalException;
import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.runners.*;
import io.kestra.core.services.ConditionService;
import io.kestra.core.services.FlowListenersInterface;
import io.kestra.core.services.FlowService;
import io.kestra.core.services.TaskDefaultService;
import io.kestra.core.tasks.flows.Template;
import io.kestra.core.utils.Await;
import io.kestra.jdbc.repository.AbstractExecutionRepository;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.event.Level;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

@Singleton
@JdbcRunnerEnabled
@Slf4j
public class JdbcExecutor implements ExecutorInterface {
    @Inject
    private ApplicationContext applicationContext;

    @Inject
    private FlowRepositoryInterface flowRepository;

    @Inject
    private AbstractExecutionRepository executionRepository;

    @Inject
    @Named(QueueFactoryInterface.EXECUTION_NAMED)
    private QueueInterface<Execution> executionQueue;

    @Inject
    @Named(QueueFactoryInterface.WORKERTASK_NAMED)
    private QueueInterface<WorkerTask> workerTaskQueue;

    @Inject
    @Named(QueueFactoryInterface.WORKERTASKRESULT_NAMED)
    private QueueInterface<WorkerTaskResult> workerTaskResultQueue;

    @Inject
    @Named(QueueFactoryInterface.WORKERTASKLOG_NAMED)
    private QueueInterface<LogEntry> logQueue;

    @Inject
    private RunContextFactory runContextFactory;

    @Inject
    private FlowService flowService;

    @Inject
    private TaskDefaultService taskDefaultService;

    @Inject
    private Template.TemplateExecutorInterface templateExecutorInterface;

    @Inject
    private ExecutorService executorService;

    @Inject
    private ConditionService conditionService;

    @Inject
    private MetricRegistry metricRegistry;

    @Inject
    protected FlowListenersInterface flowListeners;

    @Inject
    private AbstractJdbcMultipleConditionStorage multipleConditionStorage;

    @Inject
    private AbstractWorkerTaskExecutionStorage workerTaskExecutionStorage;

    private List<Flow> allFlows;

    @SneakyThrows
    @Override
    public void run() {
        flowListeners.run();
        flowListeners.listen(flows -> this.allFlows = flows);

        Await.until(() -> this.allFlows != null, Duration.ofMillis(100), Duration.ofMinutes(5));

        applicationContext.registerSingleton(new DefaultFlowExecutor(flowListeners, this.flowRepository));

        this.executionQueue.receive(Executor.class, this::executionQueue);
        this.workerTaskResultQueue.receive(Executor.class, this::workerTaskResultQueue);
    }

    private void executionQueue(Execution message) {
        executionRepository.lock(message.getId(), pair -> {
            Execution execution = pair.getLeft();
            JdbcExecutorState executorState = pair.getRight();

            final Flow flow = transform(this.flowRepository.findByExecution(execution), execution);
            Executor executor = new Executor(execution, null).withFlow(flow);

            if (log.isDebugEnabled()) {
                executorService.log(log, true, executor);
            }

            executor = executorService.process(executor);

            if (executor.getNexts().size() > 0 && deduplicateNexts(execution, executorState, executor.getNexts())) {
                executor.withExecution(
                    executorService.onNexts(executor.getFlow(), executor.getExecution(), executor.getNexts()),
                    "onNexts"
                );
            }

            if (executor.getException() != null) {
                toExecution(
                    handleFailedExecutionFromExecutor(executor, executor.getException())
                );
            } else if (executor.isExecutionUpdated()) {
                toExecution(executor);
            }

            // worker task
            if (executor.getWorkerTasks().size() > 0) {
                List<WorkerTask> workerTasksDedup = executor
                    .getWorkerTasks()
                    .stream()
                    .filter(workerTask -> this.deduplicateWorkerTask(execution, executorState, workerTask.getTaskRun()))
                    .collect(Collectors.toList());

                // WorkerTask not flowable to workerTask
                workerTasksDedup
                    .stream()
                    .filter(workerTask -> workerTask.getTask().isSendToWorkerTask())
                    .forEach(workerTaskQueue::emit);

                // WorkerTask not flowable to workerTaskResult as Running
                workerTasksDedup
                    .stream()
                    .filter(workerTask -> workerTask.getTask().isFlowable())
                    .map(workerTask -> new WorkerTaskResult(workerTask.withTaskRun(workerTask.getTaskRun().withState(State.Type.RUNNING))))
                    .forEach(workerTaskResultQueue::emit);
            }

            // worker tasks results
            if (executor.getWorkerTaskResults().size() > 0) {
                executor.getWorkerTaskResults()
                    .forEach(workerTaskResultQueue::emit);
            }


//            // schedulerDelay
//            if (executor.getExecutionDelays().size() > 0) {
//                executor.getExecutionDelays()
//                    .forEach(workerTaskResultDelay -> {
//                        long between = ChronoUnit.MICROS.between(Instant.now(), workerTaskResultDelay.getDate());
//
//                        if (between <= 0) {
//                            between = 1;
//                        }
//
//                        schedulerDelay.schedule(
//                            () -> {
//                                try {
//                                    ExecutionState executionState = EXECUTIONS.get(workerTaskResultDelay.getExecutionId());
//
//                                    Execution markAsExecution = executionService.markAs(
//                                        executionState.execution,
//                                        workerTaskResultDelay.getTaskRunId(),
//                                        State.Type.RUNNING
//                                    );
//
//                                    executionQueue.emit(markAsExecution);
//                                } catch (Exception e) {
//                                    throw new RuntimeException(e);
//                                }
//                            },
//                            between,
//                            TimeUnit.MICROSECONDS
//                        );
//                    });
//            }


            // worker task execution watchers
            if (executor.getWorkerTaskExecutions().size() > 0) {
                workerTaskExecutionStorage.save(executor.getWorkerTaskExecutions());

                executor
                    .getWorkerTaskExecutions()
                    .forEach(workerTaskExecution -> {
                        String log = "Create new execution for flow '" +
                            workerTaskExecution.getExecution().getNamespace() + "'." + workerTaskExecution.getExecution().getFlowId() +
                            "' with id '" + workerTaskExecution.getExecution().getId() + "' from task '" + workerTaskExecution.getTask().getId() +
                            "' and taskrun '" + workerTaskExecution.getTaskRun().getId() +
                            (workerTaskExecution.getTaskRun().getValue() != null  ? " (" +  workerTaskExecution.getTaskRun().getValue() + ")" : "") + "'";

                        JdbcExecutor.log.info(log);

                        logQueue.emit(LogEntry.of(workerTaskExecution.getTaskRun()).toBuilder()
                            .level(Level.INFO)
                            .message(log)
                            .timestamp(workerTaskExecution.getTaskRun().getState().getStartDate())
                            .thread(Thread.currentThread().getName())
                            .build()
                        );

                        executionQueue.emit(workerTaskExecution.getExecution());
                    });
            }

            // multiple condition
            if (
                conditionService.isTerminatedWithListeners(flow, execution) &&
                this.deduplicateFlowTrigger(execution, executorState)
            ) {
                // multiple conditions storage
                multipleConditionStorage.save(
                    flowService
                        .multipleFlowTrigger(allFlows.stream(), flow, execution, multipleConditionStorage)
                );

                // Flow Trigger
                flowService
                    .flowTriggerExecution(allFlows.stream(), execution, multipleConditionStorage)
                    .forEach(this.executionQueue::emit);

                // Trigger is done, remove matching multiple condition
                flowService
                    .multipleFlowToDelete(allFlows.stream(), multipleConditionStorage)
                    .forEach(multipleConditionStorage::delete);
            }

            // worker task execution
            if (conditionService.isTerminatedWithListeners(flow, execution)) {
                workerTaskExecutionStorage.get(execution.getId())
                    .ifPresent(workerTaskExecution -> {
                        Flow workerTaskFlow = this.flowRepository.findByExecution(execution);

                        WorkerTaskResult workerTaskResult = workerTaskExecution
                            .getTask()
                            .createWorkerTaskResult(runContextFactory, workerTaskExecution, workerTaskFlow, execution);

                        this.workerTaskResultQueue.emit(workerTaskResult);

                        workerTaskExecutionStorage.delete(workerTaskExecution);
                    });
            }

            return Pair.of(
                executor,
                executorState
            );
        });
    }


    private void workerTaskResultQueue(WorkerTaskResult message) {
        if (log.isDebugEnabled()) {
            executorService.log(log, true, message);
        }

        // send metrics on terminated
        if (message.getTaskRun().getState().isTerninated()) {
            metricRegistry
                .counter(MetricRegistry.EXECUTOR_TASKRUN_ENDED_COUNT, metricRegistry.tags(message))
                .increment();

            metricRegistry
                .timer(MetricRegistry.EXECUTOR_TASKRUN_ENDED_DURATION, metricRegistry.tags(message))
                .record(message.getTaskRun().getState().getDuration());
        }

        Executor executor = executionRepository.lock(message.getTaskRun().getExecutionId(), pair -> {
            Execution execution = pair.getLeft();
            Executor current = new Executor(execution, null);

            if (execution == null) {
                throw new IllegalStateException("Execution state don't exist for " + message.getTaskRun().getExecutionId() + ", receive " + message);
            }

            if (execution.hasTaskRunJoinable(message.getTaskRun())) {
                try {
                    // dynamic task
                    Execution newExecution = executorService.addDynamicTaskRun(
                        current.getExecution(),
                        flowRepository.findByExecution(current.getExecution()),
                        message
                    );

                    if (newExecution != null) {
                        current = current.withExecution(newExecution, "addDynamicTaskRun");
                    }

                    // join worker result
                    return Pair.of(
                        current.withExecution(current.getExecution().withTaskRun(message.getTaskRun()), "joinWorkerResult"),
                        pair.getRight()
                    );
                } catch (InternalException e) {
                    return Pair.of(
                        handleFailedExecutionFromExecutor(current, e),
                        pair.getRight()
                    );
                }
            }

            return null;
        });

        if (executor != null) {
            this.toExecution(executor);
        }
    }

    private void toExecution(Executor executor) {
        if (log.isDebugEnabled()) {
            executorService.log(log, false, executor);
        }

        // emit for other consumer than executor
        this.executionQueue.emit(executor.getExecution());

        // delete if ended
        if (executorService.canBePurged(executor)) {
            // TODO
        }
    }


    private Flow transform(Flow flow, Execution execution) {
        try {
            flow = Template.injectTemplate(
                flow,
                execution,
                (namespace, id) -> templateExecutorInterface.findById(namespace, id).orElse(null)
            );
        } catch (InternalException e) {
            log.warn("Failed to inject template",  e);
        }

        return taskDefaultService.injectDefaults(flow, execution);
    }

    private boolean deduplicateNexts(Execution execution, JdbcExecutorState executorState, List<TaskRun> taskRuns) {
        return taskRuns
            .stream()
            .anyMatch(taskRun -> {
                String deduplicationKey = taskRun.getParentTaskRunId() + "-" + taskRun.getTaskId() + "-" + taskRun.getValue();

                if (executorState.getChildDeduplication().containsKey(deduplicationKey)) {
                    log.trace("Duplicate Nexts on execution '{}' with key '{}'", execution.getId(), deduplicationKey);
                    return false;
                } else {
                    executorState.getChildDeduplication().put(deduplicationKey, taskRun.getId());
                    return true;
                }
            });
    }

    private boolean deduplicateWorkerTask(Execution execution, JdbcExecutorState executorState, TaskRun taskRun) {
        String deduplicationKey = taskRun.getId();
        State.Type current = executorState.getWorkerTaskDeduplication().get(deduplicationKey);

        if (current == taskRun.getState().getCurrent()) {
            log.trace("Duplicate WorkerTask on execution '{}' for taskRun '{}', value '{}, taskId '{}'", execution.getId(), taskRun.getId(), taskRun.getValue(), taskRun.getTaskId());
            return false;
        } else {
            executorState.getWorkerTaskDeduplication().put(deduplicationKey, taskRun.getState().getCurrent());
            return true;
        }
    }

    private boolean deduplicateFlowTrigger(Execution execution, JdbcExecutorState executorState) {
        Boolean flowTriggerDeduplication = executorState.getFlowTriggerDeduplication();

        if (flowTriggerDeduplication) {
            log.trace("Duplicate Flow Trigger on execution '{}'", execution.getId());
            return false;
        } else {
            executorState.setFlowTriggerDeduplication(true);
            return true;
        }
    }

    private Executor handleFailedExecutionFromExecutor(Executor executor, Exception e) {
        Execution.FailedExecutionWithLog failedExecutionWithLog = executor.getExecution().failedExecutionFromExecutor(e);
        try {
            failedExecutionWithLog.getLogs().forEach(logQueue::emit);

            return executor.withExecution(failedExecutionWithLog.getExecution(), "exception");
        } catch (Exception ex) {
            log.error("Failed to produce {}", e.getMessage(), ex);
        }

        return executor;
    }

    @Override
    public void close() throws IOException {
        executionQueue.close();
        workerTaskQueue.close();
        workerTaskResultQueue.close();
        logQueue.close();
    }
}
