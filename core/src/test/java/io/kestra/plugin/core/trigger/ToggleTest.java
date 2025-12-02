package io.kestra.plugin.core.trigger;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.junit.annotations.LoadFlows;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.triggers.TriggerId;
import io.kestra.core.scheduler.model.TriggerState;
import io.kestra.core.repositories.TriggerRepositoryInterface;
import io.kestra.core.runners.Scheduler;
import io.kestra.core.runners.TestRunnerUtils;
import io.kestra.core.utils.Await;
import jakarta.inject.Inject;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@KestraTest(startRunner = true, startScheduler = true)
class ToggleTest {
    @Inject
    private TriggerRepositoryInterface triggerRepository;
    
    @Inject
    private Scheduler scheduler;

    @Inject
    private TestRunnerUtils runnerUtils;

    @Test
    @LoadFlows({"flows/valids/trigger-toggle.yaml"})
    void toggle() throws Exception {
        // GIVEN
        // we need to await for the scheduler to be ready otherwise there may be an issue with updating the trigger
        Await.until(() -> scheduler.isActive(), Duration.ofMillis(100), Duration.ofSeconds(20));
        TriggerId triggerId = TriggerId.of(MAIN_TENANT, "io.kestra.tests.trigger", "trigger-toggle", "schedule");
        
        // WHEN
        Execution execution = runnerUtils.runOne(MAIN_TENANT, "io.kestra.tests.trigger", "trigger-toggle");
        
        // THEN
        try {
            Await.until(() -> {
                Optional<TriggerState> current = triggerRepository.findById(triggerId);
                return !current.get().isDisabled();
            }, Duration.ofSeconds(1), Duration.ofSeconds(10));
        } catch (TimeoutException e) {
            Assertions.fail("Timeout waiting for trigger-toggle");
        }
        
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.getTaskRunList()).hasSize(1);
    }
}