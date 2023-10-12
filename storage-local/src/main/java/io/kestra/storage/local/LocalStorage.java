package io.kestra.storage.local;

import io.kestra.core.storages.StorageInterface;
import org.apache.commons.lang3.tuple.Pair;

import java.io.*;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

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
        return tenantId == null ? Paths.get(config.getBasePath().toAbsolutePath().toString(), uri.toString())
            : Paths.get(config.getBasePath().toAbsolutePath().toString(), tenantId, uri.toString());
    }

    private void createDirectory(String tenantId, URI append) {
        Path path = getPath(tenantId, append);
        File directory = path.getParent().toFile();

        if (!directory.exists()) {
            if (!directory.mkdirs()) {
                throw new RuntimeException("Cannot create directory: " + directory.getAbsolutePath());
            }
        }
    }

    @Override
    public InputStream get(String tenantId, URI uri) throws IOException {
        return new BufferedInputStream(new FileInputStream(getPath(tenantId, URI.create(uri.getPath()))
            .toAbsolutePath()
            .toString())
        );
    }

    @Override
    public boolean exists(String tenantId, URI uri) {
        return Files.exists(getPath(tenantId, uri));
    }

    @Override
    public Long size(String tenantId, URI uri) throws IOException {
        try {
            return Files.size(getPath(tenantId, URI.create(uri.getPath())));
        } catch (NoSuchFileException e) {
            throw new FileNotFoundException("Unable to find file at '" + uri + "'");
        } catch (IOException e) {
            throw new IOException("Unable to find file at '" + uri + "' with message '" + e.getMessage() + "'");
        }
    }

    @Override
    public Long lastModifiedTime(String tenantId, URI uri) throws IOException {
        FileTime lastModifiedTime = Files.getLastModifiedTime(getPath(tenantId, uri));
        return lastModifiedTime.toMillis();
    }

    @Override
    public URI put(String tenantId, URI uri, InputStream data) throws IOException {
        this.createDirectory(tenantId, uri);

        try (data; OutputStream outStream = new FileOutputStream(getPath(tenantId, uri).toFile())) {
            byte[] buffer = new byte[8 * 1024];
            int bytesRead;
            while ((bytesRead = data.read(buffer)) != -1) {
                outStream.write(buffer, 0, bytesRead);
            }
        }

        return URI.create("kestra://" + uri.getPath());
    }

    @Override
    public boolean delete(String tenantId, URI uri) throws IOException {
        File file = getPath(tenantId, URI.create(uri.getPath())).toFile();
        if (!file.exists()) {
            return false;
        }

        return file.delete();
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
                .map(r -> Pair.of(r.toFile(), r.toFile().isFile()))
                .peek(r -> r.getLeft().delete())
                .filter(Pair::getRight)
                .map(r -> URI.create("kestra://" + r.getLeft().toURI().getPath().substring(config.getBasePath().toAbsolutePath().toString().length())))
                .collect(Collectors.toList());
        }
    }
}
