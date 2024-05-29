package io.kestra.core.runners;

import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@MicronautTest
class FilesServiceTest {
    @Inject
    private RunContextFactory runContextFactory;

    @Test
    void overrideExistingInputFile() throws Exception {
        RunContext runContext = runContextFactory.of();
        FilesService.inputFiles(runContext, Map.of("file.txt", "content"));

        FilesService.inputFiles(runContext, Map.of("file.txt", "overriden content"));

        String fileContent = FileUtils.readFileToString(runContext.tempDir().resolve("file.txt").toFile(), "UTF-8");
        assertThat(fileContent, is("overriden content"));
    }

    @Test
    void renderInputFile() throws Exception {
        RunContext runContext = runContextFactory.of(Map.of("filename", "file.txt", "content", "Hello World"));
        Map<String, String> content = FilesService.inputFiles(runContext, Map.of("{{filename}}", "{{content}}"));
        assertThat(content.get("file.txt"), is("Hello World"));
    }

    @Test
    void outputFiles() throws Exception {
        RunContext runContext = runContextFactory.of();
        Map<String, String> files = FilesService.inputFiles(runContext, Map.of("file.txt", "content"));

        Map<String, URI> outputs = FilesService.outputFiles(runContext, files.keySet().stream().toList());
        assertThat(outputs.size(), is(1));
    }
}
