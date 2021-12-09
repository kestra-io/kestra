package io.kestra.runner.kafka.streams;

import com.google.common.collect.Streams;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.kstream.ValueTransformerWithKey;
import org.apache.kafka.streams.processor.ProcessorContext;
import org.apache.kafka.streams.state.KeyValueIterator;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.ValueAndTimestamp;
import io.kestra.core.runners.WorkerInstance;
import io.kestra.core.runners.WorkerTask;
import io.kestra.core.runners.WorkerTaskRunning;
import io.kestra.core.services.WorkerInstanceService;
import io.kestra.runner.kafka.KafkaExecutor;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@SuppressWarnings("UnstableApiUsage")
public class WorkerInstanceTransformer implements ValueTransformerWithKey<String, WorkerInstance, List<WorkerInstanceTransformer.Result>> {
    private KeyValueStore<String, WorkerInstance> instanceStore;
    private KeyValueStore<String, ValueAndTimestamp<WorkerTaskRunning>> runningStore;

    public WorkerInstanceTransformer() {
    }

    @Override
    @SuppressWarnings("unchecked")
    public void init(final ProcessorContext context) {
        this.instanceStore = (KeyValueStore<String, WorkerInstance>) context.getStateStore(KafkaExecutor.WORKERINSTANCE_STATE_STORE_NAME);
        this.runningStore = (KeyValueStore<String, ValueAndTimestamp<WorkerTaskRunning>>) context.getStateStore(KafkaExecutor.WORKER_RUNNING_STATE_STORE_NAME);
    }

    @Override
    public List<Result> transform(final String key, final WorkerInstance value) {
        log.trace("Incoming instance: {} {}", key, value);

        if (value == null) {
            return Collections.emptyList();
        }

        List<WorkerInstance> allInstances;

        try (KeyValueIterator<String, WorkerInstance> all = this.instanceStore.all()) {
            allInstances = Streams.stream(all)
                .map(r -> r.value)
                .collect(Collectors.toList());
        }

        log.trace("All instance defined: {}", allInstances);

        List<WorkerInstance> updatedInstances = WorkerInstanceService.removeEvictedPartitions(
            allInstances.stream(),
            value
        );

        log.trace("Updated instances: {}", updatedInstances);

        return updatedInstances
            .stream()
            .map(updated -> {
                String finalInstanceKey = updated.getWorkerUuid().toString();

                if (updated.getPartitions().size() > 0) {
                    return new Result(
                        Collections.emptyList(),
                        KeyValue.pair(finalInstanceKey, updated)
                    );
                } else {
                    // no more partitions for this WorkerInstance, this one doesn't exist any more.
                    // we delete this one and resend all the running tasks
                    List<WorkerTask> workerTasks = this.listRunningForWorkerInstance(updated);

                    if (workerTasks.size() > 0) {
                        log.warn("Detected evicted worker: {}", updated);
                    } else {
                        log.debug("Detected evicted worker: {}", updated);
                    }

                    workerTasks.forEach(workerTask ->
                        log.warn(
                            "[namespace: {}] [flow: {}] [execution: {}] [taskrun: {}] WorkerTask is being resend",
                            workerTask.getTaskRun().getNamespace(),
                            workerTask.getTaskRun().getFlowId(),
                            workerTask.getTaskRun().getExecutionId(),
                            workerTask.getTaskRun().getId()
                        )
                    );

                    return new Result(
                        workerTasks,
                        KeyValue.pair(finalInstanceKey, null)
                    );
                }
            })
            .collect(Collectors.toList());
    }

    private List<WorkerTask> listRunningForWorkerInstance(WorkerInstance workerInstance) {
        try (KeyValueIterator<String, ValueAndTimestamp<WorkerTaskRunning>> all = this.runningStore.all()) {
            List<KeyValue<String, ValueAndTimestamp<WorkerTaskRunning>>> runnings = Streams
                .stream(all)
                .collect(Collectors.toList());

            if (log.isDebugEnabled()) {
                runnings
                    .forEach(kv -> {
                        log.debug(
                            "Current running tasks: {}",
                            runnings.stream()
                                .map(s -> kv.value.value())
                                .collect(Collectors.toList())
                        );
                    });
            }

            return runnings
                .stream()
                .map(r -> r.value.value())
                .filter(r -> r.getWorkerInstance().getWorkerUuid().toString().equals(workerInstance.getWorkerUuid().toString()))
                .map(r -> WorkerTask.builder()
                    .taskRun(r.getTaskRun().onRunningResend())
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
    @ToString
    public static class Result {
        List<WorkerTask> workerTasksToSend;
        KeyValue<String, WorkerInstance> workerInstanceUpdated;
    }
}
