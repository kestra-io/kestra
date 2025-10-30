package io.kestra.core.runners;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;

import io.kestra.core.junit.annotations.ExecuteFlow;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.junit.annotations.LoadFlows;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.QueueException;
import io.kestra.core.storages.StorageInterface;
import jakarta.inject.Inject;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.stream.IntStream;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;

@KestraTest(startRunner = true)
public class TaskWithAllowFailureTest {
    @Inject
    private StorageInterface storageInterface;

    @Inject
    private FlowInputOutput flowIO;

    @Inject
    private TestRunnerUtils runnerUtils;

    @Test
    @ExecuteFlow("flows/valids/task-allow-failure-runnable.yml")
    void runnableTask(Execution execution) {
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.WARNING);
        assertThat(execution.getTaskRunList()).hasSize(2);
        assertThat(execution.findTaskRunsByTaskId("fail").getFirst().getAttempts().size()).isEqualTo(3);
    }

    @Test
    @LoadFlows(value = {"flows/valids/task-allow-failure-executable-flow.yml",
        "flows/valids/for-each-item-subflow-failed.yaml"}, tenantId = "tenant1")
    void executableTask_Flow() throws QueueException, TimeoutException {
        Execution execution = runnerUtils.runOne("tenant1", "io.kestra.tests", "task-allow-failure-executable-flow");
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.WARNING);
        assertThat(execution.getTaskRunList()).hasSize(2);
    }

    @Test
    @LoadFlows({"flows/valids/task-allow-failure-executable-foreachitem.yml",
        "flows/valids/for-each-item-subflow-failed.yaml"})
    void executableTask_ForEachItem() throws TimeoutException, QueueException, URISyntaxException, IOException {
        URI file = storageUpload();
        Map<String, Object> inputs = Map.of("file", file.toString());
        Execution execution = runnerUtils.runOne(MAIN_TENANT, "io.kestra.tests", "task-allow-failure-executable-foreachitem", null, (flow, execution1) -> flowIO.readExecutionInputs(flow, execution1, inputs));

        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.WARNING);
        assertThat(execution.getTaskRunList()).hasSize(4);
    }

    @Test
    @ExecuteFlow("flows/valids/task-allow-failure-flowable.yml")
    void flowableTask(Execution execution) {
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.WARNING);
        assertThat(execution.getTaskRunList()).hasSize(3);
    }

    private URI storageUpload() throws URISyntaxException, IOException {
        File tempFile = File.createTempFile("file", ".txt");

        Files.write(tempFile.toPath(), content());

        return storageInterface.put(
            MAIN_TENANT,
            null,
            new URI("/file/storage/file.txt"),
            new FileInputStream(tempFile)
        );
    }

    private List<String> content() {
        return IntStream
            .range(0, 10)
            .mapToObj(value -> StringUtils.leftPad(value + "", 20))
            .toList();
    }
}
