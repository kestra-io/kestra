package io.kestra.core.runners;

import com.google.common.collect.ImmutableMap;
import com.google.common.io.CharStreams;
import io.kestra.core.exceptions.MissingRequiredArgument;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.storages.StorageInterface;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolationException;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class InputsTest extends AbstractMemoryRunnerTest {
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
        .put("array", """
            ["s1", "s2", "s3"]""")
        .build();

    @Inject
    private FlowRepositoryInterface flowRepository;

    @Inject
    private StorageInterface storageInterface;

    private Map<String, Object> typedInputs(Map<String, Object> map) {
        return typedInputs(map, flowRepository.findById(null, "io.kestra.tests", "inputs").get());
    }

    private Map<String, Object> typedInputs(Map<String, Object> map, Flow flow) {
        return runnerUtils.typedInputs(
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
    void missingRequired() {
        assertThrows(IllegalArgumentException.class, () -> {
            typedInputs(new HashMap<>());
        });
    }

    @Test
    void nonRequiredNoDefaultNoValueIsNull() {
        HashMap<String, Object> inputsWithMissingOptionalInput = new HashMap<>(inputs);
        inputsWithMissingOptionalInput.remove("bool");

        assertThat(typedInputs(inputsWithMissingOptionalInput).containsKey("bool"), is(true));
        assertThat(typedInputs(inputsWithMissingOptionalInput).get("bool"), nullValue());
    }

    @SuppressWarnings("unchecked")
    @Test
    void allValidInputs() throws URISyntaxException, IOException {
        Map<String, Object> typeds = typedInputs(inputs);

        assertThat(typeds.get("string"), is("myString"));
        assertThat(typeds.get("int"), is(42));
        assertThat(typeds.get("float"), is(42.42F));
        assertThat(typeds.get("bool"), is(false));
        assertThat(typeds.get("instant"), is(Instant.parse("2019-10-06T18:27:49Z")));
        assertThat(typeds.get("instantDefaults"), is(Instant.parse("2013-08-09T14:19:00Z")));
        assertThat(typeds.get("date"), is(LocalDate.parse("2019-10-06")));
        assertThat(typeds.get("time"), is(LocalTime.parse("18:27:49")));
        assertThat(typeds.get("duration"), is(Duration.parse("PT5M6S")));
        assertThat((URI) typeds.get("file"), is(new URI("kestra:///io/kestra/tests/inputs/executions/test/inputs/file/application-test.yml")));
        assertThat(
            CharStreams.toString(new InputStreamReader(storageInterface.get(null, (URI) typeds.get("file")))),
            is(CharStreams.toString(new InputStreamReader(new FileInputStream((String) inputs.get("file")))))
        );
        assertThat(typeds.get("json"), is(Map.of("a", "b")));
        assertThat(typeds.get("uri"), is("https://www.google.com"));
        assertThat(((Map<String, Object>)typeds.get("nested")).get("string"), is("a string"));
        assertThat(((Map<String, Object>)typeds.get("nested")).get("bool"), is(true));
        assertThat(((Map<String, Object>)((Map<String, Object>)typeds.get("nested")).get("more")).get("int"), is(123));
        assertThat(typeds.get("validatedString"), is("A123"));
        assertThat(typeds.get("validatedInt"), is(12));
        assertThat(typeds.get("validatedDate"), is(LocalDate.parse("2023-01-02")));
        assertThat(typeds.get("validatedDateTime"), is(Instant.parse("2023-01-01T00:00:10Z")));
        assertThat(typeds.get("validatedDuration"), is(Duration.parse("PT15S")));
        assertThat(typeds.get("validatedFloat"), is(0.42F));
        assertThat(typeds.get("validatedTime"), is(LocalTime.parse("11:27:49")));
        assertThat(typeds.get("secret"), not("secret")); // secret inputs are encrypted
        assertThat(typeds.get("array"), instanceOf(List.class));
        assertThat((List<String>)typeds.get("array"), hasSize(3));
        assertThat((List<String>)typeds.get("array"), contains("s1", "s2", "s3"));
    }

    @Test
    void allValidTypedInputs() {
        Map<String, Object> typeds = typedInputs(inputs);
        typeds.put("int", 42);
        typeds.put("float", 42.42F);
        typeds.put("bool", false);

        assertThat(typeds.get("string"), is("myString"));
        assertThat(typeds.get("enum"), is("ENUM_VALUE"));
        assertThat(typeds.get("int"), is(42));
        assertThat(typeds.get("float"), is(42.42F));
        assertThat(typeds.get("bool"), is(false));
    }

    @Test
    void inputFlow() throws TimeoutException {
        Execution execution = runnerUtils.runOne(
            null,
            "io.kestra.tests",
            "inputs",
            null,
            (flow, execution1) -> runnerUtils.typedInputs(flow, execution1, inputs)
        );

        assertThat(execution.getTaskRunList(), hasSize(13));
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(
            (String) execution.findTaskRunsByTaskId("file").get(0).getOutputs().get("value"),
            matchesRegex("kestra:///io/kestra/tests/inputs/executions/.*/inputs/file/application-test.yml")
        );
        // secret inputs are decrypted to be used as task properties
        assertThat(
            (String) execution.findTaskRunsByTaskId("secret").get(0).getOutputs().get("value"),
            is("secret")
        );
        // null inputs are serialized
        assertThat(
            (String) execution.findTaskRunsByTaskId("optional").get(0).getOutputs().get("value"),
            emptyString()
        );
    }

    @Test
    void inputValidatedStringBadValue() {
        HashMap<String, Object> map = new HashMap<>(inputs);
        map.put("validatedString", "foo");

        ConstraintViolationException e = assertThrows(ConstraintViolationException.class, () -> {
            Map<String, Object> typeds = typedInputs(map);
        });

        assertThat(e.getMessage(), is("Invalid input 'foo', it must match the pattern 'A\\d+'"));
    }

    @Test
    void inputValidatedIntegerBadValue() {
        HashMap<String, Object> mapMin = new HashMap<>(inputs);
        mapMin.put("validatedInt", "9");
        ConstraintViolationException e = assertThrows(ConstraintViolationException.class, () -> {
            Map<String, Object> typeds = typedInputs(mapMin);
        });
        assertThat(e.getMessage(), is("Invalid input '9', it must be more than '10'"));

        HashMap<String, Object> mapMax = new HashMap<>(inputs);
        mapMax.put("validatedInt", "21");

        e = assertThrows(ConstraintViolationException.class, () -> {
            Map<String, Object> typeds = typedInputs(mapMax);
        });

        assertThat(e.getMessage(), is("Invalid input '21', it must be less than '20'"));
    }

    @Test
    void inputValidatedDateBadValue() {
        HashMap<String, Object> mapMin = new HashMap<>(inputs);
        mapMin.put("validatedDate", "2022-01-01");
        ConstraintViolationException e = assertThrows(ConstraintViolationException.class, () -> {
            Map<String, Object> typeds = typedInputs(mapMin);
        });
        assertThat(e.getMessage(), is("Invalid input '2022-01-01', it must be after '2023-01-01'"));

        HashMap<String, Object> mapMax = new HashMap<>(inputs);
        mapMax.put("validatedDate", "2024-01-01");

        e = assertThrows(ConstraintViolationException.class, () -> {
            Map<String, Object> typeds = typedInputs(mapMax);
        });

        assertThat(e.getMessage(), is("Invalid input '2024-01-01', it must be before '2023-12-31'"));
    }

    @Test
    void inputValidatedDateTimeBadValue() {
        HashMap<String, Object> mapMin = new HashMap<>(inputs);
        mapMin.put("validatedDateTime", "2022-01-01T00:00:00Z");
        ConstraintViolationException e = assertThrows(ConstraintViolationException.class, () -> {
            Map<String, Object> typeds = typedInputs(mapMin);
        });
        assertThat(e.getMessage(), is("Invalid input '2022-01-01T00:00:00Z', it must be after '2023-01-01T00:00:00Z'"));

        HashMap<String, Object> mapMax = new HashMap<>(inputs);
        mapMax.put("validatedDateTime", "2024-01-01T00:00:00Z");

        e = assertThrows(ConstraintViolationException.class, () -> {
            Map<String, Object> typeds = typedInputs(mapMax);
        });

        assertThat(e.getMessage(), is("Invalid input '2024-01-01T00:00:00Z', it must be before '2023-12-31T23:59:59Z'"));
    }

    @Test
    void inputValidatedDurationBadValue() {
        HashMap<String, Object> mapMin = new HashMap<>(inputs);
        mapMin.put("validatedDuration", "PT1S");
        ConstraintViolationException e = assertThrows(ConstraintViolationException.class, () -> {
            Map<String, Object> typeds = typedInputs(mapMin);
        });
        assertThat(e.getMessage(), is("Invalid input 'PT1S', it must be more than 'PT10S'"));

        HashMap<String, Object> mapMax = new HashMap<>(inputs);
        mapMax.put("validatedDuration", "PT30S");

        e = assertThrows(ConstraintViolationException.class, () -> {
            Map<String, Object> typeds = typedInputs(mapMax);
        });

        assertThat(e.getMessage(), is("Invalid input 'PT30S', it must be less than 'PT20S'"));
    }

    @Test
    void inputValidatedFloatBadValue() {
        HashMap<String, Object> mapMin = new HashMap<>(inputs);
        mapMin.put("validatedFloat", "0.01");
        ConstraintViolationException e = assertThrows(ConstraintViolationException.class, () -> {
            Map<String, Object> typeds = typedInputs(mapMin);
        });
        assertThat(e.getMessage(), is("Invalid input '0.01', it must be more than '0.1'"));

        HashMap<String, Object> mapMax = new HashMap<>(inputs);
        mapMax.put("validatedFloat", "1.01");

        e = assertThrows(ConstraintViolationException.class, () -> {
            Map<String, Object> typeds = typedInputs(mapMax);
        });

        assertThat(e.getMessage(), is("Invalid input '1.01', it must be less than '0.5'"));
    }

    @Test
    void inputValidatedTimeBadValue() {
        HashMap<String, Object> mapMin = new HashMap<>(inputs);
        mapMin.put("validatedTime", "00:00:01");
        ConstraintViolationException e = assertThrows(ConstraintViolationException.class, () -> {
            Map<String, Object> typeds = typedInputs(mapMin);
        });
        assertThat(e.getMessage(), is("Invalid input '00:00:01', it must be after '01:00'"));

        HashMap<String, Object> mapMax = new HashMap<>(inputs);
        mapMax.put("validatedTime", "14:00:00");

        e = assertThrows(ConstraintViolationException.class, () -> {
            Map<String, Object> typeds = typedInputs(mapMax);
        });

        assertThat(e.getMessage(), is("Invalid input '14:00', it must be before '11:59:59'"));
    }

    @Test
    void inputFailed() {
        HashMap<String, Object> map = new HashMap<>(inputs);
        map.put("uri", "http:/bla");

        MissingRequiredArgument e = assertThrows(MissingRequiredArgument.class, () -> {
            Map<String, Object> typeds = typedInputs(map);
        });

        assertThat(e.getMessage(), containsString("Invalid URI format"));
    }

    @Test
    void inputEnumFailed() {
        HashMap<String, Object> map = new HashMap<>(inputs);
        map.put("enum", "INVALID");

        ConstraintViolationException e = assertThrows(ConstraintViolationException.class, () -> {
            Map<String, Object> typeds = typedInputs(map);
        });

        assertThat(e.getMessage(), is("Invalid input 'INVALID', it must match the values '[ENUM_VALUE, OTHER_ONE]'"));
    }

    @Test
    void inputEmptyJson() {
        HashMap<String, Object> map = new HashMap<>(inputs);
        map.put("json", "{}");

        Map<String, Object> typeds = typedInputs(map);

        assertThat(typeds.get("json"), instanceOf(Map.class));
        assertThat(((Map<?, ?>) typeds.get("json")).size(), is(0));
    }

    @Test
    void inputEmptyJsonFlow() throws TimeoutException {
        HashMap<String, Object> map = new HashMap<>(inputs);
        map.put("json", "{}");

        Execution execution = runnerUtils.runOne(
            null,
            "io.kestra.tests",
            "inputs",
            null,
            (flow, execution1) -> runnerUtils.typedInputs(flow, execution1, map)
        );

        assertThat(execution.getTaskRunList(), hasSize(13));
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));

        assertThat(execution.getInputs().get("json"), instanceOf(Map.class));
        assertThat(((Map<?, ?>) execution.getInputs().get("json")).size(), is(0));
        assertThat((String) execution.findTaskRunsByTaskId("jsonOutput").get(0).getOutputs().get("value"), is("{}"));
    }
}
