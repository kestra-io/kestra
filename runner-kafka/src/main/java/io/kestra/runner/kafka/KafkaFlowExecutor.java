package io.kestra.runner.kafka;

import io.kestra.core.models.flows.Flow;
import io.kestra.core.runners.FlowExecutorInterface;
import io.kestra.core.services.FlowService;
import io.kestra.runner.kafka.services.SafeKeyValueStore;
import io.micronaut.context.ApplicationContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;

import java.util.Optional;

@Slf4j
public class KafkaFlowExecutor implements FlowExecutorInterface {
    private final FlowService flowService;
    private final SafeKeyValueStore<String, Flow> store;

    public KafkaFlowExecutor(ReadOnlyKeyValueStore<String, Flow> store, String name, ApplicationContext applicationContext) {
        this.store = new SafeKeyValueStore<>(store, name);
        this.flowService = applicationContext.getBean(FlowService.class);
    }

    @Override
    public Flow findById(String namespace, String id, Optional<Integer> revision, String fromNamespace, String fromId) {
        if (revision.isPresent()) {
            return this.store.get(Flow.uid(namespace, id, revision))
                .orElseThrow(() -> new IllegalStateException("Unable to find flow '" + namespace + "." + id + "'"));
        } else {
            return flowService.keepLastVersion(
                this.store.all(),
                namespace,
                id
            );
        }
    }
}
