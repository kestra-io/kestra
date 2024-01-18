package io.kestra.storage.local;

import io.kestra.core.storages.FileAttributes;
import io.kestra.core.storages.StorageInterface;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.apache.commons.io.FileUtils;

import java.io.*;
import java.net.URI;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static io.kestra.core.utils.Rethrow.throwFunction;

@Singleton
@LocalStorageEnabled
public class LocalStorage implements StorageInterface {
    LocalConfig config;

    @Inject
    public LocalStorage(LocalConfig config) throws IOException {
        this.config = config;

        if (!Files.exists(config.getBasePath())) {
            Files.createDirectories(config.getBasePath());
        }
    }

    private Path getPath(String tenantId, URI uri) {
        Path basePath = tenantId == null ? config.getBasePath().toAbsolutePath()
            : Paths.get(config.getBasePath().toAbsolutePath().toString(), tenantId);
        if(uri == null) {
            return basePath;
        }

        parentTraversalGuard(uri);
        return Paths.get(basePath.toString(), uri.getPath());
    }

    @Override
    public InputStream get(String tenantId, URI uri) throws IOException {
        return new BufferedInputStream(new FileInputStream(getPath(tenantId, uri)
            .toAbsolutePath()
            .toString())
        );
    }

    @Override
    public List<URI> allByPrefix(String tenantId, URI prefix, boolean includeDirectories) throws IOException {
        Path fsPath = getPath(tenantId, prefix);
        try (Stream<Path> walk = Files.walk(fsPath)) {
            return walk.sorted(Comparator.reverseOrder())
                .filter(path -> includeDirectories || !Files.isDirectory(path))
                .map(path -> {
                    Path relativePath = fsPath.relativize(path);
                    return relativePath + (Files.isDirectory(path) && !relativePath.toString().isEmpty() ? "/" : "");
                })
                .filter(Predicate.not(String::isEmpty))
                .map(path -> {
                    String prefixPath = prefix.getPath();
                    return URI.create("kestra://" + prefixPath + (prefixPath.endsWith("/") ? "" : "/") + path);
                })
                .toList();
        } catch (NoSuchFileException e) {
            return Collections.emptyList();
        }
    }

    @Override
    public boolean exists(String tenantId, URI uri) {
        return Files.exists(getPath(tenantId, uri));
    }

    @Override
    public List<FileAttributes> list(String tenantId, URI uri) throws IOException {
        try (Stream<Path> stream = Files.list(getPath(tenantId, uri))) {
            return stream
                .map(throwFunction(file -> {
                    URI relative = URI.create(
                        getPath(tenantId, null).relativize(
                            Path.of(file.toUri())
                        ).toString()
                    );
                    return getAttributes(tenantId, relative);
                }))
                .toList();
        } catch (NoSuchFileException e) {
            throw new FileNotFoundException(e.getMessage());
        }
    }

    @Override
    public URI put(String tenantId, URI uri, InputStream data) throws IOException {
        File file = getPath(tenantId, uri).toFile();
        File parent = file.getParentFile();
        if (!parent.exists() && !parent.mkdirs()) {
            throw new RuntimeException("Cannot create directory: " + parent.getAbsolutePath());
        }

        try (data; OutputStream outStream = new FileOutputStream(file)) {
            byte[] buffer = new byte[8 * 1024];
            int bytesRead;
            while ((bytesRead = data.read(buffer)) != -1) {
                outStream.write(buffer, 0, bytesRead);
            }
        }
        return URI.create("kestra://" + uri.getPath());
    }

    @Override
    public FileAttributes getAttributes(String tenantId, URI uri) throws IOException {
        Path path = getPath(tenantId, uri);
        try {
            return LocalFileAttributes.builder()
                .fileName(path.getFileName().toString())
                .basicFileAttributes(Files.readAttributes(path, BasicFileAttributes.class))
                .build();
        } catch (NoSuchFileException e) {
            throw new FileNotFoundException(e.getMessage());
        }
    }

    @Override
    public URI createDirectory(String tenantId, URI uri) {
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
    public URI move(String tenantId, URI from, URI to) throws IOException {
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
    public boolean delete(String tenantId, URI uri) throws IOException {
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
    public List<URI> deleteByPrefix(String tenantId, URI storagePrefix) throws IOException {
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
            config.getBasePath().toAbsolutePath() :
            Path.of(config.getBasePath().toAbsolutePath().toString(), tenantId);
        return URI.create("kestra:///" + prefix.relativize(path));
    }

    private void parentTraversalGuard(URI uri) {
        if (uri.toString().contains("..")) {
            throw new IllegalArgumentException("File should be accessed with their full path and not using relative '..' path.");
        }
    }
}
