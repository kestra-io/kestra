package io.kestra.core.plugins;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static io.kestra.core.utils.Rethrow.throwRunnable;

@Slf4j
public class PluginResolver implements AutoCloseable {
    private final Path pluginPath;
    private final ExecutorService watcherThread = Executors.newSingleThreadExecutor();
    private WatchService watchService;

    /**
     * Creates a new {@link PluginResolver} instance.
     *
     * @param pluginPath the top-level plugin path.
     */
    public PluginResolver(final Path pluginPath) {
        Objects.requireNonNull(pluginPath, "pluginPath cannot be null");
        this.pluginPath = pluginPath;
    }

    private static boolean isArchiveFile(final Path path) {
        String lowerCased = path.toString().toLowerCase();

        return lowerCased.endsWith(".jar") || lowerCased.endsWith(".zip");
    }

    private static boolean isClassFile(final Path path) {
        return path.toString().toLowerCase().endsWith(".class");
    }

    /**
     * Watches the plugin path for new plugins and notifies the specified callbacks.
     *
     * @param onNewPlugin called with ExternalPlugin information when a new plugin is found
     * @param onDeletePlugin called with the previous plugin path when a plugin is deleted
     */
    public void watch(Consumer<ExternalPlugin> onNewPlugin, Consumer<String> onDeletePlugin) throws Exception {
        this.initialPlugins().forEach(onNewPlugin);
        watchService = FileSystems.getDefault().newWatchService();

        pluginPath.register(
            watchService,
            StandardWatchEventKinds.ENTRY_CREATE,
            StandardWatchEventKinds.ENTRY_MODIFY,
            StandardWatchEventKinds.ENTRY_DELETE
        );

        watcherThread.execute(throwRunnable(() -> {
            WatchKey key;
            try {
                while ((key = watchService.take()) != null) {
                    for (WatchEvent<?> event : key.pollEvents()) {
                        WatchEvent.Kind<?> kind = event.kind();
                        Path eventPath = pluginPath.resolve((Path) event.context());
                        if (kind == StandardWatchEventKinds.ENTRY_CREATE || kind == StandardWatchEventKinds.ENTRY_MODIFY) {
                            onNewPlugin.accept(toExternalPlugin(eventPath));
                        } else {
                            onDeletePlugin.accept(eventPath.toString());
                        }
                    }
                    key.reset();
                }
            } catch (ClosedWatchServiceException e) {
                // stop watch silently
            }
        }));
    }

    public List<ExternalPlugin> initialPlugins() {
        List<ExternalPlugin> plugins = new ArrayList<>();
        try (
            final DirectoryStream<Path> paths = Files.newDirectoryStream(
                pluginPath,
                entry -> Files.isDirectory(entry) || isArchiveFile(entry)
            )
        ) {
            for (Path path : paths) {
                plugins.add(toExternalPlugin(path));
            }
        } catch (final InvalidPathException | MalformedURLException e) {
            log.error("Invalid plugin path '{}', path ignored.", pluginPath, e);
        } catch (IOException e) {
            log.error("Error while listing plugin path '{}' path ignored.", pluginPath, e);
        }

        return plugins;
    }

    private static ExternalPlugin toExternalPlugin(Path path) throws IOException {
        final List<URL> resources = resolveUrlsForPluginPath(path);
        return new ExternalPlugin(
            path.toUri().toURL(),
            resources.toArray(new URL[0])
        );
    }

    /**
     * <p>
     * This method is inspired from the original class : org.apache.kafka.connect.runtime.isolation.PluginUtils.
     * from <a href="https://github.com/apache/kafka">Apache Kafka</a> project.
     * </p>
     *
     * @throws IOException if an error occurred while traversing the given path.
     */
    private static List<URL> resolveUrlsForPluginPath(final Path path) throws IOException {
        final List<Path> archives = new ArrayList<>();

        boolean containsClassFiles = false;
        if (isArchiveFile(path)) {
            archives.add(path);
        } else {
            LinkedList<Path> directories = new LinkedList<>();
            directories.add(path);

            while (!directories.isEmpty()) {
                final Path directory = directories.poll();
                try (
                    final DirectoryStream<Path> stream = Files.newDirectoryStream(
                        directory,
                        entry -> Files.isDirectory(entry) || isArchiveFile(entry) || isClassFile(entry)
                    )
                ) {
                    for (Path entry : stream) {
                        if (isArchiveFile(entry)) {
                            log.debug("Detected plugin jar: {}", entry);
                            archives.add(entry);
                        } else if (isClassFile(entry)) {
                            log.debug("Detected plugin class file: {}", entry);
                            containsClassFiles = true;
                        } else {
                            directories.add(entry);
                        }
                    }
                } catch (final InvalidPathException e) {
                    log.error("Invalid plugin path '{}', path ignored.", directory, e);
                } catch (IOException e) {
                    log.error("Error while listing plugin path '{}' path ignored.", directory, e);
                }
            }
        }

        if (containsClassFiles) {
            if (archives.isEmpty()) {
                return Collections.singletonList(path.toUri().toURL());
            }
            log.error("Plugin path '{}' contains both java class files and JARs, " +
                "class files will be ignored and only archives will be scanned.", path);
        }

        List<URL> urls = new ArrayList<>(archives.size());
        for (Path archive : archives) {
            urls.add(archive.toUri().toURL());
        }

        return urls;
    }

    @Override
    public void close() throws Exception {
        if (watchService != null) {
            watchService.close();
            watcherThread.shutdown();
            watcherThread.awaitTermination(1, TimeUnit.MINUTES);
        }
    }
}
