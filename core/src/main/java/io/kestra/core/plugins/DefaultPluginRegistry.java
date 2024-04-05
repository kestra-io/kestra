package io.kestra.core.plugins;

import io.kestra.core.models.Plugin;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

/**
 * Registry for managing all Kestra's {@link Plugin}.
 *
 * @see io.kestra.core.plugins.serdes.PluginDeserializer
 * @see PluginScanner
 */
public final class DefaultPluginRegistry implements PluginRegistry {

    private static class LazyHolder {
        static final DefaultPluginRegistry INSTANCE = new DefaultPluginRegistry();
    }

    private final Map<PluginIdentifier, Class<? extends Plugin>> pluginClassByIdentifier = new ConcurrentHashMap<>();
    private final Map<PluginBundleIdentifier, RegisteredPlugin> plugins = new ConcurrentHashMap<>();
    private final PluginScanner scanner = new PluginScanner(DefaultPluginRegistry.class.getClassLoader());
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final Set<Path> scannedPluginPaths = new HashSet<>();

    /**
     * Gets or instantiates a {@link DefaultPluginRegistry} and register it as singleton object.
     *
     * @return the {@link DefaultPluginRegistry}.
     */
    public static DefaultPluginRegistry getOrCreate() {
        DefaultPluginRegistry instance = LazyHolder.INSTANCE;
        if (!instance.isInitialized()) {
            instance.init();
        }
        return instance;
    }

    private DefaultPluginRegistry() {
    }

    private boolean isInitialized() {
        return initialized.get();
    }

    /**
     * Initializes the registry by loading all core plugins.
     */
    private void init() {
        if (initialized.compareAndSet(false, true)) {
            register(scanner.scan());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void registerIfAbsent(final Path pluginPath) {
        if (isPluginPathValid(pluginPath) && !isPluginPathScanned(pluginPath)) {
            List<RegisteredPlugin> scanned = scanner.scan(pluginPath);
            scanned.forEach(this::register);
            scannedPluginPaths.add(pluginPath);
        }
    }

    private boolean isPluginPathScanned(final Path pluginPath) {
        return scannedPluginPaths.contains(pluginPath);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void register(final Path pluginPath) {
        if (isPluginPathValid(pluginPath)) {
            List<RegisteredPlugin> scanned = scanner.scan(pluginPath);
            scanned.forEach(this::register);
        }
    }

    private static boolean isPluginPathValid(final Path pluginPath) {
        return pluginPath != null && pluginPath.toFile().exists();
    }

    /**
     * Registers a plugin.
     *
     * @param plugin the plugin to be registered.
     */
    public void register(final RegisteredPlugin plugin) {
        if (containsPluginBundle(PluginBundleIdentifier.of(plugin))) {
            unregister(plugin);
        }
        plugins.put(PluginBundleIdentifier.of(plugin), plugin);
        plugin.allClass().forEach(clazz -> {
            @SuppressWarnings("unchecked")
            Class<? extends Plugin> pluginClass = (Class<? extends Plugin>) clazz;
            pluginClassByIdentifier.put(ClassTypeIdentifier.create(clazz), pluginClass);
        });
        plugin.getAliases().forEach((alias, clazz) -> {
            @SuppressWarnings("unchecked")
            Class<? extends Plugin> pluginClass = (Class<? extends Plugin>) clazz;
            pluginClassByIdentifier.put(ClassTypeIdentifier.create(alias), pluginClass);
        });
    }

    private boolean containsPluginBundle(PluginBundleIdentifier identifier) {
        return plugins.containsKey(identifier);
    }

    /**
     * Unregisters a given plugin.
     *
     * @param plugin the plugin to be registered.
     */
    public void unregister(final RegisteredPlugin plugin) {
        if (plugins.remove(PluginBundleIdentifier.of(plugin)) != null) {
            plugin.allClass().forEach(clazz -> {
                pluginClassByIdentifier.remove(ClassTypeIdentifier.create(clazz));
            });
        }
    }


    /** {@inheritDoc} **/
    @Override
    public List<RegisteredPlugin> plugins() {
        return plugins(null);
    }

    /** {@inheritDoc} **/
    @Override
    public List<RegisteredPlugin> externalPlugins() {
        return plugins(plugin -> plugin.getExternalPlugin() != null);
    }


    /** {@inheritDoc} **/
    @Override
    public List<RegisteredPlugin> plugins(final Predicate<RegisteredPlugin> predicate) {
        if (predicate == null) {
            return new ArrayList<>(plugins.values());
        }

        return plugins.values()
            .stream()
            .filter(predicate)
            .toList();
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    public Class<? extends Plugin> findClassByIdentifier(final PluginIdentifier identifier) {
        Objects.requireNonNull(identifier, "Cannot found plugin for null identifier");
        return pluginClassByIdentifier.get(identifier);
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    public Class<? extends Plugin> findClassByIdentifier(final String identifier) {
        Objects.requireNonNull(identifier, "Cannot found plugin for null identifier");
        return findClassByIdentifier(ClassTypeIdentifier.create(identifier));
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    public void clear() {
        pluginClassByIdentifier.clear();
    }

    private record PluginBundleIdentifier(@Nullable URL location) {

        public static PluginBundleIdentifier CORE = new PluginBundleIdentifier(null);

        public static Optional<PluginBundleIdentifier> of(final Path path) {
            try {
                return Optional.of(new PluginBundleIdentifier(path.toUri().toURL()));
            } catch (MalformedURLException e) {
                return Optional.empty();
            }
        }

        public static PluginBundleIdentifier of(final RegisteredPlugin plugin) {
            return Optional.ofNullable(plugin.getExternalPlugin())
                .map(ExternalPlugin::getLocation)
                .map(PluginBundleIdentifier::new)
                .orElse(CORE); // core plugin has no location
        }
    }

    /**
     * Represents a simple identifier based a canonical class name.
     *
     * @param type the type of the plugin.
     */
    public record ClassTypeIdentifier(@NotNull String type) implements PluginIdentifier {

        public static ClassTypeIdentifier create(final Class<?> identifier) {
            return create(identifier.getName());
        }

        public static ClassTypeIdentifier create(final String identifier) {
            if (identifier == null || identifier.isBlank()) {
                throw new IllegalArgumentException("Cannot create plugin identifier from null or empty string");
            }
            return new ClassTypeIdentifier(identifier);
        }

        /**
         * {@inheritDoc}
         **/
        @Override
        public String toString() {
            return "Plugin@[type=" + type + "]";
        }
    }
}
