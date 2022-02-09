package io.kestra.runner.kafka.streams;

import io.kestra.core.models.flows.Flow;
import io.kestra.core.runners.Executor;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.runners.WorkerTaskExecution;
import io.kestra.core.runners.WorkerTaskResult;
import io.kestra.runner.kafka.KafkaFlowExecutor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.streams.kstream.ValueTransformerWithKey;
import org.apache.kafka.streams.processor.ProcessorContext;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.ValueAndTimestamp;

import java.util.Optional;

@Slf4j
public class WorkerTaskExecutionTransformer implements ValueTransformerWithKey<String, Executor, WorkerTaskResult> {
    private final String workerTaskExecutionStoreName;
    private final RunContextFactory runContextFactory;
    private final KafkaFlowExecutor kafkaFlowExecutor;

    private KeyValueStore<String, ValueAndTimestamp<WorkerTaskExecution>> workerTaskExecutionStore;

    public WorkerTaskExecutionTransformer(RunContextFactory runContextFactory, String workerTaskExecutionStoreName, KafkaFlowExecutor kafkaFlowExecutor) {
        this.runContextFactory = runContextFactory;
        this.workerTaskExecutionStoreName = workerTaskExecutionStoreName;
        this.kafkaFlowExecutor = kafkaFlowExecutor;
    }

    @Override
    public void init(final ProcessorContext context) {
        this.workerTaskExecutionStore = context.getStateStore(this.workerTaskExecutionStoreName);
    }

    @Override
    public WorkerTaskResult transform(final String key, final Executor value) {
        ValueAndTimestamp<WorkerTaskExecution> workerTaskExecutionStoreValue = workerTaskExecutionStore.get(key);
        if (workerTaskExecutionStoreValue == null) {
            return null;
        }

        WorkerTaskExecution workerTaskExecution = workerTaskExecutionStoreValue.value();

        Optional<Flow> flowOptional = this.kafkaFlowExecutor.findByExecution(value.getExecution());
        if (flowOptional.isEmpty()) {
            return null;
        }

        Flow flow = flowOptional.get();

        return workerTaskExecution
            .getTask()
            .createWorkerTaskResult(runContextFactory, workerTaskExecution, flow, value.getExecution());
    }

    @Override
    public void close() {
    }
}
