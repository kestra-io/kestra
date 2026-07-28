package io.kestra.storage.local;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Stream;

import io.kestra.core.exceptions.KestraRuntimeException;
import org.apache.commons.io.FileUtils;

import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.storages.FileAttributes;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.storages.StorageObject;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import static io.kestra.core.utils.FileUtils.isParentTraversal;
import static io.kestra.core.utils.Rethrow.throwFunction;
import static io.kestra.core.utils.WindowsUtils.windowsToUnixPath;

@Plugin
@Plugin.Id("local")
@Getter
@Setter
@Slf4j
@NoArgsConstructor
public class LocalStorage implements StorageInterface {
    private static final int MAX_OBJECT_NAME_LENGTH = 255;

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

    protected Path getLocalPath(String tenantId, URI uri) {
        Path basePath = Paths.get(this.basePath.toAbsolutePath().toString(), tenantId);
        return getPath(uri, basePath);
    }

    protected Path getInstancePath(URI uri) {
        Path basePath = this.basePath.toAbsolutePath();
        return getPath(uri, basePath);
    }

    protected Path getPath(URI uri, Path basePath) {
        if (uri == null) {
            return basePath;
        }

        // Canonicalize Windows-style separators *before* validating. Otherwise a backslash
        // payload ("..\..\\") slips past the traversal guard and is only rewritten to "../../"
        // afterwards, escaping the storage directory (GHSA-qw4v-6w32-xx9h).
        String relativePath = windowsToUnixPath(uri.getPath());
        if (isParentTraversal(relativePath)) {
            throw new IllegalArgumentException("File should be accessed with their full path and not using relative '..' path.");
        }

        Path resolved = Paths.get(basePath.toString(), relativePath).normalize();

        // Defense in depth: the resolved path must never escape the storage base directory.
        if (!resolved.startsWith(basePath.toAbsolutePath().normalize())) {
            throw new IllegalArgumentException("File should be accessed with their full path and not using relative '..' path.");
        }

        return resolved;
    }

    @Override
    public InputStream get(String tenantId, @Nullable String namespace, URI uri) throws IOException {
        return new BufferedInputStream(new FileInputStream(getLocalPath(tenantId, uri).toAbsolutePath().toString()));
    }

    @Override
    public InputStream getInstanceResource(@Nullable String namespace, URI uri) throws IOException {
        return new BufferedInputStream(new FileInputStream(getInstancePath(uri).toAbsolutePath().toString()));
    }

    @Override
    public StorageObject getWithMetadata(String tenantId, @Nullable String namespace, URI uri) throws IOException {
        return new StorageObject(LocalFileAttributes.getMetadata(this.getLocalPath(tenantId, uri)), this.get(tenantId, namespace, uri));
    }

    @Override
    public List<URI> allByPrefix(String tenantId, @Nullable String namespace, URI prefix, boolean includeDirectories) throws IOException {
        Path fsPath = getLocalPath(tenantId, prefix);
        List<URI> uris = new ArrayList<>();

        if (!Files.exists(fsPath)) {
            return List.of();
        }

        Files.walkFileTree(fsPath, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                String dirPath = dir.toString().replace("\\", "/");
                if (includeDirectories) {
                    uris.add(pathUri(dirPath + "/"));
                }
                return super.preVisitDirectory(Path.of(dirPath), attrs);
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (!file.getFileName().toString().endsWith(".metadata")) {
                    uris.add(pathUri(file.toString().replace("\\", "/")));
                }
                return FileVisitResult.CONTINUE;
            }

            // This can happen for concurrent deletion while traversing folders so we skip in such case
            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                log.warn("Failed to visit file {} while searching all by prefix for path {}", file, prefix.getPath(), exc);
                return FileVisitResult.SKIP_SUBTREE;
            }
        });

        URI fsPathUri = pathUri(fsPath.toString().replace("\\", "/"));
        return uris.stream().sorted(Comparator.reverseOrder())
            .map(fsPathUri::relativize)
            .map(URI::getPath)
            .filter(Predicate.not(String::isEmpty))
            .map(path ->
            {
                String prefixPath = prefix.getPath();
                return kestraUri(prefixPath + (prefixPath.endsWith("/") ? "" : "/") + path);
            })
            .toList();
    }

    @Override
    public boolean exists(String tenantId, @Nullable String namespace, URI uri) {
        return Files.exists(getLocalPath(tenantId, uri));
    }

    @Override
    public List<FileAttributes> list(String tenantId, @Nullable String namespace, URI uri) throws IOException {
        try (Stream<Path> stream = Files.list(getLocalPath(tenantId, uri))) {
            return stream
                .filter(path -> !path.getFileName().toString().endsWith(".metadata"))
                .map(throwFunction(file ->
                {
                    URI relative = pathUri(
                        getLocalPath(tenantId, null).relativize(
                            Path.of(file.toUri())
                        ).toString().replace("\\", "/")
                    );
                    return getAttributes(tenantId, namespace, relative);
                }))
                .toList();
        } catch (NoSuchFileException e) {
            throw newFileNotFound(uri, e);

        }
    }

    /**
     * To prevent information disclosure, we don't include the cause exception message in the thrown exception.
     * We log the original cause in DEBUG for analysis.
     */
    private static IOException newFileNotFound(URI uri, IOException cause) {
        log.debug("No such file {}", uri, cause);
        return new FileNotFoundException("File not found for URI: " + uri.toString());
    }

    @Override
    public List<FileAttributes> listInstanceResource(@Nullable String namespace, URI uri) throws IOException {
        try (Stream<Path> stream = Files.list(getInstancePath(uri))) {
            return stream
                .filter(path -> !path.getFileName().toString().endsWith(".metadata"))
                .map(throwFunction(file ->
                {
                    URI relative = pathUri(
                        getInstancePath(null).relativize(
                            Path.of(file.toUri())
                        ).toString().replace("\\", "/")
                    );
                    return getInstanceAttributes(namespace, relative);
                }))
                .toList();
        } catch (NoSuchFileException e) {
            throw newFileNotFound(uri, e);
        }
    }

    @Override
    public URI put(String tenantId, @Nullable String namespace, URI uri, StorageObject storageObject) throws IOException {
        URI limited = limit(uri, MAX_OBJECT_NAME_LENGTH);
        File file = getLocalPath(tenantId, limited).toFile();
        return putFile(limited, storageObject, file);
    }

    @Override
    public URI putInstanceResource(@Nullable String namespace, URI uri, StorageObject storageObject) throws IOException {
        URI limited = limit(uri, MAX_OBJECT_NAME_LENGTH);
        File file = getInstancePath(limited).toFile();
        return putFile(limited, storageObject, file);
    }

    private static URI putFile(URI uri, StorageObject storageObject, File file) throws IOException {
        // Files.createDirectories throws a descriptive exception (e.g. AccessDeniedException,
        // FileAlreadyExistsException) when the parent hierarchy cannot be created, unlike
        // File#mkdirs whose boolean result was previously ignored and let the subsequent
        // FileOutputStream fail with a misleading FileNotFoundException.
        Files.createDirectories(file.toPath().getParent());

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

        return kestraUri(uri.getPath());
    }

    @Override
    public FileAttributes getAttributes(String tenantId, @Nullable String namespace, URI uri) throws IOException {
        try {
            return getAttributeFromPath(getLocalPath(tenantId, uri));
        } catch (NoSuchFileException e) {
            throw newFileNotFound(uri, e);
        }
    }

    @Override
    public FileAttributes getInstanceAttributes(@Nullable String namespace, URI uri) throws IOException {
        try {
            return getAttributeFromPath(getInstancePath(uri));
        } catch (NoSuchFileException e) {
            throw newFileNotFound(uri, e);
        }
    }

    private static LocalFileAttributes getAttributeFromPath(Path path) throws IOException {
        return LocalFileAttributes.builder()
            .filePath(path)
            .basicFileAttributes(Files.readAttributes(path, BasicFileAttributes.class))
            .build();
    }

    @Override
    public URI createDirectory(String tenantId, @Nullable String namespace, URI uri) {
        return createDirectoryFromPath(getLocalPath(tenantId, uri), uri);
    }

    @Override
    public URI createInstanceDirectory(String namespace, URI uri) {
        return createDirectoryFromPath(getInstancePath(uri), uri);
    }

    private static URI createDirectoryFromPath(Path path, URI uri) {
        if (uri == null || uri.getPath().isEmpty()) {
            throw new IllegalArgumentException("Unable to create a directory with empty url.");
        }
        File file = path.toFile();
        if (!file.exists() && !file.mkdirs()) {
            throw new KestraRuntimeException("Cannot create directory for URI: " + uri);
        }
        return kestraUri(uri.getPath());
    }

    @Override
    public URI move(String tenantId, @Nullable String namespace, URI from, URI to) throws IOException {
        try {
            Files.move(
                getLocalPath(tenantId, from),
                getLocalPath(tenantId, to),
                StandardCopyOption.ATOMIC_MOVE
            );
        } catch (NoSuchFileException e) {
            throw newFileNotFound(from, e);
        }
        return kestraUri(to.getPath());
    }

    @Override
    public boolean delete(String tenantId, @Nullable String namespace, URI uri) throws IOException {
        return deleteFromPath(getLocalPath(tenantId, uri));
    }

    @Override
    public boolean deleteInstanceResource(@Nullable String namespace, URI uri) throws IOException {
        return deleteFromPath(getInstancePath(uri));
    }

    private static boolean deleteFromPath(Path path) throws IOException {
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
        Path path = this.getLocalPath(tenantId, storagePrefix);

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
        Path prefix = basePath.toAbsolutePath().resolve(tenantId);
        subPathParentGuard(path, prefix);
        return kestraUri("/" + prefix.relativize(path).toString().replace("\\", "/"));
    }

    private void subPathParentGuard(Path path, Path prefix) {
        if (!path.toAbsolutePath().startsWith(prefix)) {
            throw new IllegalArgumentException("The path must be a subpath of the base path with the tenant ID.");
        }
    }

    private static URI pathUri(String path) {
        try {
            return new URI(null, null, windowsToUnixPath(path), null);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid storage path: " + path, e);
        }
    }

    private static URI kestraUri(String path) {
        try {
            return new URI("kestra", "", windowsToUnixPath(path), null, null);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid Kestra storage path: " + path, e);
        }
    }
}
