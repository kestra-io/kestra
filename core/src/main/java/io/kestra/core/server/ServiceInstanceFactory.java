package io.kestra.core.server;

import io.kestra.core.models.ServerType;
import io.kestra.core.utils.Network;
import io.kestra.core.utils.VersionProvider;
import io.micronaut.context.env.Environment;
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
public class ServiceInstanceFactory {

    private final ServerConfig serverConfig;
    private final VersionProvider versionProvider;
    private final Environment environment;

    @Inject
    public ServiceInstanceFactory(final ServerConfig serverConfig,
                                  final VersionProvider versionProvider,
                                  final Environment environment) {
        this.serverConfig = serverConfig;
        this.versionProvider = versionProvider;
        this.environment = environment;
    }

    /**
     * Creates a new {@link ServiceInstance} for the given type.
     *
     * @param service The service.
     * @return a new {@link ServiceInstance}.
     */
    public ServiceInstance newServiceInstance(@NotNull final Service service,
                                              @Nullable final Map<String, Object> properties) {
        Objects.requireNonNull(service, "Cannot create ServiceInstance for null service");

        final Instant now = Instant.now();
        return new ServiceInstance(
            service.getId(),
            service.getType(),
            service.getState(),
            newServerInstance(),
            now,
            now,
            List.of(),
            serverConfig,
            Optional.ofNullable(properties).orElse(Map.of())
        );
    }

    private ServerInstance newServerInstance() {
        return new ServerInstance(
            getInstanceType(),
            Optional.ofNullable(versionProvider).map(VersionProvider::getVersion).orElse(null),
            Network.localHostname(),
            Map.of(
                "server.port", getServerPort(),
                "server.management.port", getServerManagementPort()
            )
        );
    }

    private ServerInstance.Type getInstanceType() {
        return getServerType() == ServerType.STANDALONE ?
            ServerInstance.Type.STANDALONE :
            ServerInstance.Type.SERVER;
    }
    private ServerType getServerType() {
        return Optional.ofNullable(environment)
            .flatMap(env -> env.getProperty("kestra.server-type", ServerType.class))
            .orElse(ServerType.STANDALONE);
    }

    private int getServerPort() {
        return Optional.ofNullable(environment)
            .flatMap(env -> env.getProperty("micronaut.server.port", Integer.class))
            .orElse(8080);
    }

    private int getServerManagementPort() {
        return Optional.ofNullable(environment)
            .flatMap(env -> env.getProperty("endpoints.all.port", Integer.class))
            .orElse(8081);
    }
}
