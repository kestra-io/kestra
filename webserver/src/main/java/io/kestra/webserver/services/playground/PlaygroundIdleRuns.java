package io.kestra.webserver.services.playground;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.executions.ExecutionKind;
import io.kestra.core.models.flows.State;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.security.InternalCallContext;
import io.kestra.core.tenant.TenantService;
import io.kestra.webserver.annotation.WebServerEnabled;

import io.micronaut.context.annotation.Value;
import io.micronaut.data.model.Pageable;
import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * Kills a Playground run left at its breakpoint for longer than the timeout, so a panel closed without ending its run
 * does not hold the flow's concurrency slot forever.
 */
@Slf4j
@Singleton
@WebServerEnabled
public class PlaygroundIdleRuns {
    private static final List<QueryFilter> RUNS_AT_A_BREAKPOINT = List.of(
        QueryFilter.builder().field(QueryFilter.Field.KIND).operation(QueryFilter.Op.EQUALS).value(ExecutionKind.PLAYGROUND).build(),
        QueryFilter.builder().field(QueryFilter.Field.STATE).operation(QueryFilter.Op.IN).value(List.of(State.Type.BREAKPOINT.name())).build()
    );

    private final ExecutionRepositoryInterface executionRepository;
    private final TenantService tenantService;
    private final PlaygroundService playgroundService;
    private final Duration timeout;

    @Inject
    public PlaygroundIdleRuns(
        ExecutionRepositoryInterface executionRepository,
        TenantService tenantService,
        PlaygroundService playgroundService,
        @Value("${kestra.playground.breakpoint-timeout:PT30M}") Duration timeout) {
        this.executionRepository = Objects.requireNonNull(executionRepository);
        this.tenantService = Objects.requireNonNull(tenantService);
        this.playgroundService = Objects.requireNonNull(playgroundService);
        this.timeout = Objects.requireNonNull(timeout);
    }

    @Scheduled(
        fixedDelay = "${kestra.playground.breakpoint-check-interval:PT5M}",
        initialDelay = "${kestra.playground.breakpoint-check-interval:PT5M}"
    )
    public void killIdleRuns() {
        Instant deadline = Instant.now().minus(timeout);
        // a scheduler thread has no user, so the scan runs as an internal call that no ACL applies to
        InternalCallContext.runAsInternalCall(() -> tenantService.listTenants().forEach(tenantId -> killIdleRuns(tenantId, deadline)));
    }

    private void killIdleRuns(String tenantId, Instant deadline) {
        try {
            executionRepository.find(Pageable.UNPAGED, tenantId, RUNS_AT_A_BREAKPOINT)
                .stream()
                .filter(run -> run.getState().maxDate().isBefore(deadline))
                .forEach(run ->
                {
                    log.info("Killing Playground run '{}', left at its breakpoint for more than {}.", run.getId(), timeout);
                    playgroundService.kill(run);
                });
        } catch (RuntimeException e) {
            log.warn("Unable to list the Playground runs of tenant '{}'.", tenantId, e);
        }
    }
}
