package io.kestra.core.runners;

import java.io.IOException;
import java.net.URI;
import java.security.GeneralSecurityException;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.kestra.core.storages.Storage;
import io.kestra.core.models.executions.LogEntry;

import static org.mockito.Mockito.*;

import io.kestra.core.context.TestRunContextFactory;
import io.kestra.core.encryption.EncryptionService;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.tasks.common.EncryptedString;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Value;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

@MicronautTest
class DefaultRunContextTest {

    @Inject
    private ApplicationContext applicationContext;

    @Value("${kestra.encryption.secret-key}")
    private String secretKey;

    @Inject
    private TestRunContextFactory runContextFactory;

    @Test
    void dynamicWorkerResult_boundsOverLongTaskId() {
        RunContext runContext = runContextFactory.of();

        // a dynamic taskrun (only dbt produces these) whose runtime-generated task id exceeds the DB column
        String longTaskId = "n".repeat(io.kestra.core.models.tasks.Task.ID_MAX_LENGTH + 60);
        runContext.dynamicWorkerResult(new WorkerTaskResult(dynamicTaskRun(longTaskId)), java.util.List.of());

        // the stored taskrun (persisted downstream, and the one its logs are attributed to) is bounded at the source
        String storedTaskId = runContext.dynamicWorkerResults().getFirst().getTaskRun().getTaskId();
        assertThat(storedTaskId).hasSizeLessThanOrEqualTo(io.kestra.core.models.tasks.Task.ID_MAX_LENGTH);
        assertThat(storedTaskId).startsWith(longTaskId.substring(0, 250));
    }

    @Test
    void dynamicWorkerResult_keepsCollidingTaskIdsDistinct() {
        RunContext runContext = runContextFactory.of();

        // two long task ids sharing a 250-char prefix but differing at the tail (as dbt node ids do)
        String shared = "b".repeat(260);
        runContext.dynamicWorkerResult(new WorkerTaskResult(dynamicTaskRun(shared + ".taila")), java.util.List.of());
        runContext.dynamicWorkerResult(new WorkerTaskResult(dynamicTaskRun(shared + ".tailb")), java.util.List.of());

        var stored = runContext.dynamicWorkerResults();
        assertThat(stored.get(0).getTaskRun().getTaskId())
            .isNotEqualTo(stored.get(1).getTaskRun().getTaskId());
    }

    private static io.kestra.core.models.executions.TaskRun dynamicTaskRun(String taskId) {
        return io.kestra.core.models.executions.TaskRun.builder()
            .id(java.util.UUID.randomUUID().toString())
            .taskId(taskId)
            .namespace("namespace")
            .flowId("flowId")
            .executionId("executionId")
            .build();
    }

    @Test
    void shouldGetKestraVersion() {
        DefaultRunContext runContext = new DefaultRunContext();
        runContext.init(applicationContext);
        Assertions.assertNotNull(runContext.version());
    }

    @Test
    void shouldDecryptVariables() throws GeneralSecurityException, IllegalVariableEvaluationException {
        RunContext runContext = runContextFactory.of();

        String encryptedSecret = EncryptionService.encrypt(secretKey, "It's a secret");
        Map<String, Object> variables = Map.of(
            "test", "test",
            "secret", Map.of("type", EncryptedString.TYPE, "value", encryptedSecret)
        );

        String render = runContext.render("What ? {{secret}}", variables);
        assertThat(render).isEqualTo(("What ? It's a secret"));
    }

    @Test
    void shouldReturnNullAndRestoreInterruptStatusWhenLogUploadFails() throws IOException {
        Storage storage = mock(Storage.class);
        when(storage.putFile(any(java.io.File.class))).thenThrow(new RuntimeException("Thread was interrupted", new InterruptedException()));

        RunContextLogger runContextLogger = new RunContextLogger(
            mock(LogEntryEmitter.class),
            LogEntry.builder().tenantId("t").namespace("n").flowId("f").build(),
            org.slf4j.event.Level.INFO,
            true
        );
        runContextLogger.logger();

        DefaultRunContext runContext = (DefaultRunContext) runContextFactory.of();
        runContext.setStorage(storage);
        runContext.setLogger(runContextLogger);

        Thread.interrupted();

        URI uri = runContext.logFileURI();

        assertThat(uri).isNull();
        assertThat(Thread.currentThread().isInterrupted()).isTrue();

        Thread.interrupted();
    }

    @Test
    void shouldRestoreInterruptStatusWhenThreadWasAlreadyInterrupted() throws IOException {
        Storage storage = mock(Storage.class);
        when(storage.putFile(any(java.io.File.class))).thenThrow(new IOException("Generic IO error"));

        RunContextLogger runContextLogger = new RunContextLogger(
            mock(LogEntryEmitter.class),
            LogEntry.builder().tenantId("t").namespace("n").flowId("f").build(),
            org.slf4j.event.Level.INFO,
            true
        );
        runContextLogger.logger();

        DefaultRunContext runContext = (DefaultRunContext) runContextFactory.of();
        runContext.setStorage(storage);
        runContext.setLogger(runContextLogger);

        Thread.currentThread().interrupt();

        URI uri = runContext.logFileURI();

        assertThat(uri).isNull();
        assertThat(Thread.currentThread().isInterrupted()).isTrue();

        Thread.interrupted();
    }

    @Test
    void shouldReturnNullWhenLogUploadThrowsIOException() throws IOException {
        Storage storage = mock(Storage.class);
        when(storage.putFile(any(java.io.File.class))).thenThrow(new IOException("Disk full"));

        RunContextLogger runContextLogger = new RunContextLogger(
            mock(LogEntryEmitter.class),
            LogEntry.builder().tenantId("t").namespace("n").flowId("f").build(),
            org.slf4j.event.Level.INFO,
            true
        );
        runContextLogger.logger();

        DefaultRunContext runContext = (DefaultRunContext) runContextFactory.of();
        runContext.setStorage(storage);
        runContext.setLogger(runContextLogger);

        Thread.interrupted();

        URI uri = runContext.logFileURI();

        assertThat(uri).isNull();
        assertThat(Thread.currentThread().isInterrupted()).isFalse();
    }
}