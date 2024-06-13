package io.kestra.core.runners;

import io.kestra.core.models.tasks.NamespaceFiles;
import io.kestra.core.storages.StorageContext;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.utils.IdUtils;
import io.micronaut.core.annotation.Nullable;
import io.kestra.core.junit.annotations.KestraTest;
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
import static org.hamcrest.Matchers.*;

@KestraTest
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
        put(null, namespace, "/a/2.sql", "2");
        put(null, namespace, "/b/c/d/3.sql", "3");
        put(null, namespace, "/b/d/4.sql", "4");
        put(null, namespace, "/c/5.sql", "5");

        List<URI> injected = namespaceFilesService.inject(
            runContextFactory.of(),
            null,
            namespace,
            basePath,
            NamespaceFiles.builder()
                .include(List.of(
                    "/a/**",
                    "c/**"
                ))
                .exclude(List.of("**/2.sql"))
                .build()
        );

        assertThat(injected, containsInAnyOrder(
            hasProperty("path", endsWith("1.sql")),
            hasProperty("path", endsWith("3.sql")),
            hasProperty("path", endsWith("5.sql"))
        ));
        List<String> tempDirEntries = Files.walk(basePath).filter(path -> path.toFile().isFile())
            .map(Path::toString)
            .toList();
        assertThat(tempDirEntries, containsInAnyOrder(
            is(Paths.get(basePath.toString(), "/a/b/c/1.sql").toString()),
            is(Paths.get(basePath.toString(), "/b/c/d/3.sql").toString()),
            is(Paths.get(basePath.toString(), "/c/5.sql").toString())
        ));
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
            runContext.workingDir().path(),
            NamespaceFiles
                .builder()
                .enabled(true)
                .build()
        );
        assertThat(injected.size(), is(1));

        String content = Files.walk(runContext.workingDir().path()).filter(path -> path.toFile().isFile()).findFirst().map(throwFunction(Files::readString)).orElseThrow();
        assertThat(content, is("1"));

        runContext = runContextFactory.of();
        injected = namespaceFilesService.inject(
            runContextFactory.of(),
            "tenant2",
            namespace,
            runContext.workingDir().path(),
            NamespaceFiles
                .builder()
                .enabled(true)
                .build()
        );
        assertThat(injected.size(), is(1));

        content = Files.walk(runContext.workingDir().path()).filter(path -> path.toFile().isFile()).findFirst().map(throwFunction(Files::readString)).orElseThrow();
        assertThat(content, is("2"));
    }

    @Test
    public void nsFilesRootFolderDoesntExist() throws Exception {
        RunContext runContext = runContextFactory.of();
        List<URI> injected = namespaceFilesService.inject(
            runContextFactory.of(),
            "tenant1",
            "io.kestra." + IdUtils.create(),
            runContext.workingDir().path(),
            NamespaceFiles
                .builder()
                .enabled(true)
                .build()
        );
        assertThat(injected.size(), is(0));
    }

    private void put(@Nullable String tenantId, String namespace, String path, String content) throws IOException {
        storageInterface.put(
            tenantId,
            URI.create(StorageContext.namespaceFilePrefix(namespace)  + path),
            new ByteArrayInputStream(content.getBytes())
        );
    }
}