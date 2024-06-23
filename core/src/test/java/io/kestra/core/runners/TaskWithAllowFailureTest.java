package io.kestra.core.runners;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.storages.StorageInterface;
import jakarta.inject.Inject;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

public class TaskWithAllowFailureTest extends AbstractMemoryRunnerTest {
    @Inject
    private StorageInterface storageInterface;

    @Inject
    private FlowInputOutput flowIO;

    @Test
    void runnableTask() throws TimeoutException {
        Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "task-allow-failure-runnable");

        assertThat(execution.getState().getCurrent(), is(State.Type.WARNING));
        assertThat(execution.getTaskRunList(), hasSize(2));
        assertThat(execution.findTaskRunsByTaskId("fail").getFirst().getAttempts().size(), is(3));
    }

    @Test
    void executableTask_Flow() throws TimeoutException {
        Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "task-allow-failure-executable-flow");

        assertThat(execution.getState().getCurrent(), is(State.Type.WARNING));
        assertThat(execution.getTaskRunList(), hasSize(2));
    }

    @Test
    void executableTask_ForEachItem() throws TimeoutException, URISyntaxException, IOException {
        URI file = storageUpload();
        Map<String, Object> inputs = Map.of("file", file.toString());
        Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "task-allow-failure-executable-foreachitem", null, (flow, execution1) -> flowIO.typedInputs(flow, execution1, inputs));

        assertThat(execution.getState().getCurrent(), is(State.Type.WARNING));
        assertThat(execution.getTaskRunList(), hasSize(4));
    }

    @Test
    void flowableTask() throws TimeoutException {
        Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "task-allow-failure-flowable");

        assertThat(execution.getState().getCurrent(), is(State.Type.WARNING));
        assertThat(execution.getTaskRunList(), hasSize(3));
    }

    private URI storageUpload() throws URISyntaxException, IOException {
        File tempFile = File.createTempFile("file", ".txt");

        Files.write(tempFile.toPath(), content());

        return storageInterface.put(
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
