package io.kestra.runner.kafka.streams;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.runners.Executor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.streams.kstream.ValueTransformerWithKey;
import org.apache.kafka.streams.processor.ProcessorContext;
import org.apache.kafka.streams.state.KeyValueStore;

@Slf4j
public class ExecutorFromExecutionTransformer implements ValueTransformerWithKey<String, Execution, Executor> {
    private ProcessorContext context;
    private final String storeName;
    private KeyValueStore<String, Executor> store;

    public ExecutorFromExecutionTransformer(String storeName) {
        this.storeName = storeName;
    }

    @Override
    public void init(final ProcessorContext context) {
        this.context = context;
        this.store = context.getStateStore(this.storeName);
    }

    @Override
    public Executor transform(final String key, final Execution value) {
        if (value == null) {
            return null;
        }

        Executor executor = new Executor(
            value,
            this.context.offset()
        );

        // restart need to be saved on state store for future join
        if (executor.getExecution().getState().getCurrent() == State.Type.RESTARTED) {
            store.put(key, executor.serialize());
        }

        this.context.headers().remove("from");
        this.context.headers().remove("offset");

        return executor;
    }

    @Override
    public void close() {
    }
}
