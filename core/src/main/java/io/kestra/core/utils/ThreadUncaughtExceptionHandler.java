package io.kestra.core.utils;

import java.lang.Thread.UncaughtExceptionHandler;

import io.kestra.core.contexts.KestraContext;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class ThreadUncaughtExceptionHandler implements UncaughtExceptionHandler {
    public static final UncaughtExceptionHandler INSTANCE = new ThreadUncaughtExceptionHandler();

    @Override
    public void uncaughtException(Thread t, Throwable e) {
        boolean isTest = KestraContext.getContext().getEnvironments().contains("test");

        try {
            // cannot use FormattingLogger due to a dependency loop
            log.error("Caught an exception in {}. Shutting down.", t, e);
        } catch (Throwable errorInLogging) {
            // If logging fails, e.g. due to missing memory, at least try to log the
            // message and the cause for the failed logging.
            System.err.println(e.getMessage());
            System.err.println(errorInLogging.getMessage());
        } finally {
            KestraContext.getContext().shutdown();

            if (!isTest) {
                Runtime.getRuntime().exit(1);
            }
        }
    }
}
