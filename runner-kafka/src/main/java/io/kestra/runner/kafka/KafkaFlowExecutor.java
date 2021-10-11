package io.kestra.runner.kafka;

import com.google.common.collect.Streams;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.runners.FlowExecutorInterface;
import io.kestra.core.services.FlowService;
import io.micronaut.context.ApplicationContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.streams.state.KeyValueIterator;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;

import java.util.Optional;

@Slf4j
public class KafkaFlowExecutor implements FlowExecutorInterface {
    private final FlowService flowService;
    private final ReadOnlyKeyValueStore<String, Flow> store;

    public KafkaFlowExecutor(ReadOnlyKeyValueStore<String, Flow> store, ApplicationContext applicationContext) {
        this.store = store;
        this.flowService = applicationContext.getBean(FlowService.class);
    }

    @SuppressWarnings("UnstableApiUsage")
    @Override
    public Flow findById(String namespace, String id, Optional<Integer> revision, String fromNamespace, String fromId) {
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
