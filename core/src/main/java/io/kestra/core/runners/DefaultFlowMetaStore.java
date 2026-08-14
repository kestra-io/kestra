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

import io.micronaut.core.annotation.Nullable;
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
    public Optional<FlowWithSource> findByExecutionForRuntime(Execution execution) {
        // probe the cache before resolving the flow: this runs on every executor message, and an execution
        // pinned to a revision the meta-store no longer holds resolves through the repository. The key is the
        // one injectDefaults would compute, as findByExecution asks for that exact revision.
        if (execution.getFlowRevision() != null) {
            var fromCache = withDefaultCache.getIfPresent(
                FlowId.uid(execution.getTenantId(), execution.getNamespace(), execution.getFlowId(), Optional.of(execution.getFlowRevision()))
            );
            if (fromCache.isPresent()) {
                return fromCache;
            }
        }

        return findByExecution(execution).map(it -> injectDefaults(it, execution));
    }

    @Override
    public Optional<FlowWithSource> findByIdForRuntime(String tenantId, String namespace, String id, Optional<Integer> revision) {
        return findById(tenantId, namespace, id, revision).map(it -> injectDefaults(it, null));
    }

    @Override
    public Optional<FlowWithSource> findByIdFromTaskForRuntime(String tenantId, String namespace, String id, Optional<Integer> revision, String fromTenant,
        String fromNamespace, String fromId) {
        return findByIdFromTask(tenantId, namespace, id, revision, fromTenant, fromNamespace, fromId).map(it -> injectDefaults(it, null));
    }

    /**
     * Parses a flow for runtime, memoized on the resolved flow UID. Keying on the resolved flow rather than on
     * the requested identifier is what makes the cache safe: the UID always carries a revision, and the entry
     * for a revision is expired whenever that flow — or a setting it resolves against — changes.
     */
    private FlowWithSource injectDefaults(FlowInterface flow, @Nullable Execution execution) {
        var fromCache = withDefaultCache.getIfPresent(flow.uid());
        if (fromCache.isPresent()) {
            return fromCache.get();
        }

        ParsedFlow parsed = parseForRuntimeSafely(flow, execution);
        if (parsed.cacheable()) {
            withDefaultCache.put(flow.uid(), parsed.flow());
        }
        return parsed.flow();
    }

    /**
     * Parses the flow resolved for an execution — running or about to be created — converting failures into
     * outcomes the executor handles: this method must never throw, a throw here would escape the executor's
     * queue consumer:
     * <ul>
     * <li>governance rejection ({@link FlowBlockedException}) is logged and surfaced as a
     * {@link FlowWithException}, which the executor fails fast on;</li>
     * <li>any other parse failure is logged and the flow is returned as stored, so the execution proceeds
     * with the un-processed flow.</li>
     * </ul>
     * Failures are logged against the execution when there is one, which is not the case on the creation path
     * where the execution does not exist yet.
     */
    private ParsedFlow parseForRuntimeSafely(FlowInterface flow, @Nullable Execution execution) {
        try {
            return new ParsedFlow(flowParsingService.parseForRuntime(flow), true);
        } catch (FlowBlockedException e) {
            logBlocked(flow, execution, e);
            // a governance rejection is deterministic, so it is memoized like a successful parse
            return new ParsedFlow(FlowWithException.from(flow, e), true);
        } catch (Exception e) {
            logParseFailure(flow, execution, e);
            // possibly transient, and the cache has no TTL: memoizing it would pin an un-governed flow for
            // every later execution of this revision until the flow or a setting it resolves against changes
            return new ParsedFlow(FlowParsingService.toFlowWithSource(flow), false);
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

    private void logBlocked(FlowInterface flow, @Nullable Execution execution, FlowBlockedException e) {
        if (execution == null) {
            log.warn("Flow {} is blocked by governance: {}", flow.uid(), e.getMessage());
            return;
        }

        logToExecution(execution, e);
    }

    private void logParseFailure(FlowInterface flow, @Nullable Execution execution, Exception e) {
        if (execution == null) {
            log.error("Unable to parse flow {} for runtime", flow.uid(), e);
            return;
        }

        logToExecution(execution, e);
    }

    private void logToExecution(Execution execution, Exception e) {
        var logger = runContextLoggerFactory.create(execution);
        logger.emitLogs(RunContextLogger.logEntries(Execution.loggingEventFromException(e), LogEntry.of(execution)));
    }

    /**
     * A flow parsed for runtime, and whether the outcome is stable enough to memoize.
     */
    private record ParsedFlow(FlowWithSource flow, boolean cacheable) {
    }

}
