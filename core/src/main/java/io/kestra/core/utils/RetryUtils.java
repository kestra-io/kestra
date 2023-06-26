package io.kestra.core.utils;

import io.kestra.core.models.tasks.retrys.AbstractRetry;
import io.kestra.core.models.tasks.retrys.Exponential;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.*;
import net.jodah.failsafe.event.ExecutionAttemptedEvent;
import org.slf4j.Logger;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;

import jakarta.inject.Singleton;

@Singleton
public class RetryUtils {
    public <T, E extends Throwable> Instance<T, E> of() {
        return Instance.<T, E>builder()
            .build();
    }

    public <T, E extends Throwable> Instance<T, E> of(AbstractRetry policy) {
        return Instance.<T, E>builder()
            .policy(policy)
            .build();
    }

    public <T, E extends Throwable> Instance<T, E> of(AbstractRetry policy, Function<RetryFailed, E> failureFunction) {
        return Instance.<T, E>builder()
            .policy(policy)
            .failureFunction(failureFunction)
            .build();
    }

    public <T, E extends Throwable> Instance<T, E> of(AbstractRetry policy, Logger logger) {
        return Instance.<T, E>builder()
            .policy(policy)
            .logger(logger)
            .build();
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
            .maxAttempt(3)
            .build();

        @Builder.Default
        private final Logger logger = log;

        private final Function<RetryFailed, E> failureFunction;

        public T run(Class<E> exception, CheckedSupplier<T> run) throws E {
            return wrap(
                Failsafe
                    .with(
                        this.exceptionFallback(this.failureFunction)
                            .handle(exception),
                        this.toPolicy(this.policy)
                            .handle(exception)
                    ),
                run
            );
        }

        public T run(List<Class<? extends Throwable>> list, CheckedSupplier<T> run) throws Throwable {
            return wrap(
                Failsafe
                    .with(
                        this.exceptionFallback(this.failureFunction)
                            .handleIf((t, throwable) -> list.stream().anyMatch(cls -> cls.isInstance(throwable))),
                        this.toPolicy(this.policy)
                            .handleIf((t, throwable) -> list.stream().anyMatch(cls -> cls.isInstance(throwable)))
                    ),
                run
            );
        }

        public T runRetryIf(Predicate<? extends E> predicate, CheckedSupplier<T> run) {
            return wrap(
                Failsafe
                    .with(
                        this.exceptionFallback(this.failureFunction)
                            .handleIf(predicate),
                        this.toPolicy(this.policy)
                            .handleIf(predicate)
                    ),
                run
            );
        }

        public T run(BiPredicate<T, Throwable> predicate, CheckedSupplier<T> run) throws E {
            return wrap(
                Failsafe
                    .with(
                        this.exceptionFallback(this.failureFunction)
                            .handleIf(predicate),
                        this.toPolicy(this.policy)
                            .handleIf(predicate)
                    ),
                run
            );
        }

        public T run(Predicate<T> predicate, CheckedSupplier<T> run) throws E {
            return wrap(
                Failsafe
                    .with(
                        this.exceptionFallback(this.failureFunction)
                            .handleResultIf(predicate),
                        this.toPolicy(this.policy)
                            .handleResultIf(predicate)
                    ),
                run
            );
        }

        @SuppressWarnings("unchecked")
        private static <T, E extends Throwable> T wrap(FailsafeExecutor<T> failsafeExecutor, CheckedSupplier<T> run) throws E {
            try {
                return failsafeExecutor.get(run::get);
            } catch (FailsafeException e) {
                throw (E) e.getCause();
            }
        }

        private Fallback<T> exceptionFallback(Function<RetryFailed, E> failureFunction) {
            return Fallback
                .ofException((ExecutionAttemptedEvent<? extends T> executionAttemptedEvent) -> {
                    RetryFailed retryFailed = new RetryFailed(executionAttemptedEvent);

                    throw failureFunction != null ? failureFunction.apply(retryFailed) : retryFailed;
                });
        }

        private RetryPolicy<T> toPolicy(AbstractRetry abstractRetry) {
            RetryPolicy<T> retryPolicy = abstractRetry.toPolicy();
            Logger currentLogger = this.logger != null ? this.logger : log;

            retryPolicy
                .onFailure(event -> currentLogger.warn(
                    "Stop retry{}, elapsed {} and {} attempts",
                    finalMethod(),
                    event.getElapsedTime().truncatedTo(ChronoUnit.SECONDS),
                    event.getAttemptCount(),
                    event.getFailure()
                ))
                .onRetry(event -> currentLogger.info(
                    "Retrying{}, elapsed {} and {} attempts",
                    finalMethod(),
                    event.getElapsedTime().truncatedTo(ChronoUnit.SECONDS),
                    event.getAttemptCount()
                ));

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

    @Getter
    public static class RetryFailed extends Exception {
        private static final long serialVersionUID = 1L;

        private final int attemptCount;
        private final Duration elapsedTime;
        private final Duration startTime;

        public <T> RetryFailed(ExecutionAttemptedEvent<? extends T> event) {
            super(
                "Stop retry, attempts " + event.getAttemptCount() + " elapsed after " +
                    event.getElapsedTime().getSeconds() + " seconds",
                event.getLastFailure()
            );

            this.attemptCount = event.getAttemptCount();
            this.elapsedTime = event.getElapsedTime();
            this.startTime = event.getStartTime();
        }
    }
}
