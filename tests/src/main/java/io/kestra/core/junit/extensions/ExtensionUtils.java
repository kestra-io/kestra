package io.kestra.core.junit.extensions;

import java.net.URL;

import org.opentest4j.TestAbortedException;

import io.kestra.core.junit.services.ContextShutdownRecorder;

import io.micronaut.context.ApplicationContext;

public final class ExtensionUtils {

    private ExtensionUtils() {
    }

    public static URL loadFile(String path) {
        URL resource = ExtensionUtils.class.getClassLoader().getResource(path);
        if (resource == null) {
            throw new IllegalArgumentException("Unable to load flow: " + path);
        }
        return resource;
    }

    /**
     * Abort the test when the context has already been stopped, rather than letting a fixture fail with
     * an unrelated error.
     * <p>
     * Reported as aborted rather than failed.
     * The abort reason carries the recorded shutdown so it survives in the JUnit report. Note this only
     * covers a context that has finished stopping, which is what produces a null-property binding;
     * a resolution racing an in-progress {@code stop()} can still fail in its own way.
     *
     * @param context the context a fixture is about to resolve beans from
     * @throws TestAbortedException if the context has already stopped
     */
    public static void abortIfContextStopped(ApplicationContext context) {
        if (!context.isRunning()) {
            throw new TestAbortedException(
                "Application context is no longer running, skipping test. %s".formatted(ContextShutdownRecorder.describeLastShutdown(context))
            );
        }
    }

}
