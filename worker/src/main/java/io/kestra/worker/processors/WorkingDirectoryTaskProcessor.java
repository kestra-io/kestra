package io.kestra.worker.processors;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.models.executions.MetricEntry;
import io.kestra.core.models.executions.TaskRunAttempt;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.DefaultRunContext;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextInitializer;
import io.kestra.core.runners.RunContextLogger;
import io.kestra.core.runners.RunContextLoggerFactory;
import io.kestra.core.runners.WorkerTask;
import io.kestra.core.runners.WorkerTaskResult;
import io.kestra.core.server.ServerConfig;
import io.kestra.core.trace.Tracer;
import io.kestra.core.utils.TruthUtils;
import io.kestra.plugin.core.flow.WorkingDirectory;
import io.kestra.worker.WorkerSecurityService;
import io.kestra.worker.queues.WorkerQueue;
import io.kestra.worker.services.ExecutionKilledManager;

import static io.kestra.core.models.flows.State.Type.SKIPPED;

/**
 * Processor executing a {@link WorkerTask} holding a {@link WorkingDirectory} task.
 * <p>
 * Orchestrates the sequential execution of the children tasks inside a shared working directory:
 * {@code preExecuteTasks}, then each child through the inherited single-task pipeline
 * (chaining the {@link RunContext} from one child to the next), and finally {@code postExecuteTasks}.
 */
public class WorkingDirectoryTaskProcessor extends AbstractWorkerTaskProcessor {

    public WorkingDirectoryTaskProcessor(final String workerId,
        final String workerGroup,
        final ServerConfig serverConfig,
        final MetricRegistry metricRegistry,
        final WorkerSecurityService workerSecurityService,
        final Tracer tracer,
        final RunContextInitializer runContextInitializer,
        final RunContextLoggerFactory runContextLoggerFactory,
        final WorkerQueue<WorkerTaskResult> workerTaskResultQueue,
        final WorkerQueue<MetricEntry> workerMetricQueue,
        final ExecutionKilledManager executionKilledManager) {
        super(workerId, workerGroup, serverConfig, metricRegistry, workerSecurityService, tracer,
            runContextInitializer, runContextLoggerFactory, workerTaskResultQueue, workerMetricQueue, executionKilledManager);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void doProcess(WorkerTask workerTask) {
        if (!(workerTask.getTask() instanceof WorkingDirectory workingDirectory)) {
            throw new IllegalArgumentException("Unable to process the task '" + workerTask.getTask().getId() + "' as it's not a WorkingDirectory task");
        }

        DefaultRunContext runContext = runContextInitializer.forWorkingDirectory(workerTask);
        final RunContext workingDirectoryRunContext = runContext.clone();

        try {
            // preExecuteTasks
            try {
                workingDirectory.preExecuteTasks(workingDirectoryRunContext, workerTask.getTaskRun());
            } catch (Exception e) {
                runContext.logger().error("Failed preExecuteTasks on WorkingDirectory: {}", e.getMessage(), e);
                workerTask = workerTask.withTaskRun(workerTask.fail());
                workerTaskResultQueue.put(new WorkerTaskResult(workerTask.getTaskRun()));
                return;
            }

            // execute all tasks
            for (Task currentTask : workingDirectory.getTasks()) {
                if (Boolean.TRUE.equals(currentTask.getDisabled())) {
                    continue;
                }
                WorkerTask currentWorkerTask = workingDirectory.workerTask(
                    workerTask.getTaskRun(),
                    currentTask,
                    runContext.cloneForPlugin(currentTask)
                );

                // all tasks will be handled immediately by the worker
                WorkerTaskResult workerTaskResult = null;
                try {
                    if (!TruthUtils.isTruthy(runContext.render(currentWorkerTask.getTask().getRunIf()))) {
                        workerTaskResult = new WorkerTaskResult(
                            currentWorkerTask.getTaskRun()
                                .withState(SKIPPED)
                                .addAttempt(TaskRunAttempt.builder().workerId(workerId).state(new State().withState(SKIPPED)).build())
                        );
                        workerTaskResultQueue.put(workerTaskResult);
                    } else {
                        workerTaskResult = this.runTask(
                            currentWorkerTask, false,
                            runContextInitializer.forWorkingDirectorySubtask(currentWorkerTask, runContext.workingDir())
                        );
                    }
                } catch (IllegalVariableEvaluationException e) {
                    RunContextLogger contextLogger = runContextLoggerFactory.create(currentWorkerTask);
                    contextLogger.logger().error("Failed evaluating runIf: {}", e.getMessage(), e);
                    workerTaskResultQueue.put(new WorkerTaskResult(workerTask.fail()));
                }

                if (workerTaskResult == null || workerTaskResult.getTaskRun().getState().isFailed() && !currentWorkerTask.getTask().isAllowFailure()) {
                    break;
                }

                // create the next RunContext populated with the previous WorkerTaskResult
                runContext = runContextInitializer.forWorker(runContext.clone(), workerTaskResult, workerTask.getTaskRun());
            }

            // postExecuteTasks
            try {
                workingDirectory.postExecuteTasks(workingDirectoryRunContext, workerTask.getTaskRun());
            } catch (Exception e) {
                workingDirectoryRunContext.logger().error("Failed postExecuteTasks on WorkingDirectory: {}", e.getMessage(), e);
                workerTaskResultQueue.put(new WorkerTaskResult(workerTask.fail()));
            }
            this.logTerminated(workerTask, workerTask.getTaskRun());
        } finally {
            runContext.cleanup();
        }
    }
}
