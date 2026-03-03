package io.kestra.core.runners;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.QueueException;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.services.IgnoreExecutionService;
import io.kestra.core.utils.Await;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeoutException;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;

@Singleton
public class IgnoreExecutionCaseTest {
    @Inject
    @Named(QueueFactoryInterface.EXECUTION_NAMED)
    protected QueueInterface<Execution> executionQueue;

    @Inject
    protected TestRunnerUtils runnerUtils;

    @Inject
    private ExecutionRepositoryInterface executionRepository;

    @Inject
    private IgnoreExecutionService ignoreExecutionService;

    @Inject
    private FlowRepositoryInterface flowRepository;

    public void shouldIgnoreExecutionById() throws TimeoutException, QueueException {
        Flow flow = flowRepository.findById(MAIN_TENANT, "io.kestra.tests", "minimal").orElseThrow();
        Execution execution1 = Execution.newExecution(flow, null, null, Optional.empty());
        String execution1Id = execution1.getId();
        ignoreExecutionService.setIgnoredExecutions(List.of(execution1Id));

        executionQueue.emit(execution1);
        Execution execution2 = runnerUtils.runOne(MAIN_TENANT, "io.kestra.tests", "minimal");

        // the execution 2 should be in success and the 1 still created
        assertThat(execution2.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        execution1 = Await.until(() -> executionRepository.findById(MAIN_TENANT, execution1Id).orElse(null), Duration.ofMillis(100), Duration.ofSeconds(1));
        assertThat(execution1.getState().getCurrent()).isEqualTo(State.Type.CREATED);
    }

    public void shouldIgnoreExecutionByFlowId() throws TimeoutException, QueueException {
        Flow flow = flowRepository.findById(MAIN_TENANT, "io.kestra.tests", "output-values").orElseThrow();
        Execution execution1 = Execution.newExecution(flow, null, null, Optional.empty());
        String execution1Id = execution1.getId();
        ignoreExecutionService.setIgnoredFlows(List.of(MAIN_TENANT + "|" + "io.kestra.tests" + "|" + "output-values"));

        executionQueue.emit(execution1);
        Execution execution2 = runnerUtils.runOne(MAIN_TENANT, "io.kestra.tests", "minimal");

        // the execution 2 should be in success and the 1 still created
        assertThat(execution2.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        execution1 = Await.until(() -> executionRepository.findById(MAIN_TENANT, execution1Id).orElse(null), Duration.ofMillis(100), Duration.ofSeconds(1));
        assertThat(execution1.getState().getCurrent()).isEqualTo(State.Type.CREATED);
    }

    public void shouldIgnoreExecutionByNamespace() throws TimeoutException, QueueException {
        Flow flow = flowRepository.findById(MAIN_TENANT, "io.kestra.tests2", "minimal").orElseThrow();
        Execution execution1 = Execution.newExecution(flow, null, null, Optional.empty());
        String execution1Id = execution1.getId();
        ignoreExecutionService.setIgnoredNamespaces(List.of(MAIN_TENANT + "|" + "io.kestra.tests2"));

        executionQueue.emit(execution1);
        Execution execution2 = runnerUtils.runOne(MAIN_TENANT, "io.kestra.tests", "minimal");

        // the execution 2 should be in success and the 1 still created
        assertThat(execution2.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        execution1 = Await.until(() -> executionRepository.findById(MAIN_TENANT, execution1Id).orElse(null), Duration.ofMillis(100), Duration.ofSeconds(1));
        assertThat(execution1.getState().getCurrent()).isEqualTo(State.Type.CREATED);
    }
}
