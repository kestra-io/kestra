package io.kestra.runner.kafka;

import io.kestra.core.models.flows.Flow;
import io.kestra.core.runners.FlowExecutorInterface;
import io.kestra.core.services.FlowService;
import io.kestra.core.utils.Await;
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
    private Map<String, Flow> flows;
    private Map<String, Flow> flowsLast;

    public synchronized void setFlows(List<Flow> flows) {
        this.flows = flows
            .stream()
            .map(flow -> new AbstractMap.SimpleEntry<>(
                flow.uid(),
                flow
            ))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        this.flowsLast = flowService.keepLastVersion(flows)
            .stream()
            .map(flow -> new AbstractMap.SimpleEntry<>(
                flow.uidWithoutRevision(),
                flow
            ))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @SneakyThrows
    private void await() {
        if (flows == null) {
            Await.until(() -> this.flows != null, Duration.ofMillis(100), Duration.ofMinutes(5));
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

        if (revision.isPresent()) {
            String uid = Flow.uid(namespace, id, revision);

            return Optional.ofNullable(this.flows.get(uid));
        }

        String uid = Flow.uidWithoutRevision(namespace, id);

        return Optional.ofNullable(this.flowsLast.get(uid));
    }
}
