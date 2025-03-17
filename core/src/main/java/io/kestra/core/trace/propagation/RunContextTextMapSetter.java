package io.kestra.core.trace.propagation;

import io.kestra.core.runners.RunContext;
import io.opentelemetry.context.propagation.TextMapSetter;

import javax.annotation.Nullable;

public class RunContextTextMapSetter implements TextMapSetter<RunContext> {
    public static final RunContextTextMapSetter INSTANCE = new RunContextTextMapSetter();

    @Override
    public void set(@Nullable RunContext carrier, String key, String value) {
        if (carrier != null) {
            switch (key) {
                case "traceparent" -> carrier.setTraceParent(value);
                default -> {
                }
            }
        }
    }
}
