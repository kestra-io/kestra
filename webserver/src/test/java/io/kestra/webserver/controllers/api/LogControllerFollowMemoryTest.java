package io.kestra.webserver.controllers.api;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;
import org.slf4j.event.Level;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.BroadcastQueueInterface;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.repositories.LogDataStoreInterface;
import io.kestra.core.runners.FollowLogEvent;
import io.kestra.core.tenant.TenantService;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.TestsUtils;
import io.kestra.webserver.tenants.TenantValidationFilter;

import io.micronaut.http.sse.Event;
import io.micronaut.test.annotation.MockBean;
import jakarta.inject.Inject;
import reactor.core.publisher.Flux;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@KestraTest
class LogControllerFollowMemoryTest {
    private static final int LOGS = 1_000;
    private static final int MESSAGE_SIZE = 100_000;

    @Inject
    private ExecutionRepositoryInterface executionRepository;

    @Inject
    private LogDataStoreInterface logRepository;

    @Inject
    private TenantService tenantService;

    @Inject
    private LogController logController;

    @Inject
    private BroadcastQueueInterface<FollowLogEvent> logQueue;

    @MockBean(LogDataStoreInterface.class)
    public LogDataStoreInterface getLogRepository() {
        return mock(LogDataStoreInterface.class);
    }

    @MockBean(TenantService.class)
    public TenantService getTenantService() {
        return mock(TenantService.class);
    }

    @MockBean(TenantValidationFilter.class)
    public TenantValidationFilter getTenantValidationFilter() {
        return mock(TenantValidationFilter.class);
    }

    @Test
    void shouldNotHoldTheLogsInMemoryWhileReplayingHistory() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        when(tenantService.resolveTenant()).thenReturn(tenant);
        String executionId = IdUtils.create();
        executionRepository.save(
            Execution.builder()
                .id(executionId)
                .namespace("io.kestra.unittest")
                .tenantId(tenant)
                .flowId("full")
                .flowRevision(1)
                .state(new State().withState(State.Type.RUNNING))
                .build()
        );
        String filler = "x".repeat(MESSAGE_SIZE);
        AtomicInteger generatedLogs = new AtomicInteger();
        AtomicLong heapBeforeTheFirstLog = new AtomicLong();
        AtomicLong heapAfterTakingLogs = new AtomicLong();
        Flux<LogEntry> logs = Flux.generate(
            () -> 0, (index, sink) ->
            {
                if (index == 0) {
                    heapBeforeTheFirstLog.set(retainedHeap());
                }
                if (index == LOGS) {
                    sink.complete();
                    return index;
                }
                generatedLogs.incrementAndGet();
                sink.next(
                    LogEntry.builder()
                        .tenantId(tenant)
                        .namespace("io.kestra.unittest")
                        .flowId("full")
                        .executionId(executionId)
                        .timestamp(Instant.now())
                        .level(Level.INFO)
                        .message("line " + index + " " + filler)
                        .build()
                );
                return index + 1;
            }
        );
        when(logRepository.findAsync(eq(tenant), anyList())).thenReturn(logs);

        Flux<Event<FollowLogEvent>> flux = logController.followLogsFromExecution(executionId, null);
        Event<FollowLogEvent> firstLog = flux.take(2).blockLast();
        heapAfterTakingLogs.set(retainedHeap());

        assertThat(firstLog).isNotNull();
        assertThat(generatedLogs.get()).isLessThan(5);
        long heapHeldByTheFollow = heapAfterTakingLogs.get() - heapBeforeTheFirstLog.get();
        assertThat(heapHeldByTheFollow).isLessThan(LOGS * MESSAGE_SIZE / 4);
    }

    @Test
    void shouldStreamRealtimeEventsEmittedDuringHistoryReplay() {
        String tenant = TestsUtils.randomTenant(this.getClass().getSimpleName());
        when(tenantService.resolveTenant()).thenReturn(tenant);
        String executionId = IdUtils.create();
        executionRepository.save(
            Execution.builder()
                .id(executionId)
                .namespace("io.kestra.unittest")
                .tenantId(tenant)
                .flowId("full")
                .flowRevision(1)
                .state(new State().withState(State.Type.RUNNING))
                .build()
        );

        LogEntry historicalLog = LogEntry.builder()
            .tenantId(tenant)
            .namespace("io.kestra.unittest")
            .flowId("full")
            .executionId(executionId)
            .timestamp(Instant.now())
            .level(Level.INFO)
            .message("historical log")
            .build();

        FollowLogEvent realtimeLog = new FollowLogEvent(
            tenant, "io.kestra.unittest", "full", "t1", executionId,
            "tr1", 1, null, Instant.now(), Level.INFO, "thread-1", "realtime log", null, null
        );

        Flux<LogEntry> logs = Flux.defer(() -> {
            logQueue.emit(realtimeLog);
            return Flux.just(historicalLog);
        });
        when(logRepository.findAsync(eq(tenant), anyList())).thenReturn(logs);

        List<Event<FollowLogEvent>> events = logController.followLogsFromExecution(executionId, null)
            .take(3)
            .collectList()
            .block(Duration.ofSeconds(5));

        assertThat(events).isNotNull().hasSize(3);
        assertThat(events.get(0).getId()).isEqualTo("start");
        assertThat(events.get(1).getData().message()).isEqualTo("historical log");
        assertThat(events.get(2).getData().message()).isEqualTo("realtime log");
    }

    private static long retainedHeap() {
        System.gc();
        return ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
    }
}
