package io.kestra.executor.testkit;

import java.util.concurrent.Callable;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.runners.RunContext;
import io.kestra.core.trace.Tracer;

import io.opentelemetry.api.common.Attributes;

/**
 * {@link Tracer} that runs the instrumented block without any tracing (the core NoopTracer is
 * package-private).
 */
public class PassthroughTracer implements Tracer {

    @Override
    public <V> V inCurrentContext(RunContext runContext, String spanName, Callable<V> callable) {
        return call(callable);
    }

    @Override
    public <V> V inCurrentContext(RunContext runContext, String spanName, Attributes additionalAttributes, Callable<V> callable) {
        return call(callable);
    }

    @Override
    public <V> V inCurrentContext(Execution execution, String spanName, Callable<V> callable) {
        return call(callable);
    }

    @Override
    public <V> V inCurrentContext(Execution execution, String spanName, Attributes additionalAttributes, Callable<V> callable) {
        return call(callable);
    }

    @Override
    public <V> V inNewContext(Execution execution, String spanName, Callable<V> callable) {
        return call(callable);
    }

    @Override
    public <V> V inNewContext(Execution execution, String spanName, Attributes additionalAttributes, Callable<V> callable) {
        return call(callable);
    }

    private static <V> V call(Callable<V> callable) {
        try {
            return callable.call();
        } catch (Exception e) {
            throw e instanceof RuntimeException runtimeException ? runtimeException : new RuntimeException(e);
        }
    }
}
