package io.kestra.plugin.core.storage;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.runners.RunContextFactory;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@KestraTest
class PurgeInternalStorageTest {
    @Inject
    private RunContextFactory runContextFactory;

    @Test
    void run() throws Exception {
        // create a file
        var flow  = Flow.builder()
            .namespace("namespace")
            .id("flowId")
            .build();
        var runContext = runContextFactory.of(flow, Map.of(
            "execution", Map.of("id", "executionId"),
            "task", Map.of("id", "taskId"),
            "taskrun", Map.of("id", "taskRunId")
        ));
        var file = runContext.workingDir().createFile("test.txt", "Hello World".getBytes());
        runContext.storage().putFile(file.toFile());

        var purge = PurgeInternalStorage.builder()
            .build();
        var output = purge.run(runContext);

        assertThat(output.getUris().size(), is(2));
    }
}