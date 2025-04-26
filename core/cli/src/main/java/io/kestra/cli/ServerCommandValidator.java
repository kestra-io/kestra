package io.kestra.cli;

import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.io.Serial;
import java.util.List;
import java.util.Map;

@Slf4j
@Context
@Requires(property = "kestra.server-type")
public class ServerCommandValidator {
    private static final Map<String, String> VALIDATED_PROPERTIES = Map.of(
        "kestra.queue.type", "https://kestra.io/docs/configuration-guide/setup#queue-configuration",
        "kestra.repository.type", "https://kestra.io/docs/configuration-guide/setup#repository-configuration",
        "kestra.storage.type", "https://kestra.io/docs/configuration-guide/setup#internal-storage-configuration"
    );

    private final Environment environment;

    @Inject
    public ServerCommandValidator(final Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    void validate() {
        final List<Map.Entry<String, String>> missingProperties = VALIDATED_PROPERTIES.entrySet().stream()
            .filter((property) -> !environment.containsProperty(property.getKey()))
            .toList();

        missingProperties.forEach(property -> log.error("""
            Server configuration requires the '{}' property to be defined.
            For more details, please follow the official setup guide at: {}""", property.getKey(), property.getValue())
        );

        if (!missingProperties.isEmpty()) {
            throw new ServerCommandException("Incomplete server configuration - missing required properties");
        }
    }

    public static class ServerCommandException extends RuntimeException {
        @Serial
        private static final long serialVersionUID = 1L;

        public ServerCommandException(String errorMessage) {
            super(errorMessage);
        }
    }
}