package io.kestra.core.runners;

import com.google.common.annotations.VisibleForTesting;
import io.kestra.core.exceptions.FlowProcessingException;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.FlowId;
import io.kestra.core.models.flows.FlowInterface;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.services.PluginDefaultService;
import io.kestra.core.utils.Disposable;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
@Slf4j
public class DefaultFlowMetaStore implements FlowMetaStoreInterface {
    private final FlowRepositoryInterface flowRepository;
    private final PluginDefaultService pluginDefaultService;
    private final ConcurrentHashMap<String, FlowWithSource> cache = new ConcurrentHashMap<>();
    private final QueueInterface<FlowInterface> flowQueue;

    private Disposable cancellation;

    public DefaultFlowMetaStore(FlowRepositoryInterface flowRepository, PluginDefaultService pluginDefaultService, @Named(QueueFactoryInterface.FLOW_NAMED) QueueInterface<FlowInterface> flowQueue) {
        this.flowRepository = flowRepository;
        this.pluginDefaultService = pluginDefaultService;
        this.flowQueue = flowQueue;

        flowRepository.findAllWithSourceForAllTenants().forEach(it -> cache.put(it.uidWithoutRevision(), it));
    }

    @VisibleForTesting
    void clearCache() {
        cache.clear();
    }

    @PostConstruct
    void start() {
        // listen to flow updates from the flow queue
        this.cancellation = Disposable.of(this.flowQueue.receive(either -> {
            if (either.isRight()) {
                log.error("Unable to deserialize a flow event: {}", either.getRight().getMessage());
            } else {
                FlowInterface flow = either.getLeft();
                // we only keep the last version of a flow so we use uidWithoutRevision
                if (flow.isDeleted()) {
                    cache.remove(flow.uidWithoutRevision());
                } else {
                    try {
                        FlowWithSource flowWithSource = pluginDefaultService.injectVersionDefaults(flow, true);
                        cache.put(flow.uidWithoutRevision(), flowWithSource);
                    } catch (FlowProcessingException e) {
                        log.error("Unable to inject version defaults for flow {}", flow.getId(), e);
                    }
                }
            }
        }));
    }

    @PreDestroy
    void close() {
        this.cancellation.dispose();
    }

    @Override
    public Collection<FlowWithSource> allLastVersion() {
        return this.cache.values();
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Optional<FlowInterface> findById(String tenantId, String namespace, String id, Optional<Integer> revision) {
        FlowWithSource flow = cache.get(FlowId.uidWithoutRevision(tenantId, namespace, id));
        // as we only keep the last version of a flow, we need to check if the revision is the one we asked for
        if (flow != null && revision.isPresent() && !revision.get().equals(flow.getRevision())) {
            flow = null; // force a reload
        }
        return (Optional) Optional
            .ofNullable(flow)
            // this can happen if an execution is still running with an old revision or if the flow was deleted
            .or(() -> flowRepository.findByIdWithSource(tenantId, namespace, id, revision)); // TODO evaluate if a cache is needed here
    }

    @Override
    public Optional<FlowWithSource> findByExecutionThenInjectDefaults(Execution execution) {
        return findByExecution(execution).map(it -> pluginDefaultService.injectDefaults(it, execution));
    }
}
