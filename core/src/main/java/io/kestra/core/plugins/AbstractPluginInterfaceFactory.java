package io.kestra.core.plugins;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.tuple.Pair;

import io.kestra.core.exceptions.KestraRuntimeException;
import io.kestra.core.models.Plugin;
import io.kestra.core.serializers.JacksonMapper;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;

/**
 * Base class for factories that construct a plugin instance selected by a {@code type} configuration
 * property and instantiated from a configuration map.
 * <p>
 * Centralizes the shared mechanism — parse a {@code <id>:<version>} identifier, look up the plugin
 * class by {@link io.kestra.core.models.annotations.Plugin.Id} across the {@link PluginRegistry}, deserialize the configuration via
 * {@link JacksonMapper}, and validate it — so it lives (and is fixed) in a single place. Concrete
 * factories provide the four seams below and add their own post-deserialization step (init,
 * decorators, …) in their own {@code make(...)} method.
 *
 * @param <T> the plugin contract returned by the factory (e.g. {@code StorageInterface}).
 */
public abstract class AbstractPluginInterfaceFactory<T> {

    protected final PluginRegistry pluginRegistry;
    protected final Validator validator;

    protected AbstractPluginInterfaceFactory(final PluginRegistry pluginRegistry,
        final Validator validator) {
        this.pluginRegistry = pluginRegistry;
        this.validator = validator;
    }

    /**
     * @return the configuration property that selects the plugin type, e.g. {@code kestra.storage.type}.
     */
    protected abstract String typeProperty();

    /**
     * @return the noun used in not-found / version error messages, e.g. {@code "storage interface"}.
     */
    protected abstract String lookupDisplayName();

    /**
     * @return the noun used in create / validate error messages, e.g. {@code "storage"}.
     */
    protected abstract String configDisplayName();

    /**
     * @return the candidate plugin classes registered for this contract within the given plugin,
     *         e.g. {@code plugin.getStorages()}.
     */
    protected abstract List<? extends Class<?>> pluginClasses(RegisteredPlugin plugin);

    /**
     * Finds, deserializes, and validates the plugin for the given identifier and configuration.
     * The returned instance is <b>not</b> initialized — concrete factories perform any init /
     * decoration in their own {@code make(...)}.
     *
     * @param identifier the plugin identifier, optionally in the form {@code <id>:<version>}.
     * @param pluginConfiguration the plugin configuration. May be {@code null}.
     * @return the validated, uninitialized plugin instance.
     * @throws KestraRuntimeException if no plugin can be found, or the configuration is invalid.
     */
    @SuppressWarnings("unchecked")
    protected T resolve(final String identifier, final Map<String, Object> pluginConfiguration) {
        // Passed 'identifier' can be in the form '<id>:<version>'.
        Pair<String, String> idAndVersion = PluginIdentifier.parseIdentifier(identifier);

        final String pluginId = idAndVersion.getLeft();
        final String pluginVersion = idAndVersion.getRight();

        Optional<TypeAndId> optional = allTypes()
            .filter(typeAndId -> Optional.ofNullable(typeAndId.id).map(id -> id.equalsIgnoreCase(pluginId)).orElse(false))
            .findFirst();

        if (optional.isEmpty()) {
            throw notFoundException(pluginId);
        }

        // Find the corresponding plugin 'class'.
        final String pluginType = optional.get().type();

        Class<? extends Plugin> pluginClass = pluginRegistry
            .findClassByIdentifier(pluginVersion == null ? pluginType : pluginType + ":" + pluginVersion);
        if (pluginClass == null) {
            List<String> supportedVersions = pluginRegistry.getAllVersionsForType(pluginType);
            throw new KestraRuntimeException(
                "No %s can be found for '%s=%s', and version=%s. Supported versions are: %s".formatted(
                    lookupDisplayName(), typeProperty(), pluginId, pluginVersion, supportedVersions
                )
            );
        }

        // Plugins are handled as any serializable/deserialize plugins.
        T plugin;
        try {
            // Make sure config is not null, otherwise deserialization result will be null too.
            Map<String, Object> nonEmptyConfig = Optional.ofNullable(pluginConfiguration).orElse(Map.of());
            plugin = (T) JacksonMapper.toMap(nonEmptyConfig, pluginClass);
        } catch (Exception e) {
            throw new KestraRuntimeException(
                String.format("Failed to create %s '%s'. Error: %s", configDisplayName(), pluginId, e.getMessage())
            );
        }

        // Validate configuration.
        Set<ConstraintViolation<T>> violations;
        try {
            violations = validator.validate(plugin);
        } catch (ConstraintViolationException e) {
            throw new KestraRuntimeException(
                String.format("Failed to validate configuration for %s '%s'. Error: %s", configDisplayName(), pluginId, e.getMessage())
            );
        }
        if (!violations.isEmpty()) {
            ConstraintViolationException e = new ConstraintViolationException(violations);
            throw new KestraRuntimeException(
                String.format("Invalid configuration for %s '%s'. Error: '%s'", configDisplayName(), pluginId, e.getMessage()), e
            );
        }

        return plugin;
    }

    /**
     * Builds the exception thrown when no plugin matches the requested id. Overridable so subclasses
     * can enrich the message (e.g. EE surfacing a license-gated type as "installed but not enabled").
     *
     * @param pluginId the requested (unmatched) plugin id.
     * @return the exception to throw.
     */
    protected KestraRuntimeException notFoundException(final String pluginId) {
        return new KestraRuntimeException(
            "No %s can be found for '%s=%s'. Supported types are: %s".formatted(
                lookupDisplayName(), typeProperty(), pluginId, getLoggableTypeIds()
            )
        );
    }

    /**
     * @return the comma-separated, bracket-wrapped list of supported plugin ids.
     */
    public String getLoggableTypeIds() {
        return allTypes()
            .map(TypeAndId::id)
            .filter(Objects::nonNull)
            .collect(Collectors.joining(",", "[", "]"));
    }

    private Stream<TypeAndId> allTypes() {
        return pluginRegistry.plugins()
            .stream()
            .flatMap(plugin -> pluginClasses(plugin).stream())
            .map(clazz -> new TypeAndId(clazz.getName(), Plugin.getId(clazz).orElse(null)));
    }

    private record TypeAndId(String type, String id) {
    }
}
