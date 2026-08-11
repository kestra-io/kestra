package io.kestra.core.runners;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeoutException;

import io.kestra.core.executor.command.Create;
import io.kestra.core.executor.command.ExecutionCommand;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionId;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.DispatchQueueInterface;
import io.kestra.core.queues.QueueException;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.services.IgnoreExecutionService;
import io.kestra.core.utils.Await;
import io.kestra.core.utils.IdUtils;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;

@Singleton
public class IgnoreExecutionCaseTest {
    @Inject
    protected DispatchQueueInterface<ExecutionCommand> executionCommandQueue;

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
        String execution1Id = IdUtils.create();
        ignoreExecutionService.setIgnoredExecutions(List.of(execution1Id));

        executionCommandQueue.emit(Create.of(new ExecutionId(flow.getTenantId(), flow.getNamespace(), flow.getId(), execution1Id, flow.getRevision())));
        Execution execution2 = runnerUtils.runOne(MAIN_TENANT, "io.kestra.tests", "minimal");

        // the execution 2 should be in success and the 1 still created
        assertThat(execution2.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        Execution execution1 = Await.until(() -> executionRepository.findById(MAIN_TENANT, execution1Id).orElse(null), Duration.ofMillis(100), Duration.ofSeconds(1));
        assertThat(execution1.getState().getCurrent()).isEqualTo(State.Type.CREATED);
    }

    public void shouldIgnoreExecutionByFlowId() throws TimeoutException, QueueException {
        Flow flow = flowRepository.findById(MAIN_TENANT, "io.kestra.tests", "output-values").orElseThrow();
        String execution1Id = IdUtils.create();
        ignoreExecutionService.setIgnoredFlows(List.of(MAIN_TENANT + "|" + "io.kestra.tests" + "|" + "output-values"));

        executionCommandQueue.emit(Create.of(new ExecutionId(flow.getTenantId(), flow.getNamespace(), flow.getId(), execution1Id, flow.getRevision())));
        Execution execution2 = runnerUtils.runOne(MAIN_TENANT, "io.kestra.tests", "minimal");

        // the execution 2 should be in success and the 1 still created
        assertThat(execution2.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        Execution execution1 = Await.until(() -> executionRepository.findById(MAIN_TENANT, execution1Id).orElse(null), Duration.ofMillis(100), Duration.ofSeconds(1));
        assertThat(execution1.getState().getCurrent()).isEqualTo(State.Type.CREATED);
    }

    public void shouldIgnoreExecutionByNamespace() throws TimeoutException, QueueException {
        Flow flow = flowRepository.findById(MAIN_TENANT, "io.kestra.tests2", "minimal").orElseThrow();
        String execution1Id = IdUtils.create();
        ignoreExecutionService.setIgnoredNamespaces(List.of(MAIN_TENANT + "|" + "io.kestra.tests2"));

        executionCommandQueue.emit(Create.of(new ExecutionId(flow.getTenantId(), flow.getNamespace(), flow.getId(), execution1Id, flow.getRevision())));
        Execution execution2 = runnerUtils.runOne(MAIN_TENANT, "io.kestra.tests", "minimal");

        // the execution 2 should be in success and the 1 still created
        assertThat(execution2.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        Execution execution1 = Await.until(() -> executionRepository.findById(MAIN_TENANT, execution1Id).orElse(null), Duration.ofMillis(100), Duration.ofSeconds(1));
        assertThat(execution1.getState().getCurrent()).isEqualTo(State.Type.CREATED);
    }
}
