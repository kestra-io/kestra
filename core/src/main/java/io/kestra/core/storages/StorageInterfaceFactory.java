package io.kestra.core.storages;

import io.kestra.core.exceptions.KestraRuntimeException;
import io.kestra.core.models.Plugin;
import io.kestra.core.plugins.PluginRegistry;
import io.kestra.core.plugins.RegisteredPlugin;
import io.kestra.core.serializers.JacksonMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Factor class for constructing {@link StorageInterface} objects.
 */
public class StorageInterfaceFactory {
    
    public static final String KESTRA_STORAGE_TYPE_CONFIG = "kestra.storage.type";

    private final PluginRegistry pluginRegistry;
    private final Validator validator;

    public StorageInterfaceFactory(final PluginRegistry pluginRegistry,
                                   final Validator validator) {
        this.pluginRegistry = pluginRegistry;
        this.validator = validator;
    }

    /**
     * Factory method for constructing and validating new {@link StorageInterface} of the given type with the given configuration.
     *
     * @param pluginId            The ID of the storage. cannot be {@code null}.
     * @param pluginConfiguration The configuration of the storage. cannot be {@code null}.
     * @return a new {@link StorageInterface}.
     * @throws KestraRuntimeException if no storage can be found.
     */
    public StorageInterface make(final StorageConfiguration storageConfiguration,
                                 final String pluginId,
                                 final Map<String, Object> pluginConfiguration) {
        Optional<Class<? extends StorageInterface>> optional = allStorageClasses(pluginRegistry)
            .filter(clazz -> Plugin.getId(clazz).map(id -> id.equalsIgnoreCase(pluginId)).orElse(false))
            .findFirst();

        if (optional.isEmpty()) {
            String storageIds = getLoggableStorageIds();
            throw new KestraRuntimeException(String.format(
                "No storage interface can be found for '%s=%s'. Supported types are: %s", KESTRA_STORAGE_TYPE_CONFIG, pluginId, storageIds
            ));
        }

        Class<? extends StorageInterface> pluginClass = optional.get();

        // Storage are handle as any serializable/deserialize plugins.
        StorageInterface plugin;
        try {
            // Make sure config is not null, otherwise deserialization result will be null too.
            Map<String, Object> nonEmptyConfig = Optional.ofNullable(pluginConfiguration).orElse(Map.of());
            plugin = JacksonMapper.toMap(nonEmptyConfig, pluginClass);
        } catch (Exception e) {
            throw new KestraRuntimeException(String.format(
                "Failed to create storage '%s'. Error: %s", pluginId, e.getMessage())
            );
        }

        // Validate configuration.
        Set<ConstraintViolation<StorageInterface>> violations;
        try {
            violations = validator.validate(plugin);
        } catch (ConstraintViolationException e) {
            throw new KestraRuntimeException(String.format(
                "Failed to validate configuration for storage '%s'. Error: %s", pluginId, e.getMessage())
            );
        }
        if (!violations.isEmpty()) {
            ConstraintViolationException e = new ConstraintViolationException(violations);
            throw new KestraRuntimeException(String.format(
                "Invalid configuration for storage '%s'. Error: '%s'", pluginId, e.getMessage()), e
            );
        }

        try {
            plugin = init(storageConfiguration, plugin);
        } catch (IOException e) {
            throw new KestraRuntimeException(String.format(
                "Failed to initialize storage '%s'. Error: %s", pluginId, e.getMessage()), e
            );
        }
        return plugin;
    }

    protected StorageInterface init(final StorageConfiguration storageConfiguration,
                                    final StorageInterface plugin) throws IOException {
        plugin.init();
        return plugin;
    }

    public String getLoggableStorageIds() {
        return allIdsFor(allStorageClasses(pluginRegistry));
    }

    /**
     * @return all plugin classes for the {@link StorageInterface}s.
     */
    private static Stream<Class<? extends StorageInterface>> allStorageClasses(final PluginRegistry pluginRegistry) {
        return pluginRegistry.plugins()
            .stream()
            .map(RegisteredPlugin::getStorages)
            .flatMap(List::stream);
    }

    /**
     * @return all plugin identifier for the {@link StorageInterface}s.
     */
    private static String allIdsFor(final Stream<Class<? extends StorageInterface>> classes) {
        return classes
            .map(Plugin::getId)
            .flatMap(Optional::stream)
            .collect(Collectors.joining(",", "[", "]"));
    }
}
