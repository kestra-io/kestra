package io.kestra.core.runners;

import java.security.GeneralSecurityException;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

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
        runContext.dynamicWorkerResult(java.util.List.of(new WorkerTaskResult(dynamicTaskRun(longTaskId))));

        // the stored taskrun (persisted downstream) is bounded at the source
        String storedTaskId = runContext.dynamicWorkerResults().get(0).getTaskRun().getTaskId();
        assertThat(storedTaskId).hasSizeLessThanOrEqualTo(io.kestra.core.models.tasks.Task.ID_MAX_LENGTH);
        assertThat(storedTaskId).startsWith(longTaskId.substring(0, 250));
    }

    @Test
    void dynamicWorkerResult_keepsCollidingTaskIdsDistinct() {
        RunContext runContext = runContextFactory.of();

        // two long task ids sharing a 250-char prefix but differing at the tail (as dbt node ids do)
        String shared = "b".repeat(260);
        runContext.dynamicWorkerResult(java.util.List.of(
            new WorkerTaskResult(dynamicTaskRun(shared + ".taila")),
            new WorkerTaskResult(dynamicTaskRun(shared + ".tailb"))
        ));

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
}