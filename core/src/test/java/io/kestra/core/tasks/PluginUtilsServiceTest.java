package io.kestra.core.tasks;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.startsWith;

public class PluginUtilsServiceTest {
    @Test
    void outputFiles() throws IOException {
        Path tempDirectory = Files.createTempDirectory("plugin-utils");
        Map<String, String> outputFilesMap = PluginUtilsService.createOutputFiles(
            tempDirectory,
            List.of("out"),
            new HashMap<>(Map.of("workingDir", tempDirectory.toAbsolutePath().toString()))
        );

        assertThat(outputFilesMap.get("out"), startsWith(tempDirectory.resolve("out_").toString()));
    }
}
