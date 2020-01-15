package org.kestra.storage.local;

import org.kestra.core.storages.StorageInterface;
import org.kestra.core.storages.StorageObject;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.*;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;

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
        return new FileInputStream(new File(getPath(URI.create(uri.getPath()))
            .toAbsolutePath()
            .toString()
        ));
    }

    @Override
    public StorageObject put(URI uri, InputStream data) throws IOException {
        this.createDirectory(uri);

        OutputStream outStream = new FileOutputStream(getPath(uri).toFile());

        byte[] buffer = new byte[8 * 1024];
        int bytesRead;
        while ((bytesRead = data.read(buffer)) != -1) {
            outStream.write(buffer, 0, bytesRead);
        }

        outStream.close();
        data.close();

        URI result = URI.create("kestra://" + uri.getPath());

        return new StorageObject(this, result);
    }
}
