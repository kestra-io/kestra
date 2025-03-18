package io.kestra.core.trace.propagation;

import io.kestra.core.models.executions.Execution;
import io.opentelemetry.context.propagation.TextMapSetter;

import javax.annotation.Nullable;

public class ExecutionTextMapSetter implements TextMapSetter<Execution> {
    public static final ExecutionTextMapSetter INSTANCE = new ExecutionTextMapSetter();

    @Override
    public void set(@Nullable Execution carrier, String key, String value) {
        if (carrier != null) {
            switch (key) {
                case "traceparent" -> carrier.setTraceParent(value);
                default -> {
                }
            }
        }
    }
}
