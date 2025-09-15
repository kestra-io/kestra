package io.kestra.core.contexts;

import io.kestra.core.exceptions.KestraRuntimeException;
import io.kestra.core.plugins.DefaultPluginRegistry;
import io.kestra.core.plugins.PluginCatalogService;
import io.kestra.core.plugins.PluginRegistry;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.storages.StorageInterfaceFactory;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.format.MapFormat;
import io.micronaut.core.naming.conventions.StringConvention;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.validation.Validator;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

import static io.kestra.core.storages.StorageInterfaceFactory.KESTRA_STORAGE_TYPE_CONFIG;

@Factory
public class KestraBeansFactory {

    @Inject
    Validator validator;

    @Inject
    StorageConfig storageConfig;

    @Value("${kestra.storage.type}")
    protected Optional<String> storageType;

    @Singleton
    public PluginCatalogService pluginCatalogService(@Client("api") HttpClient httpClient) {
        return new PluginCatalogService(httpClient, false, true);
    }

    @Requires(missingBeans = PluginRegistry.class)
    @Singleton
    public PluginRegistry pluginRegistry() {
        return DefaultPluginRegistry.getOrCreate();
    }

    @Singleton
    public StorageInterfaceFactory storageInterfaceFactory(final PluginRegistry pluginRegistry){
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
        return storageType.orElseThrow(() -> new KestraRuntimeException(String.format(
            "No storage configured through the application property '%s'. Supported types are: %s"
            , KESTRA_STORAGE_TYPE_CONFIG,
            storageInterfaceFactory.getLoggableStorageIds()
        )));
    }

    @ConfigurationProperties("kestra")
    public record StorageConfig(
        @Nullable
        @MapFormat(keyFormat = StringConvention.CAMEL_CASE, transformation = MapFormat.MapTransformation.NESTED)
        Map<String, Object> storage
    ) {

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
