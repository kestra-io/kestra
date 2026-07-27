package io.kestra.core.utils;

import java.lang.Thread.UncaughtExceptionHandler;
import java.util.Objects;

import io.kestra.core.contexts.KestraContext;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class ThreadUncaughtExceptionHandler implements UncaughtExceptionHandler {
    /**
     * Shared instance created at class-load time, i.e. usually before any {@link KestraContext} is
     * initialized: it then resolves the context at failure time. Prefer creating a new instance
     * from the owning context (see {@link #ThreadUncaughtExceptionHandler()}) — the static context
     * points to the newest context in the JVM, which at failure time can be a different, freshly
     * started one when several contexts follow each other in the same JVM (tests).
     */
    public static final UncaughtExceptionHandler INSTANCE = new ThreadUncaughtExceptionHandler();

    private final KestraContext kestraContext;

    /**
     * Creates a handler bound to the {@link KestraContext} current at construction time, so the
     * threads it is installed on shut down the context that created them.
     * Falls back to failure-time resolution when no context is initialized yet.
     * <p>
     * Known limitation: the capture reads the same JVM-global static, so it is only correct when
     * the constructing code runs while its owning context is the current one. When several
     * application contexts genuinely coexist in one JVM (nested/embedded contexts), a thread pool
     * built by context A after context B became current is permanently bound to B, and an uncaught
     * exception in A's pool shuts B down. Thread pools are normally built during their context's
     * bean construction — right after its own initializer became current — so this window is
     * narrow; code needing a strict binding should use
     * {@link #ThreadUncaughtExceptionHandler(KestraContext)} with an injected context instead.
     */
    public ThreadUncaughtExceptionHandler() {
        this.kestraContext = currentContextOrNull();
    }

    /**
     * Creates a handler that shuts down the given owning context on an uncaught exception.
     *
     * @param kestraContext the context owning the threads this handler is installed on.
     */
    public ThreadUncaughtExceptionHandler(KestraContext kestraContext) {
        this.kestraContext = Objects.requireNonNull(kestraContext, "kestraContext cannot be null");
    }

    @Override
    public void uncaughtException(Thread t, Throwable e) {
        KestraContext context = kestraContext != null ? kestraContext : KestraContext.getContext();
        boolean isTest = context.getEnvironments().contains("test");

        try {
            // cannot use FormattingLogger due to a dependency loop
            log.error("Caught an exception in {}. Shutting down.", t, e);
        } catch (Throwable errorInLogging) {
            // If logging fails, e.g. due to missing memory, at least try to log the
            // message and the cause for the failed logging.
            System.err.println(e.getMessage());
            System.err.println(errorInLogging.getMessage());
        } finally {
            context.shutdown();

            if (!isTest) {
                Runtime.getRuntime().exit(1);
            }
        }
    }

    private static KestraContext currentContextOrNull() {
        try {
            return KestraContext.getContext();
        } catch (IllegalStateException e) {
            // no context initialized yet (e.g. the INSTANCE created at class-load time):
            // the context will be resolved at failure time instead
            return null;
        }
    }
}
