package io.kestra.jdbc.runner;

import io.kestra.core.exceptions.DeserializationException;
import io.kestra.core.exceptions.InternalException;
import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.FlowWithException;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.topologies.FlowTopology;
import io.kestra.core.models.triggers.multipleflows.MultipleConditionStorageInterface;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.runners.Executor;
import io.kestra.core.runners.ExecutorService;
import io.kestra.core.runners.*;
import io.kestra.core.services.*;
import io.kestra.core.tasks.flows.Template;
import io.kestra.core.topologies.FlowTopologyService;
import io.kestra.core.utils.Await;
import io.kestra.core.utils.Either;
import io.kestra.jdbc.repository.AbstractJdbcExecutionRepository;
import io.kestra.jdbc.repository.AbstractJdbcFlowTopologyRepository;
import io.kestra.jdbc.repository.AbstractJdbcWorkerInstanceRepository;
import io.kestra.jdbc.repository.AbstractJdbcWorkerJobRunningRepository;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Value;
import io.micronaut.transaction.exceptions.CannotCreateTransactionException;
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
import java.util.Optional;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Singleton
@JdbcRunnerEnabled
@Slf4j
public class JdbcExecutor implements ExecutorInterface {
    private final ScheduledExecutorService schedulerDelay = Executors.newSingleThreadScheduledExecutor();

    private final ScheduledExecutorService schedulerHeartbeat = Executors.newSingleThreadScheduledExecutor();

    private Boolean isShutdown = false;

    @Inject
    private ApplicationContext applicationContext;

    @Inject
    private FlowRepositoryInterface flowRepository;

    @Inject
    private AbstractJdbcExecutionRepository executionRepository;

    @Inject
    @Named(QueueFactoryInterface.EXECUTION_NAMED)
    private QueueInterface<Execution> executionQueue;

    @Inject
    @Named(QueueFactoryInterface.WORKERJOB_NAMED)
    private QueueInterface<WorkerJob> workerTaskQueue;

    @Inject
    @Named(QueueFactoryInterface.WORKERTASKRESULT_NAMED)
    private QueueInterface<WorkerTaskResult> workerTaskResultQueue;

    @Inject
    @Named(QueueFactoryInterface.WORKERTASKLOG_NAMED)
    private QueueInterface<LogEntry> logQueue;

    @Inject
    private RunContextFactory runContextFactory;

    @Inject
    private TaskDefaultService taskDefaultService;

    @Inject
    private Optional<Template.TemplateExecutorInterface> templateExecutorInterface;

    @Inject
    private ExecutorService executorService;

    @Inject
    private ConditionService conditionService;

    @Inject
    private MultipleConditionStorageInterface multipleConditionStorage;

    @Inject
    private AbstractFlowTriggerService flowTriggerService;

    @Inject
    private MetricRegistry metricRegistry;

    @Inject
    protected FlowListenersInterface flowListeners;

    @Inject
    private AbstractJdbcWorkerTaskExecutionStorage workerTaskExecutionStorage;

    @Inject
    private ExecutionService executionService;

    @Inject
    private AbstractJdbcExecutionDelayStorage abstractExecutionDelayStorage;

    @Inject
    private AbstractJdbcExecutorStateStorage executorStateStorage;

    @Inject
    private FlowTopologyService flowTopologyService;

    @Inject
    private AbstractJdbcFlowTopologyRepository flowTopologyRepository;

    @Inject
    private AbstractJdbcWorkerInstanceRepository workerInstanceRepository;

    protected List<Flow> allFlows;

    @Inject
    @Named(QueueFactoryInterface.FLOW_NAMED)
    private QueueInterface<Flow> flowQueue;

    @Inject
    private WorkerGroupService workerGroupService;

    @Inject
    private SkipExecutionService skipExecutionService;

    @Inject
    private AbstractJdbcWorkerJobRunningRepository workerJobRunningRepository;

    @Value("${kestra.heartbeat.frequency}")
    private Duration frequency;

    @SneakyThrows
    @Override
    public void run() {
        flowListeners.run();
        flowListeners.listen(flows -> this.allFlows = flows);

        Await.until(() -> this.allFlows != null, Duration.ofMillis(100), Duration.ofMinutes(5));

        applicationContext.registerSingleton(new DefaultFlowExecutor(flowListeners, this.flowRepository));

        this.executionQueue.receive(Executor.class, this::executionQueue);
        this.workerTaskResultQueue.receive(Executor.class, this::workerTaskResultQueue);

        ScheduledFuture<?> handle = schedulerDelay.scheduleAtFixedRate(
            this::executionDelaySend,
            0,
            1,
            TimeUnit.SECONDS
        );

        schedulerHeartbeat.scheduleAtFixedRate(
            this::workersUpdate,
            frequency.toSeconds(),
            frequency.toSeconds(),
            TimeUnit.SECONDS
        );

        // look at exception on the main thread
        Thread schedulerDelayThread = new Thread(
            () -> {
                Await.until(handle::isDone);

                try {
                    handle.get();
                } catch (ExecutionException | InterruptedException e) {
                    if (e.getCause().getClass() != CannotCreateTransactionException.class) {
                        log.error("Executor fatal exception", e);

                        applicationContext.close();
                        Runtime.getRuntime().exit(1);
                    }
                }
            },
            "jdbc-delay"
        );

        schedulerDelayThread.start();

        flowQueue.receive(
            FlowTopology.class,
            either -> {
                if (either == null || either.isRight() || either.getLeft() == null || either.getLeft() instanceof FlowWithException) {
                    return;
                }

                Flow flow = either.getLeft();
                flowTopologyRepository.save(
                    flow,
                    (flow.isDeleted() ?
                        Stream.<FlowTopology>empty() :
                        flowTopologyService
                            .topology(
                                flow,
                                this.allFlows.stream()
                            )
                    )
                        .distinct()
                        .collect(Collectors.toList())
                );
            }
        );

    }

    protected void workersUpdate() {
        workerInstanceRepository.lockedWorkersUpdate(context -> {
            List<WorkerInstance> workersToDelete = workerInstanceRepository
                .findAllToDelete(context);
            List<String> workersToDeleteUuids = workersToDelete.stream().map(worker -> worker.getWorkerUuid().toString()).collect(Collectors.toList());

            // Before deleting a worker, we resubmit all his tasks
            workerJobRunningRepository.getWorkerJobWithWorkerDead(context, workersToDeleteUuids)
                .forEach(workerJobRunning -> {
                    if (workerJobRunning instanceof WorkerTaskRunning workerTaskRunning) {
                        workerTaskQueue.emit(WorkerTask.builder()
                            .taskRun(workerTaskRunning.getTaskRun())
                            .task(workerTaskRunning.getTask())
                            .runContext(workerTaskRunning.getRunContext())
                            .build()
                        );

                        log.warn(
                            "[namespace: {}] [flow: {}] [execution: {}] [taskrun: {}] WorkerTask is being resend",
                            workerTaskRunning.getTaskRun().getNamespace(),
                            workerTaskRunning.getTaskRun().getFlowId(),
                            workerTaskRunning.getTaskRun().getExecutionId(),
                            workerTaskRunning.getTaskRun().getId()
                        );
                    } else if (workerJobRunning instanceof WorkerTriggerRunning workerTriggerRunning) {
                        workerTaskQueue.emit(WorkerTrigger.builder()
                            .trigger(workerTriggerRunning.getTrigger())
                            .conditionContext(workerTriggerRunning.getConditionContext())
                            .triggerContext(workerTriggerRunning.getTriggerContext())
                            .build());

                        log.warn(
                            "[namespace: {}] [flow: {}] [trigger: {}] WorkerTrigger is being resend",
                            workerTriggerRunning.getTriggerContext().getNamespace(),
                            workerTriggerRunning.getTriggerContext().getFlowId(),
                            workerTriggerRunning.getTriggerContext().getTriggerId()
                        );
                    } else {
                        throw new IllegalArgumentException("Object is of type " + workerJobRunning.getClass() + " which should never occurs");
                    }
                });

            workersToDelete.forEach(worker -> {
                workerInstanceRepository.delete(context, worker);
            });

            return null;
        });
    }

    private void executionQueue(Either<Execution, DeserializationException> either) {
        if (either.isRight()) {
            log.error("Unable to deserialize an execution: {}", either.getRight().getMessage());
            return;
        }

        Execution message = either.getLeft();
        if (skipExecutionService.skipExecution(message.getId())) {
            log.warn("Skipping execution {}", message.getId());
            return;
        }

        Executor result = executionRepository.lock(message.getId(), pair -> {
            // as tasks can be processed in parallel, we must merge the execution from the database to the one we received in the queue
            Execution execution = mergeExecution(pair.getLeft(), message);
            ExecutorState executorState = pair.getRight();

            final Flow flow = transform(this.flowRepository.findByExecution(execution), execution);
            Executor executor = new Executor(execution, null).withFlow(flow);

            if (log.isDebugEnabled()) {
                executorService.log(log, true, executor);
            }

            executor = executorService.process(executor);

            if (!executor.getNexts().isEmpty() && deduplicateNexts(execution, executorState, executor.getNexts())) {
                executor.withExecution(
                    executorService.onNexts(executor.getFlow(), executor.getExecution(), executor.getNexts()),
                    "onNexts"
                );
            }

            // worker task
            if (!executor.getWorkerTasks().isEmpty()) {
                List<WorkerTask> workerTasksDedup = executor
                    .getWorkerTasks()
                    .stream()
                    .filter(workerTask -> this.deduplicateWorkerTask(execution, executorState, workerTask.getTaskRun()))
                    .toList();

                // WorkerTask not flowable to workerTask
                workerTasksDedup
                    .stream()
                    .filter(workerTask -> workerTask.getTask().isSendToWorkerTask())
                    .forEach(workerTask -> workerTaskQueue.emit(workerGroupService.resolveGroupFromJob(workerTask), workerTask));

                // WorkerTask not flowable to workerTaskResult as Running
                workerTasksDedup
                    .stream()
                    .filter(workerTask -> workerTask.getTask().isFlowable())
                    .map(workerTask -> new WorkerTaskResult(workerTask.withTaskRun(workerTask.getTaskRun().withState(State.Type.RUNNING))))
                    .forEach(workerTaskResultQueue::emit);
            }

            // worker tasks results
            if (!executor.getWorkerTaskResults().isEmpty()) {
                executor.getWorkerTaskResults()
                    .forEach(workerTaskResultQueue::emit);
            }

            // schedulerDelay
            if (!executor.getExecutionDelays().isEmpty()) {
                executor.getExecutionDelays()
                    .forEach(executionDelay -> abstractExecutionDelayStorage.save(executionDelay));
            }

            // worker task execution watchers
            if (!executor.getWorkerTaskExecutions().isEmpty()) {
                workerTaskExecutionStorage.save(executor.getWorkerTaskExecutions());

                List<WorkerTaskExecution> workerTasksExecutionDedup = executor
                    .getWorkerTaskExecutions()
                    .stream()
                    .filter(workerTaskExecution -> this.deduplicateWorkerTaskExecution(execution, executorState, workerTaskExecution.getTaskRun()))
                    .toList();

                workerTasksExecutionDedup
                    .forEach(workerTaskExecution -> {
                        String log = "Create new execution for flow '" +
                            workerTaskExecution.getExecution()
                                .getNamespace() + "'.'" + workerTaskExecution.getExecution().getFlowId() +
                            "' with id '" + workerTaskExecution.getExecution()
                            .getId() + "' from task '" + workerTaskExecution.getTask().getId() +
                            "' and taskrun '" + workerTaskExecution.getTaskRun().getId() +
                            (workerTaskExecution.getTaskRun()
                                .getValue() != null ? " (" + workerTaskExecution.getTaskRun()
                                .getValue() + ")" : "") + "'";

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
                flowTriggerService.computeExecutionsFromFlowTriggers(execution, allFlows, Optional.of(multipleConditionStorage))
                    .forEach(this.executionQueue::emit);
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

        if (result != null) {
            this.toExecution(result);
        }
    }

    private Execution mergeExecution(Execution locked, Execution message) {
        Execution newExecution = locked;
        if (message.getTaskRunList() != null) {
            for (TaskRun taskRun : message.getTaskRunList()) {
                try {
                    TaskRun existing = newExecution.findTaskRunByTaskRunId(taskRun.getId());
                    // if the taskrun from the message is newer than the one from the execution, we replace it!
                    if (existing != null && taskRun.getState().maxDate().isAfter(existing.getState().maxDate())) {
                        newExecution = newExecution.withTaskRun(taskRun);
                    }
                }
                catch (InternalException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        return newExecution;
    }

    private void workerTaskResultQueue(Either<WorkerTaskResult, DeserializationException> either) {
        if (either.isRight()) {
            log.error("Unable to deserialize a worker task result: {}", either.getRight().getMessage());
            return;
        }

        WorkerTaskResult message = either.getLeft();
        if (skipExecutionService.skipExecution(message.getTaskRun().getExecutionId())) {
            log.warn("Skipping execution {}", message.getTaskRun().getExecutionId());
            return;
        }

        if (log.isDebugEnabled()) {
            executorService.log(log, true, message);
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
                    newExecution = current.getExecution().withTaskRun(message.getTaskRun());
                    current = current.withExecution(newExecution, "joinWorkerResult");

                    // send metrics on terminated
                    if (message.getTaskRun().getState().isTerminated()) {
                        metricRegistry
                            .counter(MetricRegistry.EXECUTOR_TASKRUN_ENDED_COUNT, metricRegistry.tags(message))
                            .increment();

                        metricRegistry
                            .timer(MetricRegistry.EXECUTOR_TASKRUN_ENDED_DURATION, metricRegistry.tags(message))
                            .record(message.getTaskRun().getState().getDuration());

                        log.trace("TaskRun terminated: {}", message.getTaskRun());
                        workerJobRunningRepository.deleteByKey(message.getTaskRun().getId());
                    }

                    // join worker result
                    return Pair.of(
                        current,
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
        boolean shouldSend = false;
        boolean hasFailure = false;

        if (executor.getException() != null) {
            executor = handleFailedExecutionFromExecutor(executor, executor.getException());
            shouldSend = true;
            hasFailure = true;
        } else if (executor.isExecutionUpdated()) {
            shouldSend = true;
        }

        if (!shouldSend) {
            return;
        }

        if (log.isDebugEnabled()) {
            executorService.log(log, false, executor);
        }

        // emit for other consumer than executor if no failure
        if (hasFailure) {
            this.executionQueue.emit(executor.getExecution());
        } else {
            ((JdbcQueue<Execution>) this.executionQueue).emitOnly(null, executor.getExecution());
        }

        // delete if ended
        if (executorService.canBePurged(executor)) {
            executorStateStorage.delete(executor.getExecution());
        }
    }

    private Flow transform(Flow flow, Execution execution) {
        if (templateExecutorInterface.isPresent()) {
            try {
                flow = Template.injectTemplate(
                    flow,
                    execution,
                    (tenantId, namespace, id) -> templateExecutorInterface.get().findById(tenantId, namespace, id).orElse(null)
                );
            } catch (InternalException e) {
                log.warn("Failed to inject template", e);
            }
        }

        return taskDefaultService.injectDefaults(flow, execution);
    }

    private void executionDelaySend() {
        if (isShutdown) {
            return;
        }

        abstractExecutionDelayStorage.get(executionDelay -> {
            Executor result = executionRepository.lock(executionDelay.getExecutionId(), pair -> {
                Executor executor = new Executor(pair.getLeft(), null);

                try {
                    if (executor.getExecution().findTaskRunByTaskRunId(executionDelay.getTaskRunId()).getState().getCurrent() == State.Type.PAUSED) {

                        Execution markAsExecution = executionService.markAs(
                            pair.getKey(),
                            executionDelay.getTaskRunId(),
                            executionDelay.getState()
                        );

                        executor = executor.withExecution(markAsExecution, "pausedRestart");
                    }
                } catch (Exception e) {
                    executor = handleFailedExecutionFromExecutor(executor, e);
                }

                return Pair.of(
                    executor,
                    pair.getRight()
                );
            });

            if (result != null) {
                this.toExecution(result);
            }
        });
    }

    private boolean deduplicateNexts(Execution execution, ExecutorState executorState, List<TaskRun> taskRuns) {
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

    private boolean deduplicateWorkerTask(Execution execution, ExecutorState executorState, TaskRun taskRun) {
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

    private boolean deduplicateWorkerTaskExecution(Execution execution, ExecutorState executorState, TaskRun taskRun) {
        String deduplicationKey = taskRun.getId();
        State.Type current = executorState.getWorkerTaskExecutionDeduplication().get(deduplicationKey);

        if (current == taskRun.getState().getCurrent()) {
            log.trace("Duplicate WorkerTaskExecution on execution '{}' for taskRun '{}', value '{}, taskId '{}'", execution.getId(), taskRun.getId(), taskRun.getValue(), taskRun.getTaskId());
            return false;
        } else {
            executorState.getWorkerTaskExecutionDeduplication().put(deduplicationKey, taskRun.getState().getCurrent());
            return true;
        }
    }

    private boolean deduplicateFlowTrigger(Execution execution, ExecutorState executorState) {
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
            failedExecutionWithLog.getLogs().forEach(logQueue::emitAsync);

            return executor.withExecution(failedExecutionWithLog.getExecution(), "exception");
        } catch (Exception ex) {
            log.error("Failed to produce {}", e.getMessage(), ex);
        }

        return executor;
    }

    @Override
    public void close() throws IOException {
        isShutdown = true;
        schedulerDelay.shutdown();
        schedulerHeartbeat.shutdown();
        executionQueue.close();
        workerTaskQueue.close();
        workerTaskResultQueue.close();
        logQueue.close();
    }
}
