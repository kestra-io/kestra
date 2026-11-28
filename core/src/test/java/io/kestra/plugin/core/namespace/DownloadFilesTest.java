package io.kestra.plugin.core.namespace;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.storages.Namespace;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.TestsUtils;

import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
public class DownloadFilesTest {
    @Inject
    StorageInterface storageInterface;

    @Inject
    RunContextFactory runContextFactory;

    @Test
    void shouldDownloadNamespaceFile() throws Exception {
        String namespaceId = "io.kestra." + IdUtils.create();
        DownloadFiles downloadFiles = DownloadFiles.builder()
            .id(DownloadFiles.class.getSimpleName())
            .type(DownloadFiles.class.getName())
            .files(List.of("**test1.txt"))
            .namespace(Property.ofExpression("{{ inputs.namespace }}"))
            .build();

        final RunContext runContext = TestsUtils.mockRunContext(this.runContextFactory, downloadFiles, Map.of("namespace", namespaceId));
        final Namespace namespace = runContext.storage().namespace(namespaceId);

        namespace.putFile(Path.of("/a/b/test1.txt"), new ByteArrayInputStream("1".getBytes(StandardCharsets.UTF_8)));
        namespace.putFile(Path.of("/a/b/test2.txt"), new ByteArrayInputStream("2".getBytes(StandardCharsets.UTF_8)));

        DownloadFiles.Output output = downloadFiles.run(runContext);

        assertThat(output.getFiles().size()).isEqualTo(1);
        assertThat(output.getFiles().get("/a/b/test1.txt")).isNotNull();

    }

    @Test
    void shouldDownloadNestedDotfileNamespaceFile() throws Exception {
        String namespaceId = "io.kestra." + IdUtils.create();
        DownloadFiles downloadFiles = DownloadFiles.builder()
            .id(DownloadFiles.class.getSimpleName())
            .type(DownloadFiles.class.getName())
            .files(List.of(".env"))
            .namespace(Property.ofExpression("{{ inputs.namespace }}"))
            .build();

        final RunContext runContext = TestsUtils.mockRunContext(this.runContextFactory, downloadFiles, Map.of("namespace", namespaceId));
        final Namespace namespace = runContext.storage().namespace(namespaceId);

        namespace.putFile(Path.of("/a/b/.env"), new ByteArrayInputStream("1".getBytes(StandardCharsets.UTF_8)));

        DownloadFiles.Output output = downloadFiles.run(runContext);

        assertThat(output.getFiles().size()).isEqualTo(1);
        assertThat(output.getFiles().get("/a/b/.env")).isNotNull();

    }

    @Test
    void shouldStoreFilesInsideTheDestinationFolder() throws Exception {
        var namespaceId = "io.kestra." + IdUtils.create();
        var downloadFiles = DownloadFiles.builder()
            .id(DownloadFiles.class.getSimpleName())
            .type(DownloadFiles.class.getName())
            .files(List.of("**"))
            .namespace(Property.ofExpression("{{ inputs.namespace }}"))
            .destination(Property.ofValue("downloaded"))
            .build();

        var runContext = TestsUtils.mockRunContext(this.runContextFactory, downloadFiles, Map.of("namespace", namespaceId));
        var namespace = runContext.storage().namespace(namespaceId);

        namespace.putFile(Path.of("/a/b/test1.txt"), new ByteArrayInputStream("1".getBytes(StandardCharsets.UTF_8)));
        namespace.putFile(Path.of("/main.py"), new ByteArrayInputStream("2".getBytes(StandardCharsets.UTF_8)));

        var output = downloadFiles.run(runContext);

        assertThat(output.getFiles().get("/a/b/test1.txt").getPath()).endsWith("/downloaded/a/b/test1.txt");
        assertThat(output.getFiles().get("/main.py").getPath()).endsWith("/downloaded/main.py");
    }
}
