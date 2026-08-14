package io.kestra.core.validations;

import java.io.Serial;
import java.util.List;
import java.util.Map;

import io.kestra.core.models.ServerType;
import io.kestra.core.utils.Enums;

import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Context
@Requires(property = "kestra.server-type")
public class ServerCommandValidator {
    private static final Map<String, String> ALL_REQUIRED_PROPERTIES = Map.of(
        "kestra.queue.type", "https://kestra.io/docs/configuration-guide/setup#queue-configuration",
        "kestra.repository.type", "https://kestra.io/docs/configuration-guide/setup#repository-configuration",
        "kestra.storage.type", "https://kestra.io/docs/configuration-guide/setup#internal-storage-configuration"
    );

    private static final Map<String, String> WORKER_REQUIRED_PROPERTIES = Map.of(
        "kestra.storage.type", "https://kestra.io/docs/configuration-guide/setup#internal-storage-configuration"
    );

    /**
     * Database properties a worker inherits from a shared configuration but never uses: it owns no
     * repository and reaches the rest of the cluster over gRPC.
     */
    private static final List<String> WORKER_IGNORED_PROPERTIES = List.of(
        "datasources",
        "kestra.repository.type"
    );

    private final Environment environment;

    private final ServerType serverType;

    @Inject
    public ServerCommandValidator(final Environment environment) {
        this.environment = environment;
        this.serverType = Enums.getForNameIgnoreCase(environment.getRequiredProperty("kestra.server-type", String.class), ServerType.class);
    }

    @PostConstruct
    void validate() {
        final List<ConfigValidationResult> results = validateServerConfiguration(environment, serverType);

        results.stream()
            .filter(result -> !result.valid())
            .forEach(result -> log.error(result.message()));

        if (results.stream().anyMatch(result -> !result.valid())) {
            throw new ServerCommandException("Incomplete server configuration - missing required properties");
        }

        warnAboutIgnoredWorkerProperties();
    }

    private void warnAboutIgnoredWorkerProperties() {
        final List<String> ignored = ignoredWorkerProperties(environment, serverType);

        if (!ignored.isEmpty()) {
            log.warn(
                "A worker does not use any database, so the following configuration is ignored: {}. It can safely be removed from the worker configuration.",
                String.join(", ", ignored)
            );
        }
    }

    /**
     * Lists the database properties the given server type inherits but never uses. A worker ignores
     * them entirely — it opens no database connection — so reporting them beats letting an operator
     * believe their worker reads from a database it never contacts.
     *
     * @param environment the configuration environment to inspect
     * @param serverType the server type the configuration is applied to
     * @return the ignored property paths, empty for every server type but {@code WORKER}
     */
    static List<String> ignoredWorkerProperties(final Environment environment, final ServerType serverType) {
        if (!ServerType.WORKER.equals(serverType)) {
            return List.of();
        }

        return WORKER_IGNORED_PROPERTIES.stream()
            .filter(environment::containsProperties)
            .toList();
    }

    /**
     * Validates that the properties required to start the given {@link ServerType} are defined.
     *
     * <p>
     * This method is side-effect free (it neither logs nor throws) so the same checks can be
     * reused for on-demand validation.
     *
     * @param environment the configuration environment to validate
     * @param serverType the server type whose required properties must be present
     * @return the outcome of each required-property check, never {@code null}
     */
    public static List<ConfigValidationResult> validateServerConfiguration(final Environment environment, final ServerType serverType) {
        final Map<String, String> required = ServerType.WORKER.equals(serverType) ? WORKER_REQUIRED_PROPERTIES : ALL_REQUIRED_PROPERTIES;

        return required.entrySet().stream()
            .map(
                property -> environment.containsProperty(property.getKey())
                    ? ConfigValidationResult.valid(property.getKey())
                    : ConfigValidationResult.invalid(property.getKey(), missingPropertyMessage(property.getKey(), property.getValue()))
            )
            .toList();
    }

    private static String missingPropertyMessage(final String key, final String documentationUrl) {
        return """
            Server configuration requires the '%s' property to be defined.
            For more details, please follow the official setup guide at: %s""".formatted(key, documentationUrl);
    }

    public static class ServerCommandException extends RuntimeException {
        @Serial
        private static final long serialVersionUID = 1L;

        public ServerCommandException(String errorMessage) {
            super(errorMessage);
        }
    }
}
