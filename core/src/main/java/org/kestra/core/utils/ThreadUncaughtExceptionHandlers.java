package org.kestra.core.utils;

import io.micronaut.context.ApplicationContext;
import lombok.extern.slf4j.Slf4j;

import java.lang.Thread.UncaughtExceptionHandler;
import javax.inject.Inject;
import javax.inject.Singleton;

@Slf4j
@Singleton
public final class ThreadUncaughtExceptionHandlers implements UncaughtExceptionHandler {
    @Inject
    private ApplicationContext applicationContext;

    private final Runtime runtime = Runtime.getRuntime();

    @Override
    public void uncaughtException(Thread t, Throwable e) {
        boolean isTest = applicationContext.getEnvironment().getActiveNames().contains("test");

        try {
            // cannot use FormattingLogger due to a dependency loop
            log.error("Caught an exception in {}. " + (isTest ? "Keeping it running for test." : "Shutting down."), t, e);
        } catch (Throwable errorInLogging) {
            // If logging fails, e.g. due to missing memory, at least try to log the
            // message and the cause for the failed logging.
            System.err.println(e.getMessage());
            System.err.println(errorInLogging.getMessage());
        } finally {
            if (!isTest) {
                applicationContext.close();
                runtime.exit(1);
            }
        }
    }
}
