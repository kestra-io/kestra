package org.kestra.runner.kafka;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.schedulers.SchedulerExecutionStateInterface;

import java.util.Optional;
import javax.inject.Singleton;

@Slf4j
@KafkaQueueEnabled
@Singleton
public class KafkaSchedulerExecutionState implements SchedulerExecutionStateInterface {
    private final ReadOnlyKeyValueStore<String, Execution> store;

    public KafkaSchedulerExecutionState(ReadOnlyKeyValueStore<String, Execution> store) {
        this.store = store;
    }

    @Override
    public Optional<Execution> findById(String id) {
        return Optional.ofNullable(this.store.get(id));
    }
}
