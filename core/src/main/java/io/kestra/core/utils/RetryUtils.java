package io.kestra.core.utils;

import java.io.Serial;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;

import org.slf4j.Logger;

import io.kestra.core.models.tasks.retrys.AbstractRetry;
import io.kestra.core.models.tasks.retrys.Exponential;

import dev.failsafe.Failsafe;
import dev.failsafe.FailsafeException;
import dev.failsafe.FailsafeExecutor;
import dev.failsafe.Fallback;
import dev.failsafe.FallbackBuilder;
import dev.failsafe.RetryPolicyBuilder;
import dev.failsafe.event.ExecutionAttemptedEvent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

public final class RetryUtils {
    private RetryUtils() {
        // utility class pattern
    }

    public static <T, E extends Throwable> Instance<T, E> of() {
        return Instance.<T, E> builder()
            .build();
    }

    public static <T, E extends Throwable> Instance<T, E> of(AbstractRetry policy) {
        return Instance.<T, E> builder()
            .policy(policy)
            .build();
    }

    public static <T, E extends Throwable> Instance<T, E> of(AbstractRetry policy, Function<RetryFailed, E> failureFunction) {
        return Instance.<T, E> builder()
            .policy(policy)
            .failureFunction(failureFunction)
            .build();
    }

    public static <T, E extends Throwable> Instance<T, E> of(AbstractRetry policy, Logger logger) {
        return Instance.<T, E> builder()
            .policy(policy)
            .logger(logger)
            .build();
    }

    @SuppressWarnings("unchecked")
    private static <T, E extends Throwable> T wrap(FailsafeExecutor<T> failsafeExecutor, CheckedSupplier<T> run) throws E {
        try {
            return failsafeExecutor.get(run::get);
        } catch (FailsafeException e) {
            throw (E) e.getCause();
        }
    }

    @Slf4j
    @Builder
    @AllArgsConstructor
    public static class Instance<T, E extends Throwable> {
        @Builder.Default
        private final AbstractRetry policy = Exponential.builder()
            .delayFactor(2.0)
            .interval(Duration.ofSeconds(1))
            .maxInterval(Duration.ofSeconds(10))
            .maxAttempts(3)
            .build();

        @Builder.Default
        private final Logger logger = log;

        private final Function<RetryFailed, E> failureFunction;

        public T run(Class<E> exception, CheckedSupplier<T> run) throws E {
            return wrap(
                Failsafe
                    .with(
                        this.exceptionFallback(this.failureFunction)
                            .handle(exception)
                            .build(),
                        this.toPolicy(this.policy)
                            .handle(exception)
                            .build()
                    ),
                run
            );
        }

        public T run(List<Class<? extends Throwable>> list, CheckedSupplier<T> run) throws Throwable {
            return wrap(
                Failsafe
                    .with(
                        this.exceptionFallback(this.failureFunction)
                            .handleIf((t, throwable) -> list.stream().anyMatch(cls -> cls.isInstance(throwable)))
                            .build(),
                        this.toPolicy(this.policy)
                            .handleIf((t, throwable) -> list.stream().anyMatch(cls -> cls.isInstance(throwable)))
                            .build()
                    ),
                run
            );
        }

        public T runRetryIf(Predicate<Throwable> predicate, CheckedSupplier<T> run) {
            return this.retryerIf(predicate).run(run);
        }

        /**
         * Builds a reusable {@link Retryer} for the given predicate.
         * <p>
         * Prefer this over {@link #runRetryIf(Predicate, CheckedSupplier)} on hot paths: the latter
         * rebuilds the whole policy chain (retry policy, fallback and their listeners) on every
         * call, whereas the returned retryer builds it once. Failsafe policies and executors are
         * immutable and thread-safe, so the result is safe to cache in a static field and share
         * across threads.
         * <p>
         * The returned {@link Retryer} is not parameterized by {@code T}: the policy and fallback it
         * was built with only ever match on the thrown exception, never on the result value, so the
         * same underlying executor can safely run work of any result type. That lets callers reuse
         * one cached retryer across calls with different result types without needing to know
         * anything about Failsafe or perform a cast themselves — see {@link Retryer#run}.
         *
         * @param predicate decides whether a thrown exception should be retried
         * @return a retryer that can be reused for any number of calls, for any result type
         */
        @SuppressWarnings("unchecked")
        public Retryer retryerIf(Predicate<Throwable> predicate) {
            FailsafeExecutor<T> failsafeExecutor = Failsafe
                .with(
                    this.exceptionFallback(this.failureFunction)
                        .handleIf(predicate::test).build(),
                    this.toPolicy(this.policy)
                        .handleIf(predicate::test).build()
                );

            return new Retryer((FailsafeExecutor<Object>) failsafeExecutor);
        }

        public T run(BiPredicate<T, Throwable> predicate, CheckedSupplier<T> run) throws E {
            return wrap(
                Failsafe
                    .with(
                        this.exceptionFallback(this.failureFunction)
                            .handleIf(predicate::test).build(),
                        this.toPolicy(this.policy)
                            .handleIf(predicate::test).build()
                    ),
                run
            );
        }

        public T run(Predicate<T> predicate, CheckedSupplier<T> run) throws E {
            return wrap(
                Failsafe
                    .with(
                        this.exceptionFallback(this.failureFunction)
                            .handleResultIf(predicate::test).build(),
                        this.toPolicy(this.policy)
                            .handleResultIf(predicate::test).build()
                    ),
                run
            );
        }

        private FallbackBuilder<T> exceptionFallback(Function<RetryFailed, E> failureFunction) {
            return Fallback.builder(
                (ExecutionAttemptedEvent<? extends T> executionAttemptedEvent) ->
                {
                    RetryFailed retryFailed = new RetryFailed(executionAttemptedEvent);
                    throw failureFunction != null ? failureFunction.apply(retryFailed) : retryFailed;
                }
            );
        }

        private RetryPolicyBuilder<T> toPolicy(AbstractRetry abstractRetry) {
            RetryPolicyBuilder<T> retryPolicy = abstractRetry.toPolicy();
            Logger currentLogger = this.logger != null ? this.logger : log;

            retryPolicy
                .onFailure(
                    event -> currentLogger.warn(
                        "Stop retry{}, elapsed {} and {} attempts",
                        finalMethod(),
                        event.getElapsedTime().truncatedTo(ChronoUnit.SECONDS),
                        event.getAttemptCount(),
                        event.getException()
                    )
                )
                .onRetry(
                    event -> currentLogger.info(
                        "Retrying{}, elapsed {} and {} attempts",
                        finalMethod(),
                        event.getElapsedTime().truncatedTo(ChronoUnit.SECONDS),
                        event.getAttemptCount()
                    )
                );
            return retryPolicy;
        }

        private String finalMethod() {
            var stackTraces = Thread.currentThread().getStackTrace();
            if (stackTraces.length > 4) {
                return " [class '" + stackTraces[3].getClassName() + "'" +
                    ", method '" + stackTraces[3].getMethodName() + "'" +
                    " on line '" + stackTraces[3].getLineNumber() + "']";
            }
            return "";
        }
    }

    @FunctionalInterface
    public interface CheckedSupplier<T> {
        T get() throws Throwable;
    }

    /**
     * A pre-built, reusable retry executor, obtained from {@link Instance#retryerIf(Predicate)}.
     * <p>
     * Immutable and thread-safe. Not parameterized by a result type: {@link #run} is a generic
     * method instead, so a single cached instance can run work of any result type.
     */
    public static final class Retryer {
        private final FailsafeExecutor<Object> failsafeExecutor;

        private Retryer(FailsafeExecutor<Object> failsafeExecutor) {
            this.failsafeExecutor = failsafeExecutor;
        }

        /**
         * Runs the given work, retrying it according to the policy this retryer was built with.
         * <p>
         * The cast is safe: this retryer's policy and fallback only ever match on the thrown
         * exception, never on the result value, so the same executor works for any {@code T}.
         *
         * @param run the work to run
         * @return the value returned by {@code run}
         */
        @SuppressWarnings("unchecked")
        public <T> T run(CheckedSupplier<T> run) {
            return RetryUtils.<T, RuntimeException> wrap((FailsafeExecutor<T>) (FailsafeExecutor<?>) failsafeExecutor, run);
        }
    }

    @Getter
    public static class RetryFailed extends Exception {
        @Serial
        private static final long serialVersionUID = 1L;

        private final int attemptCount;
        private final Duration elapsedTime;

        public <T> RetryFailed(ExecutionAttemptedEvent<? extends T> event) {
            super(
                "Stop retry, attempts " + event.getAttemptCount() + " elapsed after " +
                    event.getElapsedTime().getSeconds() + " seconds",
                event.getLastException()
            );

            this.attemptCount = event.getAttemptCount();
            this.elapsedTime = event.getElapsedTime();
        }
    }
}
