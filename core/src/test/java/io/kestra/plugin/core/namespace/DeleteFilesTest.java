package io.kestra.plugin.core.namespace;

import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.storages.Namespace;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.TestsUtils;
import io.kestra.core.junit.annotations.KestraTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

@KestraTest
public class DeleteFilesTest {
    @Inject
    StorageInterface storageInterface;

    @Inject
    RunContextFactory runContextFactory;

    @Test
    void shouldDeleteNamespaceFilesForMatchingExpression() throws Exception {
        // Given
        String namespaceId = "io.kestra." + IdUtils.create();

        DeleteFiles deleteFiles = DeleteFiles.builder()
            .id(DeleteFiles.class.getSimpleName())
            .type(DeleteFiles.class.getName())
            .files(List.of("**test1*"))
            .namespace("{{ inputs.namespace }}")
            .build();

        final RunContext runContext = TestsUtils.mockRunContext(this.runContextFactory, deleteFiles, Map.of("namespace", namespaceId));
        final Namespace namespace = runContext.storage().namespace(namespaceId);

        namespace.putFile(Path.of("/a/b/test1.txt"), new ByteArrayInputStream("1".getBytes(StandardCharsets.UTF_8)));
        namespace.putFile(Path.of("/a/b/test2.txt"), new ByteArrayInputStream("2".getBytes(StandardCharsets.UTF_8)));

        assertThat(namespace.all("/a/b/", false).size(), is(2));

        // When
        assertThat(deleteFiles.run(runContext), notNullValue());

        // Then
        assertThat(namespace.all("/a/b/", false).size(), is(1));
    }
}
