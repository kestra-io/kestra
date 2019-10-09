package org.floworc.storage.local;

import com.google.common.io.Files;
import org.floworc.core.storages.StorageInterface;
import org.floworc.core.storages.StorageObject;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
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

    private URI getUri(URI uri) {
        try {
            return new URI(this.config.getBasePath() + File.separator + uri.toString());
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(e);
        }
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private void createDirectory(URI append) {
        File file;

        if (append != null) {
            try {
                URI uri = new URI(config.getBasePath().toString() + File.separator + append.toString());
                Path path = Paths.get(uri);
                file = path.getParent().toFile();
            } catch (URISyntaxException e) {
                throw new IllegalArgumentException(e);
            }
        } else {
            file = new File(config.getBasePath());
        }

        if (!file.exists()) {
            file.mkdirs();
        }
    }

    @Override
    public InputStream get(URI uri) throws FileNotFoundException {
        return new FileInputStream(new File(getUri(uri)));
    }

    @Override
    public StorageObject put(URI uri, InputStream data) throws IOException {
        this.createDirectory(uri);

        OutputStream outStream = new FileOutputStream(getUri(uri).getPath());

        byte[] buffer = new byte[8 * 1024];
        int bytesRead;
        while ((bytesRead = data.read(buffer)) != -1) {
            outStream.write(buffer, 0, bytesRead);
        }

        outStream.close();
        data.close();

        return new StorageObject(this, uri);
    }
}
