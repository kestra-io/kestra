package io.kestra.plugin.core.storage;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;

import io.kestra.core.context.TestRunContextFactory;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;

import jakarta.inject.Inject;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
class PurgeCurrentExecutionFilesTest {
    @Inject
    private TestRunContextFactory runContextFactory;

    @Test
    void run() throws Exception {
        var flow = Flow.builder()
            .namespace("namespace")
            .id("flowId")
            .tenantId(MAIN_TENANT)
            .build();
        var runContext = runContextFactory.of(
            flow, Map.of(
                "execution", Map.of("id", "executionId"),
                "task", Map.of("id", "taskId"),
                "taskrun", Map.of("id", "taskRunId")
            )
        );
        var file = runContext.workingDir().createFile("test.txt", "Hello World".getBytes());
        URI executionFileUri = runContext.storage().putFile(file.toFile());
        URI cacheUri = createTaskCache(runContext, executionFileUri.toString());

        var purge = PurgeCurrentExecutionFiles.builder()
            .build();
        purge.run(runContext);

        assertThat(runContext.storage().isFileExist(executionFileUri)).isFalse();
        assertThat(runContext.storage().isFileExist(cacheUri)).isFalse();
        assertThat(runContext.storage().getCacheFile("task-cache", null)).isEmpty();
    }

    private URI createTaskCache(RunContext runContext, String executionFileUri) throws Exception {
        byte[] outputs = JacksonMapper.ofIon().writeValueAsBytes(Map.of("uri", executionFileUri));
        Path archiveFile = runContext.workingDir().createTempFile(".zip");

        try (ByteArrayOutputStream bos = new ByteArrayOutputStream(); ZipOutputStream archive = new ZipOutputStream(bos)) {
            archive.putNextEntry(new ZipEntry("outputs.ion"));
            archive.write(outputs);
            archive.closeEntry();
            archive.finish();
            Files.write(archiveFile, bos.toByteArray());
        }

        return runContext.storage().putCacheFile(archiveFile.toFile(), "task-cache", null);
    }
}
