package io.kestra.core.trace.propagation;

import java.util.List;

import io.kestra.core.runners.RunContext;

import io.opentelemetry.context.propagation.TextMapGetter;
import jakarta.annotation.Nullable;

public class RunContextTextMapGetter implements TextMapGetter<RunContext> {
    public static final RunContextTextMapGetter INSTANCE = new RunContextTextMapGetter();

    @Override
    public Iterable<String> keys(RunContext carrier) {
        return List.of("traceparent");
    }

    @Nullable
    @Override
    public String get(@Nullable RunContext carrier, String key) {
        if (carrier == null) {
            return null;
        }

        return switch (key) {
            case "traceparent" -> carrier.getTraceParent();
            default -> null;
        };
    }
}
