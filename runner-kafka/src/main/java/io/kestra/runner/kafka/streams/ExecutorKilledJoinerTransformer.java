package io.kestra.runner.kafka.streams;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionKilled;
import io.kestra.core.models.flows.State;
import io.kestra.core.runners.Executor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.streams.kstream.ValueTransformerWithKey;
import org.apache.kafka.streams.processor.ProcessorContext;
import org.apache.kafka.streams.state.KeyValueStore;

@Slf4j
public class ExecutorKilledJoinerTransformer implements ValueTransformerWithKey<String, ExecutionKilled, Executor> {
    private final String storeName;
    private KeyValueStore<String, Executor> store;

    public ExecutorKilledJoinerTransformer(String storeName) {
        this.storeName = storeName;
    }

    @Override
    public void init(final ProcessorContext context) {
        this.store = context.getStateStore(this.storeName);
    }

    @Override
    public Executor transform(final String key, final ExecutionKilled value) {
        if (value == null) {
            return null;
        }

        if (!value.getExecutionId().equals(key)) {
            throw new IllegalStateException("Invalid key for killed with key='" + key + "' and execution='" + value.getExecutionId() + "'");
        }

        Executor executor = this.store.get(key);

        if (executor == null) {
            log.warn("Unable to find Executor with key '" + key + "' for Killed id '" + value.getExecutionId() + "'");
            return null;
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
