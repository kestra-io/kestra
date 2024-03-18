package io.kestra.core.server;

import io.kestra.core.utils.Await;
import jakarta.inject.Singleton;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;

/**
 * Service for registering local service states.
 *
 * @see Service
 */
@Singleton
public final class ServiceRegistry {

    private final ConcurrentHashMap<Service.ServiceType, LocalServiceState> services = new ConcurrentHashMap<>();

    /**
     * Registers or update the given {@link LocalServiceState}.
     *
     * @param service The {@link LocalServiceState} to be registered.
     */
    public void register(final LocalServiceState service) {
        services.put(service.service().getType(), service);
    }

    /**
     * Unregisters the given {@link LocalServiceState}.
     *
     * @param service The {@link LocalServiceState} to be un-registered.
     */
    public void unregister(final LocalServiceState service) {
        services.remove(service.service().getType());
    }

    public boolean containsService(final Service.ServiceType type) {
        return services.containsKey(type);
    }

    public Service getServiceByType(final Service.ServiceType type) {
        return services.get(type).service();
    }

    public Service waitForServiceAndGet(final Service.ServiceType type) {
        Await.until(() -> containsService(type));
        return getServiceByType(type);
    }

    /**
     * Gets the {@link LocalServiceState} for the given service type.
     *
     * @param type The service type.
     * @return The {@link LocalServiceState} or {@code null}.
     */
    public LocalServiceState get(final Service.ServiceType type) {
        return services.get(type);
    }

    /**
     * Gets all the registered {@link LocalServiceState}.
     *
     * @return The list of {@link LocalServiceState}.
     */
    public List<LocalServiceState> all() {
        return new ArrayList<>(services.values());
    }

    /**
     * Waits for a given service to be in a given state if registered.
     *
     * @param type            The service type
     * @param state           The expected state.
     * @param maxWaitDuration The max wait duration.
     * @return {@code true} if the service is in the expected state. Otherwise {@code false}.
     */
    public boolean waitForServiceInState(final Service.ServiceType type,
                                         final Service.ServiceState state,
                                         final Duration maxWaitDuration) {
        if (!containsService(type)) return false;
        try {
            Await.until(() -> {
                LocalServiceState service = get(type);
                return service != null && service.instance().is(state);
            }, Duration.ofMillis(100), maxWaitDuration);
        } catch (TimeoutException e) {
            return false;
        }
        return true;
    }

    /**
     * Checks whether this registry is empty.
     *
     * @return {@code} true if no service is registered.
     */
    public boolean isEmpty() {
        return services.isEmpty();
    }
}
