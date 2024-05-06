package io.kestra.core.plugins;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.*;

@Slf4j
public class PluginResolver {
    private final Path pluginPath;

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

    public List<ExternalPlugin> resolves() {
        List<ExternalPlugin> plugins = new ArrayList<>(100);
        try (
            final DirectoryStream<Path> paths = Files.newDirectoryStream(
                pluginPath,
                entry -> Files.isDirectory(entry) || isArchiveFile(entry)
            )
        ) {
            for (Path path : paths) {
                final List<URL> resources = resolveUrlsForPluginPath(path);
                plugins.add(new ExternalPlugin(
                    path.toUri().toURL(),
                    resources.toArray(new URL[0])
                ));
            }
        } catch (final InvalidPathException | MalformedURLException e) {
            log.error("Invalid plugin path '{}', path ignored.", pluginPath, e);
        } catch (IOException e) {
            log.error("Error while listing plugin path '{}' path ignored.", pluginPath, e);
        }

        return plugins;
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
}
