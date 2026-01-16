package io.kestra.core.runners;

import com.google.common.collect.ImmutableMap;
import com.google.common.io.CharStreams;
import io.kestra.core.exceptions.InputOutputValidationException;
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
import io.kestra.core.storages.Namespace;
import io.kestra.core.storages.NamespaceFactory;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.utils.TestsUtils;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolationException;
import reactor.core.publisher.Flux;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.concurrent.TimeoutException;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@KestraTest(startRunner = true)
public class InputsTest {
    @Inject
    @Named(QueueFactoryInterface.WORKERTASKLOG_NAMED)
    private QueueInterface<LogEntry> logQueue;

    @Inject
    private TestRunnerUtils runnerUtils;

    @Inject
    private NamespaceFactory namespaceFactory;

    private static final Map<String , Object> object = Map.of(
        "people", List.of(
            Map.of(
                "first", "Mustafa",
                "last", "Tarek"
            ),
            Map.of(
                "first", "Ahmed",
                "last", "Tarek"
            )
        )
    );
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
        .put("json1", "{\"a\": \"b\"}")
        .put("json2", object)
        .put("yaml1", """
            some: property
            alist:
            - of
            - values""")
        .put("yaml2", object)
        .build();

    @Inject
    private FlowRepositoryInterface flowRepository;

    @Inject
    private StorageInterface storageInterface;

    @Inject
    private FlowInputOutput flowIO;

    @Inject
    private FlowInputOutput flowInputOutput;

    private Map<String, Object> typedInputs(Map<String, Object> map, String tenantId) {
        return typedInputs(map, flowRepository.findById(tenantId, "io.kestra.tests", "inputs").get());
    }

    private Map<String, Object> typedInputs(Map<String, Object> map, Flow flow) {
        return flowIO.readExecutionInputs(
            flow,
            Execution.builder()
                .id("test")
                .namespace(flow.getNamespace())
                .tenantId(flow.getTenantId())
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
        InputOutputValidationException e = assertThrows(InputOutputValidationException.class, () -> typedInputs(inputs, MAIN_TENANT));
        assertThat(e.getMessage()).contains("Missing required input:string");
    }

    @Test
    @LoadFlows(value = {"flows/valids/inputs.yaml"}, tenantId = "tenant")
    void nonRequiredNoDefaultNoValueIsNull() {
        HashMap<String, Object> inputsWithMissingOptionalInput = new HashMap<>(inputs);
        inputsWithMissingOptionalInput.remove("bool");

        assertThat(typedInputs(inputsWithMissingOptionalInput, "tenant").containsKey("bool")).isTrue();
        assertThat(typedInputs(inputsWithMissingOptionalInput, "tenant").get("bool")).isNull();
    }

    @SuppressWarnings("unchecked")
    @Test
    @LoadFlows(value = {"flows/valids/inputs.yaml"}, tenantId = "tenant1")
    void allValidInputs() throws URISyntaxException, IOException {
        Map<String, Object> typeds = typedInputs(inputs, "tenant1");

        assertThat(typeds.get("string")).isEqualTo("myString");
        assertThat(typeds.get("int")).isEqualTo(42);
        assertThat(typeds.get("float")).isEqualTo(42.42F);
        assertThat((Boolean) typeds.get("bool")).isFalse();
        assertThat(typeds.get("instant")).isEqualTo(Instant.parse("2019-10-06T18:27:49Z"));
        assertThat(typeds.get("instantDefaults")).isEqualTo(Instant.parse("2013-08-09T14:19:00Z"));
        assertThat(typeds.get("date")).isEqualTo(LocalDate.parse("2019-10-06"));
        assertThat(typeds.get("time")).isEqualTo(LocalTime.parse("18:27:49"));
        assertThat(typeds.get("duration")).isEqualTo(Duration.parse("PT5M6S"));
        assertThat((URI) typeds.get("file")).isEqualTo(new URI("kestra:///io/kestra/tests/inputs/executions/test/inputs/file/application-test.yml"));
        assertThat(CharStreams.toString(new InputStreamReader(storageInterface.get("tenant1", null, (URI) typeds.get("file"))))).isEqualTo(CharStreams.toString(new InputStreamReader(new FileInputStream((String) inputs.get("file")))));
        assertThat(typeds.get("uri")).isEqualTo("https://www.google.com");
        assertThat(((Map<String, Object>) typeds.get("nested")).get("string")).isEqualTo("a string");
        assertThat((Boolean) ((Map<String, Object>) typeds.get("nested")).get("bool")).isTrue();
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
        assertThat(typeds.get("json1")).isEqualTo(Map.of("a", "b"));
        assertThat(typeds.get("json2")).isEqualTo(object);
        assertThat(typeds.get("yaml1")).isEqualTo(Map.of(
            "some", "property",
            "alist", List.of("of", "values")));
        assertThat(typeds.get("yaml2")).isEqualTo(object);
    }

    @Test
    @LoadFlows(value = {"flows/valids/inputs.yaml"}, tenantId = "tenant2")
    void allValidTypedInputs() {
        Map<String, Object> typeds = typedInputs(inputs, "tenant2");
        typeds.put("int", 42);
        typeds.put("float", 42.42F);
        typeds.put("bool", false);

        assertThat(typeds.get("string")).isEqualTo("myString");
        assertThat(typeds.get("enum")).isEqualTo("ENUM_VALUE");
        assertThat(typeds.get("int")).isEqualTo(42);
        assertThat(typeds.get("float")).isEqualTo(42.42F);
        assertThat((Boolean) typeds.get("bool")).isFalse();
    }

    @Test
    @LoadFlows(value = {"flows/valids/inputs.yaml"}, tenantId = "tenant3")
    void inputFlow() throws TimeoutException, QueueException {
        Execution execution = runnerUtils.runOne(
            "tenant3",
            "io.kestra.tests",
            "inputs",
            null,
            (flow, execution1) -> flowIO.readExecutionInputs(flow, execution1, inputs)
        );

        assertThat(execution.getTaskRunList()).hasSize(16);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat((String) execution.findTaskRunsByTaskId("file").getFirst().getOutputs().get("value")).matches("kestra:///io/kestra/tests/inputs/executions/.*/inputs/file/application-test.yml");
        // secret inputs are decrypted to be used as task properties
        assertThat((String) execution.findTaskRunsByTaskId("secret").getFirst().getOutputs().get("value")).isEqualTo("secret");
        // null inputs are serialized
        assertThat((String) execution.findTaskRunsByTaskId("optional").getFirst().getOutputs().get("value")).isEmpty();
    }

    @Test
    @LoadFlows(value = {"flows/valids/inputs.yaml"}, tenantId = "tenant4")
    void inputValidatedStringBadValue() {
        HashMap<String, Object> map = new HashMap<>(inputs);
        map.put("validatedString", "foo");

        InputOutputValidationException e = assertThrows(InputOutputValidationException.class, () -> typedInputs(map, "tenant4"));

        assertThat(e.getMessage()).contains(  "Invalid value for input `validatedString`. Cause: it must match the pattern");
    }

    @Test
    @LoadFlows(value = {"flows/valids/inputs.yaml"}, tenantId = "tenant5")
    void inputValidatedIntegerBadValue() {
        HashMap<String, Object> mapMin = new HashMap<>(inputs);
        mapMin.put("validatedInt", "9");
        InputOutputValidationException e = assertThrows(InputOutputValidationException.class, () -> typedInputs(mapMin, "tenant5"));
        assertThat(e.getMessage()).contains("Invalid value for input `validatedInt`. Cause: it must be more than `10`");

        HashMap<String, Object> mapMax = new HashMap<>(inputs);
        mapMax.put("validatedInt", "21");

        e = assertThrows(InputOutputValidationException.class, () -> typedInputs(mapMax, "tenant5"));

        assertThat(e.getMessage()).contains("Invalid value for input `validatedInt`. Cause: it must be less than `20`");
    }

    @Test
    @LoadFlows(value = {"flows/valids/inputs.yaml"}, tenantId = "tenant6")
    void inputValidatedDateBadValue() {
        HashMap<String, Object> mapMin = new HashMap<>(inputs);
        mapMin.put("validatedDate", "2022-01-01");
        InputOutputValidationException e = assertThrows(InputOutputValidationException.class, () -> typedInputs(mapMin, "tenant6"));
        assertThat(e.getMessage()).contains("Invalid value for input `validatedDate`. Cause: it must be after `2023-01-01`");

        HashMap<String, Object> mapMax = new HashMap<>(inputs);
        mapMax.put("validatedDate", "2024-01-01");

        e = assertThrows(InputOutputValidationException.class, () -> typedInputs(mapMax, "tenant6"));

        assertThat(e.getMessage()).contains("Invalid value for input `validatedDate`. Cause: it must be before `2023-12-31`");
    }

    @Test
    @LoadFlows(value = {"flows/valids/inputs.yaml"}, tenantId = "tenant7")
    void inputValidatedDateTimeBadValue() {
        HashMap<String, Object> mapMin = new HashMap<>(inputs);
        mapMin.put("validatedDateTime", "2022-01-01T00:00:00Z");
        InputOutputValidationException e = assertThrows(InputOutputValidationException.class, () -> typedInputs(mapMin, "tenant7"));
        assertThat(e.getMessage()).contains("Invalid value for input `validatedDateTime`. Cause: it must be after `2023-01-01T00:00:00Z`");

        HashMap<String, Object> mapMax = new HashMap<>(inputs);
        mapMax.put("validatedDateTime", "2024-01-01T00:00:00Z");

        e = assertThrows(InputOutputValidationException.class, () -> typedInputs(mapMax, "tenant7"));

        assertThat(e.getMessage()).contains("Invalid value for input `validatedDateTime`. Cause: it must be before `2023-12-31T23:59:59Z`");
    }

    @Test
    @LoadFlows(value = {"flows/valids/inputs.yaml"}, tenantId = "tenant8")
    void inputValidatedDurationBadValue() {
        HashMap<String, Object> mapMin = new HashMap<>(inputs);
        mapMin.put("validatedDuration", "PT1S");
        InputOutputValidationException e = assertThrows(InputOutputValidationException.class, () -> typedInputs(mapMin, "tenant8"));
        assertThat(e.getMessage()).contains("Invalid value for input `validatedDuration`. Cause: It must be more than `PT10S`");

        HashMap<String, Object> mapMax = new HashMap<>(inputs);
        mapMax.put("validatedDuration", "PT30S");

        e = assertThrows(InputOutputValidationException.class, () -> typedInputs(mapMax, "tenant8"));

        assertThat(e.getMessage()).contains("Invalid value for input `validatedDuration`. Cause: It must be less than `PT20S`");
    }

    @Test
    @LoadFlows(value = {"flows/valids/inputs.yaml"}, tenantId = "tenant9")
    void inputValidatedFloatBadValue() {
        HashMap<String, Object> mapMin = new HashMap<>(inputs);
        mapMin.put("validatedFloat", "0.01");
        InputOutputValidationException e = assertThrows(InputOutputValidationException.class, () -> typedInputs(mapMin, "tenant9"));
        assertThat(e.getMessage()).contains("Invalid value for input `validatedFloat`. Cause: it must be more than `0.1`");

        HashMap<String, Object> mapMax = new HashMap<>(inputs);
        mapMax.put("validatedFloat", "1.01");

        e = assertThrows(InputOutputValidationException.class, () -> typedInputs(mapMax, "tenant9"));

        assertThat(e.getMessage()).contains("Invalid value for input `validatedFloat`. Cause: it must be less than `0.5`");
    }

    @Test
    @LoadFlows(value = {"flows/valids/inputs.yaml"}, tenantId = "tenant10")
    void inputValidatedTimeBadValue() {
        HashMap<String, Object> mapMin = new HashMap<>(inputs);
        mapMin.put("validatedTime", "00:00:01");
        InputOutputValidationException e = assertThrows(InputOutputValidationException.class, () -> typedInputs(mapMin, "tenant10"));
        assertThat(e.getMessage()).contains(  "Invalid value for input `validatedTime`. Cause: it must be after `01:00`");

        HashMap<String, Object> mapMax = new HashMap<>(inputs);
        mapMax.put("validatedTime", "14:00:00");

        e = assertThrows(InputOutputValidationException.class, () -> typedInputs(mapMax, "tenant10"));

        assertThat(e.getMessage()).contains("Invalid value for input `validatedTime`. Cause: it must be before `11:59:59`");
    }

    @Test
    @LoadFlows(value = {"flows/valids/inputs.yaml"}, tenantId = "tenant11")
    void inputFailed() {
        HashMap<String, Object> map = new HashMap<>(inputs);
        map.put("uri", "http:/bla");

        InputOutputValidationException e = assertThrows(InputOutputValidationException.class, () -> typedInputs(map, "tenant11"));

        assertThat(e.getMessage()).contains(  "Invalid value for input `uri`. Cause: Invalid URI format." );
    }

    @Test
    @LoadFlows(value = {"flows/valids/inputs.yaml"}, tenantId = "tenant12")
    void inputEnumFailed() {
        HashMap<String, Object> map = new HashMap<>(inputs);
        map.put("enum", "INVALID");

        InputOutputValidationException e = assertThrows(InputOutputValidationException.class, () -> typedInputs(map, "tenant12"));

        assertThat(e.getMessage()).isEqualTo("Invalid value for input `enum`. Cause: it must match the values `[ENUM_VALUE, OTHER_ONE]`");
    }

    @Test
    @LoadFlows(value = {"flows/valids/inputs.yaml"}, tenantId = "tenant13")
    void inputArrayFailed() {
        HashMap<String, Object> map = new HashMap<>(inputs);
        map.put("array", "[\"s1\", \"s2\"]");

        InputOutputValidationException e = assertThrows(InputOutputValidationException.class, () -> typedInputs(map, "tenant13"));

        assertThat(e.getMessage()).contains(  "Invalid value for input `array`. Cause: Unable to parse array element as `INT` on `s1`");
    }

    @Test
    @LoadFlows(value = {"flows/valids/inputs.yaml"}, tenantId = "tenant14")
    void inputEmptyJson() {
        HashMap<String, Object> map = new HashMap<>(inputs);
        map.put("json1", "{}");

        Map<String, Object> typeds = typedInputs(map, "tenant14");

        assertThat(typeds.get("json1")).isInstanceOf(Map.class);
        assertThat(((Map<?, ?>) typeds.get("json1")).size()).isZero();
    }

    @Test
    @LoadFlows(value = {"flows/valids/inputs.yaml"}, tenantId = "tenant15")
    void inputEmptyJsonFlow() throws TimeoutException, QueueException {
        HashMap<String, Object> map = new HashMap<>(inputs);
        map.put("json1", "{}");

        Execution execution = runnerUtils.runOne(
            "tenant15",
            "io.kestra.tests",
            "inputs",
            null,
            (flow, execution1) -> flowIO.readExecutionInputs(flow, execution1, map)
        );

        assertThat(execution.getTaskRunList()).hasSize(16);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

        assertThat(execution.getInputs().get("json1")).isInstanceOf(Map.class);
        assertThat(((Map<?, ?>) execution.getInputs().get("json1")).size()).isZero();
        assertThat((String) execution.findTaskRunsByTaskId("jsonOutput").getFirst().getOutputs().get("value")).isEqualTo("{}");
    }

    @Test
    @LoadFlows(value = {"flows/valids/input-log-secret.yaml"}, tenantId = "tenant16")
    void shouldNotLogSecretInput() throws TimeoutException, QueueException, InterruptedException {
        AtomicReference<LogEntry> logEntry = new AtomicReference<>();
        CountDownLatch countDownLatch = new CountDownLatch(1);
        Flux<LogEntry> receive = TestsUtils.receive(logQueue, l -> {
            LogEntry left = l.getLeft();
            if (left.getTenantId().equals("tenant16")){
                logEntry.set(left);
                countDownLatch.countDown();
            }
        });

        Execution execution = runnerUtils.runOne(
            "tenant16",
            "io.kestra.tests",
            "input-log-secret",
            null,
            (flow, exec) -> flowInputOutput.readExecutionInputs(flow, exec, Map.of("nested.key", "pass"))
        );

        assertThat(execution.getTaskRunList()).hasSize(1);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

        receive.blockLast();
        assertTrue(countDownLatch.await(10, TimeUnit.SECONDS));
        assertThat(logEntry.get()).isNotNull();
        assertThat(logEntry.get().getMessage()).isEqualTo("These are my secrets: ****** - ******");
    }

    @Test
    @LoadFlows(value = {"flows/valids/inputs.yaml"}, tenantId = "tenant17")
    void fileInputWithFileDefault() throws IOException, QueueException, TimeoutException {
        HashMap<String, Object> newInputs = new HashMap<>(InputsTest.inputs);
        URI file = createFile();
        newInputs.put("file", file);

        Execution execution = runnerUtils.runOne(
            "tenant17",
            "io.kestra.tests",
            "inputs",
            null,
            (flow, execution1) -> flowIO.readExecutionInputs(flow, execution1, newInputs)
        );

        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat((String) execution.findTaskRunsByTaskId("file").getFirst().getOutputs().get("value")).isEqualTo(file.toString());
    }

    @Test
    @LoadFlows(value = {"flows/valids/inputs.yaml"}, tenantId = "tenant18")
    void fileInputWithNsfile() throws IOException, QueueException, TimeoutException, URISyntaxException {
        HashMap<String, Object> inputs = new HashMap<>(InputsTest.inputs);
        URI file = createNsFile(false);
        inputs.put("file", file);

        Execution execution = runnerUtils.runOne(
            "tenant18",
            "io.kestra.tests",
            "inputs",
            null,
            (flow, execution1) -> flowIO.readExecutionInputs(flow, execution1, inputs)
        );

        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat((String) execution.findTaskRunsByTaskId("file").getFirst().getOutputs().get("value")).isEqualTo(file.toString());
    }
    @Test
    @LoadFlows(value = "flows/invalids/inputs-with-multiple-constraint-violations.yaml")
    void multipleConstraintViolations()  {
        InputOutputValidationException ex = assertThrows(InputOutputValidationException.class, ()-> runnerUtils.runOne(MAIN_TENANT, "io.kestra.tests", "inputs-with-multiple-constraint-violations", null,
            (f, e) ->flowIO.readExecutionInputs(f, e , Map.of("multi", List.of("F", "H")) )));

        List<String> messages = Arrays.asList(ex.getMessage().split(System.lineSeparator()));

        assertThat(messages).containsExactlyInAnyOrder(
            "Invalid value for input `multi`. Cause: you can't define both `values` and `options`",
            "Invalid value for input `multi`. Cause: value `F` doesn't match the values `[A, B, C]`",
            "Invalid value for input `multi`. Cause: value `H` doesn't match the values `[A, B, C]`"
        );
    }
    private URI createFile() throws IOException {
        File tempFile = File.createTempFile("file", ".txt");
        Files.write(tempFile.toPath(), "Hello World".getBytes());
        return tempFile.toPath().toUri();
    }

    private URI createNsFile(boolean nsInAuthority) throws IOException, URISyntaxException {
        String namespace = "io.kestra.tests";
        String filePath = "file.txt";
        Namespace namespaceStorage = namespaceFactory.of(MAIN_TENANT, namespace, storageInterface);
        namespaceStorage.putFile(Path.of("/" + filePath), new ByteArrayInputStream("Hello World".getBytes()));
        return URI.create("nsfile://" + (nsInAuthority ? namespace : "") + "/" + filePath);
    }
}
