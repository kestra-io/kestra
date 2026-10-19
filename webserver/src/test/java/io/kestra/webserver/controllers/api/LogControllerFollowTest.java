package io.kestra.webserver.controllers.api;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.event.Level;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.queues.BroadcastQueueInterface;
import io.kestra.core.queues.QueueException;
import io.kestra.core.repositories.LogDataStoreInterface;
import io.kestra.core.runners.FollowLogEvent;
import io.kestra.core.services.LogStreamingService;
import io.kestra.core.tenant.TenantService;
import io.kestra.core.utils.IdUtils;
import io.kestra.webserver.tenants.TenantValidationFilter;

import io.micronaut.http.sse.Event;
import io.micronaut.test.annotation.MockBean;
import jakarta.inject.Inject;
import reactor.core.publisher.Flux;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@KestraTest
class LogControllerFollowTest {
    private static final String SYNC = "sync";
    private static final String END = "end";

    @Inject
    private LogController logController;

    @Inject
    private BroadcastQueueInterface<FollowLogEvent> logQueue;

    @Inject
    private LogStreamingService logStreamingService;

    @Inject
    private TenantService tenantService;

    @Inject
    private LogDataStoreInterface logRepository;

    private String executionId;
    private String probeId;
    private CountDownLatch dispatched;

    @MockBean(TenantService.class)
    TenantService tenantService() {
        return mock(TenantService.class);
    }

    @MockBean(TenantValidationFilter.class)
    TenantValidationFilter tenantValidationFilter() {
        return mock(TenantValidationFilter.class);
    }

    @MockBean(LogDataStoreInterface.class)
    LogDataStoreInterface logRepository() {
        return mock(LogDataStoreInterface.class);
    }

    @BeforeEach
    void setUp() {
        when(tenantService.resolveTenant()).thenReturn("main");
        executionId = IdUtils.create();
        probeId = IdUtils.create();
        dispatched = new CountDownLatch(1);

        // Another follower keeps the queue consumer polling, which is when the gap opens, and reports when the marker is dispatched.
        Flux.<Event<FollowLogEvent>> create(sink -> logStreamingService.registerSubscriber(executionId, probeId, sink, List.of()))
            .filter(event -> SYNC.equals(event.getData().message()))
            .subscribe(event -> dispatched.countDown());
    }

    @AfterEach
    void tearDown() {
        logStreamingService.unregisterSubscriber(executionId, probeId);
    }

    @Test
    void shouldSendALogWhenItIsPublishedDuringTheHistoryRead() {
        LogEntry published = logEntry("published during the read");
        when(logRepository.findAsync(any(), any())).thenAnswer(
            invocation -> Flux.just(logEntry("stored"))
                .doOnNext(stored -> publishAndAwaitDispatch(published))
                .doOnComplete(() -> publish(logEntry(END)))
        );

        assertThat(follow()).containsExactly("stored", "published during the read", END);
    }

    @Test
    void shouldSendALogOnceWhenTheHistoryReadAlsoReturnsIt() {
        LogEntry storedAndPublished = logEntry("stored and published");
        when(logRepository.findAsync(any(), any())).thenAnswer(
            invocation -> Flux.just(logEntry("stored"), storedAndPublished)
                .doOnNext(entry ->
                {
                    if (entry != storedAndPublished) {
                        publishAndAwaitDispatch(storedAndPublished);
                    }
                })
                .doOnComplete(() -> publish(logEntry(END)))
        );

        assertThat(follow()).containsExactly("stored", "stored and published", END);
    }

    private List<String> follow() {
        return logController.followLogsFromExecution(executionId, List.of())
            .filter(event -> "progress".equals(event.getId()))
            .map(event -> event.getData().message())
            .filter(message -> !SYNC.equals(message))
            .takeUntil(END::equals)
            .take(Duration.ofSeconds(10))
            .collectList()
            .block(Duration.ofSeconds(15));
    }

    // The queue dispatches one log at a time to every follower, so once the marker reaches the probe, the log has reached them all.
    private void publishAndAwaitDispatch(LogEntry entry) {
        publish(entry);
        publish(logEntry(SYNC));
        try {
            assertThat(dispatched.await(30, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private void publish(LogEntry entry) {
        try {
            logQueue.emit(FollowLogEvent.from(entry));
        } catch (QueueException e) {
            throw new IllegalStateException(e);
        }
    }

    private LogEntry logEntry(String message) {
        return LogEntry.builder()
            .tenantId("main")
            .namespace("io.kestra.unittest")
            .flowId("follow")
            .executionId(executionId)
            .taskId("task")
            .taskRunId("task-run")
            .attemptNumber(0)
            // Truncated like every timestamp on the queue and in the store, so the stored copy here equals the one the queue dispatches.
            .timestamp(Instant.now().truncatedTo(ChronoUnit.MICROS))
            .level(Level.INFO)
            .thread("main")
            .message(message)
            .build();
    }
}
