package io.kestra.core.trace;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.runners.RunContext;
import io.kestra.core.utils.Rethrow;
import io.opentelemetry.api.common.Attributes;

/**
 * A <code>Tracer</code> allow to instrument a block of code with OpenTelemetry traces.
 */
public interface Tracer {
    /**
     * Instrument a block of code with a trace context extracted from the run context.
     *
     * @see #inCurrentContext(RunContext, String, Attributes, Callable)
     */
    <V> V inCurrentContext(RunContext runContext, String spanName, Callable<V> callable);

    /**
     * Instrument a block of code with a trace context extracted from the run context.
     * Default span attributes will be added derived from the run context.
     *
     * @param runContext the run context
     * @param spanName the name of the span
     * @param additionalAttributes additional span attributes
     * @param callable the bock of code
     */
    <V> V inCurrentContext(RunContext runContext, String spanName, Attributes additionalAttributes, Callable<V> callable);

    /**
     * Instrument a block of code with a trace context extracted from the execution.
     *
     * @see #inCurrentContext(Execution, String, Attributes, Callable)
     */
    <V> V inCurrentContext(Execution execution, String spanName, Callable<V> callable);

    /**
     * Instrument a block of code with a trace context extracted from the execution.
     * Default span attributes will be added derived from the execution.
     *
     * @param execution the execution
     * @param spanName the name of the span
     * @param additionalAttributes additional span attributes
     * @param callable the bock of code
     */
    <V> V inCurrentContext(Execution execution, String spanName, Attributes additionalAttributes, Callable<V> callable);

    /**
     * Instrument a block of code with a new trace context from a parent context extracted from the execution.
     *
     * @see #inNewContext(Execution, String, Attributes, Callable)
     */
    <V> V inNewContext(Execution execution, String spanName, Callable<V> callable);

    /**
     * Instrument a block of code with a new trace context from a parent context extracted from the execution.
     * Default span attributes will be added derived from the execution.
     *
     * @param execution the execution
     * @param spanName the name of the span
     * @param additionalAttributes additional span attributes
     * @param callable the bock of code
     */
    <V> V inNewContext(Execution execution, String spanName, Attributes additionalAttributes, Callable<V> callable);

    @FunctionalInterface
    interface Callable<V> {
        V call();
    }

    @FunctionalInterface
    interface CallableChecked<R, E extends Exception> {
        R call() throws E;
    }

    static <R, E extends Exception> Callable<R> throwCallable(Rethrow.CallableChecked<R, E> runnable) throws E {
        return () -> {
            try {
                return runnable.call();
            } catch (Exception exception) {
                return throwException(exception);
            }
        };
    }

    @SuppressWarnings("unchecked")
    private static <E extends Exception, R> R throwException(Exception exception) throws E {
        throw (E) exception;
    }
}
