package io.kestra.core.endpoints;

import io.micronaut.context.annotation.Requires;
import io.micronaut.management.endpoint.annotation.Endpoint;
import io.micronaut.management.endpoint.annotation.Read;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.runners.Worker;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import javax.inject.Inject;

@Endpoint(id = "worker", defaultSensitive = false)
@Requires(property = "kestra.server-type", pattern = "(WORKER|STANDALONE)")
public class WorkerEndpoint {
    @Inject
    Worker worker;

    @Read
    public WorkerEndpointResult running() {
        return WorkerEndpointResult.builder()
            .runningCount(worker.getMetricRunningCount().values()
                .stream()
                .mapToInt(AtomicInteger::get)
                .sum()
            )
            .runnings(
                worker.getWorkerThreadReferences()
                    .stream()
                    .map(workerThread -> new WorkerEndpointWorkerTask(
                        workerThread.getWorkerTask().getTaskRun(),
                        workerThread.getWorkerTask().getTask())
                    )
                    .collect(Collectors.toList())
            )
            .build()
        ;
    }

    @Getter
    @Builder
    public static class WorkerEndpointResult {
        private final int runningCount;
        private final List<WorkerEndpointWorkerTask> runnings;
    }

    @Getter
    @AllArgsConstructor
    public static class WorkerEndpointWorkerTask {
        private final TaskRun taskRun;
        private final Task task;
    }
}
