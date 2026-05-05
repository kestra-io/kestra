package io.kestra.core.utils;

import org.awaitility.Awaitility;
import org.awaitility.core.ConditionFactory;

import java.time.Duration;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;


public class Await {
    private static final Duration defaultSleep = Duration.ofMillis(100);

    public static ConditionFactory await(){
        return Awaitility
            .await()
            .pollInSameThread()
            .pollDelay(defaultSleep);
    }

    public static void until(BooleanSupplier condition) {
        await().forever().until(condition::getAsBoolean);
    }

    /**
     * @deprecated use {@link #await()} instead
     */
    @Deprecated
    public static void until(BooleanSupplier condition, Duration sleep) {
        if (sleep == null) {
            sleep = defaultSleep;
        }

        while (!condition.getAsBoolean()) {
            try {
                Thread.sleep(sleep.toMillis());
            } catch (InterruptedException e) {
                throw new RuntimeException("Can't sleep:" + e.getMessage());
            }
        }
    }

    /**
     * @deprecated use {@link #await()} instead
     */
    @Deprecated
    public static void until(BooleanSupplier condition, Duration sleep, Duration timeout) throws TimeoutException {
        until(null, condition, sleep, timeout);
    }

    /**
     * @deprecated use {@link #await()} instead
     */
    @Deprecated
    public static void until(Supplier<String> errorMessageInCaseOfFailure, BooleanSupplier condition, Duration sleep, Duration timeout) throws TimeoutException {
        if (sleep == null) {
            sleep = defaultSleep;
        }

        long start = System.currentTimeMillis();
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() - start > timeout.toMillis()) {
                throw new TimeoutException(
                    String.format(
                        "Await failed to terminate within %s.%s",
                        timeout,
                        errorMessageInCaseOfFailure == null ? "" : " " + errorMessageInCaseOfFailure.get()
                    )
                );
            } else {
                try {
                    Thread.sleep(sleep.toMillis());
                } catch (InterruptedException e) {
                    throw new RuntimeException("Can't sleep:" + e.getMessage());
                }
            }
        }
    }

    private static <T> BooleanSupplier untilSupplier(Supplier<T> supplier, AtomicReference<T> result) {
        return () ->
        {
            T t = supplier.get();
            if (t != null) {
                result.set(t);
                return true;
            } else {
                return false;
            }
        };
    }

    /**
     * @deprecated use {@link #await()} instead
     */
    @Deprecated
    public static <T> T until(Supplier<T> supplier, Duration sleep, Duration timeout) throws TimeoutException {
        AtomicReference<T> result = new AtomicReference<>();

        Await.until(untilSupplier(supplier, result), sleep, timeout);

        return result.get();
    }

    /**
     * @deprecated use {@link #await()} instead
     */
    @Deprecated
    public static <T> T until(Supplier<T> supplier, Duration sleep) {
        AtomicReference<T> result = new AtomicReference<>();

        Await.until(untilSupplier(supplier, result), sleep);

        return result.get();
    }
}
