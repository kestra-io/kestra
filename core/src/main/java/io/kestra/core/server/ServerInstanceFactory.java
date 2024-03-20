package io.kestra.core.server;

import io.kestra.core.contexts.KestraContext;
import io.kestra.core.models.ServerType;
import io.kestra.core.utils.Network;
import io.micronaut.context.env.Environment;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Default factory service for constructing {@link ServiceInstance} objects.
 */
@Singleton
public class ServerInstanceFactory {

    private final KestraContext context;

    private final Environment environment;

    @Inject
    public ServerInstanceFactory(final KestraContext context,
                                 final Environment environment) {
        this.context = Objects.requireNonNull(context, "KestraContext cannot be null");
        this.environment = environment;
    }

    /**
     * Creates a new {@link ServiceInstance} for the given type.
     *
     * @return a new {@link ServiceInstance}.
     **/
    public ServerInstance newServerInstance() {
        return new ServerInstance(
            getInstanceType(),
            context.getVersion(),
            Network.localHostname(),
            Map.of(
                "server.port", getServerPort(),
                "server.management.port", getServerManagementPort()
            ),
            Set.of()
        );
    }

    private ServerInstance.Type getInstanceType() {
        return getServerType() == ServerType.STANDALONE ?
            ServerInstance.Type.STANDALONE :
            ServerInstance.Type.SERVER;
    }

    private ServerType getServerType() {
        return context.getServerType();
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
