package io.kestra.core.contexts;

import java.io.IOException;
import java.util.Map;

import io.kestra.core.contexts.configuration.RepositoryConfiguration;
import io.kestra.core.contexts.configuration.StorageConfiguration;
import io.kestra.core.exceptions.KestraRuntimeException;
import io.kestra.core.plugins.DefaultPluginRegistry;
import io.kestra.core.plugins.PluginCatalogService;
import io.kestra.core.plugins.PluginRegistry;
import io.kestra.core.repositories.LogDataStoreInterface;
import io.kestra.core.repositories.log.LogDataStoreInterfaceFactory;
import io.kestra.core.repositories.log.LogsConfig;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.storages.StorageInterfaceFactory;
import io.kestra.core.utils.ExecutorsUtils;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.format.MapFormat;
import io.micronaut.core.naming.conventions.StringConvention;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.validation.Validator;

import static io.kestra.core.repositories.log.LogDataStoreInterfaceFactory.KESTRA_LOGS_TYPE_CONFIG;
import static io.kestra.core.storages.StorageInterfaceFactory.KESTRA_STORAGE_TYPE_CONFIG;

@Factory
public class KestraBeansFactory {

    @Inject
    Validator validator;

    @Inject
    StorageConfig storageConfig;

    @Inject
    protected StorageConfiguration storageConfiguration;

    @Inject
    LogsConfig logsConfig;

    @Inject
    RepositoryConfiguration repositoryConfiguration;

    @Singleton
    public PluginCatalogService pluginCatalogService(@Client("api") HttpClient httpClient, ExecutorsUtils executorsUtils) {
        return new PluginCatalogService(httpClient, false, true, executorsUtils);
    }

    @Requires(missingBeans = PluginRegistry.class)
    @Singleton
    public PluginRegistry pluginRegistry() {
        return DefaultPluginRegistry.getOrCreate();
    }

    @Singleton
    public StorageInterfaceFactory storageInterfaceFactory(final PluginRegistry pluginRegistry) {
        return new StorageInterfaceFactory(pluginRegistry, validator);
    }

    @Requires(missingBeans = StorageInterface.class)
    @Singleton
    @Bean(preDestroy = "close")
    public StorageInterface storageInterface(final StorageInterfaceFactory storageInterfaceFactory) throws IOException {
        String pluginId = getStoragePluginId(storageInterfaceFactory);
        return storageInterfaceFactory.make(null, pluginId, storageConfig.getStorageConfig(pluginId));
    }

    public String getStoragePluginId(StorageInterfaceFactory storageInterfaceFactory) {
        return storageConfiguration.type().orElseThrow(
            () -> new KestraRuntimeException(
                String.format(
                    "No storage configured through the application property '%s'. Supported types are: %s", KESTRA_STORAGE_TYPE_CONFIG,
                    storageInterfaceFactory.getLoggableTypeIds()
                )
            )
        );
    }

    @Singleton
    public LogDataStoreInterfaceFactory logDataStoreInterfaceFactory(final PluginRegistry pluginRegistry,
        final ApplicationContext applicationContext) {
        return new LogDataStoreInterfaceFactory(pluginRegistry, validator, applicationContext);
    }

    @Requires(property = "kestra.server-type", notEquals = "WORKER")
    @Singleton
    public LogDataStoreInterface logDataStore(final LogDataStoreInterfaceFactory logDataStoreInterfaceFactory) {
        String pluginId = getLogDataStorePluginId(logDataStoreInterfaceFactory);
        return logDataStoreInterfaceFactory.make(pluginId, logsConfig.getLogConfig(pluginId));
    }

    /**
     * Resolves the configured log data store type, falling back to {@code kestra.repository.type} so
     * that existing installs (no {@code kestra.logs.type}) keep storing logs in the main database.
     */
    public String getLogDataStorePluginId(LogDataStoreInterfaceFactory logDataStoreInterfaceFactory) {
        String type = logsConfig.type().orElse(repositoryConfiguration.type());
        if (type == null) {
            throw new KestraRuntimeException(
                String.format(
                    "No log store configured through the application property '%s' (nor a fallback '%s'). Supported types are: %s",
                    KESTRA_LOGS_TYPE_CONFIG, "kestra.repository.type", logDataStoreInterfaceFactory.getLoggableTypeIds()
                )
            );
        }
        // The in-memory backend is H2 with an in-memory datasource.
        return "memory".equalsIgnoreCase(type) ? "h2" : type;
    }

    @ConfigurationProperties("kestra")
    public record StorageConfig(
        @Nullable
        @MapFormat(keyFormat = StringConvention.CAMEL_CASE, transformation = MapFormat.MapTransformation.NESTED) Map<String, Object> storage) {

        /**
         * Returns the configuration for the configured storage.
         *
         * @return the configuration.
         */
        @SuppressWarnings("unchecked")
        public Map<String, Object> getStorageConfig(String type) {
            return (Map<String, Object>) storage.get(StringConvention.CAMEL_CASE.format(type));
        }
    }
}
