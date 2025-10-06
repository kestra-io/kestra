package io.kestra.core.runners;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.flows.State.Type;
import io.kestra.core.queues.QueueException;
import io.kestra.core.repositories.FlowRepositoryInterface;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.time.ZonedDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@Singleton
public class ScheduleDateCaseTest {
    @Inject
    private FlowRepositoryInterface flowRepository;
    @Inject
    private TestRunnerUtils runnerUtils;

    public void shouldScheduleOnDate(String tenantId) throws QueueException {
        ZonedDateTime scheduleOn = ZonedDateTime.now().plusSeconds(1);
        Flow flow = flowRepository.findById(tenantId, "io.kestra.tests", "minimal").orElseThrow();
        Execution execution = Execution.newExecution(flow, null, null, Optional.of(scheduleOn));
        assertThat(execution.getScheduleDate()).isEqualTo(scheduleOn.toInstant());
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.CREATED);

        runnerUtils.emitAndAwaitExecution(e -> e.getState().getCurrent().equals(Type.SUCCESS), execution);
    }
}
