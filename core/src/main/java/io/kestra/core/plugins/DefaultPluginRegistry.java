package io.kestra.core.plugins;

import io.kestra.core.models.Plugin;
import io.kestra.core.models.assets.Asset;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static java.util.Objects.requireNonNull;

/**
 * Registry for managing all Kestra's {@link Plugin}.
 *
 * @see io.kestra.core.plugins.serdes.PluginDeserializer
 * @see PluginScanner
 */
public class DefaultPluginRegistry implements PluginRegistry {

    private static final Logger log = LoggerFactory.getLogger(DefaultPluginRegistry.class);

    private static class LazyHolder {
        static final DefaultPluginRegistry INSTANCE = new DefaultPluginRegistry();
    }

    protected final Map<PluginIdentifier, PluginClassAndMetadata<? extends Plugin>> pluginClassByIdentifier = new ConcurrentHashMap<>();
    private final Map<PluginBundleIdentifier, RegisteredPlugin> plugins = new ConcurrentHashMap<>();
    private final PluginScanner scanner = new PluginScanner(DefaultPluginRegistry.class.getClassLoader());
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final Set<Path> scannedPluginPaths = new HashSet<>();

    private final ReentrantLock lock = new ReentrantLock();

    /**
     * Gets or instantiates a {@link DefaultPluginRegistry} and register it as singleton object.
     *
     * @return the {@link DefaultPluginRegistry}.
     */
    public synchronized static DefaultPluginRegistry getOrCreate() {
        DefaultPluginRegistry instance = LazyHolder.INSTANCE;
        if (!instance.isInitialized()) {
            instance.init();
        }
        return instance;
    }

    protected DefaultPluginRegistry() {
    }

    private boolean isInitialized() {
        return initialized.get();
    }

    /**
     * Initializes the registry by loading all core plugins.
     */
    protected synchronized void init() {
        if (initialized.compareAndSet(false, true)) {
            register(scanner.scan());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getAllVersionsForType(final String type) {
        return plugins.values()
            .stream().filter(
                registered -> registered.allClass()
                    .stream()
                    .map(Class::getName)
                    .anyMatch(cls -> cls.equalsIgnoreCase(type))
            ).findFirst()
            .map(RegisteredPlugin::version)
            .filter(Objects::nonNull)
            .map(List::of)
            .orElse(List.of());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void registerIfAbsent(final Path pluginPath) {
        long start = System.currentTimeMillis();
        if (isPluginPathValid(pluginPath) && !isPluginPathScanned(pluginPath)) {
            List<RegisteredPlugin> scanned = scanner.scan(pluginPath);
            scanned.forEach(this::register);
            scannedPluginPaths.add(pluginPath);
        }
        log.debug("Registered if absent plugins from path {} in {} ms", pluginPath, System.currentTimeMillis() - start);
    }

    private boolean isPluginPathScanned(final Path pluginPath) {
        return scannedPluginPaths.contains(pluginPath);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void register(final Path pluginPath) {
        long start = System.currentTimeMillis();
        if (isPluginPathValid(pluginPath)) {
            List<RegisteredPlugin> scanned = scanner.scan(pluginPath);
            scanned.forEach(this::register);
        }
        log.debug("Registered plugins from path {} in {} ms", pluginPath, System.currentTimeMillis() - start);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void unregister(final List<RegisteredPlugin> pluginsToUnregister) {
        if (pluginsToUnregister == null || pluginsToUnregister.isEmpty()) {
            return;
        }

        var mutablePluginsToUnregister = new ArrayList<>(pluginsToUnregister);

        lock.lock();
        try {
            ListIterator<RegisteredPlugin> iter = mutablePluginsToUnregister.listIterator();
            while (iter.hasNext()) {
                final RegisteredPlugin current = iter.next();
                final PluginBundleIdentifier identifier = PluginBundleIdentifier.of(current);

                if (identifier.equals(PluginBundleIdentifier.CORE)) {
                    continue; // Skip the core plugin
                }

                // Remove the plugin from the registry
                this.plugins.remove(identifier);

                // Remove all classes to this plugin from the registry
                this.pluginClassByIdentifier.entrySet().removeIf(entry -> {
                    PluginClassAndMetadata metadata = entry.getValue();
                    return metadata.type().getClassLoader().equals(current.getClassLoader());
                });

                // Close ClassLoader resources if applicable
                if (current.getClassLoader() instanceof Closeable closeable) {
                    try {
                        closeable.close();
                    } catch (IOException e) {
                        log.warn("Unexpected error while closing ClassLoader for plugins under {}", identifier.location(), e);
                    }
                }
                // Remove the plugin from the input list
                iter.remove();
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void registerClassForIdentifier(PluginIdentifier identifier, PluginClassAndMetadata<? extends Plugin> plugin) {
        this.pluginClassByIdentifier.put(identifier, plugin);
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
        final PluginBundleIdentifier identifier = PluginBundleIdentifier.of(plugin);
        // Skip registration if the same plugin already exists in the registry.
        final RegisteredPlugin existing = plugins.get(identifier);
        if (existing != null && existing.crc32() == plugin.crc32()) {
            return; // same plugin already registered
        }

        lock.lock();
        try {
            if (existing != null) {
                unregister(List.of(existing));
            }
            plugins.put(PluginBundleIdentifier.of(plugin), plugin);
            registerAll(getPluginClassesByIdentifier(plugin));
        } finally {
            lock.unlock();
        }
    }

    protected void registerAll(Map<PluginIdentifier, PluginClassAndMetadata<? extends Plugin>> plugins) {
        pluginClassByIdentifier.putAll(plugins);
    }

    @SuppressWarnings("unchecked")
    protected Map<PluginIdentifier, PluginClassAndMetadata<? extends Plugin>> getPluginClassesByIdentifier(final RegisteredPlugin plugin) {
        Map<PluginIdentifier, PluginClassAndMetadata<? extends Plugin>> classes = new HashMap<>();
        classes.putAll(plugin.allClass()
            .stream()
            .map(cls -> {

                Class<? extends Plugin> pluginClass = (Class<? extends Plugin>) cls;
                Class<Plugin> pluginBaseClass = plugin.baseClass(pluginClass.getName());

                return new SimpleEntry<>(
                    ClassTypeIdentifier.create(cls.getName()),
                    PluginClassAndMetadata.create(plugin, pluginClass, pluginBaseClass, null)
                );
            })
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));

        classes.putAll(plugin.getAliases().values().stream().map(e -> {
                Class<? extends Plugin> pluginClass = (Class<? extends Plugin>) e.getValue();
                Class<Plugin> pluginBaseClass = plugin.baseClass(pluginClass.getName());

                return new SimpleEntry<>(
                    ClassTypeIdentifier.create(e.getKey()),
                    PluginClassAndMetadata.create(plugin, pluginClass, pluginBaseClass, e.getKey())
                );
            })
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
        return classes;
    }

    private boolean containsPluginBundle(PluginBundleIdentifier identifier) {
        return plugins.containsKey(identifier);
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    public List<RegisteredPlugin> plugins() {
        return plugins(null);
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    public List<RegisteredPlugin> externalPlugins() {
        return plugins(plugin -> plugin.getExternalPlugin() != null);
    }

    /**
     * {@inheritDoc}
     **/
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
        requireNonNull(identifier, "Cannot found plugin for null identifier");
        lock.lock();
        try {
            return findMetadataByIdentifier(identifier).map(PluginClassAndMetadata::type).orElse(null);
        } finally {
            lock.unlock();
        }
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    public Class<? extends Plugin> findClassByIdentifier(final String identifier) {
        requireNonNull(identifier, "Cannot found plugin for null identifier");
        lock.lock();
        try {
            return findClassByIdentifier(ClassTypeIdentifier.create(identifier));
        } finally {
            lock.unlock();
        }
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    public Optional<PluginClassAndMetadata<? extends Plugin>> findMetadataByIdentifier(final String identifier) {
        return findMetadataByIdentifier(ClassTypeIdentifier.create(identifier));
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    public Optional<PluginClassAndMetadata<? extends Plugin>> findMetadataByIdentifier(final PluginIdentifier identifier) {
        requireNonNull(identifier, "Cannot found plugin for null identifier");
        lock.lock();
        try {
            return Optional.ofNullable(pluginClassByIdentifier.get(identifier));
        } finally {
            lock.unlock();
        }
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    public void clear() {
        pluginClassByIdentifier.clear();
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    public boolean isVersioningSupported() {
        return false;
    }

    public record PluginBundleIdentifier(@Nullable URL location) {

        public static PluginBundleIdentifier CORE = new PluginBundleIdentifier(null);

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
