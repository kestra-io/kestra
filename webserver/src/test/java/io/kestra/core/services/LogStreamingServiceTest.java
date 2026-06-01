package io.kestra.core.services;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import org.awaitility.Awaitility;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.FieldSource;
import org.slf4j.event.Level;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.QueryFilter;
import io.kestra.core.queues.BroadcastQueueInterface;
import io.kestra.core.runners.FollowLogEvent;
import io.kestra.core.utils.IdUtils;

import io.micronaut.http.sse.Event;
import jakarta.inject.Inject;
import lombok.Builder;
import reactor.core.publisher.Flux;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
class LogStreamingServiceTest {

    private static final String EXECUTION_ID = "exec-streaming-test";

    @Inject
    BroadcastQueueInterface<FollowLogEvent> queue;

    @Inject
    LogStreamingService service;

    @ParameterizedTest
    @FieldSource("filtersTestCases")
    void shouldDispatchOnlyMatchingEvents(FiltersTestCase testCase) {
        // Given
        String subscriberId = IdUtils.create();
        List<FollowLogEvent> received = new CopyOnWriteArrayList<>();

        Flux.<Event<FollowLogEvent>>create(sink ->
                service.registerSubscriber(EXECUTION_ID, subscriberId, sink, testCase.filters())
            )
            .doFinally(sig -> service.unregisterSubscriber(EXECUTION_ID, subscriberId))
            .subscribe(event -> received.add(event.getData()));

        // When
        testCase.events().forEach(event -> {
            try {
                queue.emit(event);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        // Then
        try {
            Awaitility.await()
                .atMost(Duration.ofSeconds(5))
                .pollInterval(50, TimeUnit.MILLISECONDS)
                .until(() -> received.size() >= testCase.expectedEvents().size());
        } finally {
            service.unregisterSubscriber(EXECUTION_ID, subscriberId);
        }

        assertThat(received)
            .usingRecursiveFieldByFieldElementComparatorOnFields("executionId", "level", "taskId", "taskRunId", "attemptNumber", "message")
            .containsExactlyInAnyOrderElementsOf(testCase.expectedEvents());
    }

    private static final FollowLogEvent traceEvent = event(Level.TRACE, "load-data", "task-run-1", 0, "trace line");
    private static final FollowLogEvent debugEvent = event(Level.DEBUG, "load-data", "task-run-1", 0, "debug line");
    private static final FollowLogEvent infoEvent = event(Level.INFO, "load-data", "task-run-1", 0, "info line");
    private static final FollowLogEvent warnEvent = event(Level.WARN, "transform", "task-run-2", 0, "warn line");
    private static final FollowLogEvent errorEvent = event(Level.ERROR, "transform", "task-run-2", 1, "error line");
    private static final List<FollowLogEvent> allEvents = List.of(traceEvent, debugEvent, infoEvent, warnEvent, errorEvent);

    private static final List<FiltersTestCase> filtersTestCases = List.of(
        FiltersTestCase.builder()
            .events(allEvents)
            .expectedEvents(allEvents)
            .filters(List.of())
            .build(),

        FiltersTestCase.builder()
            .events(allEvents)
            .expectedEvents(List.of(infoEvent, warnEvent, errorEvent))
            .filters(List.of(
                QueryFilter.builder()
                    .field(QueryFilter.Field.LEVEL)
                    .operation(QueryFilter.Op.GREATER_THAN_OR_EQUAL_TO)
                    .value(Level.INFO)
                    .build()
            ))
            .build(),

        FiltersTestCase.builder()
            .events(allEvents)
            .expectedEvents(List.of(traceEvent, debugEvent, infoEvent))
            .filters(List.of(
                QueryFilter.builder()
                    .field(QueryFilter.Field.LEVEL)
                    .operation(QueryFilter.Op.LESS_THAN_OR_EQUAL_TO)
                    .value(Level.INFO)
                    .build()
            ))
            .build(),

        FiltersTestCase.builder()
            .events(allEvents)
            .expectedEvents(List.of(warnEvent, errorEvent))
            .filters(List.of(
                QueryFilter.builder()
                    .field(QueryFilter.Field.TASK_ID)
                    .operation(QueryFilter.Op.EQUALS)
                    .value("transform")
                    .build()
            ))
            .build(),

        FiltersTestCase.builder()
            .events(allEvents)
            .expectedEvents(List.of(traceEvent, debugEvent, infoEvent))
            .filters(List.of(
                QueryFilter.builder()
                    .field(QueryFilter.Field.TASK_RUN_ID)
                    .operation(QueryFilter.Op.EQUALS)
                    .value("task-run-1")
                    .build()
            ))
            .build(),

        FiltersTestCase.builder()
            .events(allEvents)
            .expectedEvents(List.of(errorEvent))
            .filters(List.of(
                QueryFilter.builder()
                    .field(QueryFilter.Field.ATTEMPT_NUMBER)
                    .operation(QueryFilter.Op.EQUALS)
                    .value(1)
                    .build()
            ))
            .build(),

        FiltersTestCase.builder()
            .events(allEvents)
            .expectedEvents(List.of(infoEvent))
            .filters(List.of(
                QueryFilter.builder()
                    .field(QueryFilter.Field.LEVEL)
                    .operation(QueryFilter.Op.GREATER_THAN_OR_EQUAL_TO)
                    .value(Level.INFO)
                    .build(),
                QueryFilter.builder()
                    .field(QueryFilter.Field.TASK_ID)
                    .operation(QueryFilter.Op.EQUALS)
                    .value("load-data")
                    .build()
            ))
            .build(),

        FiltersTestCase.builder()
            .events(allEvents)
            .expectedEvents(List.of())
            .filters(List.of(
                QueryFilter.builder()
                    .field(QueryFilter.Field.EXECUTION_ID)
                    .operation(QueryFilter.Op.EQUALS)
                    .value("some-other-execution")
                    .build()
            ))
            .build()
    );

    private static FollowLogEvent event(Level level, String taskId, String taskRunId, Integer attempt, String message) {
        return new FollowLogEvent(
            null,
            "io.kestra.demo",
            "demo-flow",
            taskId,
            EXECUTION_ID,
            taskRunId,
            attempt,
            null,
            Instant.now(),
            level,
            "main",
            message,
            null
        );
    }

    @Builder
    private record FiltersTestCase(
        List<FollowLogEvent> events,
        List<FollowLogEvent> expectedEvents,
        List<QueryFilter> filters
    ) {
    }
}
