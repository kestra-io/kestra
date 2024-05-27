package io.kestra.plugin.core.namespace;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.runners.NamespaceFilesService;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.storages.StorageContext;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.TestsUtils;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

@MicronautTest
public class DownloadFilesTest {
    @Inject
    StorageInterface storageInterface;

    @Inject
    NamespaceFilesService namespaceFilesService;

    @Inject
    RunContextFactory runContextFactory;

    @Test
    void download() throws Exception {
        String namespace = "io.kestra." + IdUtils.create();

        put(namespace, "/a/b/test1.txt", "1");
        put(namespace, "/a/b/test2.txt", "1");

        DownloadFiles downloadFiles = DownloadFiles.builder()
            .id(DownloadFiles.class.getSimpleName())
            .type(DownloadFiles.class.getName())
            .files(List.of("test1"))
            .namespace(namespace)
            .build();

        RunContext runContext = TestsUtils.mockRunContext(this.runContextFactory, downloadFiles, ImmutableMap.of());

        DownloadFiles.Output output = downloadFiles.run(runContext);

        assertThat(output.getFiles().size(), is(1));
        assertThat(output.getFiles().get("a/b/test1.txt"), notNullValue());

    }

    private void put(String namespace, String path, String content) throws IOException {
        storageInterface.put(
            null,
            URI.create(StorageContext.namespaceFilePrefix(namespace) + path),
            new ByteArrayInputStream(content.getBytes())
        );
    }
}
