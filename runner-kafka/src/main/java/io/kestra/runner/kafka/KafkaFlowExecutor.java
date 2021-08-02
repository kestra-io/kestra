package io.kestra.runner.kafka;

import com.google.common.collect.Streams;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.runners.FlowExecutorInterface;
import io.kestra.core.runners.MemoryFlowExecutor;
import io.kestra.core.services.FlowService;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.streams.state.KeyValueIterator;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;

import java.util.Optional;

@Slf4j
@KafkaQueueEnabled
@Replaces(MemoryFlowExecutor.class)
@Requires(property = "kestra.server-type", value = "EXECUTOR")
public class KafkaFlowExecutor implements FlowExecutorInterface {
    private final FlowService flowService;
    private final ReadOnlyKeyValueStore<String, Flow> store;

    public KafkaFlowExecutor(ReadOnlyKeyValueStore<String, Flow> store, FlowService flowService) {
        this.store = store;
        this.flowService = flowService;
    }

    @SuppressWarnings("UnstableApiUsage")
    @Override
    public Flow findById(String namespace, String id, Optional<Integer> revision) {
        if (revision.isPresent()) {
            return this.store.get(Flow.uid(namespace, id, revision));
        } else {

            try (KeyValueIterator<String, Flow> flows = this.store.all()) {
                return flowService.keepLastVersion(
                    Streams.stream(flows)
                        .map(kv -> kv.value),
                    namespace,
                    id
                );

            }
        }
    }
}
