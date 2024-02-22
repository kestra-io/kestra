package io.kestra.core.runners;

import com.google.common.annotations.VisibleForTesting;
import io.micronaut.context.annotation.Value;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

import java.util.Objects;
import java.util.UUID;

/**
 * Runtime information about a Kestra server.
 */
public record ServerInstance(@NotNull UUID id) {
    private static final ServerInstance INSTANCE = new ServerInstance(UUID.randomUUID());

    public ServerInstance {
        Objects.requireNonNull(id, "id cannot be null");
    }

    /**
     * @return the local {@link ServerInstance}.
     */
    public static ServerInstance getInstance() {
        return INSTANCE;
    }

}
