package io.kestra.core.storages;

import io.kestra.core.exceptions.KestraRuntimeException;
import io.kestra.core.models.Plugin;
import io.kestra.core.plugins.PluginIdentifier;
import io.kestra.core.plugins.PluginRegistry;
import io.kestra.core.plugins.RegisteredPlugin;
import io.kestra.core.serializers.JacksonMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import org.apache.commons.lang3.tuple.Pair;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
     * @param identifier            The ID of the storage. cannot be {@code null}.
     * @param pluginConfiguration The configuration of the storage. cannot be {@code null}.
     * @return a new {@link StorageInterface}.
     * @throws KestraRuntimeException if no storage can be found.
     */
    public StorageInterface make(final StorageConfiguration storageConfiguration,
                                 final String identifier,
                                 final Map<String, Object> pluginConfiguration) {

        // Passed 'identifier' can be in the form '<id>:<version>'.
        Pair<String, String> idAndVersion = PluginIdentifier.parseIdentifier(identifier);

        final String pluginId = idAndVersion.getLeft();
        final String pluginVersion = idAndVersion.getRight();

        Optional<TypeAndId> optional = allStorageClasses(pluginRegistry)
            .filter(typeAndId -> Optional.ofNullable(typeAndId.id).map(id -> id.equalsIgnoreCase(pluginId)).orElse(false))
            .findFirst();

        if (optional.isEmpty()) {
            String storageIds = getLoggableStorageIds();
            throw new KestraRuntimeException(
                "No storage interface can be found for '%s=%s'. Supported types are: %s".formatted(KESTRA_STORAGE_TYPE_CONFIG, pluginId, storageIds
            ));
        }

        // Find the corresponding plugin 'class'
        final String pluginType = optional.get().type();

        @SuppressWarnings("unchecked")
        Class<? extends StorageInterface> pluginClass = (Class<? extends StorageInterface>) pluginRegistry.findClassByIdentifier(pluginVersion == null ? pluginType : pluginType + ":" + pluginVersion);
        if (pluginClass == null) {
            List<String> supportedVersions = pluginRegistry.getAllVersionsForType(pluginType);
            throw new KestraRuntimeException(
                "No storage interface can be found for '%s=%s', and version=%s. Supported versions are: %s".formatted(KESTRA_STORAGE_TYPE_CONFIG, idAndVersion.getLeft(), pluginVersion, supportedVersions)
            );
        }

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
    private static Stream<TypeAndId> allStorageClasses(final PluginRegistry pluginRegistry) {
        return pluginRegistry.plugins()
            .stream()
            .map(RegisteredPlugin::getStorages)
            .flatMap(List::stream)
            .map(clazz -> new TypeAndId(clazz.getName(), Plugin.getId(clazz).orElse(null)));
    }

    /**
     * @return all plugin identifier for the {@link StorageInterface}s.
     */
    private static String allIdsFor(final Stream<TypeAndId> classes) {
        return classes
            .map(TypeAndId::id)
            .filter(Objects::nonNull)
            .collect(Collectors.joining(",", "[", "]"));
    }

    private record TypeAndId(String type, String id) {}
}
