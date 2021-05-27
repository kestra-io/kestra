package io.kestra.runner.kafka.streams;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionKilled;
import io.kestra.core.models.flows.State;
import io.kestra.core.runners.AbstractExecutor;
import io.kestra.runner.kafka.KafkaExecutor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.streams.kstream.ValueTransformerWithKey;
import org.apache.kafka.streams.processor.ProcessorContext;
import org.apache.kafka.streams.state.KeyValueStore;

@Slf4j
public class ExecutorKilledJoinerTransformer implements ValueTransformerWithKey<String, ExecutionKilled, KafkaExecutor.Executor> {
    private final String storeName;
    private KeyValueStore<String, AbstractExecutor.Executor> store;

    public ExecutorKilledJoinerTransformer(String storeName) {
        this.storeName = storeName;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void init(final ProcessorContext context) {
        this.store = (KeyValueStore<String, AbstractExecutor.Executor>) context.getStateStore(this.storeName);
    }

    @Override
    public KafkaExecutor.Executor transform(final String key, final ExecutionKilled value) {
        if (value == null) {
            return null;
        }

        if (!value.getExecutionId().equals(key)) {
            throw new IllegalStateException("Invalid key for killed with key='" + key + "' and execution='" + value.getExecutionId() + "'");
        }

        AbstractExecutor.Executor executor = this.store.get(key);

        if (executor == null) {
            throw new IllegalStateException("Unable to find executor with key '" + key + "'");
        }

        if (executor.getExecution().getState().getCurrent() != State.Type.KILLING &&
            !executor.getExecution().getState().isTerninated()
        ) {
            Execution newExecution = executor.getExecution().withState(State.Type.KILLING);

            if (log.isDebugEnabled()) {
                log.debug("Killed << IN\n{}", newExecution.toStringState());
            }

            return executor.withExecution(newExecution, "joinExecutionKilled");
        }

        return executor;
    }

    @Override
    public void close() {
    }
}
