package io.kestra.executor;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.kestra.core.assets.AssetService;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.metrics.MetricRegistry;
import io.kestra.core.models.assets.Asset;
import io.kestra.core.models.assets.AssetsInOut;
import io.kestra.core.models.assets.Custom;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionKind;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.flows.State;
import io.kestra.core.runners.WorkerTaskResult;
import io.kestra.core.utils.IdUtils;
import io.kestra.plugin.core.log.Log;

import io.micrometer.core.instrument.MeterRegistry;
import io.micronaut.test.annotation.MockBean;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Covers ExecutorService's own logic around processTaskRunAssets: the guard deciding whether to call
 * it, and applying the escalated TaskRun it returns. The escalation logic itself (assetFailureBehavior,
 * allowFailure/allowWarning clamps, mark-and-continue upserts) only exists in the EE AssetService
 * implementation and is covered there directly (core-ee's AssetServiceTest).
 */
@KestraTest
class ExecutorServiceProcessTaskRunAssetsTest {
    @Inject
    private ExecutorService executorService;

    @Inject
    private AssetService assetService;

    @Inject
    private MeterRegistry meterRegistry;

    @MockBean(AssetService.class)
    AssetService assetService() {
        return mock(AssetService.class);
    }

    @BeforeEach
    void setUp() {
        reset(assetService);
    }

    @Test
    void shouldNotCallAssetServiceWhenTaskRunHasNoAssets() throws Exception {
        runExecutor(null, ExecutionKind.NORMAL, State.Type.SUCCESS);

        verifyNoInteractions(assetService);
    }

    @Test
    void shouldNotCallAssetServiceWhenAssetsAreEmpty() throws Exception {
        runExecutor(new AssetsInOut(Collections.emptyList(), Collections.emptyList()), ExecutionKind.NORMAL, State.Type.SUCCESS);

        verifyNoInteractions(assetService);
    }

    @Test
    void shouldNotCallAssetServiceForTestExecutions() throws Exception {
        runExecutor(new AssetsInOut(Collections.emptyList(), List.of(asset("a"))), ExecutionKind.TEST, State.Type.SUCCESS);

        verifyNoInteractions(assetService);
    }

    @Test
    void shouldNotCallAssetServiceWhenTaskRunNotTerminated() throws Exception {
        runExecutor(new AssetsInOut(Collections.emptyList(), List.of(asset("a"))), ExecutionKind.NORMAL, State.Type.RUNNING);

        verifyNoInteractions(assetService);
    }

    @Test
    void shouldApplyEscalatedTaskRunWhenAssetServiceReturnsOne() throws Exception {
        when(assetService.processTaskRunAssets(any(), any(), any(), any(), any()))
            .thenAnswer(invocation -> Optional.of(((TaskRun) invocation.getArgument(1)).withState(State.Type.WARNING)));

        ExecutorContext executor = runExecutor(new AssetsInOut(Collections.emptyList(), List.of(asset("a"))), ExecutionKind.NORMAL, State.Type.SUCCESS);

        assertThat(executor.getExecution().getTaskRunList().getFirst().getState().getCurrent()).isEqualTo(State.Type.WARNING);
        // the ended-count metric must be tagged from the escalated state, not the original SUCCESS the
        // taskRun actually finished with
        String flowId = executor.getFlow().getId();
        assertThat(
            meterRegistry.get(MetricRegistry.METRIC_EXECUTOR_TASKRUN_ENDED_COUNT)
                .tag(MetricRegistry.TAG_FLOW_ID, flowId)
                .tag(MetricRegistry.TAG_STATE, State.Type.WARNING.name())
                .counter().count()
        ).isEqualTo(1.0);
    }

    @Test
    void shouldKeepTaskRunResultWhenAssetServiceThrows() throws Exception {
        when(assetService.processTaskRunAssets(any(), any(), any(), any(), any()))
            .thenThrow(new RuntimeException("boom"));

        ExecutorContext executor = runExecutor(new AssetsInOut(Collections.emptyList(), List.of(asset("a"))), ExecutionKind.NORMAL, State.Type.SUCCESS);

        // a bug in asset-lineage/escalation processing must not prevent the taskRun's own
        // already-terminated result from being recorded
        assertThat(executor.getExecution().getTaskRunList().getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        String flowId = executor.getFlow().getId();
        assertThat(
            meterRegistry.get(MetricRegistry.METRIC_EXECUTOR_TASKRUN_ENDED_COUNT)
                .tag(MetricRegistry.TAG_FLOW_ID, flowId)
                .tag(MetricRegistry.TAG_STATE, State.Type.SUCCESS.name())
                .counter().count()
        ).isEqualTo(1.0);
    }

    @Test
    void shouldKeepOriginalTaskRunWhenAssetServiceReturnsEmpty() throws Exception {
        when(assetService.processTaskRunAssets(any(), any(), any(), any(), any())).thenReturn(Optional.empty());

        ExecutorContext executor = runExecutor(new AssetsInOut(Collections.emptyList(), List.of(asset("a"))), ExecutionKind.NORMAL, State.Type.SUCCESS);

        assertThat(executor.getExecution().getTaskRunList().getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        verify(assetService).processTaskRunAssets(any(), any(), any(), any(), any());
    }

    private Asset asset(String id) {
        return Custom.builder().tenantId("tenant").namespace("io.kestra.unit-test").id(id).type("io.kestra.Custom").build();
    }

    private ExecutorContext runExecutor(AssetsInOut assets, ExecutionKind kind, State.Type terminalState) throws Exception {
        var task = Log.builder().id("task").type(Log.class.getName()).message("hello").build();
        var flow = Flow.builder().tenantId("tenant").namespace("io.kestra.unit-test").id(IdUtils.create()).tasks(List.of(task)).build();

        var placeholder = TaskRun.builder()
            .tenantId("tenant")
            .id(IdUtils.create())
            .executionId(IdUtils.create())
            .namespace(flow.getNamespace())
            .flowId(flow.getId())
            .taskId(task.getId())
            .state(new State())
            .build();

        var execution = Execution.builder()
            .tenantId("tenant")
            .id(placeholder.getExecutionId())
            .namespace(flow.getNamespace())
            .flowId(flow.getId())
            .flowRevision(1)
            .kind(kind)
            .state(new State())
            .taskRunList(List.of(placeholder))
            .build();

        var terminalTaskRun = placeholder
            .withState(terminalState)
            .withAssetEmits(assets == null ? null : List.of(assets));

        var executor = new ExecutorContext(execution, FlowWithSource.of(flow, "flow-source"));
        var workerTaskResult = new WorkerTaskResult(terminalTaskRun);

        executorService.addWorkerTaskResult(executor, executor::getFlow, workerTaskResult);

        return executor;
    }
}
