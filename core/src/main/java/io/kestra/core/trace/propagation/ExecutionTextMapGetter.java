package io.kestra.core.trace.propagation;

import io.kestra.core.models.executions.Execution;
import io.opentelemetry.context.propagation.TextMapGetter;

import javax.annotation.Nullable;
import java.util.List;

public class ExecutionTextMapGetter implements TextMapGetter<Execution> {
    public static final ExecutionTextMapGetter INSTANCE = new ExecutionTextMapGetter();

    @Override
    public Iterable<String> keys(Execution carrier) {
        return List.of("traceparent");
    }

    @Nullable
    @Override
    public String get(@Nullable Execution carrier, String key) {
        if (carrier == null) {
            return null;
        }

        return switch(key) {
            case "traceparent" -> carrier.getTraceParent();
            default -> null;
        };
    }
}
