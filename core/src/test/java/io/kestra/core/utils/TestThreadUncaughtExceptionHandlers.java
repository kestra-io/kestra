package io.kestra.core.utils;

import io.micronaut.context.annotation.Replaces;
import lombok.extern.slf4j.Slf4j;

import java.lang.Thread.UncaughtExceptionHandler;
import jakarta.inject.Singleton;

@Slf4j
@Singleton
@Replaces(ThreadUncaughtExceptionHandlers.class)
public final class TestThreadUncaughtExceptionHandlers implements UncaughtExceptionHandler {
    @Override
    public void uncaughtException(Thread t, Throwable e) {
        try {
            log.error("Caught an exception in {}. Shutting down.", t, e);
        } catch (Throwable errorInLogging) {
            System.err.println(e.getMessage());
            System.err.println(errorInLogging.getMessage());
        }
    }
}
