package org.kestra.core.utils;

import java.time.Duration;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;

public class Await {
    private static final Duration defaultSleep = Duration.ofMillis(100);

    public static void until(BooleanSupplier condition) {
        Await.until(condition, null);
    }

    public static void until(BooleanSupplier condition, Duration sleep) {
        if (sleep == null) {
            sleep = defaultSleep;
        }

        while (!condition.getAsBoolean()) {
            try {
                Thread.sleep(sleep.toMillis());
            } catch (InterruptedException e) {
                throw new RuntimeException("Can't sleep");
            }
        }
    }

    public static void until(BooleanSupplier condition, Duration sleep, Duration timeout) throws TimeoutException {
        if (sleep == null) {
            sleep = defaultSleep;
        }

        long start = System.currentTimeMillis();
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() - start > timeout.toMillis()) {
                throw new TimeoutException(String.format("Execution failed to terminate within %s", timeout));
            } else {
                try {
                    Thread.sleep(sleep.toMillis());
                } catch (InterruptedException e) {
                    throw new RuntimeException("Can't sleep");
                }
            }
        }
    }

}
