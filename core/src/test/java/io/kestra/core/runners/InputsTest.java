package io.kestra.core.runners;

import com.google.common.collect.ImmutableMap;
import com.google.common.io.CharStreams;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.junit.annotations.LoadFlows;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.queues.QueueException;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.utils.TestsUtils;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolationException;
import org.junitpioneer.jupiter.RetryingTest;
import reactor.core.publisher.Flux;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest(startRunner = true)
public class InputsTest {
    @Inject
    @Named(QueueFactoryInterface.WORKERTASKLOG_NAMED)
    private QueueInterface<LogEntry> logQueue;

    @Inject
    private RunnerUtils runnerUtils;

    public static Map<String, Object> inputs = ImmutableMap.<String, Object>builder()
        .put("string", "myString")
        .put("enum", "ENUM_VALUE")
        .put("int", "42")
        .put("float", "42.42")
        .put("bool", "false")
        .put("instant", "2019-10-06T18:27:49Z")
        .put("date", "2019-10-06")
        .put("time", "18:27:49")
        .put("duration", "PT5M6S")
        .put("file", Objects.requireNonNull(InputsTest.class.getClassLoader().getResource("application-test.yml")).getPath())
        .put("json", "{\"a\": \"b\"}")
        .put("uri", "https://www.google.com")
        .put("nested.string", "a string")
        .put("nested.more.int", "123")
        .put("nested.bool", "true")
        .put("validatedString", "A123")
        .put("validatedInt", "12")
        .put("validatedDate", "2023-01-02")
        .put("validatedDateTime", "2023-01-01T00:00:10Z")
        .put("validatedDuration", "PT15S")
        .put("validatedFloat", "0.42")
        .put("validatedTime", "11:27:49")
        .put("secret", "secret")
        .put("array", "[1, 2, 3]")
        .put("yaml", """
            some: property
            alist:
            - of
            - values""")
        .build();

    @Inject
    private FlowRepositoryInterface flowRepository;

    @Inject
    private StorageInterface storageInterface;

    @Inject
    private FlowInputOutput flowIO;

    private Map<String, Object> typedInputs(Map<String, Object> map) {
        return typedInputs(map, flowRepository.findById(null, "io.kestra.tests", "inputs").get());
    }

    private Map<String, Object> typedInputs(Map<String, Object> map, Flow flow) {
        return flowIO.readExecutionInputs(
            flow,
            Execution.builder()
                .id("test")
                .namespace(flow.getNamespace())
                .flowRevision(1)
                .flowId(flow.getId())
                .build(),
            map
        );
    }

    @Test
    @LoadFlows({"flows/valids/inputs.yaml"})
    void missingRequired() {
        HashMap<String, Object> inputs = new HashMap<>(InputsTest.inputs);
        inputs.put("string", null);
        ConstraintViolationException e = assertThrows(ConstraintViolationException.class, () -> typedInputs(inputs));
        assertThat(e.getMessage()).contains("Invalid input for `string`, missing required input, but received `null`");
    }

    @Test
    @LoadFlows({"flows/valids/inputs.yaml"})
    void nonRequiredNoDefaultNoValueIsNull() {
        HashMap<String, Object> inputsWithMissingOptionalInput = new HashMap<>(inputs);
        inputsWithMissingOptionalInput.remove("bool");

        assertThat(typedInputs(inputsWithMissingOptionalInput).containsKey("bool")).isEqualTo(true);
        assertThat(typedInputs(inputsWithMissingOptionalInput).get("bool")).isNull();
    }

    @SuppressWarnings("unchecked")
    @Test
    @LoadFlows({"flows/valids/inputs.yaml"})
    void allValidInputs() throws URISyntaxException, IOException {
        Map<String, Object> typeds = typedInputs(inputs);

        assertThat(typeds.get("string")).isEqualTo("myString");
        assertThat(typeds.get("int")).isEqualTo(42);
        assertThat(typeds.get("float")).isEqualTo(42.42F);
        assertThat(typeds.get("bool")).isEqualTo(false);
        assertThat(typeds.get("instant")).isEqualTo(Instant.parse("2019-10-06T18:27:49Z"));
        assertThat(typeds.get("instantDefaults")).isEqualTo(Instant.parse("2013-08-09T14:19:00Z"));
        assertThat(typeds.get("date")).isEqualTo(LocalDate.parse("2019-10-06"));
        assertThat(typeds.get("time")).isEqualTo(LocalTime.parse("18:27:49"));
        assertThat(typeds.get("duration")).isEqualTo(Duration.parse("PT5M6S"));
        assertThat((URI) typeds.get("file")).isEqualTo(new URI("kestra:///io/kestra/tests/inputs/executions/test/inputs/file/application-test.yml"));
        assertThat(CharStreams.toString(new InputStreamReader(storageInterface.get(null, null, (URI) typeds.get("file"))))).isEqualTo(CharStreams.toString(new InputStreamReader(new FileInputStream((String) inputs.get("file")))));
        assertThat(typeds.get("json")).isEqualTo(Map.of("a", "b"));
        assertThat(typeds.get("uri")).isEqualTo("https://www.google.com");
        assertThat(((Map<String, Object>) typeds.get("nested")).get("string")).isEqualTo("a string");
        assertThat(((Map<String, Object>) typeds.get("nested")).get("bool")).isEqualTo(true);
        assertThat(((Map<String, Object>) ((Map<String, Object>) typeds.get("nested")).get("more")).get("int")).isEqualTo(123);
        assertThat(typeds.get("validatedString")).isEqualTo("A123");
        assertThat(typeds.get("validatedInt")).isEqualTo(12);
        assertThat(typeds.get("validatedDate")).isEqualTo(LocalDate.parse("2023-01-02"));
        assertThat(typeds.get("validatedDateTime")).isEqualTo(Instant.parse("2023-01-01T00:00:10Z"));
        assertThat(typeds.get("validatedDuration")).isEqualTo(Duration.parse("PT15S"));
        assertThat(typeds.get("validatedFloat")).isEqualTo(0.42F);
        assertThat(typeds.get("validatedTime")).isEqualTo(LocalTime.parse("11:27:49"));
        assertThat(typeds.get("secret")).isNotEqualTo("secret"); // secret inputs are encrypted
        assertThat(typeds.get("array")).isInstanceOf(List.class);
        assertThat((List<Integer>) typeds.get("array")).hasSize(3);
        assertThat((List<Integer>) typeds.get("array")).isEqualTo(List.of(1, 2, 3));
        assertThat(typeds.get("yaml")).isEqualTo(Map.of(
            "some", "property",
            "alist", List.of("of", "values")));
    }

    @Test
    @LoadFlows({"flows/valids/inputs.yaml"})
    void allValidTypedInputs() {
        Map<String, Object> typeds = typedInputs(inputs);
        typeds.put("int", 42);
        typeds.put("float", 42.42F);
        typeds.put("bool", false);

        assertThat(typeds.get("string")).isEqualTo("myString");
        assertThat(typeds.get("enum")).isEqualTo("ENUM_VALUE");
        assertThat(typeds.get("int")).isEqualTo(42);
        assertThat(typeds.get("float")).isEqualTo(42.42F);
        assertThat(typeds.get("bool")).isEqualTo(false);
    }

    @Test
    @LoadFlows({"flows/valids/inputs.yaml"})
    void inputFlow() throws TimeoutException, QueueException {
        Execution execution = runnerUtils.runOne(
            null,
            "io.kestra.tests",
            "inputs",
            null,
            (flow, execution1) -> flowIO.readExecutionInputs(flow, execution1, inputs)
        );

        assertThat(execution.getTaskRunList()).hasSize(14);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat((String) execution.findTaskRunsByTaskId("file").getFirst().getOutputs().get("value")).matches("kestra:///io/kestra/tests/inputs/executions/.*/inputs/file/application-test.yml");
        // secret inputs are decrypted to be used as task properties
        assertThat((String) execution.findTaskRunsByTaskId("secret").getFirst().getOutputs().get("value")).isEqualTo("secret");
        // null inputs are serialized
        assertThat((String) execution.findTaskRunsByTaskId("optional").getFirst().getOutputs().get("value")).isEmpty();
    }

    @Test
    @LoadFlows({"flows/valids/inputs.yaml"})
    void inputValidatedStringBadValue() {
        HashMap<String, Object> map = new HashMap<>(inputs);
        map.put("validatedString", "foo");

        ConstraintViolationException e = assertThrows(ConstraintViolationException.class, () -> typedInputs(map));

        assertThat(e.getMessage()).contains("Invalid input for `validatedString`, it must match the pattern");
    }

    @Test
    @LoadFlows({"flows/valids/inputs.yaml"})
    void inputValidatedIntegerBadValue() {
        HashMap<String, Object> mapMin = new HashMap<>(inputs);
        mapMin.put("validatedInt", "9");
        ConstraintViolationException e = assertThrows(ConstraintViolationException.class, () -> typedInputs(mapMin));
        assertThat(e.getMessage()).contains("Invalid input for `validatedInt`, it must be more than `10`, but received `9`");

        HashMap<String, Object> mapMax = new HashMap<>(inputs);
        mapMax.put("validatedInt", "21");

        e = assertThrows(ConstraintViolationException.class, () -> typedInputs(mapMax));

        assertThat(e.getMessage()).contains("Invalid input for `validatedInt`, it must be less than `20`, but received `21`");
    }

    @Test
    @LoadFlows({"flows/valids/inputs.yaml"})
    void inputValidatedDateBadValue() {
        HashMap<String, Object> mapMin = new HashMap<>(inputs);
        mapMin.put("validatedDate", "2022-01-01");
        ConstraintViolationException e = assertThrows(ConstraintViolationException.class, () -> typedInputs(mapMin));
        assertThat(e.getMessage()).contains("Invalid input for `validatedDate`, it must be after `2023-01-01`, but received `2022-01-01`");

        HashMap<String, Object> mapMax = new HashMap<>(inputs);
        mapMax.put("validatedDate", "2024-01-01");

        e = assertThrows(ConstraintViolationException.class, () -> typedInputs(mapMax));

        assertThat(e.getMessage()).contains("Invalid input for `validatedDate`, it must be before `2023-12-31`, but received `2024-01-01`");
    }

    @Test
    @LoadFlows({"flows/valids/inputs.yaml"})
    void inputValidatedDateTimeBadValue() {
        HashMap<String, Object> mapMin = new HashMap<>(inputs);
        mapMin.put("validatedDateTime", "2022-01-01T00:00:00Z");
        ConstraintViolationException e = assertThrows(ConstraintViolationException.class, () -> typedInputs(mapMin));
        assertThat(e.getMessage()).contains("Invalid input for `validatedDateTime`, it must be after `2023-01-01T00:00:00Z`, but received `2022-01-01T00:00:00Z`");

        HashMap<String, Object> mapMax = new HashMap<>(inputs);
        mapMax.put("validatedDateTime", "2024-01-01T00:00:00Z");

        e = assertThrows(ConstraintViolationException.class, () -> typedInputs(mapMax));

        assertThat(e.getMessage()).contains("Invalid input for `validatedDateTime`, it must be before `2023-12-31T23:59:59Z`");
    }

    @Test
    @LoadFlows({"flows/valids/inputs.yaml"})
    void inputValidatedDurationBadValue() {
        HashMap<String, Object> mapMin = new HashMap<>(inputs);
        mapMin.put("validatedDuration", "PT1S");
        ConstraintViolationException e = assertThrows(ConstraintViolationException.class, () -> typedInputs(mapMin));
        assertThat(e.getMessage()).contains("Invalid input for `validatedDuration`, It must be more than `PT10S`, but received `PT1S`");

        HashMap<String, Object> mapMax = new HashMap<>(inputs);
        mapMax.put("validatedDuration", "PT30S");

        e = assertThrows(ConstraintViolationException.class, () -> typedInputs(mapMax));

        assertThat(e.getMessage()).contains("Invalid input for `validatedDuration`, It must be less than `PT20S`, but received `PT30S`");
    }

    @Test
    @LoadFlows({"flows/valids/inputs.yaml"})
    void inputValidatedFloatBadValue() {
        HashMap<String, Object> mapMin = new HashMap<>(inputs);
        mapMin.put("validatedFloat", "0.01");
        ConstraintViolationException e = assertThrows(ConstraintViolationException.class, () -> typedInputs(mapMin));
        assertThat(e.getMessage()).contains("Invalid input for `validatedFloat`, it must be more than `0.1`, but received `0.01`");

        HashMap<String, Object> mapMax = new HashMap<>(inputs);
        mapMax.put("validatedFloat", "1.01");

        e = assertThrows(ConstraintViolationException.class, () -> typedInputs(mapMax));

        assertThat(e.getMessage()).contains("Invalid input for `validatedFloat`, it must be less than `0.5`, but received `1.01`");
    }

    @Test
    @LoadFlows({"flows/valids/inputs.yaml"})
    void inputValidatedTimeBadValue() {
        HashMap<String, Object> mapMin = new HashMap<>(inputs);
        mapMin.put("validatedTime", "00:00:01");
        ConstraintViolationException e = assertThrows(ConstraintViolationException.class, () -> typedInputs(mapMin));
        assertThat(e.getMessage()).contains("Invalid input for `validatedTime`, it must be after `01:00`, but received `00:00:01`");

        HashMap<String, Object> mapMax = new HashMap<>(inputs);
        mapMax.put("validatedTime", "14:00:00");

        e = assertThrows(ConstraintViolationException.class, () -> typedInputs(mapMax));

        assertThat(e.getMessage()).contains("Invalid input for `validatedTime`, it must be before `11:59:59`, but received `14:00:00`");
    }

    @Test
    @LoadFlows({"flows/valids/inputs.yaml"})
    void inputFailed() {
        HashMap<String, Object> map = new HashMap<>(inputs);
        map.put("uri", "http:/bla");

        ConstraintViolationException e = assertThrows(ConstraintViolationException.class, () -> typedInputs(map));

        assertThat(e.getMessage()).contains("Invalid input for `uri`, Expected `URI` but received `http:/bla`, but received `http:/bla`");
    }

    @Test
    @LoadFlows({"flows/valids/inputs.yaml"})
    void inputEnumFailed() {
        HashMap<String, Object> map = new HashMap<>(inputs);
        map.put("enum", "INVALID");

        ConstraintViolationException e = assertThrows(ConstraintViolationException.class, () -> typedInputs(map));

        assertThat(e.getMessage()).isEqualTo("enum: Invalid input for `enum`, it must match the values `[ENUM_VALUE, OTHER_ONE]`, but received `INVALID`");
    }

    @Test
    @LoadFlows({"flows/valids/inputs.yaml"})
    void inputArrayFailed() {
        HashMap<String, Object> map = new HashMap<>(inputs);
        map.put("array", "[\"s1\", \"s2\"]");

        ConstraintViolationException e = assertThrows(ConstraintViolationException.class, () -> typedInputs(map));

        assertThat(e.getMessage()).contains("Invalid input for `array`, Unable to parse array element as `INT` on `s1`, but received `[\"s1\", \"s2\"]`");
    }

    @Test
    @LoadFlows({"flows/valids/inputs.yaml"})
    void inputEmptyJson() {
        HashMap<String, Object> map = new HashMap<>(inputs);
        map.put("json", "{}");

        Map<String, Object> typeds = typedInputs(map);

        assertThat(typeds.get("json")).isInstanceOf(Map.class);
        assertThat(((Map<?, ?>) typeds.get("json")).size()).isEqualTo(0);
    }

    @Test
    @LoadFlows({"flows/valids/inputs.yaml"})
    void inputEmptyJsonFlow() throws TimeoutException, QueueException {
        HashMap<String, Object> map = new HashMap<>(inputs);
        map.put("json", "{}");

        Execution execution = runnerUtils.runOne(
            null,
            "io.kestra.tests",
            "inputs",
            null,
            (flow, execution1) -> flowIO.readExecutionInputs(flow, execution1, map)
        );

        assertThat(execution.getTaskRunList()).hasSize(14);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

        assertThat(execution.getInputs().get("json")).isInstanceOf(Map.class);
        assertThat(((Map<?, ?>) execution.getInputs().get("json")).size()).isEqualTo(0);
        assertThat((String) execution.findTaskRunsByTaskId("jsonOutput").getFirst().getOutputs().get("value")).isEqualTo("{}");
    }

    @RetryingTest(5) // it can happen that a log from another execution arrives first, so we enable retry
    @LoadFlows({"flows/valids/input-log-secret.yaml"})
    void shouldNotLogSecretInput() throws TimeoutException, QueueException {
        Flux<LogEntry> receive = TestsUtils.receive(logQueue, l -> {});

        Execution execution = runnerUtils.runOne(
            null,
            "io.kestra.tests",
            "input-log-secret"
        );

        assertThat(execution.getTaskRunList()).hasSize(1);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

        var logEntry = receive.blockLast();
        assertThat(logEntry).isNotNull();
        assertThat(logEntry.getMessage()).isEqualTo("This is my secret: ******");
    }
}
