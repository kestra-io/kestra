package io.kestra.core.runners;

import io.micronaut.context.annotation.Value;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

@Singleton
public class LocalPathFactory {
    private final List<String> globalAllowedPaths;

    @Inject
    public LocalPathFactory(@Value("${kestra.plugins.allowed-paths:}") List<String> globalAllowedPaths) {
        this.globalAllowedPaths = globalAllowedPaths;
    }

    public LocalPath createLocalPath(RunContext runContext) {
        return new DefaultLocalPath(globalAllowedPaths, runContext);
    }

    static class DefaultLocalPath implements LocalPath {
        private final List<String> globalAllowedPaths;
        private final RunContext runContext;

        DefaultLocalPath(List<String> globalAllowedPaths, RunContext runContext) {
            this.globalAllowedPaths = globalAllowedPaths;
            this.runContext = runContext;
        }

        @Override
        @SuppressWarnings("unchecked")
        public InputStream get(URI uri) throws IOException {
            Path workingDirectory = runContext.workingDir().path(true);
            if (!uri.getScheme().equals(LocalPath.FILE_SCHEME)) {
                throw new IllegalArgumentException("The uri '" + uri + "' is not a valid file URI.");
            }

            Path path = Path.of(uri).toRealPath(); // toRealPath() will protect about path traversal issues
            // We allow working directory or globally allowed path
            if (!path.startsWith(workingDirectory) && globalAllowedPaths.stream().noneMatch(path::startsWith)) {
                // if not globally allowed, we check if it's allowed for this specific plugin
                List<String> pluginAllowedPaths = (List<String>) runContext.pluginConfiguration("allowed-paths").orElse(Collections.emptyList());
                if (pluginAllowedPaths.stream().noneMatch(path::startsWith)) {
                    throw new SecurityException("The path " + path + " is not authorized. " +
                        "Only files inside the working directory are allowed by default, other path must be allowed either globally inside the Kestra configuration using the `kestra.plugins.allowed-paths` property, " +
                        "or by plugin using the `allowed-paths` plugin configuration.");
                }
            }

            return new FileInputStream(path.toFile());
        }
    }
}
