package io.kestra.core.junit.services;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import io.micronaut.context.annotation.Context;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.context.event.ShutdownEvent;

/**
 * Records who shut a test application context down, so a fixture that later finds a stopped context can
 * name the culprit instead of failing on something unrelated.
 * <p>
 * A stopped context resolves {@code @ConfigurationProperties} against an emptied environment
 * ({@code DefaultApplicationContext#stop} clears the resolved-property catalog and drops the
 * environment), so any bean built afterward binds nulls and fails validation on an arbitrary property.
 * The shutdown itself is logged by whoever requested it, but that happens on a background thread and
 * Gradle's per-test stdout capture routinely drops those lines, whereas an exception message reaches the
 * JUnit XML report intact. Consumed by {@link io.kestra.core.junit.extensions.AbstractLoaderExtension}.
 */
@Context
public final class ContextShutdownRecorder implements ApplicationEventListener<ShutdownEvent> {

    private static final int MAX_FRAMES = 12;

    /** Frames that only ever describe the shutdown plumbing, never the caller that requested it. */
    private static final List<String> PLUMBING_PREFIXES = List.of("io.micronaut.context.", "io.micronaut.inject.");

    private static final AtomicReference<Shutdown> LAST_SHUTDOWN = new AtomicReference<>();

    @Override
    public void onApplicationEvent(ShutdownEvent event) {
        LAST_SHUTDOWN.set(new Shutdown(
            System.identityHashCode(event.getSource()),
            describe(Thread.currentThread(), Thread.currentThread().getStackTrace())
        ));
    }

    /**
     * Describes the most recent context shutdown observed in this JVM.
     *
     * @param context the context the caller found stopped, used to flag an unrelated recording
     * @return the recording, or a note that none was seen
     */
    public static String describeLastShutdown(Object context) {
        Shutdown lastShutdown = LAST_SHUTDOWN.get();
        if (lastShutdown == null) {
            return "No context shutdown was recorded in this JVM.";
        }
        if (lastShutdown.contextId() != System.identityHashCode(context)) {
            return "%s%n(This shutdown was recorded for a different context, so it may be unrelated.)"
                .formatted(lastShutdown.description());
        }
        return lastShutdown.description();
    }

    /**
     * Renders the thread and the frames that identify the caller, dropping the framework plumbing. Falls
     * back to the unfiltered frames when filtering leaves nothing, so a shutdown driven entirely by the
     * framework still reports something.
     */
    static String describe(Thread thread, StackTraceElement[] stackTrace) {
        List<String> frames = frames(stackTrace, true);
        if (frames.isEmpty()) {
            frames = frames(stackTrace, false);
        }
        return "Context was shut down by thread '%s'. Caller frames (framework plumbing removed):%n%s"
            .formatted(thread.getName(), String.join(System.lineSeparator(), frames));
    }

    private static List<String> frames(StackTraceElement[] stackTrace, boolean skipPlumbing) {
        List<String> frames = new ArrayList<>(MAX_FRAMES);
        for (StackTraceElement frame : stackTrace) {
            String className = frame.getClassName();
            // This recorder and Thread#getStackTrace are always the top frames and never informative.
            if (className.equals(ContextShutdownRecorder.class.getName()) || className.equals(Thread.class.getName())) {
                continue;
            }
            if (skipPlumbing && PLUMBING_PREFIXES.stream().anyMatch(className::startsWith)) {
                continue;
            }
            frames.add("\tat " + frame);
            if (frames.size() == MAX_FRAMES) {
                break;
            }
        }
        return frames;
    }

    private record Shutdown(int contextId, String description) {
    }
}
