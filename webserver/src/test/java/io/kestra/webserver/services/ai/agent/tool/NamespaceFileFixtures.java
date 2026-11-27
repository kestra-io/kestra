package io.kestra.webserver.services.ai.agent.tool;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import io.kestra.core.storages.Namespace;
import io.kestra.core.utils.IdUtils;

final class NamespaceFileFixtures {
    private NamespaceFileFixtures() {
    }

    static String randomNamespace() {
        return "io.kestra.test.ai." + IdUtils.create().toLowerCase();
    }

    static void put(final Namespace storage, final String path, final String content) {
        try {
            storage.putFile(Path.of(path), new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
        } catch (IOException | URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    static String read(final Namespace storage, final String path) {
        try (InputStream content = storage.getFileContent(Path.of(path))) {
            return new String(content.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    static boolean exists(final Namespace storage, final String path) {
        try {
            return storage.exists(Path.of(path));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
