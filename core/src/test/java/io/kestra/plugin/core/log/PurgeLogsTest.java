package io.kestra.plugin.core.log;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.junit.annotations.LoadFlows;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.repositories.LogRepositoryInterface;
import io.kestra.core.runners.TestRunnerUtils;
import jakarta.inject.Inject;
import java.time.temporal.ChronoUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.event.Level;

import java.time.Instant;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

@KestraTest(startRunner = true)
class PurgeLogsTest {

    @Inject
    private LogRepositoryInterface logRepository;

    @Inject
    protected TestRunnerUtils runnerUtils;

    @Test
    @LoadFlows("flows/valids/purge_logs_no_arguments.yaml")
    void run_with_no_arguments() throws Exception {
        // create an execution to delete
        var logEntry = LogEntry.builder()
            .namespace("namespace")
            .flowId("flowId")
            .tenantId(MAIN_TENANT)
            .timestamp(Instant.now())
            .level(Level.INFO)
            .message("Hello World")
            .build();
        logRepository.save(logEntry);

        Execution execution = runnerUtils.runOne(MAIN_TENANT, "io.kestra.tests", "purge_logs_no_arguments");

        assertTrue(execution.getState().isSuccess());
        assertThat(execution.getTaskRunList()).hasSize(1);
        assertThat((int) execution.getTaskRunList().getFirst().getOutputs().get("count")).isPositive();
    }

    @org.junit.jupiter.api.parallel.Execution(ExecutionMode.SAME_THREAD)
    @ParameterizedTest
    @MethodSource("buildArguments")
    @LoadFlows("flows/valids/purge_logs_full_arguments.yaml")
    void run_with_full_arguments(LogEntry logEntry, int resultCount, String failingReason) throws Exception {
        logRepository.save(logEntry);

        Execution execution = runnerUtils.runOne(MAIN_TENANT, "io.kestra.tests", "purge_logs_full_arguments");

        assertTrue(execution.getState().isSuccess());
        assertThat(execution.getTaskRunList().size()).isEqualTo(1);
        assertThat(execution.getTaskRunList().getFirst().getOutputs().get("count")).as(failingReason).isEqualTo(resultCount);
    }

    static Stream<Arguments> buildArguments() {
        return Stream.of(
            Arguments.of(LogEntry.builder()
                .namespace("purge.namespace")
                .flowId("purgeFlowId")
                .tenantId(MAIN_TENANT)
                .timestamp(Instant.now().plus(5, ChronoUnit.HOURS))
                .level(Level.INFO)
                .message("Hello World")
                .build(), 0, "The log is too recent to be found"),
            Arguments.of(LogEntry.builder()
                .namespace("purge.namespace")
                .flowId("purgeFlowId")
                .tenantId(MAIN_TENANT)
                .timestamp(Instant.now().minus(5, ChronoUnit.HOURS))
                .level(Level.INFO)
                .message("Hello World")
                .build(), 0, "The log is too old to be found"),
            Arguments.of(LogEntry.builder()
                .namespace("uncorrect.namespace")
                .flowId("purgeFlowId")
                .tenantId(MAIN_TENANT)
                .timestamp(Instant.now().minusSeconds(10))
                .level(Level.INFO)
                .message("Hello World")
                .build(), 0, "The log has an incorrect namespace"),
            Arguments.of(LogEntry.builder()
                .namespace("purge.namespace")
                .flowId("wrongFlowId")
                .tenantId(MAIN_TENANT)
                .timestamp(Instant.now().minusSeconds(10))
                .level(Level.INFO)
                .message("Hello World")
                .build(), 0, "The log has an incorrect flow id"),
            Arguments.of(LogEntry.builder()
                .namespace("purge.namespace")
                .flowId("purgeFlowId")
                .tenantId(MAIN_TENANT)
                .timestamp(Instant.now().minusSeconds(10))
                .level(Level.WARN)
                .message("Hello World")
                .build(), 0, "The log has an incorrect LogLevel"),
            Arguments.of(LogEntry.builder()
                .namespace("purge.namespace")
                .flowId("purgeFlowId")
                .tenantId(MAIN_TENANT)
                .timestamp(Instant.now().minusSeconds(10))
                .level(Level.INFO)
                .message("Hello World")
                .build(), 1, "The log should be deleted")
        );
    }
}