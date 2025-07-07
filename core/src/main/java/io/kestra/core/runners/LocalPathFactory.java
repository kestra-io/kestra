package io.kestra.core.runners;

import io.micronaut.context.annotation.Value;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.List;

@Singleton
public class LocalPathFactory {
    private final List<String> globalAllowedPaths;

    @Inject
    public LocalPathFactory(@Value("${" + LocalPath.ALLOWED_PATHS_CONFIG + ":}") List<String> globalAllowedPaths) {
        this.globalAllowedPaths = globalAllowedPaths;
    }

    /**
     * Create a LocalPath based on a RunContext, this is the preferred way as it would be allowed to check
     * working directory and plugin configuration.
     * If no RunContext is available {@link #createLocalPath()} can be used instead but this LocalPath would only be able to check
     * paths globally allowed inside the Kestra configuration.
     */
    public LocalPath createLocalPath(RunContext runContext) {
        return new RunContextLocalPath(globalAllowedPaths, runContext);
    }

    /**
     * Create a LocalPath.
     * If a RunContext is available, this is preferable to use {@link #createLocalPath(RunContext)} as it would be possible to
     * check for paths inside the working directory or allowed inside the plugin configuration.
     */
    public LocalPath createLocalPath() {
        return new DefaultLocalPath(globalAllowedPaths);
    }

    abstract static class AbstractLocalPath implements LocalPath {
        @Override
        public InputStream get(URI uri) throws IOException {
            if (!LocalPath.FILE_SCHEME.equals(uri.getScheme())) {
                throw new IllegalArgumentException("The uri '" + uri + "' is not a valid file URI.");
            }

            Path path = checkPath(uri);
            return new FileInputStream(path.toFile());
        }

        @Override
        public boolean exists(URI uri) throws IOException {
            if (!LocalPath.FILE_SCHEME.equals(uri.getScheme())) {
                throw new IllegalArgumentException("The uri '" + uri + "' is not a valid file URI.");
            }

            Path path = checkPath(uri);
            return Files.exists(path);
        }

        @Override
        public BasicFileAttributes getAttributes(URI uri) throws IOException {
            if (!LocalPath.FILE_SCHEME.equals(uri.getScheme())) {
                throw new IllegalArgumentException("The uri '" + uri + "' is not a valid file URI.");
            }

            Path path = checkPath(uri);
            return Files.readAttributes(path, BasicFileAttributes.class);
        }

        /**
         * Check the URI then return it as a Path.
         * Based on the available context, implementors should:
         * - check if the file is inside the working directory
         * - check globally allowed paths
         * - check if plugin allowed paths
         */
        protected abstract Path checkPath(URI uri) throws IOException;
    }

    static class RunContextLocalPath extends AbstractLocalPath {
        private final List<String> globalAllowedPaths;
        private final RunContext runContext;

        RunContextLocalPath(List<String> globalAllowedPaths, RunContext runContext) {
            this.globalAllowedPaths = globalAllowedPaths;
            this.runContext = runContext;
        }

        @Override
        @SuppressWarnings("unchecked")
        protected Path checkPath(URI uri) throws IOException {
            Path workingDirectory = runContext.workingDir().path(true);
            Path path = Path.of(uri).toRealPath(); // toRealPath() will protect about path traversal issues
            // We allow working directory or globally allowed path
            if (!path.startsWith(workingDirectory) && globalAllowedPaths.stream().noneMatch(path::startsWith)) {
                // if not globally allowed, we check if it's allowed for this specific plugin
                List<String> pluginAllowedPaths = (List<String>) runContext.pluginConfiguration("allowed-paths").orElse(Collections.emptyList());
                if (pluginAllowedPaths.stream().noneMatch(path::startsWith)) {
                    throw new SecurityException("The path " + path + " is not authorized. " +
                        "Only files inside the working directory are allowed by default, other path must be allowed either globally inside the Kestra configuration using the `" + LocalPath.ALLOWED_PATHS_CONFIG + "` property, " +
                        "or by plugin using the `allowed-paths` plugin configuration.");
                }
            }

            return path;
        }
    }

    static class DefaultLocalPath extends AbstractLocalPath {
        private final List<String> globalAllowedPaths;

        DefaultLocalPath(List<String> globalAllowedPaths) {
            this.globalAllowedPaths = globalAllowedPaths;
        }

        @Override
        protected Path checkPath(URI uri) throws IOException {
            Path path = Path.of(uri).toRealPath(); // toRealPath() will protect about path traversal issues
            // we only allow globally allowed as we don't have a run context to get the working directory nor the plugin configuration
            if (globalAllowedPaths.stream().noneMatch(path::startsWith)) {
                throw new SecurityException("The path " + path + " is not authorized. " +
                    "Path must be allowed either globally inside the Kestra configuration using the `" + LocalPath.ALLOWED_PATHS_CONFIG + "` property.");
            }

            return path;
        }
    }
}
