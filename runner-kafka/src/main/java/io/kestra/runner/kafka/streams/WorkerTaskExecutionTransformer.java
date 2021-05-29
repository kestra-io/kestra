package io.kestra.runner.kafka.streams;

import io.kestra.core.models.flows.Flow;
import io.kestra.core.runners.Executor;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.runners.WorkerTaskExecution;
import io.kestra.core.runners.WorkerTaskResult;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.streams.kstream.ValueTransformerWithKey;
import org.apache.kafka.streams.processor.ProcessorContext;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.ValueAndTimestamp;

@Slf4j
public class WorkerTaskExecutionTransformer implements ValueTransformerWithKey<String, Executor, WorkerTaskResult> {
    private final String workerTaskExecutionStoreName;
    private final RunContextFactory runContextFactory;

    private KeyValueStore<String, ValueAndTimestamp<WorkerTaskExecution>> workerTaskExecutionStore;
    private KeyValueStore<String, ValueAndTimestamp<Flow>> flowStore;

    public WorkerTaskExecutionTransformer(RunContextFactory runContextFactory, String workerTaskExecutionStoreName) {
        this.runContextFactory = runContextFactory;
        this.workerTaskExecutionStoreName = workerTaskExecutionStoreName;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void init(final ProcessorContext context) {
        this.flowStore = (KeyValueStore<String, ValueAndTimestamp<Flow>>) context.getStateStore("flow");
        this.workerTaskExecutionStore = (KeyValueStore<String, ValueAndTimestamp<WorkerTaskExecution>>) context.getStateStore(this.workerTaskExecutionStoreName);
    }

    @Override
    public WorkerTaskResult transform(final String key, final Executor value) {
        ValueAndTimestamp<WorkerTaskExecution> workerTaskExecutionStoreValue = workerTaskExecutionStore.get(key);
        if (workerTaskExecutionStoreValue == null) {
            return null;
        }

        WorkerTaskExecution workerTaskExecution = workerTaskExecutionStoreValue.value();

        ValueAndTimestamp<Flow> flowValueAndTimestamp = this.flowStore.get(Flow.uid(value.getExecution()));
        Flow flow = flowValueAndTimestamp.value();

        return workerTaskExecution
            .getTask()
            .createWorkerTaskResult(runContextFactory, workerTaskExecution, flow, value.getExecution());
    }

    @Override
    public void close() {
    }
}
