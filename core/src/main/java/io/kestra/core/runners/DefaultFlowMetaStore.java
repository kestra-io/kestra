package io.kestra.core.runners;

import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.google.common.annotations.VisibleForTesting;

import io.kestra.core.exceptions.FlowBlockedException;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.flows.FlowId;
import io.kestra.core.models.flows.FlowInterface;
import io.kestra.core.models.flows.FlowWithException;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.queues.BroadcastQueueInterface;
import io.kestra.core.queues.QueueSubscriber;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.services.FlowParsingService;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class DefaultFlowMetaStore implements FlowMetaStoreInterface {
    private final FlowRepositoryInterface flowRepository;
    private final FlowParsingService flowParsingService;
    private final RunContextLoggerFactory runContextLoggerFactory;
    private final ConcurrentHashMap<String, FlowWithSource> cache = new ConcurrentHashMap<>();
    private final BroadcastQueueInterface<FlowInterface> flowQueue;
    private final FlowWithDefaultCache withDefaultCache;

    private QueueSubscriber<FlowInterface> subscriber;

    public DefaultFlowMetaStore(FlowRepositoryInterface flowRepository, FlowParsingService flowParsingService,
        RunContextLoggerFactory runContextLoggerFactory, BroadcastQueueInterface<FlowInterface> flowQueue,
        FlowWithDefaultCache withDefaultCache) {
        this.flowRepository = flowRepository;
        this.flowParsingService = flowParsingService;
        this.runContextLoggerFactory = runContextLoggerFactory;
        this.flowQueue = flowQueue;
        this.withDefaultCache = withDefaultCache;

        flowRepository.findAllWithSourceForAllTenants().forEach(it -> cache.put(it.uidWithoutRevision(), it));
    }

    @VisibleForTesting
    void clearCache() {
        cache.clear();
    }

    @PostConstruct
    void start() {
        // listen to flow updates from the flow queue
        this.subscriber = this.flowQueue.subscriber().subscribe(either ->
        {
            if (either.isRight()) {
                log.error("Unable to deserialize a flow event: {}", either.getRight().getMessage());
            } else {
                FlowInterface flow = either.getLeft();
                // we only keep the last version of a flow so we use uidWithoutRevision
                if (flow.isDeleted()) {
                    cache.remove(flow.uidWithoutRevision());
                } else {
                    cache.put(flow.uidWithoutRevision(), parseOrKeepAsException(flow));
                }

                // always clear the withDefault cache so it's recomputed
                withDefaultCache.invalidate(flow.uid());
            }
        });
    }

    @PreDestroy
    void close() {
        this.subscriber.close();
    }

    @Override
    public boolean isNamespaceExists(String tenant, String namespace) {
        return flowRepository.isNamespaceExists(tenant, namespace);
    }

    @Override
    public Collection<FlowWithSource> allLastVersion() {
        return this.cache.values();
    }

    @Override
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public Optional<FlowInterface> findById(String tenantId, String namespace, String id, Optional<Integer> revision) {
        FlowWithSource flow = cache.get(FlowId.uidWithoutRevision(tenantId, namespace, id));
        // as we only keep the last version of a flow, we need to check if the revision is the one we asked for
        if (flow != null && revision.isPresent() && !revision.get().equals(flow.getRevision())) {
            flow = null; // force a reload
        }
        // No explicit revision requested: this is an execution-time lookup (subflow, etc.) and
        // must resolve to the latest non-draft revision. If the cached head is a draft we drop
        // the cache hit and let the repository find the most recent non-draft revision.
        if (revision.isEmpty() && flow != null && flow.isDraft()) {
            flow = null;
        }
        if (flow != null) {
            return (Optional) Optional.of(flow);
        }
        // this can happen if an execution is still running with an old revision or if the flow was deleted
        if (revision.isEmpty()) {
            return (Optional) flowRepository.findByIdWithSourceForExecution(tenantId, namespace, id);
        }
        return (Optional) flowRepository.findByIdWithSource(tenantId, namespace, id, revision);
    }

    @Override
    public Optional<FlowWithSource> findByExecutionThenInjectDefaults(Execution execution) {
        var flowId = FlowId.uid(execution.getTenantId(), execution.getNamespace(), execution.getFlowId(), Optional.ofNullable(execution.getFlowRevision()));
        var fromCache = withDefaultCache.getIfPresent(flowId);
        if (fromCache.isPresent()) {
            return fromCache;
        }

        var flowWithDefault = findByExecution(execution).map(it -> parseForRuntime(it, execution));
        flowWithDefault.ifPresent(it -> withDefaultCache.put(flowId, it));
        return flowWithDefault;
    }

    /**
     * Parses the flow resolved for a running execution, converting failures into outcomes the executor
     * handles — this method must never throw, a throw here would escape the executor's queue consumer:
     * <ul>
     * <li>governance rejection ({@link FlowBlockedException}) is logged against the execution and surfaced as a
     * {@link FlowWithException}, which the executor fails fast on;</li>
     * <li>any other parse failure is logged against the execution and the flow is returned as stored, so the
     * execution proceeds with the un-processed flow.</li>
     * </ul>
     */
    private FlowWithSource parseForRuntime(FlowInterface flow, Execution execution) {
        try {
            return flowParsingService.parseForRuntime(flow);
        } catch (FlowBlockedException e) {
            logToExecution(execution, e);
            return FlowWithException.from(flow, e);
        } catch (Exception e) {
            logToExecution(execution, e);
            return FlowParsingService.toFlowWithSource(flow);
        }
    }

    /**
     * Parses a flow for the meta-store cache, keeping unparsable flows as {@link FlowWithException} entries so
     * lookups can still resolve them.
     */
    private FlowWithSource parseOrKeepAsException(FlowInterface flow) {
        if (flow instanceof FlowWithSource flowWithSource) {
            return flowWithSource;
        }
        try {
            return flowParsingService.parse(flow, false);
        } catch (Exception e) {
            log.error("Unable to parse flow {}", flow.getId(), e);
            return FlowWithException.from(flow, e).toBuilder().deleted(flow.isDeleted()).build();
        }
    }

    private void logToExecution(Execution execution, Exception e) {
        var logger = runContextLoggerFactory.create(execution);
        logger.emitLogs(RunContextLogger.logEntries(Execution.loggingEventFromException(e), LogEntry.of(execution)));
    }

}
