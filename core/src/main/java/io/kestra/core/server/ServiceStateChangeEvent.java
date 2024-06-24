package io.kestra.core.server;

import io.micronaut.context.event.ApplicationEvent;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

import java.io.Serial;
import java.util.Map;
import java.util.Objects;

/**
 * Event fired when a Service's state is changing.
 */
public final class ServiceStateChangeEvent extends ApplicationEvent {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Opaque map of properties associated to a service.
     */
    private final Map<String, Object> properties;

    /**
     * Creates a new {@link ServiceStateChangeEvent} instance.
     *
     * @param source       The object on which the Event initially occurred.
     * @throws IllegalArgumentException if source is null.
     */
    public ServiceStateChangeEvent(@NotNull final Service source) {
        this(source, null);
    }

    /**
     * Creates a new {@link ServiceStateChangeEvent} instance.
     *
     * @param source       The object on which the Event initially occurred.
     * @param properties   The properties to pass the event listeners.
     * @throws IllegalArgumentException if source is null.
     */
    public ServiceStateChangeEvent(@NotNull final Service source,
                                   @Nullable final Map<String, Object> properties) {
        super(source);
        this.properties = properties;
    }

    /**
     * Gets the properties attached to the service.
     * @return  a map of key/value pairs.
     */
    public Map<String, Object> properties() {
        return properties;
    }

    public Service getService() {
        return (Service) getSource();
    }
}
