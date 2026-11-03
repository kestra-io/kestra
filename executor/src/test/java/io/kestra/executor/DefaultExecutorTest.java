package io.kestra.executor;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import io.kestra.core.exceptions.DeserializationException;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionTrigger;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.triggers.PollingTriggerInterface;
import io.kestra.core.models.triggers.RealtimeTriggerInterface;
import io.kestra.core.utils.Either;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultExecutorTest {

    @Test
    void shouldProcessDifferentExecutionsConcurrently() {
        // Given: the message of execution A only ends once the message of execution B started
        CountDownLatch bStarted = new CountDownLatch(1);
        AtomicBoolean sawB = new AtomicBoolean();
        List<Either<Message, DeserializationException>> messages = List.of(
            Either.left(new Message("A", 1)),
            Either.left(new Message("B", 1))
        );
        ExecutorService executorService = Executors.newFixedThreadPool(2);

        try {
            // When
            DefaultExecutor.processGroupedByExecution(messages, Message::executionId, either -> {
                if (either.getLeft().executionId().equals("B")) {
                    bStarted.countDown();
                    return;
                }
                try {
                    sawB.set(bStarted.await(5, TimeUnit.SECONDS));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, executorService);

            // Then
            assertThat(sawB).as("B started while A was being processed").isTrue();
        } finally {
            executorService.shutdownNow();
        }
    }

    @Test
    void shouldProcessMessagesOfOneExecutionInOrderAndOneAtATime() {
        // Given
        List<Either<Message, DeserializationException>> messages = List.of(
            Either.left(new Message("A", 1)),
            Either.left(new Message("B", 1)),
            Either.left(new Message("A", 2)),
            Either.left(new Message("A", 3)),
            Either.left(new Message("B", 2))
        );
        Map<String, List<Integer>> processed = new ConcurrentHashMap<>();
        Map<String, AtomicInteger> running = new ConcurrentHashMap<>();
        AtomicBoolean overlapped = new AtomicBoolean();
        ExecutorService executorService = Executors.newFixedThreadPool(4);

        try {
            // When
            DefaultExecutor.processGroupedByExecution(messages, Message::executionId, either -> {
                Message message = either.getLeft();
                if (running.computeIfAbsent(message.executionId(), id -> new AtomicInteger()).incrementAndGet() > 1) {
                    overlapped.set(true);
                }
                processed.computeIfAbsent(message.executionId(), id -> Collections.synchronizedList(new ArrayList<>())).add(message.sequence());
                running.get(message.executionId()).decrementAndGet();
            }, executorService);

            // Then
            assertThat(processed).containsOnlyKeys("A", "B");
            assertThat(processed.get("A")).containsExactly(1, 2, 3);
            assertThat(processed.get("B")).containsExactly(1, 2);
            assertThat(overlapped).isFalse();
        } finally {
            executorService.shutdownNow();
        }
    }

    @Test
    void shouldHandleDeserializationFailures() {
        // Given
        DeserializationException failure = new DeserializationException("boom");
        List<Either<Message, DeserializationException>> messages = List.of(
            Either.left(new Message("A", 1)),
            Either.right(failure)
        );
        List<Either<Message, DeserializationException>> handled = Collections.synchronizedList(new ArrayList<>());
        ExecutorService executorService = Executors.newFixedThreadPool(2);

        try {
            // When
            DefaultExecutor.processGroupedByExecution(messages, Message::executionId, handled::add, executorService);

            // Then
            assertThat(handled).hasSize(2);
            assertThat(handled).anyMatch(either -> either.isRight() && either.getRight() == failure);
        } finally {
            executorService.shutdownNow();
        }
    }

    private record Message(String executionId, int sequence) {
    }

    @Test
    void shouldDetectRealtimeTriggerExecutionWhenTriggerIsRealtime() {
        // Given
        FlowWithSource flow = flowWith(TestRealtimeTrigger.builder().id("realtime").type(TestRealtimeTrigger.class.getName()).build());
        Execution execution = executionWithTrigger("realtime", TestRealtimeTrigger.class.getName());

        // When - Then
        assertThat(ExecutorCore.isRealtimeTriggerExecution(flow, execution)).isTrue();
    }

    @Test
    void shouldNotDetectRealtimeTriggerExecutionWhenTriggerIsPolling() {
        // Given
        FlowWithSource flow = flowWith(TestPollingTrigger.builder().id("polling").type(TestPollingTrigger.class.getName()).build());
        Execution execution = executionWithTrigger("polling", TestPollingTrigger.class.getName());

        // When - Then
        assertThat(ExecutorCore.isRealtimeTriggerExecution(flow, execution)).isFalse();
    }

    @Test
    void shouldNotDetectRealtimeTriggerExecutionWhenTriggerIsNotFound() {
        // Given
        FlowWithSource flow = flowWith(TestRealtimeTrigger.builder().id("realtime").type(TestRealtimeTrigger.class.getName()).build());
        Execution execution = executionWithTrigger("removed", TestRealtimeTrigger.class.getName());

        // When - Then
        assertThat(ExecutorCore.isRealtimeTriggerExecution(flow, execution)).isFalse();
    }

    @Test
    void shouldNotDetectRealtimeTriggerExecutionWhenFlowIsNull() {
        // Given
        Execution execution = executionWithTrigger("realtime", TestRealtimeTrigger.class.getName());

        // When - Then
        assertThat(ExecutorCore.isRealtimeTriggerExecution(null, execution)).isFalse();
    }

    private static FlowWithSource flowWith(AbstractTrigger trigger) {
        return FlowWithSource.builder()
            .id("flow")
            .namespace("io.kestra.tests")
            .revision(1)
            .triggers(List.of(trigger))
            .build();
    }

    private static Execution executionWithTrigger(String triggerId, String triggerType) {
        return Execution.builder()
            .id("execution")
            .trigger(ExecutionTrigger.builder().id(triggerId).type(triggerType).build())
            .build();
    }

    @Plugin(internal = true)
    @SuperBuilder
    @NoArgsConstructor
    public static class TestRealtimeTrigger extends AbstractTrigger implements RealtimeTriggerInterface {
    }

    @Plugin(internal = true)
    @SuperBuilder
    @NoArgsConstructor
    @Getter
    public static class TestPollingTrigger extends AbstractTrigger implements PollingTriggerInterface {
        private Duration interval;
    }
}
