package io.kestra.core.repositories.log;

import java.util.Map;
import java.util.Optional;

import io.kestra.core.contexts.configuration.RepositoryConfiguration;
import io.kestra.core.exceptions.KestraRuntimeException;
import io.kestra.core.plugins.PluginRegistry;
import io.kestra.core.repositories.LogRepositoryInterface;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Factory;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.format.MapFormat;
import io.micronaut.core.naming.conventions.StringConvention;
import jakarta.inject.Singleton;
import jakarta.validation.Validator;

import static io.kestra.core.repositories.log.LogRepositoryInterfaceFactory.KESTRA_LOGS_TYPE_CONFIG;

/**
 * Produces the pluggable {@link LogRepositoryInterface} bean selected by {@code kestra.logs.type}.
 * <p>
 * Kept as a dedicated {@link Factory} (rather than in the shared {@code KestraBeansFactory}) so the
 * {@code @Requires(missingBeans = LogRepositoryInterface.class)} producer is defined exactly once:
 * {@code KestraBeansFactory} is subclassed in EE, which would give the inherited producer a second
 * bean definition and make the two {@code missingBeans} conditions recurse on each other.
 */
@Factory
public class LogRepositoryFactory {

    private final Validator validator;
    private final LogsConfig logsConfig;
    private final RepositoryConfiguration repositoryConfiguration;

    public LogRepositoryFactory(final Validator validator,
        final LogsConfig logsConfig,
        final RepositoryConfiguration repositoryConfiguration) {
        this.validator = validator;
        this.logsConfig = logsConfig;
        this.repositoryConfiguration = repositoryConfiguration;
    }

    @Singleton
    public LogRepositoryInterfaceFactory logRepositoryInterfaceFactory(final PluginRegistry pluginRegistry,
        final ApplicationContext applicationContext) {
        return new LogRepositoryInterfaceFactory(pluginRegistry, validator, applicationContext);
    }

    @Singleton
    public LogRepositoryInterface logRepository(final LogRepositoryInterfaceFactory logRepositoryInterfaceFactory) {
        String pluginId = getLogStorePluginId(logRepositoryInterfaceFactory);
        return logRepositoryInterfaceFactory.make(pluginId, logsConfig.getLogConfig(pluginId));
    }

    /**
     * Resolves the configured log store type, falling back to {@code kestra.repository.type} so that
     * existing installs (no {@code kestra.logs.type}) keep storing logs in the main database.
     */
    public String getLogStorePluginId(LogRepositoryInterfaceFactory logRepositoryInterfaceFactory) {
        String type = logsConfig.type().orElse(repositoryConfiguration.type());
        if (type == null) {
            throw new KestraRuntimeException(
                String.format(
                    "No log store configured through the application property '%s' (nor a fallback '%s'). Supported types are: %s",
                    KESTRA_LOGS_TYPE_CONFIG, "kestra.repository.type", logRepositoryInterfaceFactory.getLoggableTypeIds()
                )
            );
        }
        return type;
    }

    @ConfigurationProperties("kestra")
    public record LogsConfig(
        @Nullable
        @MapFormat(keyFormat = StringConvention.CAMEL_CASE, transformation = MapFormat.MapTransformation.NESTED) Map<String, Object> logs) {

        /**
         * @return the configured {@code kestra.logs.type}, if any.
         */
        public Optional<String> type() {
            return Optional.ofNullable(logs)
                .map(m -> m.get("type"))
                .map(Object::toString);
        }

        /**
         * Returns the per-type configuration sub-map for the configured log store.
         *
         * @param type the resolved log store type.
         * @return the configuration, or an empty map when none is provided.
         */
        @SuppressWarnings("unchecked")
        public Map<String, Object> getLogConfig(String type) {
            if (logs == null) {
                return Map.of();
            }
            Object config = logs.get(StringConvention.CAMEL_CASE.format(type));
            return config == null ? Map.of() : (Map<String, Object>) config;
        }
    }
}
