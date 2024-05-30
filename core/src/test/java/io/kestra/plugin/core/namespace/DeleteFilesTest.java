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

@MicronautTest
public class DeleteFilesTest {
    @Inject
    StorageInterface storageInterface;

    @Inject
    NamespaceFilesService namespaceFilesService;

    @Inject
    RunContextFactory runContextFactory;

    @Test
    void delete() throws Exception {
        String namespace = "io.kestra." + IdUtils.create();

        put(namespace, "/a/b/test1.txt", "1");
        put(namespace, "/a/b/test2.txt", "1");

        DeleteFiles deleteFiles = DeleteFiles.builder()
            .id(DeleteFiles.class.getSimpleName())
            .type(DeleteFiles.class.getName())
            .files(List.of("test1"))
            .namespace("{{ inputs.namespace }}")
            .build();

        RunContext runContext = TestsUtils.mockRunContext(this.runContextFactory, deleteFiles,  ImmutableMap.of("namespace", namespace));

        DeleteFiles.Output output = deleteFiles.run(runContext);

        assertThat(namespaceFilesService.recursiveList(null, namespace, URI.create("/a/b/")).size(), is(1));

    }

    private void put(String namespace, String path, String content) throws IOException {
        storageInterface.put(
            null,
            URI.create(StorageContext.namespaceFilePrefix(namespace) + path),
            new ByteArrayInputStream(content.getBytes())
        );
    }
}
