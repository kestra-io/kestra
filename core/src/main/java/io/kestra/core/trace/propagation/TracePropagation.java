package io.kestra.core.trace.propagation;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.ContextPropagators;

public final class TracePropagation {
    private TracePropagation() {
    }

    public static Optional<String> traceParent(Optional<OpenTelemetry> openTelemetry, Context context) {
        return openTelemetry
            .map(OpenTelemetry::getPropagators)
            .map(ContextPropagators::getTextMapPropagator)
            .flatMap(propagator -> {
                Map<String, String> carrier = new HashMap<>(1);
                propagator.inject(context, carrier, Map::put);
                return Optional.ofNullable(carrier.get("traceparent"));
            });
    }
}