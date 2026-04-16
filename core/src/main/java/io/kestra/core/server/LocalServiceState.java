package io.kestra.core.server;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Immutable class holding a {@link Service} and its {@link ServiceInstance}.
 *
 * @param service The service bean.
 * @param instance The service instance.
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
     * Constructs a new {@link LocalServiceState} from a given instance, preserving the same
     * {@link AtomicBoolean} reference for {@code isStateUpdatable}. This means that any caller
     * holding a reference to the old {@code LocalServiceState} can still mutate the shared flag
     * (e.g., {@code holder.isStateUpdatable().set(false)}) and the change will be visible on the
     * newly registered instance.
     *
     * @param instance The new instance.
     * @return a new {@link LocalServiceState} sharing the same {@code isStateUpdatable} flag.
     */
    public LocalServiceState with(final ServiceInstance instance) {
        return new LocalServiceState(service, instance, isStateUpdatable);
    }
}
