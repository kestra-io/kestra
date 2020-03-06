package org.kestra.storage.local;

import org.kestra.core.storages.StorageInterface;

import java.io.*;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.inject.Inject;
import javax.inject.Singleton;

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

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private void createDirectory(URI append) {
        File file;

        if (append != null) {
            Path path = Paths.get(config.getBasePath().toAbsolutePath().toString(), append.getPath());
            file = path.getParent().toFile();
        } else {
            file = new File(config.getBasePath().toUri());
        }

        if (!file.exists()) {
            file.mkdirs();
        }
    }

    @Override
    public InputStream get(URI uri) throws FileNotFoundException {
        return new BufferedInputStream(new FileInputStream(new File(getPath(URI.create(uri.getPath()))
            .toAbsolutePath()
            .toString()
        )));
    }

    @Override
    public URI put(URI uri, InputStream data) throws IOException {
        this.createDirectory(uri);

        try (OutputStream outStream = new FileOutputStream(getPath(uri).toFile())) {
            byte[] buffer = new byte[8 * 1024];
            int bytesRead;
            while ((bytesRead = data.read(buffer)) != -1) {
                outStream.write(buffer, 0, bytesRead);
            }
        } finally {
            data.close();
        }

        return URI.create("kestra://" + uri.getPath());
    }
}
