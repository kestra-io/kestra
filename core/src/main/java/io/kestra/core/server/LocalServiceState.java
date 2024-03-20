package io.kestra.core.server;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Immutable class holding a {@link Service} and its {@link ServiceInstance}.
 *
 * @param service          The service bean.
 * @param instance         The service instance.
 * @param isStateUpdatable Flag indicating whether the service's state is updatable or not.
 */
public record LocalServiceState(Service service,
                                ServiceInstance instance,
                                AtomicBoolean isStateUpdatable) {

    public LocalServiceState(Service service,
                             ServiceInstance instance) {
        this(service, instance, new AtomicBoolean(true));
    }

    /**
     * Convenient method for constructing a new {@link LocalServiceState} from a given instance.
     *
     * @param instance The new instance.
     * @return a new {@link LocalServiceState}
     */
    public LocalServiceState with(final ServiceInstance instance) {
        return new LocalServiceState(service, instance, isStateUpdatable);
    }
}
