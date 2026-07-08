package io.kestra.executor.statemachine.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionMetadata;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.executions.TaskRunAttempt;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.tasks.retrys.Constant;
import io.kestra.core.models.tasks.retrys.Exponential;
import io.kestra.core.models.tasks.retrys.Random;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Layer-0 truth tables for the retry-date arithmetic behind every retry decision
 * ({@code AbstractRetry} backoffs and {@code TaskRun#nextRetryDate} limit handling).
 * Twins the date math otherwise only exercised through AbstractRunnerRetryTest
 * (retrySuccess/retryFailed/retryRandom/retryExpo × 5 backends). Pure JUnit, no harness.
 */
class RetryDateTest {

    private static final Instant LAST_ATTEMPT = Instant.parse("2026-07-06T10:00:00Z");

    // --- backoff arithmetic

    @Test
    void shouldAddConstantIntervalWhenConstantRetry() {
        Constant retry = Constant.builder().interval(Duration.ofSeconds(30)).build();

        assertThat(retry.nextRetryDate(1, LAST_ATTEMPT)).isEqualTo(LAST_ATTEMPT.plusSeconds(30));
        assertThat(retry.nextRetryDate(5, LAST_ATTEMPT)).isEqualTo(LAST_ATTEMPT.plusSeconds(30));
    }

    static Stream<Arguments> exponentialBackoff() {
        return Stream.of(
            // attemptCount, expected delay with interval=1s, factor=2 (default), maxInterval=1min
            Arguments.of(1, Duration.ofSeconds(1)),
            Arguments.of(2, Duration.ofSeconds(2)),
            Arguments.of(3, Duration.ofSeconds(4)),
            Arguments.of(4, Duration.ofSeconds(8)),
            Arguments.of(7, Duration.ofSeconds(60)), // 64s capped at maxInterval
            Arguments.of(100, Duration.ofSeconds(60)), // overflow-territory exponent stays capped
            Arguments.of(10_000, Duration.ofSeconds(60)) // Double.POSITIVE_INFINITY delay stays capped
        );
    }

    @ParameterizedTest
    @MethodSource("exponentialBackoff")
    void shouldGrowExponentiallyAndCapAtMaxIntervalWhenExponentialRetry(int attemptCount, Duration expectedDelay) {
        Exponential retry = Exponential.builder()
            .interval(Duration.ofSeconds(1))
            .maxInterval(Duration.ofMinutes(1))
            .build();

        assertThat(retry.nextRetryDate(attemptCount, LAST_ATTEMPT)).isEqualTo(LAST_ATTEMPT.plus(expectedDelay));
    }

    @Test
    void shouldApplyCustomDelayFactorWhenExponentialRetryDefinesOne() {
        Exponential retry = Exponential.builder()
            .interval(Duration.ofSeconds(10))
            .maxInterval(Duration.ofHours(1))
            .delayFactor(3.0)
            .build();

        // 10s * 3^(3-1) = 90s
        assertThat(retry.nextRetryDate(3, LAST_ATTEMPT)).isEqualTo(LAST_ATTEMPT.plusSeconds(90));
    }

    @Test
    void shouldStayWithinBoundsWhenRandomRetry() {
        Random retry = Random.builder()
            .minInterval(Duration.ofSeconds(10))
            .maxInterval(Duration.ofSeconds(20))
            .build();

        for (int i = 0; i < 100; i++) {
            Instant next = retry.nextRetryDate(1, LAST_ATTEMPT);
            assertThat(next).isAfterOrEqualTo(LAST_ATTEMPT.plusSeconds(10));
            assertThat(next).isBefore(LAST_ATTEMPT.plusSeconds(20));
        }
    }

    // --- TaskRun.nextRetryDate(retry): task/flow-level RETRY_FAILED_TASK limits

    @Test
    void shouldBaseNextDateOnLastAttemptEndWhenRetryFailedTask() {
        Constant retry = Constant.builder().interval(Duration.ofMinutes(1)).maxAttempts(5).build();
        TaskRun taskRun = taskRunWithFailedAttempts(2);

        assertThat(taskRun.nextRetryDate(retry)).isEqualTo(lastAttemptEnd(2).plus(Duration.ofMinutes(1)));
    }

    @Test
    void shouldReturnNullWhenMaxAttemptsReached() {
        Constant retry = Constant.builder().interval(Duration.ofMinutes(1)).maxAttempts(2).build();

        assertThat(taskRunWithFailedAttempts(2).nextRetryDate(retry)).isNull();
        assertThat(taskRunWithFailedAttempts(3).nextRetryDate(retry)).isNull();
    }

    @Test
    void shouldReturnNullWhenNoAttemptExists() {
        Constant retry = Constant.builder().interval(Duration.ofMinutes(1)).build();

        assertThat(TaskRun.builder().id("taskrun").taskId("task").build().nextRetryDate(retry)).isNull();
    }

    @Test
    void shouldReturnNullWhenNextDateExceedsMaxDuration() {
        // first attempt started at T0; next retry would land after T0 + maxDuration
        Constant retry = Constant.builder()
            .interval(Duration.ofMinutes(10))
            .maxDuration(Duration.ofMinutes(11))
            .build();

        // attempt 1 ends at T0+59s, attempt 2 at T0+119s => next = T0+119s+10min > T0+11min
        assertThat(taskRunWithFailedAttempts(2).nextRetryDate(retry)).isNull();
        // a single attempt ending at T0+59s => next = T0+59s+10min < T0+11min
        assertThat(taskRunWithFailedAttempts(1).nextRetryDate(retry)).isNotNull();
    }

    // --- TaskRun.nextRetryDate(retry, execution): CREATE_NEW_EXECUTION limits

    @Test
    void shouldUseExecutionAttemptNumberWhenCreateNewExecution() {
        Constant retry = Constant.builder().interval(Duration.ofMinutes(1)).maxAttempts(3).build();
        TaskRun taskRun = taskRunWithFailedAttempts(1);

        // execution attempt 2 of 3 => allowed, based on the taskrun's last attempt end date
        Instant next = taskRun.nextRetryDate(retry, executionWithAttemptNumber(2));
        assertThat(next).isEqualTo(lastAttemptEnd(1).plus(Duration.ofMinutes(1)));

        // execution attempt 3 of 3 => exhausted
        assertThat(taskRun.nextRetryDate(retry, executionWithAttemptNumber(3))).isNull();
    }

    @Test
    void shouldReturnNullWhenCreateNewExecutionExceedsMaxDurationFromOriginalCreation() {
        Constant retry = Constant.builder()
            .interval(Duration.ofMinutes(10))
            .maxDuration(Duration.ofMinutes(5))
            .build();

        // next date (last attempt + 10min) is after originalCreatedDate + 5min
        assertThat(taskRunWithFailedAttempts(1).nextRetryDate(retry, executionWithAttemptNumber(1))).isNull();
    }

    // --- fixtures: explicit attempt end dates make every assertion deterministic arithmetic

    private static final Instant FIRST_ATTEMPT_START = Instant.parse("2026-07-06T09:00:00Z");

    private static Instant lastAttemptEnd(int attemptCount) {
        return FIRST_ATTEMPT_START.plusSeconds(60L * attemptCount - 1);
    }

    private static TaskRun taskRunWithFailedAttempts(int attemptCount) {
        List<TaskRunAttempt> attempts = new java.util.ArrayList<>(attemptCount);
        for (int i = 1; i <= attemptCount; i++) {
            Instant start = FIRST_ATTEMPT_START.plusSeconds(60L * (i - 1));
            attempts.add(
                TaskRunAttempt.builder()
                    .state(
                        new State(
                            State.Type.FAILED, List.of(
                                new State.History(State.Type.CREATED, start),
                                new State.History(State.Type.FAILED, start.plusSeconds(59))
                            )
                        )
                    )
                    .build()
            );
        }
        return TaskRun.builder().id("taskrun").taskId("task").attempts(attempts).build();
    }

    private static Execution executionWithAttemptNumber(int attemptNumber) {
        // metadata must be applied AFTER build(): ExecutionBuilder.prebuild() unconditionally
        // overwrites builder-supplied metadata with attemptNumber=1 / originalCreatedDate=now()
        return Execution.builder()
            .id("execution")
            .namespace("io.kestra.tests")
            .flowId("flow")
            .state(new State())
            .build()
            .withMetadata(
                ExecutionMetadata.builder()
                    .attemptNumber(attemptNumber)
                    .originalCreatedDate(FIRST_ATTEMPT_START)
                    .build()
            );
    }
}
