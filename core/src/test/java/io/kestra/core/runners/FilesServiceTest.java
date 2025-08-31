package io.kestra.core.runners;

import io.kestra.core.context.TestRunContextFactory;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.storages.StorageContext;
import io.kestra.core.storages.StorageInterface;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Property;
import jakarta.inject.Inject;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;

@KestraTest(rebuildContext = true)
class FilesServiceTest {
    @Inject
    private TestRunContextFactory runContextFactory;

    @Inject
    private ApplicationContext applicationContext;

    @Inject
    private StorageInterface storageInterface;

    @Test
    void overrideExistingInputFile() throws Exception {
        RunContext runContext = runContextFactory.of();
        FilesService.inputFiles(runContext, Map.of("file.txt", "content"));

        FilesService.inputFiles(runContext, Map.of("file.txt", "overridden content"));

        String fileContent = FileUtils.readFileToString(runContext.workingDir().path().resolve("file.txt").toFile(), "UTF-8");
        assertThat(fileContent).isEqualTo("overridden content");
    }

    @Test
    void renderInputFile() throws Exception {
        RunContext runContext = runContextFactory.of(Map.of("filename", "file.txt", "content", "Hello World"));
        Map<String, String> content = FilesService.inputFiles(runContext, Map.of("{{filename}}", "{{content}}"));
        assertThat(content.get("file.txt")).isEqualTo("Hello World");
    }

    @Test
    void renderRawFile() throws Exception {
        RunContext runContext = runContextFactory.of(Map.of("filename", "file.txt", "content", "Hello World"));
        Map<String, String> content = FilesService.inputFiles(runContext, Map.of("{{filename}}", "{% raw %}{{content}}{% endraw %}"));
        assertThat(content.get("file.txt")).isEqualTo("{{content}}");
    }

    @Test
    @Property(name = LocalPath.ALLOWED_PATHS_CONFIG, value = "/tmp")
    void localFileAsInputFile() throws Exception {
        URI uri = createFile();
        RunContext runContext = runContextFactory.of();
        ((DefaultRunContext) runContext).init(applicationContext);
        FilesService.inputFiles(runContext, Map.of("file.txt", uri.toString()));
        Path file = runContext.workingDir().resolve(Path.of("file.txt"));
        assertThat(new String(Files.readAllBytes(file))).isEqualTo("Hello World");
    }

    @Test
    @Property(name = LocalPath.ALLOWED_PATHS_CONFIG, value = "/tmp")
    void nsFileAsInputFile() throws Exception {
        URI uri = createNsFile(false);
        RunContext runContext = runContextFactory.of();
        FilesService.inputFiles(runContext, Map.of("file.txt", uri.toString()));
        Path file = runContext.workingDir().resolve(Path.of("file.txt"));
        assertThat(new String(Files.readAllBytes(file))).isEqualTo("Hello World");
    }

    @Test
    void outputFiles() throws Exception {
        RunContext runContext = runContextFactory.of();
        Map<String, String> files = FilesService.inputFiles(runContext, Map.of("file.txt", "content"));

        Map<String, URI> outputs = FilesService.outputFiles(runContext, files.keySet().stream().toList());
        assertThat(outputs.size()).isEqualTo(1);
    }

    @Test
    void renderOutputFiles() throws Exception {
        RunContext runContext = runContextFactory.of(Map.of("extension", "txt"));
        Map<String, String> files = FilesService.inputFiles(runContext, Map.of("file.txt", "content"));

        Map<String, URI> outputs = FilesService.outputFiles(runContext, List.of("*.{{extension}}"));
        assertThat(outputs.size()).isEqualTo(1);
    }

    private URI createFile() throws IOException {
        File tempFile = File.createTempFile("file", ".txt");
        Files.write(tempFile.toPath(), "Hello World".getBytes());
        return tempFile.toPath().toUri();
    }

    private URI createNsFile(boolean nsInAuthority) throws IOException {
        String namespace = "namespace";
        String filePath = "file.txt";
        storageInterface.createDirectory(MAIN_TENANT, namespace, URI.create(StorageContext.namespaceFilePrefix(namespace)));
        storageInterface.put(MAIN_TENANT, namespace, URI.create(StorageContext.namespaceFilePrefix(namespace) + "/" + filePath), new ByteArrayInputStream("Hello World".getBytes()));
        return URI.create("nsfile://" + (nsInAuthority ? namespace : "") + "/" + filePath);
    }
}
