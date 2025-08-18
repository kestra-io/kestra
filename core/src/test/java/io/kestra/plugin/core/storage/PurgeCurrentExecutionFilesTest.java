package io.kestra.plugin.core.storage;

import io.kestra.core.context.TestRunContextFactory;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.flows.Flow;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
class PurgeCurrentExecutionFilesTest {
    @Inject
    private TestRunContextFactory runContextFactory;

    @Test
    void run() throws Exception {
        // create a file
        var flow  = Flow.builder()
            .namespace("namespace")
            .id("flowId")
            .tenantId(MAIN_TENANT)
            .build();
        var runContext = runContextFactory.of(flow, Map.of(
            "execution", Map.of("id", "executionId"),
            "task", Map.of("id", "taskId"),
            "taskrun", Map.of("id", "taskRunId")
        ));
        var file = runContext.workingDir().createFile("test.txt", "Hello World".getBytes());
        runContext.storage().putFile(file.toFile());

        var purge = PurgeCurrentExecutionFiles.builder()
            .build();
        var output = purge.run(runContext);

        assertThat(output.getUris().size()).isEqualTo(2);
    }
}