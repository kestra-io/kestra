package io.kestra.storage.local;

import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.storages.FileAttributes;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.storages.StorageObject;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.*;
import java.net.URI;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static io.kestra.core.utils.Rethrow.throwFunction;
import static io.kestra.core.utils.WindowsUtils.windowsToUnixPath;

@Plugin
@Plugin.Id("local")
@Getter
@Setter
@NoArgsConstructor
public class LocalStorage implements StorageInterface {
    private static final Logger log = LoggerFactory.getLogger(LocalStorage.class);

    @PluginProperty
    @NotNull
    private Path basePath;

    /** {@inheritDoc} **/
    @Override
    public void init() throws IOException {
        if (!Files.exists(this.basePath)) {
            Files.createDirectories(this.basePath);
        }
    }

    private Path getPath(String tenantId, URI uri) {
        Path basePath = tenantId == null ? this.basePath.toAbsolutePath()
            : Paths.get(this.basePath.toAbsolutePath().toString(), tenantId);
        if(uri == null) {
            return basePath;
        }

        parentTraversalGuard(uri);
        return Paths.get(basePath.toString(), windowsToUnixPath(uri.getPath()));
    }

    @Override
    public InputStream get(String tenantId, @Nullable String namespace, URI uri) throws IOException {
        return new BufferedInputStream(new FileInputStream(getPath(tenantId, uri)
            .toAbsolutePath()
            .toString())
        );
    }

    @Override
    public StorageObject getWithMetadata(String tenantId, @Nullable String namespace, URI uri) throws IOException {
        return new StorageObject(LocalFileAttributes.getMetadata(this.getPath(tenantId, uri)), this.get(tenantId, namespace, uri));
    }

    @Override
    public List<URI> allByPrefix(String tenantId, @Nullable String namespace, URI prefix, boolean includeDirectories) throws IOException {
        Path fsPath = getPath(tenantId, prefix);
        List<URI> uris = new ArrayList<>();
        Files.walkFileTree(fsPath, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                String dirPath = dir.toString().replace("\\", "/");
                if (includeDirectories) {
                    uris.add(URI.create(dirPath + "/"));
                }
                return super.preVisitDirectory(Path.of(dirPath), attrs);
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (!file.getFileName().toString().endsWith(".metadata")) {
                    uris.add(URI.create(file.toString().replace("\\", "/")));
                }
                return FileVisitResult.CONTINUE;
            }

            // This can happen for concurrent deletion while traversing folders so we skip in such case
            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                log.warn("Failed to visit file " + file + " while searching all by prefix for path " + prefix.getPath(), exc);
                return FileVisitResult.SKIP_SUBTREE;
            }
        });

        URI fsPathUri = URI.create(fsPath.toString().replace("\\", "/"));
        return uris.stream().sorted(Comparator.reverseOrder())
            .map(fsPathUri::relativize)
            .map(URI::getPath)
            .filter(Predicate.not(String::isEmpty))
            .map(path -> {
                String prefixPath = prefix.getPath();
                return URI.create("kestra://" + prefixPath + (prefixPath.endsWith("/") ? "" : "/") + path);
            })
            .toList();
    }

    @Override
    public boolean exists(String tenantId, @Nullable String namespace, URI uri) {
        return Files.exists(getPath(tenantId, uri));
    }

    @Override
    public List<FileAttributes> list(String tenantId, @Nullable String namespace, URI uri) throws IOException {
        try (Stream<Path> stream = Files.list(getPath(tenantId, uri))) {
            return stream
                .filter(path -> !path.getFileName().toString().endsWith(".metadata"))
                .map(throwFunction(file -> {
                    URI relative = URI.create(
                        getPath(tenantId, null).relativize(
                            Path.of(file.toUri())
                        ).toString().replace("\\", "/")
                    );
                    return getAttributes(tenantId, namespace, relative);
                }))
                .toList();
        } catch (NoSuchFileException e) {
            throw new FileNotFoundException(e.getMessage());
        }
    }

    @Override
    public URI put(String tenantId, @Nullable String namespace, URI uri, StorageObject storageObject) throws IOException {
        File file = getPath(tenantId, uri).toFile();
        File parent = file.getParentFile();
        if (!parent.exists()) {
            parent.mkdirs();
        }

        try (InputStream data = storageObject.inputStream(); OutputStream outStream = new FileOutputStream(file)) {
            byte[] buffer = new byte[8 * 1024];
            int bytesRead;
            while ((bytesRead = data.read(buffer)) != -1) {
                outStream.write(buffer, 0, bytesRead);
            }
        }

        Map<String, String> metadata = storageObject.metadata();
        if (metadata != null) {
            try (OutputStream outStream = new FileOutputStream(file.toPath() + ".metadata")) {
                outStream.write(JacksonMapper.ofIon().writeValueAsBytes(metadata));
            }
        }

        return URI.create("kestra://" + uri.getRawPath());
    }

    @Override
    public FileAttributes getAttributes(String tenantId, @Nullable String namespace, URI uri) throws IOException {
        Path path = getPath(tenantId, uri);
        try {
            return LocalFileAttributes.builder()
                .filePath(path)
                .basicFileAttributes(Files.readAttributes(path, BasicFileAttributes.class))
                .build();
        } catch (NoSuchFileException e) {
            throw new FileNotFoundException(e.getMessage());
        }
    }

    @Override
    public URI createDirectory(String tenantId, @Nullable String namespace, URI uri) {
        if (uri == null || uri.getPath().isEmpty()) {
            throw new IllegalArgumentException("Unable to create a directory with empty url.");
        }
        File file = getPath(tenantId, uri).toFile();
        if (!file.exists() && !file.mkdirs()) {
            throw new RuntimeException("Cannot create directory: " + file.getAbsolutePath());
        }
        return URI.create("kestra://" + uri.getPath());
    }

    @Override
    public URI move(String tenantId, @Nullable String namespace, URI from, URI to) throws IOException {
        try {
            Files.move(
                getPath(tenantId, from),
                getPath(tenantId, to),
                StandardCopyOption.ATOMIC_MOVE);
        } catch (NoSuchFileException e) {
            throw new FileNotFoundException(e.getMessage());
        }
        return URI.create("kestra://" + to.getPath());
    }

    @Override
    public boolean delete(String tenantId, @Nullable String namespace, URI uri) throws IOException {
        Path path = getPath(tenantId, uri);
        File file = path.toFile();

        if (file.isDirectory()) {
            FileUtils.deleteDirectory(file);
            return true;
        }

        return Files.deleteIfExists(path);
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Override
    public List<URI> deleteByPrefix(String tenantId, @Nullable String namespace, URI storagePrefix) throws IOException {
        Path path = this.getPath(tenantId, storagePrefix);

        if (!path.toFile().exists()) {
            return List.of();
        }

        try (Stream<Path> walk = Files.walk(path)) {
            return walk.sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .peek(File::delete)
                .map(r -> getKestraUri(tenantId, r.toPath()))
                .toList();
        }
    }

    private URI getKestraUri(String tenantId, Path path) {
        Path prefix = (tenantId == null) ?
            basePath.toAbsolutePath():
            basePath.toAbsolutePath().resolve(tenantId);
        subPathParentGuard(path, prefix);
        return URI.create("kestra:///" + prefix.relativize(path).toString().replace("\\", "/"));
    }

    private void subPathParentGuard(Path path, Path prefix) {
        if (!path.toAbsolutePath().startsWith(prefix)) {
            throw new IllegalArgumentException("The path must be a subpath of the base path with the tenant ID.");
        }
    }
}
