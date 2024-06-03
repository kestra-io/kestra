package io.kestra.core.tasks;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.tasks.runners.PluginUtilsService;
import io.kestra.core.runners.RunContext;
import io.micronaut.context.ApplicationContext;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertThrows;

@MicronautTest
public class PluginUtilsServiceTest {
    @Inject
    private ApplicationContext applicationContext;

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

    @Test
    void executionFromTaskParameters() throws IllegalVariableEvaluationException {
        Map<String, Object> variables = Map.of(
            "flow", Map.of(
                "namespace", "namespace",
                "id", "flow",
                "revision", 1
            ),
            "execution", Map.of(
                "id", "execution"
            )
        );
        var runContext = new RunContext(applicationContext, variables);

        var executionInfo = PluginUtilsService.executionFromTaskParameters(runContext, null, null, null);
        assertThat(executionInfo.namespace(), is("namespace"));
        assertThat(executionInfo.flowId(), is("flow"));
        assertThat(executionInfo.id(), is("execution"));

        executionInfo = PluginUtilsService.executionFromTaskParameters(runContext, null, null, "exec2");
        assertThat(executionInfo.namespace(), is("namespace"));
        assertThat(executionInfo.flowId(), is("flow"));
        assertThat(executionInfo.id(), is("exec2"));

        executionInfo = PluginUtilsService.executionFromTaskParameters(runContext, "ns2", "flow2", "exec2");
        assertThat(executionInfo.namespace(), is("ns2"));
        assertThat(executionInfo.flowId(), is("flow2"));
        assertThat(executionInfo.id(), is("exec2"));

        assertThrows(IllegalArgumentException.class, () -> {
            PluginUtilsService.executionFromTaskParameters(runContext, "ns2", "flow2", null);
        });

        assertThrows(IllegalArgumentException.class, () -> {
            PluginUtilsService.executionFromTaskParameters(runContext, "ns2", null, "exec2");
        });
    }
}
