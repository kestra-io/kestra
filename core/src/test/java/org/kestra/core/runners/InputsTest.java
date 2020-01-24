package org.kestra.core.runners;

import com.google.common.collect.ImmutableMap;
import com.google.common.io.CharStreams;
import org.junit.jupiter.api.Test;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.models.flows.State;
import org.kestra.core.repositories.FlowRepositoryInterface;
import org.kestra.core.storages.StorageObject;

import javax.inject.Inject;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SuppressWarnings("OptionalGetWithoutIsPresent")
public class InputsTest extends AbstractMemoryRunnerTest {
    public static Map<String, String> inputs = ImmutableMap.of(
        "string", "myString",
        "int", "42",
        "float", "42.42",
        "instant", "2019-10-06T18:27:49Z",
        "file", Objects.requireNonNull(InputsTest.class.getClassLoader().getResource("application.yml")).getPath()
    );

    @Inject
    private FlowRepositoryInterface flowRepository;

    private Map<String, Object> typedInputs(Map<String, String> map) {
        return runnerUtils.typedInputs(
            flowRepository.findById("org.kestra.tests", "inputs").get(),
            Execution.builder()
                .id("test")
                .namespace("test")
                .flowRevision(1)
                .build(),
            map
        );
    }

    @Test
    void missingRequired() {
        assertThrows(IllegalArgumentException.class, () -> {
            typedInputs(new HashMap<>());
        });
    }

    @Test
    void inputString() {
        Map<String, Object> typeds = typedInputs(inputs);
        assertThat(typeds.get("string"), is("myString"));
    }

    @Test
    void inputInt() {
        Map<String, Object> typeds = typedInputs(inputs);
        assertThat(typeds.get("int"), is(42));
    }

    @Test
    void inputFloat() {
        Map<String, Object> typeds = typedInputs(inputs);
        assertThat(typeds.get("float"), is(42.42F));
    }

    @Test
    void inputInstant() {
        Map<String, Object> typeds = typedInputs(inputs);
        assertThat(typeds.get("instant"), is(Instant.parse("2019-10-06T18:27:49Z")));
    }

    @Test
    void inputFile() throws URISyntaxException, IOException {
        Map<String, Object> typeds = typedInputs(inputs);
        StorageObject file = (StorageObject) typeds.get("file");

        assertThat(file.getUri(), is(new URI("kestra:///org/kestra/tests/inputs/executions/test/inputs/file/application.yml")));
        assertThat(file.getClass(), is(StorageObject.class));
        assertThat(
            file.getContent(),
            is(CharStreams.toString(new InputStreamReader(new FileInputStream(inputs.get("file")))))
        );
        assertThat(
            CharStreams.toString(new InputStreamReader(file.getInputStream())),
            is(CharStreams.toString(new InputStreamReader(new FileInputStream(inputs.get("file")))))
        );
    }

    @Test
    void inputFlow() throws TimeoutException {
        Execution execution = runnerUtils.runOne(
            "org.kestra.tests",
            "inputs",
            null,
            (flow, execution1) -> runnerUtils.typedInputs(flow, execution1, inputs)
        );

        assertThat(execution.getTaskRunList(), hasSize(7));
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
        Arrays.asList("file-uri", "file").forEach(o ->
            assertThat(
                (String) execution.findTaskRunByTaskId(o).getOutputs().get("value"),
                matchesRegex("kestra:///org/kestra/tests/inputs/executions/.*/inputs/file/application.yml")
            )
        );
    }
}
