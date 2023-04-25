package io.kestra.core.runners;

import com.google.common.collect.ImmutableMap;
import com.google.common.io.CharStreams;
import io.kestra.core.exceptions.MissingRequiredInput;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.serializers.YamlFlowParser;
import io.kestra.core.utils.TestsUtils;
import org.junit.jupiter.api.Test;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.State;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.storages.StorageInterface;

import jakarta.inject.Inject;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeoutException;

import javax.validation.ConstraintViolationException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SuppressWarnings("OptionalGetWithoutIsPresent")
public class InputsTest extends AbstractMemoryRunnerTest {
    public static Map<String, String> inputs = ImmutableMap.<String, String>builder()
        .put("string", "myString")
        .put("int", "42")
        .put("float", "42.42")
        .put("bool", "false")
        .put("instant", "2019-10-06T18:27:49Z")
        .put("date", "2019-10-06")
        .put("time", "18:27:49")
        .put("duration", "PT5M6S")
        .put("file", Objects.requireNonNull(InputsTest.class.getClassLoader().getResource("application.yml")).getPath())
        .put("json", "{\"a\": \"b\"}")
        .put("uri", "https://www.google.com")
        .put("nested.string", "a string")
        .put("nested.more.int", "123")
        .put("nested.bool", "true")
        .put("validatedString", "A123")
        .build();

    @Inject
    private FlowRepositoryInterface flowRepository;

    @Inject
    private StorageInterface storageInterface;

    @Inject
    private YamlFlowParser yamlFlowParser;

    private Map<String, Object> typedInputs(Map<String, String> map) {
        return typedInputs(map, flowRepository.findById("io.kestra.tests", "inputs").get());
    }

    private Map<String, Object> typedInputs(Map<String, String> map, Flow flow) {
        return runnerUtils.typedInputs(
            flow,
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
    void inputBool() {
        Map<String, Object> typeds = typedInputs(inputs);
        assertThat(typeds.get("bool"), is(false));
    }

    @Test
    void inputInstant() {
        Map<String, Object> typeds = typedInputs(inputs);
        assertThat(typeds.get("instant"), is(Instant.parse("2019-10-06T18:27:49Z")));
    }

    @Test
    void inputInstantDefaults() {
        Map<String, Object> typeds = typedInputs(inputs);
        assertThat(typeds.get("instantDefaults"), is(Instant.parse("2013-08-09T14:19:00Z")));
    }

    @Test
    void inputDate() {
        Map<String, Object> typeds = typedInputs(inputs);
        assertThat(typeds.get("date"), is(LocalDate.parse("2019-10-06")));
    }

    @Test
    void inputTime() {
        Map<String, Object> typeds = typedInputs(inputs);
        assertThat(typeds.get("time"), is(LocalTime.parse("18:27:49")));
    }

    @Test
    void inputDuration() {
        Map<String, Object> typeds = typedInputs(inputs);
        assertThat(typeds.get("duration"), is(Duration.parse("PT5M6S")));
    }

    @Test
    void inputFile() throws URISyntaxException, IOException {
        Map<String, Object> typeds = typedInputs(inputs);
        URI file = (URI) typeds.get("file");

        assertThat(file, is(new URI("kestra:///io/kestra/tests/inputs/executions/test/inputs/file/application.yml")));

        InputStream inputStream = storageInterface.get(file);
        assertThat(
            CharStreams.toString(new InputStreamReader(inputStream)),
            is(CharStreams.toString(new InputStreamReader(new FileInputStream(inputs.get("file")))))
        );
    }

    @Test
    void inputFlow() throws TimeoutException {
        Execution execution = runnerUtils.runOne(
            "io.kestra.tests",
            "inputs",
            null,
            (flow, execution1) -> runnerUtils.typedInputs(flow, execution1, inputs)
        );

        assertThat(execution.getTaskRunList(), hasSize(5));
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(
            (String) execution.findTaskRunsByTaskId("file").get(0).getOutputs().get("value"),
            matchesRegex("kestra:///io/kestra/tests/inputs/executions/.*/inputs/file/application.yml")
        );
    }

    @Test
    void inputJson() {
        Map<String, Object> typeds = typedInputs(inputs);
        assertThat(typeds.get("json"), is(Map.of("a", "b")));
    }

    @Test
    void inputUri() {
        Map<String, Object> typeds = typedInputs(inputs);
        assertThat(typeds.get("uri"), is("https://www.google.com"));
    }

    @Test
    void inputValidatedString() {
        Map<String, Object> typeds = typedInputs(inputs);
        assertThat(typeds.get("validatedString"), is("A123"));
    }

    @Test
    void inputValidatedStringBadValue() {
        HashMap<String, String> map = new HashMap<>(inputs);
        map.put("validatedString", "foo");

        ConstraintViolationException e = assertThrows(ConstraintViolationException.class, () -> {
            Map<String, Object> typeds = typedInputs(map);
        });

        assertThat(e.getMessage(), is("Invalid input 'foo', it must match the pattern 'A\\d+'"));
    }

    @Test
    void inputFailed() {
        HashMap<String, String> map = new HashMap<>(inputs);
        map.put("uri", "http:/bla");

        MissingRequiredInput e = assertThrows(MissingRequiredInput.class, () -> {
            Map<String, Object> typeds = typedInputs(map);
        });

        assertThat(e.getMessage(), containsString("Invalid URI format"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void inputNested() {
        Map<String, Object> typeds = typedInputs(inputs);
        assertThat(((Map<String, Object>)typeds.get("nested")).get("string"), is("a string"));
        assertThat(((Map<String, Object>)typeds.get("nested")).get("bool"), is(true));
        assertThat(((Map<String, Object>)((Map<String, Object>)typeds.get("nested")).get("more")).get("int"), is(123));
    }

    private Flow parse(String path) {
        URL resource = TestsUtils.class.getClassLoader().getResource(path);
        assert resource != null;

        File file = new File(resource.getFile());

        return yamlFlowParser.parse(file, Flow.class);
    }
}
