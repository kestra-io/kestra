package io.kestra.storage.local;

import io.kestra.core.storages.StorageInterface;
import org.apache.commons.lang3.tuple.Pair;

import java.io.*;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
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
    public LocalStorage(LocalConfig config) {
        this.config = config;
        this.createDirectory(null);
    }

    private Path getPath(URI uri) {
        return Paths.get(config.getBasePath().toAbsolutePath().toString(), uri.toString());
    }

    private void createDirectory(URI append) {
        File file;

        if (append != null) {
            Path path = Paths.get(config.getBasePath().toAbsolutePath().toString(), append.getPath());
            file = path.getParent().toFile();
        } else {
            file = new File(config.getBasePath().toUri());
        }

        if (!file.exists()) {
            if (!file.mkdirs()) {
                throw new RuntimeException("Cannot create directory: " + file.getAbsolutePath());
            }
        }
    }

    @Override
    public InputStream get(URI uri) throws FileNotFoundException {
        return new BufferedInputStream(new FileInputStream(getPath(URI.create(uri.getPath()))
            .toAbsolutePath()
            .toString())
        );
    }

    @Override
    public Long size(URI uri) throws FileNotFoundException {
        try {
            return Files.size(getPath(URI.create(uri.getPath())));
        } catch (NoSuchFileException e) {
            throw new FileNotFoundException("Unable to find file at '" + uri + "'");
        } catch (IOException e) {
            throw new FileNotFoundException("Unable to find file at '" + uri + "' with message '" + e.getMessage() + "'");
        }
    }

    @Override
    public URI put(URI uri, InputStream data) throws IOException {
        this.createDirectory(uri);

        try (data; OutputStream outStream = new FileOutputStream(getPath(uri).toFile())) {
            byte[] buffer = new byte[8 * 1024];
            int bytesRead;
            while ((bytesRead = data.read(buffer)) != -1) {
                outStream.write(buffer, 0, bytesRead);
            }
        }

        return URI.create("kestra://" + uri.getPath());
    }

    @Override
    public boolean delete(URI uri) throws IOException {
        File file = getPath(URI.create(uri.getPath())).toFile();
        if (!file.exists()) {
            return false;
        }

        return file.delete();
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Override
    public List<URI> deleteByPrefix(URI storagePrefix) throws IOException {
        try (Stream<Path> walk = Files.walk(this.getPath(storagePrefix))) {
            return walk.sorted(Comparator.reverseOrder())
                .map(r -> Pair.of(r.toFile(), r.toFile().isFile()))
                .peek(r -> r.getLeft().delete())
                .filter(Pair::getRight)
                .map(r -> URI.create("kestra://" + r.getLeft().toURI().getPath().substring(config.getBasePath().toAbsolutePath().toString().length())))
                .collect(Collectors.toList());
        }
    }
}
