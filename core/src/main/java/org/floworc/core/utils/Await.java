package org.floworc.core.utils;

import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;

public class Await {
    public static void until(BooleanSupplier condition) {
        while (!condition.getAsBoolean()) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                throw new RuntimeException("Can't sleep");
            }
        }
    }

    public static void until(BooleanSupplier condition, long timeoutMs) throws TimeoutException {
        long start = System.currentTimeMillis();
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() - start > timeoutMs) {
                throw new TimeoutException(String.format("Execution failed to terminate within %s ms", timeoutMs));
            } else {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    throw new RuntimeException("Can't sleep");
                }
            }
        }
    }

}
