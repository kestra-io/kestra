package io.kestra.core.server;

import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Default factory service for constructing {@link ServiceInstance} objects.
 */
@Singleton
public class LocalServiceStateFactory {

    private final ServerConfig serverConfig;
    private final ServerInstanceFactory serverInstanceFactory;

    @Inject
    public LocalServiceStateFactory(final ServerConfig serverConfig,
                                    final ServerInstanceFactory serverInstanceFactory) {
        this.serverConfig = serverConfig;
        this.serverInstanceFactory = serverInstanceFactory;
    }

    /**
     * Creates a new {@link ServiceInstance} for the given type.
     *
     * @param service The service.
     * @return a new {@link ServiceInstance}.
     */
    public LocalServiceState newLocalServiceState(@NotNull final Service service,
                                                  @Nullable final Map<String, Object> properties) {
        Objects.requireNonNull(service, "Cannot create ServiceInstance for null service");

        final Instant now = Instant.now();
        final ServerInstance server = serverInstanceFactory.newServerInstance();

        ServiceInstance instance = new ServiceInstance(
            service.getId(),
            service.getType(),
            service.getState(),
            server,
            now,
            now,
            List.of(),
            serverConfig,
            Optional.ofNullable(properties).orElse(Map.of()),
            service.getMetrics()
        );
        return new LocalServiceState(service, instance);
    }
}
