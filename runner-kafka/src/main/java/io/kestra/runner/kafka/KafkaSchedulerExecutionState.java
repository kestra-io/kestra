package io.kestra.runner.kafka;

import io.kestra.core.runners.Executor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.schedulers.SchedulerExecutionStateInterface;

import java.util.Optional;
import javax.inject.Singleton;

@Slf4j
@KafkaQueueEnabled
@Singleton
public class KafkaSchedulerExecutionState implements SchedulerExecutionStateInterface {
    private final ReadOnlyKeyValueStore<String, Executor> store;

    public KafkaSchedulerExecutionState(ReadOnlyKeyValueStore<String, Executor> store) {
        this.store = store;
    }

    @Override
    public Optional<Execution> findById(String id) {
        return Optional.ofNullable(this.store.get(id)).map(Executor::getExecution);
    }
}
