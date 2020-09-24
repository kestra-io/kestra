package org.kestra.runner.kafka.streams;

import com.google.common.collect.Streams;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.kstream.ValueTransformerWithKey;
import org.apache.kafka.streams.processor.ProcessorContext;
import org.apache.kafka.streams.state.KeyValueIterator;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.ValueAndTimestamp;
import org.kestra.core.runners.WorkerInstance;
import org.kestra.core.runners.WorkerTask;
import org.kestra.core.runners.WorkerTaskRunning;
import org.kestra.core.services.WorkerInstanceService;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@SuppressWarnings("UnstableApiUsage")
public class WorkerInstanceTransformer implements ValueTransformerWithKey<String, WorkerInstance, WorkerInstanceTransformer.Result> {
    private final String instanceStoreName;

    private KeyValueStore<String, WorkerInstance> instanceStore;
    private KeyValueStore<String, ValueAndTimestamp<WorkerTaskRunning>> runningStore;

    public WorkerInstanceTransformer(String instanceStoreName) {
        this.instanceStoreName = instanceStoreName;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void init(final ProcessorContext context) {
        this.instanceStore = (KeyValueStore<String, WorkerInstance>) context.getStateStore(this.instanceStoreName);
        this.runningStore = (KeyValueStore<String, ValueAndTimestamp<WorkerTaskRunning>>) context.getStateStore("worker_running");
    }

    @Override
    public Result transform(final String key, final WorkerInstance value) {
        if (value == null) {
            this.instanceStore.delete(key);

            return null;
        }

        this.instanceStore.put(key, value);

        try (KeyValueIterator<String, WorkerInstance> all = this.instanceStore.all()) {
            WorkerInstance updated = WorkerInstanceService.removeEvictedPartitions(
                Streams.stream(all).map(r -> r.value),
                value
            );

            if (updated != null) {
                String finalInstanceKey = updated.getWorkerUuid().toString();

                if (updated.getPartitions().size() > 0) {
                    return new Result(
                        Collections.emptyList(),
                        KeyValue.pair(finalInstanceKey, updated)
                    );
                } else {
                    // no more partitions for this WorkerInstance, this one doesn't exist any more.
                    // we delete this one and resend all the running tasks
                    log.warn("Detected evicted worker: {}", updated);

                    List<WorkerTask> workerTasks = this.listRunningForWorkerInstance(updated);

                    workerTasks.forEach(workerTask ->
                        log.info(
                            "[namespace: {}] [flow: {}] [execution: {}] [taskrun: {}] WorkerTask is being resend",
                            workerTask.getTaskRun().getNamespace(),
                            workerTask.getTaskRun().getFlowId(),
                            workerTask.getTaskRun().getId(),
                            workerTask.getTaskRun().getExecutionId()
                        )
                    );

                    return new Result(
                        workerTasks,
                        KeyValue.pair(finalInstanceKey, null)
                    );
                }
            }
        }

        return null;
    }

    private List<WorkerTask> listRunningForWorkerInstance(WorkerInstance workerInstance) {
        try (KeyValueIterator<String, ValueAndTimestamp<WorkerTaskRunning>> all = this.runningStore.all()) {
            return Streams.stream(all)
                .map(r -> r.value.value())
                .filter(r -> r.getWorkerInstance().getWorkerUuid().toString().equals(workerInstance.getWorkerUuid().toString()))
                .map(r -> WorkerTask.builder()
                    .runContext(r.getRunContext())
                    .taskRun(r.getTaskRun())
                    .task(r.getTask())
                    .build()
                )
                .collect(Collectors.toList());
        }
    }

    @Override
    public void close() {
    }

    @AllArgsConstructor
    @Getter
    public static class Result {
        List<WorkerTask> workerTasksToSend;
        KeyValue<String, WorkerInstance> workerInstanceUpdated;
    }
}
