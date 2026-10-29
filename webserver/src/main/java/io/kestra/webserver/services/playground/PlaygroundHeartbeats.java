package io.kestra.webserver.services.playground;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionKind;
import io.kestra.core.models.executions.PlaygroundClientEvent;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.BroadcastQueueInterface;
import io.kestra.core.queues.QueueException;
import io.kestra.core.queues.QueueSubscriber;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.security.InternalCallContext;
import io.kestra.core.tenant.TenantService;
import io.kestra.core.utils.IdUtils;

import io.micronaut.context.annotation.Value;
import io.micronaut.data.model.Pageable;
import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Keeps a Playground run alive while a panel shows it, and kills it once no panel has reported for the timeout or the last one was closed.
 * Reports are broadcast, so every webserver node holds the same view and any of them can end a run.
 */
@Slf4j
@Singleton
public class PlaygroundHeartbeats {
    /** Stands for a run found by the periodic scan, so a run nobody reports about still gets one timeout of grace. */
    private static final String ADOPTED = "";

    private final BroadcastQueueInterface<PlaygroundClientEvent> clientQueue;
    private final ExecutionRepositoryInterface executionRepository;
    private final TenantService tenantService;
    private final PlaygroundService playgroundService;
    private final Duration timeout;
    private final Map<String, Watched> watched = new ConcurrentHashMap<>();
    private QueueSubscriber<PlaygroundClientEvent> subscriber;

    @Inject
    public PlaygroundHeartbeats(
        BroadcastQueueInterface<PlaygroundClientEvent> clientQueue,
        ExecutionRepositoryInterface executionRepository,
        TenantService tenantService,
        PlaygroundService playgroundService,
        @Value("${kestra.playground.heartbeat.timeout:PT2M}") Duration timeout) {
        this.clientQueue = Objects.requireNonNull(clientQueue);
        this.executionRepository = Objects.requireNonNull(executionRepository);
        this.tenantService = Objects.requireNonNull(tenantService);
        this.playgroundService = Objects.requireNonNull(playgroundService);
        this.timeout = Objects.requireNonNull(timeout);
    }

    @PostConstruct
    public void start() {
        subscriber = clientQueue.subscriber().subscribe(either ->
        {
            if (either.isRight()) {
                log.warn("Failed to deserialize a Playground client event: {}", either.getRight().getMessage());
                return;
            }
            apply(either.getLeft(), Instant.now());
        });
    }

    @PreDestroy
    public void stop() {
        if (subscriber != null) {
            subscriber.close();
        }
    }

    /** How often a panel should report, a sixth of the timeout so a few lost reports never end a run. */
    public Duration reportEvery() {
        return timeout.dividedBy(6);
    }

    public void report(Execution run, String clientId) throws QueueException {
        PlaygroundClientEvent event = new PlaygroundClientEvent(run.getTenantId(), run.getId(), clientId, PlaygroundClientEvent.Type.REPORT);
        apply(event, Instant.now());
        clientQueue.emit(event);
    }

    /** The panel of {@code clientId} was closed; the run is killed at once when no other panel shows it. */
    public void release(Execution run, String clientId) throws QueueException {
        PlaygroundClientEvent event = new PlaygroundClientEvent(run.getTenantId(), run.getId(), clientId, PlaygroundClientEvent.Type.RELEASE);
        apply(event, Instant.now());
        clientQueue.emit(event);

        Watched current = watched.get(key(run.getTenantId(), run.getId()));
        boolean otherPanelOpen = current != null && current.clients().keySet().stream().anyMatch(id -> !ADOPTED.equals(id));
        if (!otherPanelOpen && PlaygroundService.isKillable(run)) {
            watched.remove(key(run.getTenantId(), run.getId()));
            playgroundService.kill(run);
        }
    }

    @Scheduled(fixedDelay = "${kestra.playground.heartbeat.check-interval:30s}", initialDelay = "${kestra.playground.heartbeat.check-interval:30s}")
    public void check() {
        Instant now = Instant.now();
        // a scheduler thread has no user, so the scan runs as an internal call that no ACL applies to
        InternalCallContext.runAsInternalCall(() ->
        {
            adoptUnwatchedRuns(now);
            killUnreportedRuns(now);
        });
    }

    private void apply(PlaygroundClientEvent event, Instant at) {
        String key = key(event.tenantId(), event.executionId());
        if (PlaygroundClientEvent.Type.REPORT == event.type()) {
            watched.computeIfAbsent(key, _ -> new Watched(event.tenantId(), event.executionId(), new ConcurrentHashMap<>()))
                .clients().put(event.clientId(), at);
        } else {
            Watched current = watched.get(key);
            if (current != null) {
                current.clients().remove(event.clientId());
            }
        }
    }

    private void adoptUnwatchedRuns(Instant now) {
        List<QueryFilter> liveRuns = List.of(
            QueryFilter.builder().field(QueryFilter.Field.KIND).operation(QueryFilter.Op.EQUALS).value(ExecutionKind.PLAYGROUND).build(),
            QueryFilter.builder().field(QueryFilter.Field.STATE).operation(QueryFilter.Op.IN).value(killableStates()).build()
        );
        for (String tenantId : tenantService.listTenants()) {
            try {
                executionRepository.find(Pageable.UNPAGED, tenantId, liveRuns)
                    .forEach(
                        run -> watched.computeIfAbsent(
                            key(tenantId, run.getId()),
                            _ -> new Watched(tenantId, run.getId(), new ConcurrentHashMap<>(Map.of(ADOPTED, now)))
                        )
                    );
            } catch (RuntimeException e) {
                log.warn("Unable to list the Playground runs of tenant '{}'.", tenantId, e);
            }
        }
    }

    private void killUnreportedRuns(Instant now) {
        Instant deadline = now.minus(timeout);
        watched.values().stream()
            .filter(run -> run.clients().values().stream().allMatch(seen -> seen.isBefore(deadline)))
            .toList()
            .forEach(run ->
            {
                watched.remove(key(run.tenantId(), run.executionId()));
                executionRepository.findById(run.tenantId(), run.executionId())
                    .filter(PlaygroundService::isKillable)
                    .ifPresent(execution ->
                    {
                        log.info("Killing Playground run '{}', no panel reported it for {}.", execution.getId(), timeout);
                        playgroundService.kill(execution);
                    });
            });
    }

    private static List<String> killableStates() {
        return Arrays.stream(State.Type.values())
            .filter(state -> !state.isTerminated() && State.Type.KILLING != state)
            .map(Enum::name)
            .toList();
    }

    private static String key(String tenantId, String executionId) {
        return IdUtils.fromParts(tenantId, executionId);
    }

    private record Watched(String tenantId, String executionId, Map<String, Instant> clients) {
    }
}
