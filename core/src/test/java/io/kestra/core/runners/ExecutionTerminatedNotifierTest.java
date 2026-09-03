package io.kestra.core.runners;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.junit.annotations.LoadFlows;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;

import io.micronaut.context.annotation.Replaces;
import io.micronaut.test.annotation.MockBean;
import jakarta.inject.Inject;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

@KestraTest(startRunner = true)
class ExecutionTerminatedNotifierTest {
    @Inject
    TestRunnerUtils runnerUtils;

    @Inject
    ExecutionTerminatedNotifier executionTerminatedNotifier;

    @MockBean
    @Replaces(ExecutionTerminatedNotifier.NoopExecutionTerminatedNotifier.class)
    ExecutionTerminatedNotifier executionTerminatedNotifier() {
        return mock(ExecutionTerminatedNotifier.class);
    }

    @BeforeEach
    void resetMock() {
        // The mock is bound once at DefaultExecutor construction and shared across this class's
        // test methods (the runner context persists between them), so invocations accumulate
        // unless cleared here.
        reset(executionTerminatedNotifier);
    }

    @Test
    @LoadFlows({ "flows/valids/restart_always_failed.yaml" })
    void notifiesOnceOnFailure() throws Exception {
        runnerUtils.runOne(MAIN_TENANT, "io.kestra.tests", "restart_always_failed");

        // The execution's terminal DB row is visible to runOne() as soon as the state transition
        // commits, which happens before this notifier fires later in the same doRun() cycle — so
        // the call can trail runOne()'s return by a few milliseconds; timeout() tolerates that gap
        // instead of racing it.
        verify(executionTerminatedNotifier, timeout(2000).times(1))
            .executionTerminated(argThat(execution -> execution.getState().isFailed()));
    }

    @Test
    @LoadFlows({ "flows/valids/minimal.yaml" })
    void notifiesOnceOnSuccess() throws Exception {
        runnerUtils.runOne(MAIN_TENANT, "io.kestra.tests", "minimal");

        verify(executionTerminatedNotifier, timeout(2000).times(1))
            .executionTerminated(argThat(execution -> execution.getState().getCurrent() == State.Type.SUCCESS));
    }

    @Test
    @LoadFlows({ "flows/valids/restart_always_failed.yaml" })
    void reachesFailedEvenWhenNotifierThrows() throws Exception {
        doThrow(new RuntimeException("notifier boom")).when(executionTerminatedNotifier).executionTerminated(any());

        Execution execution = runnerUtils.runOne(MAIN_TENANT, "io.kestra.tests", "restart_always_failed");

        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.FAILED);
    }
}
