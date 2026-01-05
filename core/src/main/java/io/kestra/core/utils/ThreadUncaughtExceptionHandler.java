package io.kestra.core.utils;

import io.kestra.core.contexts.KestraContext;
import lombok.extern.slf4j.Slf4j;

import java.lang.Thread.UncaughtExceptionHandler;

@Slf4j
public final class ThreadUncaughtExceptionHandler implements UncaughtExceptionHandler {
    public static final UncaughtExceptionHandler INSTANCE = new ThreadUncaughtExceptionHandler();

    @Override
    public void uncaughtException(Thread t, Throwable e) {
        boolean isTest = KestraContext.getContext().getEnvironments().contains("test");

        try {
            // cannot use FormattingLogger due to a dependency loop
            log.error("Caught an exception in {}. {}", t, isTest ? "Keeping it running for test." : "Shutting down.", e);
        } catch (Throwable errorInLogging) {
            // If logging fails, e.g. due to missing memory, at least try to log the
            // message and the cause for the failed logging.
            System.err.println(e.getMessage());
            System.err.println(errorInLogging.getMessage());
        } finally {
            if (!isTest) {
                KestraContext.getContext().shutdown();
                Runtime.getRuntime().exit(1);
            }
        }
    }
}
