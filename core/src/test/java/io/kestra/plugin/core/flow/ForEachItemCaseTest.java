package io.kestra.plugin.core.flow;

import io.kestra.core.models.Label;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.QueueException;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.runners.FlowInputOutput;
import io.kestra.core.runners.TestRunnerUtils;
import io.kestra.core.services.ExecutionService;
import io.kestra.core.storages.StorageInterface;
import io.micronaut.data.model.Pageable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.stream.IntStream;

import static io.kestra.core.models.flows.State.Type.FAILED;
import static io.kestra.core.models.flows.State.Type.SUCCESS;
import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@Singleton
public class ForEachItemCaseTest {
    static final String TEST_NAMESPACE = "io.kestra.tests";

    @Inject
    private StorageInterface storageInterface;

    @Inject
    protected TestRunnerUtils runnerUtils;

    @Inject
    private FlowInputOutput flowIO;

    @Inject
    private ExecutionService executionService;

    @Inject
    private ExecutionRepositoryInterface executionRepository;

    @SuppressWarnings("unchecked")
    public void forEachItem() throws TimeoutException, URISyntaxException, IOException, QueueException {
        URI file = storageUpload(MAIN_TENANT);
        Map<String, Object> inputs = Map.of("file", file.toString(), "batch", 4);
        Execution execution = runnerUtils.runOne(MAIN_TENANT, TEST_NAMESPACE, "for-each-item", null,
            (flow, execution1) -> flowIO.readExecutionInputs(flow, execution1, inputs),
            Duration.ofSeconds(30));

        // we should have triggered 26 subflows
        List<Execution> triggeredExecs = runnerUtils.awaitFlowExecutionNumber(26, MAIN_TENANT, TEST_NAMESPACE, "for-each-item-subflow");

        // assert that iteration starts at 0
        Execution firstTriggered = triggeredExecs.stream()
            .filter(e -> e.getTrigger() != null && e.getTrigger().getVariables().get("taskRunIteration") != null)
            .filter(e -> (Integer) e.getTrigger().getVariables().get("taskRunIteration") == 0)
            .findFirst()
            .orElse(null);
        assertThat(firstTriggered).isNotNull();
        assertThat(firstTriggered.getTrigger().getVariables().get("taskRunIteration")).isEqualTo(0);

        // assert on the main flow execution
        assertThat(execution.getTaskRunList()).hasSize(4);
        assertThat(execution.getTaskRunList().get(2).getAttempts()).hasSize(1);
        assertThat(execution.getTaskRunList().get(2).getAttempts().getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        Map<String, Object> outputs = execution.getTaskRunList().get(2).getOutputs();
        assertThat(outputs.get("numberOfBatches")).isEqualTo(26);
        assertThat(outputs.get("iterations")).isNotNull();
        Map<String, Integer> iterations = (Map<String, Integer>) outputs.get("iterations");
        assertThat(iterations.get("CREATED")).isZero();
        assertThat(iterations.get("RUNNING")).isZero();
        assertThat(iterations.get("SUCCESS")).isEqualTo(26);

        // assert on the last subflow execution
        Execution triggered = triggeredExecs.getLast();
        assertThat(triggered.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(triggered.getFlowId()).isEqualTo("for-each-item-subflow");
        assertThat((String) triggered.getInputs().get("items")).matches("kestra:///io/kestra/tests/for-each-item/executions/.*/tasks/each-split/.*\\.txt");
        assertThat(triggered.getTaskRunList()).hasSize(1);
        Optional<Label> correlationId = triggered.getLabels().stream().filter(label -> label.key().equals(Label.CORRELATION_ID)).findAny();
        assertThat(correlationId.isPresent()).isTrue();
        assertThat(correlationId.get().value()).isEqualTo(execution.getId());
    }

    public void forEachItemEmptyItems(String tenantId) throws TimeoutException, URISyntaxException, IOException, QueueException {
        URI file = emptyItems(tenantId);
        Map<String, Object> inputs = Map.of("file", file.toString(), "batch", 4);
        Execution execution = runnerUtils.runOne(tenantId, TEST_NAMESPACE, "for-each-item", null,
            (flow, execution1) -> flowIO.readExecutionInputs(flow, execution1, inputs),
            Duration.ofSeconds(30));

        // assert on the main flow execution
        assertThat(execution.getTaskRunList()).hasSize(4);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        Map<String, Object> outputs = execution.getTaskRunList().get(2).getOutputs();
        assertThat(outputs).isNull();
    }

    @SuppressWarnings("unchecked")
    public void forEachItemNoWait() throws TimeoutException, URISyntaxException, IOException, QueueException {
        URI file = storageUpload(MAIN_TENANT);
        Map<String, Object> inputs = Map.of("file", file.toString());
        Execution execution = runnerUtils.runOne(MAIN_TENANT, TEST_NAMESPACE, "for-each-item-no-wait", null,
            (flow, execution1) -> flowIO.readExecutionInputs(flow, execution1, inputs),
            Duration.ofSeconds(30));

        // assert that not all subflows ran (depending on the speed of execution, there can be some)
        // be careful that it's racy.
        ArrayListTotal<Execution> subFlowExecs = executionRepository.findByFlowId(MAIN_TENANT,
            TEST_NAMESPACE, "for-each-item-subflow-sleep", Pageable.UNPAGED);
        assertThat(subFlowExecs.size()).isLessThanOrEqualTo(26);

        // assert on the main flow execution
        assertThat(execution.getTaskRunList()).hasSize(4);
        assertThat(execution.getTaskRunList().get(2).getAttempts()).hasSize(1);
        assertThat(execution.getTaskRunList().get(2).getAttempts().getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        Map<String, Object> outputs = execution.getTaskRunList().get(2).getOutputs();
        assertThat(outputs.get("numberOfBatches")).isEqualTo(26);
        assertThat(outputs.get("iterations")).isNotNull();
        Map<String, Integer> iterations = (Map<String, Integer>) outputs.get("iterations");
        assertThat(iterations.get("CREATED")).isNull(); // if we didn't wait we will only observe RUNNING and SUCCESS
        assertThat(iterations.get("RUNNING")).isZero();
        assertThat(iterations.get("SUCCESS")).isEqualTo(26);

        // wait for the 26 flows to ends
        List<Execution> triggeredExecs = runnerUtils.awaitFlowExecutionNumber(26, MAIN_TENANT, TEST_NAMESPACE, "for-each-item-subflow-sleep");
        Execution triggered = triggeredExecs.getLast();

        // assert on the last subflow execution
        assertThat(triggered.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(triggered.getFlowId()).isEqualTo("for-each-item-subflow-sleep");
        assertThat((String) triggered.getInputs().get("items")).matches("kestra:///io/kestra/tests/for-each-item-no-wait/executions/.*/tasks/each-split/.*\\.txt");
        assertThat(triggered.getTaskRunList()).hasSize(2);
    }

    @SuppressWarnings("unchecked")
    public void forEachItemFailed() throws TimeoutException, URISyntaxException, IOException, QueueException {
        URI file = storageUpload(MAIN_TENANT);
        Map<String, Object> inputs = Map.of("file", file.toString());
        Execution execution = runnerUtils.runOne(MAIN_TENANT, TEST_NAMESPACE, "for-each-item-failed", null,
            (flow, execution1) -> flowIO.readExecutionInputs(flow, execution1, inputs),
            Duration.ofSeconds(60));

        // we should have triggered 26 subflows
        List<Execution> triggeredExecs = runnerUtils.awaitFlowExecutionNumber(26, MAIN_TENANT, TEST_NAMESPACE, "for-each-item-subflow-failed");
        Execution triggered = triggeredExecs.getLast();

        // assert on the main flow execution
        assertThat(execution.getTaskRunList()).hasSize(3);
        assertThat(execution.getTaskRunList().get(2).getAttempts()).hasSize(1);
        assertThat(execution.getTaskRunList().get(2).getAttempts().getFirst().getState().getCurrent()).isEqualTo(FAILED);
        assertThat(execution.getState().getCurrent()).isEqualTo(FAILED);
        Map<String, Object> outputs = execution.getTaskRunList().get(2).getOutputs();
        assertThat(outputs.get("numberOfBatches")).isEqualTo(26);
        assertThat(outputs.get("iterations")).isNotNull();
        Map<String, Integer> iterations = (Map<String, Integer>) outputs.get("iterations");
        assertThat(iterations.get("CREATED")).isZero();
        assertThat(iterations.get("RUNNING")).isZero();
        assertThat(iterations.get("FAILED")).isEqualTo(26);

        // assert on the last subflow execution
        assertThat(triggered.getState().getCurrent()).isEqualTo(FAILED);
        assertThat(triggered.getFlowId()).isEqualTo("for-each-item-subflow-failed");
        assertThat((String) triggered.getInputs().get("items")).matches("kestra:///io/kestra/tests/for-each-item-failed/executions/.*/tasks/each-split/.*\\.txt");
        assertThat(triggered.getTaskRunList()).hasSize(1);
    }

    @SuppressWarnings("unchecked")
    public void forEachItemWithSubflowOutputs() throws TimeoutException, URISyntaxException, IOException, QueueException {
        URI file = storageUpload(MAIN_TENANT);
        Map<String, Object> inputs = Map.of("file", file.toString());
        Execution execution = runnerUtils.runOne(MAIN_TENANT, TEST_NAMESPACE, "for-each-item-outputs", null,
            (flow, execution1) -> flowIO.readExecutionInputs(flow, execution1, inputs),
            Duration.ofSeconds(30));

        // we should have triggered 26 subflows
        List<Execution> triggeredExecs = runnerUtils.awaitFlowExecutionNumber(26, MAIN_TENANT, TEST_NAMESPACE, "for-each-item-outputs-subflow");
        Execution triggered = triggeredExecs.getLast();

        // assert on the main flow execution
        assertThat(execution.getTaskRunList()).hasSize(5);
        assertThat(execution.getTaskRunList().get(2).getAttempts()).hasSize(1);
        assertThat(execution.getTaskRunList().get(2).getAttempts().getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        Map<String, Object> outputs = execution.getTaskRunList().get(2).getOutputs();
        assertThat(outputs.get("numberOfBatches")).isEqualTo(26);
        assertThat(outputs.get("iterations")).isNotNull();

        Map<String, Integer> iterations = (Map<String, Integer>) outputs.get("iterations");
        assertThat(iterations.get("CREATED")).isZero();
        assertThat(iterations.get("RUNNING")).isZero();
        assertThat(iterations.get("SUCCESS")).isEqualTo(26);

        // assert on the last subflow execution
        assertThat(triggered.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(triggered.getFlowId()).isEqualTo("for-each-item-outputs-subflow");
        assertThat((String) triggered.getInputs().get("items")).matches("kestra:///io/kestra/tests/for-each-item-outputs/executions/.*/tasks/each-split/.*\\.txt");
        assertThat(triggered.getTaskRunList()).hasSize(1);

        // asserts for subflow merged outputs
        Map<String, Object> mergeTaskOutputs = execution.getTaskRunList().get(3).getOutputs();
        assertThat(mergeTaskOutputs.get("subflowOutputs")).isNotNull();
        InputStream stream = storageInterface.get(MAIN_TENANT, execution.getNamespace(), URI.create((String) mergeTaskOutputs.get("subflowOutputs")));

        try (var br = new BufferedReader(new InputStreamReader(stream))) {
            // one line per sub-flows
            assertThat(br.lines().count()).isEqualTo(26L);
        }
    }

    public void restartForEachItem(String tenantId) throws Exception {
        URI file = storageUpload(tenantId);
        Map<String, Object> inputs = Map.of("file", file.toString(), "batch", 20);
        final Execution failedExecution = runnerUtils.runOne(tenantId, TEST_NAMESPACE, "restart-for-each-item", null,
            (flow, execution1) -> flowIO.readExecutionInputs(flow, execution1, inputs),
            Duration.ofSeconds(30));
        assertThat(failedExecution.getTaskRunList()).hasSize(3);
        assertThat(failedExecution.getState().getCurrent()).isEqualTo(FAILED);

        // here we must have 1 failed subflows
        List<Execution> triggeredExecs = runnerUtils.awaitFlowExecutionNumber(6, tenantId, TEST_NAMESPACE, "restart-child");
        assertThat(triggeredExecs).extracting(e -> e.getState().getCurrent()).containsOnly(FAILED);

        Execution restarted = executionService.restart(failedExecution, null);
        final Execution successExecution = runnerUtils.restartExecution(
            e -> e.getState().getCurrent() == State.Type.SUCCESS && e.getFlowId().equals("restart-for-each-item"),
            restarted
        );
        assertThat(successExecution.getTaskRunList()).hasSize(4);
        triggeredExecs = runnerUtils.awaitFlowExecutionNumber(6, tenantId, TEST_NAMESPACE, "restart-child");
        assertThat(triggeredExecs).extracting(e -> e.getState().getCurrent()).containsOnly(SUCCESS);
    }

    public void forEachItemInIf(String tenantId) throws TimeoutException, URISyntaxException, IOException, QueueException {
        URI file = storageUpload(tenantId);
        Map<String, Object> inputs = Map.of("file", file.toString(), "batch", 4);
        Execution execution = runnerUtils.runOne(tenantId, TEST_NAMESPACE, "for-each-item-in-if", null,
            (flow, execution1) -> flowIO.readExecutionInputs(flow, execution1, inputs),
            Duration.ofSeconds(30));

        // we should have triggered 26 subflows
        List<Execution> triggeredExecs = runnerUtils.awaitFlowExecutionNumber(26, tenantId, TEST_NAMESPACE, "for-each-item-subflow");
        Execution triggered = triggeredExecs.getLast();

        // assert on the main flow execution
        assertThat(execution.getTaskRunList()).hasSize(5);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        Map<String, Object> outputs = execution.getTaskRunList().get(3).getOutputs();
        assertThat(outputs.get("numberOfBatches")).isEqualTo(26);
        assertThat(outputs.get("iterations")).isNotNull();
        Map<String, Integer> iterations = (Map<String, Integer>) outputs.get("iterations");
        assertThat(iterations.get("CREATED")).isZero();
        assertThat(iterations.get("RUNNING")).isZero();
        assertThat(iterations.get("SUCCESS")).isEqualTo(26);

        // assert on the last subflow execution
        assertThat(triggered.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(triggered.getFlowId()).isEqualTo("for-each-item-subflow");
        assertThat((String) triggered.getInputs().get("items")).matches("kestra:///io/kestra/tests/for-each-item-in-if/executions/.*/tasks/each-split/.*\\.txt");
        assertThat(triggered.getTaskRunList()).hasSize(1);
        Optional<Label> correlationId = triggered.getLabels().stream().filter(label -> label.key().equals(Label.CORRELATION_ID)).findAny();
        assertThat(correlationId.isPresent()).isTrue();
        assertThat(correlationId.get().value()).isEqualTo(execution.getId());
    }

    public void forEachItemWithAfterExecution() throws TimeoutException, URISyntaxException, IOException, QueueException {
        URI file = storageUpload(MAIN_TENANT);
        Map<String, Object> inputs = Map.of("file", file.toString(), "batch", 4);
        Execution execution = runnerUtils.runOne(MAIN_TENANT, TEST_NAMESPACE, "for-each-item-after-execution", null,
            (flow, execution1) -> flowIO.readExecutionInputs(flow, execution1, inputs),
            Duration.ofSeconds(30));

        // we should have triggered 26 subflows
        List<Execution> triggeredExecs = runnerUtils.awaitFlowExecutionNumber(26, MAIN_TENANT, TEST_NAMESPACE, "for-each-item-subflow-after-execution");
        Execution triggered = triggeredExecs.getLast();

        // assert on the main flow execution
        assertThat(execution.getTaskRunList()).hasSize(5);
        assertThat(execution.getTaskRunList().get(2).getAttempts()).hasSize(1);
        assertThat(execution.getTaskRunList().get(2).getAttempts().getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        Map<String, Object> outputs = execution.getTaskRunList().get(2).getOutputs();
        assertThat(outputs.get("numberOfBatches")).isEqualTo(26);
        assertThat(outputs.get("iterations")).isNotNull();
        Map<String, Integer> iterations = (Map<String, Integer>) outputs.get("iterations");
        assertThat(iterations.get("CREATED")).isZero();
        assertThat(iterations.get("RUNNING")).isZero();
        assertThat(iterations.get("SUCCESS")).isEqualTo(26);

        // assert on the last subflow execution
        assertThat(triggered.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(triggered.getFlowId()).isEqualTo("for-each-item-subflow-after-execution");
        assertThat((String) triggered.getInputs().get("items")).matches("kestra:///io/kestra/tests/for-each-item-after-execution/executions/.*/tasks/each-split/.*\\.txt");
        assertThat(triggered.getTaskRunList()).hasSize(2);
        Optional<Label> correlationId = triggered.getLabels().stream().filter(label -> label.key().equals(Label.CORRELATION_ID)).findAny();
        assertThat(correlationId.isPresent()).isTrue();
        assertThat(correlationId.get().value()).isEqualTo(execution.getId());
    }

    private URI storageUpload(String tenantId) throws URISyntaxException, IOException {
        File tempFile = File.createTempFile("file", ".txt");

        Files.write(tempFile.toPath(), content());

        return storageInterface.put(
            tenantId,
            null,
            new URI("/file/storage/file.txt"),
            new FileInputStream(tempFile)
        );
    }

    private URI emptyItems(String tenantId) throws URISyntaxException, IOException {
        File tempFile = File.createTempFile("file", ".txt");

        return storageInterface.put(
            tenantId,
            null,
            new URI("/file/storage/file.txt"),
            new FileInputStream(tempFile)
        );
    }

    private List<String> content() {
        return IntStream
            .range(0, 102)
            .mapToObj(value -> StringUtils.leftPad(value + "", 20))
            .toList();
    }
}
