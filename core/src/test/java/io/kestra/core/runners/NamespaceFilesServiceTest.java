package io.kestra.core.runners;

import io.kestra.core.models.tasks.NamespaceFiles;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.utils.IdUtils;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static io.kestra.core.utils.Rethrow.throwFunction;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

@MicronautTest
class NamespaceFilesServiceTest {
    @Inject
    StorageInterface storageInterface;

    @Inject
    NamespaceFilesService namespaceFilesService;

    @Inject
    RunContextFactory runContextFactory;

    @Test
    public void noFilter() throws Exception {
        Path basePath = Files.createTempDirectory("unit");
        String namespace = "io.kestra." + IdUtils.create();

        put(null, namespace, "/a/b/c/1.sql", "1");
        put(null, namespace, "/a/1.sql", "2");
        String expectedFileContent = "3";
        put(null, namespace, "/b/c/d/1.sql", expectedFileContent);

        RunContext runContext = runContextFactory.of();
        List<URI> injected = namespaceFilesService.inject(
            runContext,
            null,
            namespace,
            basePath,
            NamespaceFiles
                .builder()
                .enabled(true)
                .build()
        );

        assertThat(injected.size(), is(3));
        List<Path> tempDir = Files.walk(basePath).filter(path -> path.toFile().isFile()).toList();
        assertThat(tempDir.size(), is(3));
        String fileContent = FileUtils.readFileToString(
            tempDir.stream().filter(path -> path.toString().contains("b/c/d/1.sql")).findFirst().orElseThrow().toFile(),
            "UTF-8"
        );
        assertThat(fileContent, is(expectedFileContent));

        // injecting a namespace file that collapse with an existing file will override its content
        expectedFileContent = "4";
        put(null, namespace, "/b/c/d/1.sql", expectedFileContent);
        injected = namespaceFilesService.inject(
            runContext,
            null,
            namespace,
            basePath,
            NamespaceFiles
                .builder()
                .enabled(true)
                .build()
        );

        assertThat(injected.size(), is(3));
        tempDir = Files.walk(basePath).filter(path -> path.toFile().isFile()).toList();
        assertThat(tempDir.size(), is(3));
        fileContent = FileUtils.readFileToString(
            tempDir.stream().filter(path -> path.toString().contains("b/c/d/1.sql")).findFirst().orElseThrow().toFile(),
            "UTF-8"
        );
        assertThat(fileContent, is(expectedFileContent));
    }

    @Test
    public void filter() throws Exception {
        Path basePath = Files.createTempDirectory("unit");
        String namespace = "io.kestra." + IdUtils.create();

        put(null, namespace, "/a/b/c/1.sql", "1");
        put(null, namespace, "/a/3.sql", "2");
        put(null, namespace, "/b/c/d/1.sql", "3");

        List<URI> injected = namespaceFilesService.inject(
            runContextFactory.of(),
            null,
            namespace,
            basePath,
            NamespaceFiles.builder()
                .include(List.of("/a/**"))
                .exclude(List.of("**/3.sql"))
                .build()
        );

        assertThat(injected.size(), is(1));
        assertThat(injected.get(0).getPath(), containsString("c/1.sql"));
        List<Path> tempDir = Files.walk(basePath).filter(path -> path.toFile().isFile()).toList();
        assertThat(tempDir.size(), is(1));
        assertThat(tempDir.get(0).toString(), is(Paths.get(basePath.toString(), "/a/b/c/1.sql").toString()));
    }

    @Test
    public void tenant() throws Exception {
        String namespace = "io.kestra." + IdUtils.create();

        put("tenant1", namespace, "/a/b/c/1.sql", "1");
        put("tenant2", namespace, "/a/b/c/1.sql", "2");

        RunContext runContext = runContextFactory.of();
        List<URI> injected = namespaceFilesService.inject(
            runContextFactory.of(),
            "tenant1",
            namespace,
            runContext.tempDir(),
            NamespaceFiles
                .builder()
                .enabled(true)
                .build()
        );
        assertThat(injected.size(), is(1));

        String content = Files.walk(runContext.tempDir()).filter(path -> path.toFile().isFile()).findFirst().map(throwFunction(Files::readString)).orElseThrow();
        assertThat(content, is("1"));

        runContext = runContextFactory.of();
        injected = namespaceFilesService.inject(
            runContextFactory.of(),
            "tenant2",
            namespace,
            runContext.tempDir(),
            NamespaceFiles
                .builder()
                .enabled(true)
                .build()
        );
        assertThat(injected.size(), is(1));

        content = Files.walk(runContext.tempDir()).filter(path -> path.toFile().isFile()).findFirst().map(throwFunction(Files::readString)).orElseThrow();
        assertThat(content, is("2"));
    }

    private void put(@Nullable String tenantId, String namespace, String path, String content) throws IOException {
        storageInterface.put(
            tenantId,
            URI.create(storageInterface.namespaceFilePrefix(namespace)  + path),
            new ByteArrayInputStream(content.getBytes())
        );
    }
}