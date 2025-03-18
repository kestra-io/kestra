package io.kestra.core.trace;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Creates <code>Trace</code> instances.
 *
 * @see Tracer
 */
@Singleton
public class TracerFactory {
    @Inject
    private OpenTelemetry openTelemetry;

    @Inject
    private io.opentelemetry.api.trace.Tracer tracer;

    @Inject
    private TracesConfiguration tracesConfiguration;

    /**
     * Get a tracer for a class with a given prefix for the span names.
     */
    public Tracer getTracer(Class<?> clazz, String spanNamePrefix) {
        TraceLevel level = levelFromConfiguration(clazz.getName());
        Attributes attributes = TraceUtils.attributesFrom(clazz);
        return level == TraceLevel.DISABLED ? new NoopTracer() : new DefaultTracer(openTelemetry, tracer, spanNamePrefix, level, attributes);
    }

    private TraceLevel levelFromConfiguration(String name) {
        if (name == null) {
            return tracesConfiguration.root();
        } else if(tracesConfiguration.categories().containsKey(name)) {
            return tracesConfiguration.categories().get(name);
        } else {
            if (name.contains(".")) {
                return levelFromConfiguration(name.substring(0, name.lastIndexOf('.')));
            } else {
                return tracesConfiguration.root();
            }
        }
    }
}
