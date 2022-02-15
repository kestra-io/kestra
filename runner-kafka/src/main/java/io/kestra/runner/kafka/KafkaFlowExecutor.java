package io.kestra.runner.kafka;

import io.kestra.core.models.flows.Flow;
import io.kestra.core.runners.FlowExecutorInterface;
import io.kestra.core.services.FlowService;
import io.kestra.core.utils.Await;
import io.kestra.runner.kafka.services.SafeKeyValueStore;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Singleton
public class KafkaFlowExecutor implements FlowExecutorInterface {
    @Inject
    private FlowService flowService;
    private SafeKeyValueStore<String, Flow> store;
    private Map<String, Flow> flowsLast;

    public synchronized void setFlows(List<Flow> flows) {
        this.flowsLast = flowService.keepLastVersion(flows)
            .stream()
            .map(flow -> new AbstractMap.SimpleEntry<>(
                flow.uidWithoutRevision(),
                flow
            ))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public synchronized void setStore(SafeKeyValueStore<String, Flow> store) {
        this.store = store;
    }

    @SneakyThrows
    private void await() {
        if (flowsLast == null || store == null) {
            Await.until(() -> this.flowsLast != null && store != null, Duration.ofMillis(100), Duration.ofMinutes(5));
        }
    }

    @Override
    public Collection<Flow> allLastVersion() {
        this.await();

        return this.flowsLast.values();
    }

    @Override
    public Optional<Flow> findById(String namespace, String id, Optional<Integer> revision) {
        this.await();

        String uid = Flow.uidWithoutRevision(namespace, id);
        Flow flowLast = this.flowsLast.get(uid);

        if (revision.isEmpty()) {
            return Optional.ofNullable(flowLast);
        }

        if (flowLast != null && revision.get().equals(flowLast.getRevision())) {
            return Optional.of(flowLast);
        }

        return this.store.get(Flow.uid(namespace, id, revision));
    }
}
