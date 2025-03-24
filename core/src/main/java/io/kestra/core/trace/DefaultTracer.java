package io.kestra.core.trace;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.runners.RunContext;
import io.kestra.core.trace.propagation.ExecutionTextMapGetter;
import io.kestra.core.trace.propagation.RunContextTextMapGetter;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;

class DefaultTracer implements Tracer {
    private final OpenTelemetry openTelemetry;
    private final io.opentelemetry.api.trace.Tracer tracer;
    private final String spanNamePrefix;
    private final TraceLevel level; // FIXME useless for now as we didn't handle FINE level
    private final Attributes baseAttributes;

    DefaultTracer(OpenTelemetry openTelemetry, io.opentelemetry.api.trace.Tracer tracer, String spanNamePrefix, TraceLevel level, Attributes baseAttributes) {
        this.openTelemetry = openTelemetry;
        this.tracer = tracer;
        this.spanNamePrefix = spanNamePrefix;
        this.level = level;
        this.baseAttributes = baseAttributes;
    }

    @Override
    public <V> V inCurrentContext(RunContext runContext, String spanName, Callable<V> callable) {
        return inCurrentContext(runContext, spanName, null, callable);
    }

    @Override
    public <V> V inCurrentContext(RunContext runContext, String spanName, Attributes additionalAttributes, Callable<V> callable) {
        // extract the traceparent from the run context to allow trace propagation
        var propagator = openTelemetry.getPropagators().getTextMapPropagator();
        var extractedContext = propagator.extract(Context.current(), runContext, RunContextTextMapGetter.INSTANCE);

        var attributesBuilder = Attributes.builder()
            .putAll(baseAttributes)
            .putAll(TraceUtils.attributesFrom(runContext));
        if (additionalAttributes != null) {
            attributesBuilder.putAll(additionalAttributes);
        }

        return inCurrentContext(extractedContext, spanName, attributesBuilder.build(), callable);
    }

    @Override
    public <V> V inCurrentContext(Execution execution, String spanName, Callable<V> callable) {
        return inCurrentContext(execution, spanName, null, callable);
    }

    @Override
    public <V> V inCurrentContext(Execution execution, String spanName, Attributes additionalAttributes, Callable<V> callable) {
        // extract the traceparent from the execution to allow trace propagation
        var propagator = openTelemetry.getPropagators().getTextMapPropagator();
        var extractedContext = propagator.extract(Context.current(), execution, ExecutionTextMapGetter.INSTANCE);

        var attributesBuilder = Attributes.builder()
            .putAll(baseAttributes)
            .putAll(TraceUtils.attributesFrom(execution));
        if (additionalAttributes != null) {
            attributesBuilder.putAll(additionalAttributes);
        }

        return inCurrentContext(extractedContext, spanName, attributesBuilder.build(), callable);
    }

    @Override
    public <V> V inNewContext(Execution execution, String spanName, Callable<V> callable) {
        return inNewContext(execution, spanName, null, callable);
    }

    @Override
    public <V> V inNewContext(Execution execution, String spanName, Attributes additionalAttributes, Callable<V> callable) {
        var attributesBuilder = Attributes.builder()
            .putAll(baseAttributes)
            .putAll(TraceUtils.attributesFrom(execution));
        if (additionalAttributes != null) {
            attributesBuilder.putAll(additionalAttributes);
        }

        return inNewContext(spanName, attributesBuilder.build(), callable);
    }

    private <V> V inCurrentContext(Context context, String spanName, Attributes attributes, Callable<V> callable) {
        try (Scope ignored = context.makeCurrent()) {
            var span = tracer.spanBuilder(spanNamePrefix + " - " + spanName)
                .setAllAttributes(attributes)
                .startSpan();
            try {
                return callable.call();
            } catch(Exception e) {
                span.setStatus(StatusCode.ERROR, e.getMessage());
                throw e;
            } finally {
                span.end();
            }
        }
    }

    private <V> V inNewContext(String spanName, Attributes attributes, Callable<V> callable) {
        var span = tracer.spanBuilder(spanNamePrefix + " - " + spanName)
            .setAllAttributes(attributes)
            .startSpan();
        try (Scope ignored = span.makeCurrent()) {
            return callable.call();
        } catch(Exception e) {
            span.setStatus(StatusCode.ERROR, e.getMessage());
            throw e;
        } finally {
            span.end();
        }
    }
}
