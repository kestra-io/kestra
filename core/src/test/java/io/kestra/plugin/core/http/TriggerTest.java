package io.kestra.plugin.core.http;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.junit.annotations.LoadFlowsWithTenant;
import io.kestra.core.runners.Scheduler;
import io.kestra.core.runners.TestRunnerUtils;
import jakarta.inject.Inject;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import java.time.Duration;

@KestraTest(startRunner = true, startScheduler = true)
class TriggerTest {
    @Inject
    private TestRunnerUtils runnerUtils;

    @Inject
    protected Scheduler scheduler;

    @Test
    @LoadFlowsWithTenant({"flows/valids/http-listen.yaml"})
    void shouldExecuteFlowForHttpTrigger(String tenantId) {
        Awaitility.await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofMillis(100)).until(() -> scheduler.isActive());

        runnerUtils.awaitFlowExecution(tenantId, "io.kestra.tests", "http-listen", Duration.ofSeconds(20));
    }

    @Test
    @LoadFlowsWithTenant({"flows/valids/http-listen-encrypted.yaml"})
    void shouldExecuteFlowForHttpTriggerWithEncryptedBody(String tenantId) {
        Awaitility.await().atMost(Duration.ofSeconds(20)).pollInterval(Duration.ofMillis(100)).until(() -> scheduler.isActive());

        runnerUtils.awaitFlowExecution(tenantId, "io.kestra.tests", "http-listen-encrypted", Duration.ofSeconds(20));
    }
}
