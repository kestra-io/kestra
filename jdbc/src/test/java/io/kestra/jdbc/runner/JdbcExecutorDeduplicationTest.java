package io.kestra.jdbc.runner;

import com.google.common.hash.Hashing;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.executions.TaskRunAttempt;
import io.kestra.core.models.flows.State;
import io.kestra.core.runners.ExecutorState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcExecutorDeduplicationTest {
    private static final String LARGE_VALUE = "a".repeat(1025);

    private JdbcExecutor jdbcExecutor;
    private Method deduplicateNexts;
    private Execution execution;
    private ExecutorState executorState;

    @BeforeEach
    void setUp() throws Exception {
        this.jdbcExecutor = Mockito.mock(JdbcExecutor.class, Mockito.CALLS_REAL_METHODS);
        this.deduplicateNexts = JdbcExecutor.class.getDeclaredMethod(
            "deduplicateNexts",
            Execution.class,
            ExecutorState.class,
            List.class
        );
        this.deduplicateNexts.setAccessible(true);

        this.execution = Mockito.mock(Execution.class);
        Mockito.when(this.execution.getId()).thenReturn("execution-1");
        this.executorState = new ExecutorState("execution-1");
    }

    @Test
    void shouldStoreHashedKeyWhenValueExceedsThreshold() throws Exception {
        TaskRun taskRun = taskRun("task-run-1", "task-1", "parent-1", LARGE_VALUE, null, 1);

        boolean firstInsert = deduplicate(taskRun);
        boolean secondInsert = deduplicate(taskRun);

        String hashedValue = "hashed_" + Hashing.sha256().hashString(LARGE_VALUE, StandardCharsets.UTF_8);
        String expectedSafeKey = "parent-1-task-1-" + hashedValue + "-01";

        assertThat(firstInsert).isTrue();
        assertThat(secondInsert).isFalse();
        assertThat(executorState.getChildDeduplication()).containsKey(expectedSafeKey);
        assertThat(executorState.getChildDeduplication()).doesNotContainKey("parent-1-task-1-" + LARGE_VALUE + "-01");
    }

    @Test
    void shouldRejectWhenLegacyRawKeyAlreadyExistsForLargeValue() throws Exception {
        TaskRun taskRun = taskRun("task-run-1", "task-1", "parent-1", LARGE_VALUE, List.of(), 2);
        String legacyRawKey = "parent-1-task-1-" + LARGE_VALUE + "-02";
        executorState.getChildDeduplication().put(legacyRawKey, "legacy-task-run-id");

        boolean deduplicated = deduplicate(taskRun);

        assertThat(deduplicated).isFalse();
        assertThat(executorState.getChildDeduplication()).containsEntry(legacyRawKey, "legacy-task-run-id");
        assertThat(executorState.getChildDeduplication()).hasSize(1);
    }

    @Test
    void shouldKeepRawKeyForSmallValue() throws Exception {
        TaskRun taskRun = taskRun("task-run-2", "task-2", "parent-2", "small", null, 3);

        boolean deduplicated = deduplicate(taskRun);

        assertThat(deduplicated).isTrue();
        assertThat(executorState.getChildDeduplication()).containsEntry("parent-2-task-2-small-03", "task-run-2");
    }

    private boolean deduplicate(TaskRun taskRun) throws Exception {
        return (boolean) deduplicateNexts.invoke(jdbcExecutor, execution, executorState, List.of(taskRun));
    }

    private TaskRun taskRun(
        String id,
        String taskId,
        String parentTaskRunId,
        String value,
        List<TaskRunAttempt> attempts,
        Integer iteration
    ) {
        return TaskRun.builder()
            .tenantId("tenant")
            .id(id)
            .executionId("execution-1")
            .namespace("io.kestra.test")
            .flowId("flow")
            .taskId(taskId)
            .parentTaskRunId(parentTaskRunId)
            .value(value)
            .attempts(attempts)
            .state(new State())
            .iteration(iteration)
            .build();
    }
}