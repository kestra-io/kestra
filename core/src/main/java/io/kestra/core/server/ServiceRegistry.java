package io.kestra.core.server;

import io.kestra.core.utils.Await;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for registering local service states.
 *
 * @see Service
 */
@Singleton
public final class ServiceRegistry {

    private final ConcurrentHashMap<Service.ServiceType, LocalServiceState> services = new ConcurrentHashMap<>();

    /**
     * Registers or update a {@link LocalServiceState}.
     *
     * @param service The {@link LocalServiceState}.
     */
    public void register(final LocalServiceState service) {
        services.put(service.service().getType(), service);
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
     * Checks whether this registry is empty.
     *
     * @return {@code} true if no service is registered.
     */
    public boolean isEmpty() {
        return services.isEmpty();
    }

}
