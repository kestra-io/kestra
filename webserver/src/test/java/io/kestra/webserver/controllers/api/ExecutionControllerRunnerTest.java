package io.kestra.webserver.controllers.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.collect.ImmutableMap;
import io.kestra.core.junit.annotations.ExecuteFlow;
import io.kestra.core.junit.annotations.FlakyTest;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.junit.annotations.LoadFlows;
import io.kestra.core.models.Label;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionKilled;
import io.kestra.core.models.executions.ExecutionKilledExecution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.flows.State.Type;
import io.kestra.core.models.storage.FileMetas;
import io.kestra.core.queues.QueueException;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.runners.FlowInputOutput;
import io.kestra.core.runners.InputsTest;
import io.kestra.core.runners.LocalPath;
import io.kestra.core.runners.RunnerUtils;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.storages.Namespace;
import io.kestra.core.storages.NamespaceFactory;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.utils.Await;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.TestsUtils;
import io.kestra.jdbc.JdbcTestUtils;
import io.kestra.plugin.core.trigger.Webhook;
import io.kestra.webserver.responses.BulkErrorResponse;
import io.kestra.webserver.responses.BulkResponse;
import io.kestra.webserver.responses.PagedResults;
import io.micronaut.context.annotation.Property;
import io.micronaut.core.type.Argument;
import io.micronaut.data.model.Pageable;
import io.micronaut.http.*;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.client.multipart.MultipartBody;
import io.micronaut.http.sse.Event;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import io.micronaut.reactor.http.client.ReactorSseClient;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import lombok.extern.slf4j.Slf4j;
import org.awaitility.Awaitility;
import org.hamcrest.Matcher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static io.kestra.core.utils.Rethrow.throwRunnable;
import static io.micronaut.http.HttpRequest.*;
import static io.micronaut.http.HttpRequest.DELETE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Slf4j
@KestraTest(startRunner = true)
@Property(name = LocalPath.ALLOWED_PATHS_CONFIG, value = "/tmp")
class ExecutionControllerRunnerTest {
    public static final String URL_LABEL_VALUE = "https://some-url.com";
    public static final String ENCODED_URL_LABEL_VALUE = URL_LABEL_VALUE.replace("/", URLEncoder.encode("/", StandardCharsets.UTF_8));

    @Inject
    @Named(QueueFactoryInterface.EXECUTION_NAMED)
    protected QueueInterface<Execution> executionQueue;

    @Inject
    @Named(QueueFactoryInterface.KILL_NAMED)
    protected QueueInterface<ExecutionKilled> killQueue;

    @Inject
    FlowRepositoryInterface flowRepositoryInterface;

    @Inject
    ExecutionRepositoryInterface executionRepositoryInterface;

    @Inject
    @Client("/")
    ReactorHttpClient client;

    @Inject
    @Client("/")
    ReactorSseClient sseClient;

    @Inject
    private FlowInputOutput flowIO;

    @Inject
    private JdbcTestUtils jdbcTestUtils;

    @Inject
    protected RunnerUtils runnerUtils;

    @Inject
    private StorageInterface storageInterface;

    @Inject
    private NamespaceFactory namespaceFactory;

    public static final String TESTS_FLOW_NS = "io.kestra.tests";
    public static final String TENANT_ID = "main";

    public static Map<String, Object> inputs = ImmutableMap.<String, Object>builder()
        .put("failed", "NO")
        .put("string", "myString")
        .put("enum", "ENUM_VALUE")
        .put("int", "42")
        .put("float", "42.42")
        .put("instant", "2019-10-06T18:27:49Z")
        .put("file", Objects.requireNonNull(InputsTest.class.getClassLoader().getResource("data/hello.txt")).getPath())
        .put("secret", "secret")
        .put("array", "[1, 2, 3]")
        .put("json1", "{}")
        .put("yaml1", """
            some: property
            alist:
            - of
            - values""")
        .build();

    @AfterEach
    protected void setup() {
        jdbcTestUtils.drop();
        jdbcTestUtils.migrate();
    }

    @Test
    @LoadFlows({"flows/valids/inputs.yaml"})
    void triggerExecution() {
        Execution result = triggerExecutionInputsFlowExecution(false);

        assertThat(result.getState().getCurrent()).isEqualTo(State.Type.CREATED);
        assertThat(result.getFlowId()).isEqualTo("inputs");
        assertThat(result.getInputs().get("float")).isEqualTo(42.42);
        assertThat(result.getInputs().get("file").toString()).startsWith("kestra:///io/kestra/tests/inputs/executions/");
        assertThat(result.getInputs().get("file").toString()).startsWith("kestra:///io/kestra/tests/inputs/executions/");
        assertThat(result.getInputs().containsKey("bool")).isTrue();
        assertThat(result.getInputs().get("bool")).isNull();
        assertThat(result.getLabels()).containsExactlyInAnyOrder(
            new Label("flow-label-1", "flow-label-1"),
            new Label("flow-label-2", "flow-label-2"),
            new Label("a", "label-1"),
            new Label("b", "label-2"),
            new Label("url", URL_LABEL_VALUE),
            new Label(Label.CORRELATION_ID, result.getId()),
            new Label(Label.FROM, "api")
        );

        var notFound = assertThrows(HttpClientResponseException.class, () -> client.toBlocking().exchange(
            HttpRequest
                .POST("/api/v1/main/executions/foo/bar", createExecutionInputsFlowBody())
                .contentType(MediaType.MULTIPART_FORM_DATA_TYPE),
            HttpResponse.class
        ));
        assertThat(notFound.getStatus().getCode()).isEqualTo(HttpStatus.NOT_FOUND.getCode());
    }

    @Test
    @LoadFlows({"flows/valids/minimal.yaml"})
    void flowLabelsGetsOverriddenByExecutionLabelsOnSameKey() {
        final String executionLabel = "existing:fromExecution";
        Execution result = client.toBlocking().retrieve(
            HttpRequest
                .POST("/api/v1/main/executions/io.kestra.tests/minimal?labels=" + executionLabel + "&wait=true", null)
                .contentType(MediaType.MULTIPART_FORM_DATA_TYPE),
            Execution.class
        );

        Execution execution = client.toBlocking().retrieve(
            GET("/api/v1/main/executions/" + result.getId()),
            Execution.class);

        assertThat(execution.getLabels()).containsExactlyInAnyOrder(
            new Label(Label.CORRELATION_ID, execution.getId()),
            new Label(Label.FROM, "api"),
            new Label("existing", "fromExecution")
        );
    }

    @Test
    @LoadFlows({"flows/valids/inputs-small-files.yaml"})
    void triggerExecutionInputSmall() {
        File applicationFile = new File(Objects.requireNonNull(
            ExecutionControllerTest.class.getClassLoader().getResource("application-test.yml")
        ).getPath());

        MultipartBody requestBody = MultipartBody.builder()
            .addPart("files", "f", MediaType.TEXT_PLAIN_TYPE, applicationFile)
            .build();

        Execution execution = triggerExecutionExecution(TESTS_FLOW_NS, "inputs-small-files", requestBody, true);

        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat((String) execution.getOutputs().get("o")).startsWith("kestra://");
    }

    @Test
    @LoadFlows({"flows/valids/inputs.yaml"})
    void invalidInputs() {
        MultipartBody.Builder builder = MultipartBody.builder()
            .addPart("validatedString", "B-failed");
        inputs.forEach((s, o) -> builder.addPart(s, o instanceof String str ? str : null));

        HttpClientResponseException e = assertThrows(
            HttpClientResponseException.class,
            () -> triggerExecutionExecution(TESTS_FLOW_NS, "inputs", builder.build(), false)
        );

        String response = e.getResponse().getBody(String.class).orElseThrow();

        assertThat(response).contains("Invalid entity");
        assertThat(response).contains("Invalid value for input `validatedString`");
    }

    @Test
    @LoadFlows({"flows/valids/inputs.yaml"})
    void triggerExecutionAndWait() {
        Execution result = triggerExecutionInputsFlowExecution(true);

        assertThat(result.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(result.getTaskRunList().size()).isEqualTo(16);
    }

    @Test
    @LoadFlows({"flows/valids/inputs.yaml"})
    void getExecution() {
        Execution result = triggerExecutionInputsFlowExecution(false);

        // Get the triggered execution by execution id
        Execution foundExecution = client.retrieve(
            GET("/api/v1/main/executions/" + result.getId()),
            Execution.class
        ).block();

        assertThat(foundExecution).isNotNull();
        assertThat(foundExecution.getId()).isEqualTo(result.getId());
        assertThat(foundExecution.getNamespace()).isEqualTo(result.getNamespace());
    }

    @SuppressWarnings("unchecked")
    @Test
    @LoadFlows({"flows/valids/minimal-bis.yaml"})
    void searchExecutionsByFlowId() throws TimeoutException {
        String namespace = "io.kestra.tests.minimal.bis";
        String flowId = "minimal-bis";

        PagedResults<Execution> executionsBefore = client.toBlocking().retrieve(
            GET("/api/v1/main/executions?namespace=" + namespace + "&flowId=" + flowId),
            Argument.of(PagedResults.class, Execution.class)
        );

        assertThat(executionsBefore.getTotal()).isEqualTo(0L);

        triggerExecutionExecution(namespace, flowId, MultipartBody.builder().addPart("string", "myString").build(), false);

        // Wait for execution indexation
        Await.until(() -> executionRepositoryInterface.findByFlowId(TENANT_ID, namespace, flowId, Pageable.from(1)).size() == 1, Duration.ofMillis(100), Duration.ofMillis(10));
        PagedResults<Execution> executionsAfter = client.toBlocking().retrieve(
            GET("/api/v1/main/executions?namespace=" + namespace + "&flowId=" + flowId),
            Argument.of(PagedResults.class, Execution.class)
        );

        assertThat(executionsAfter.getTotal()).isEqualTo(1L);
    }

    @Test
    @LoadFlows({"flows/valids/inputs.yaml"})
    void triggerExecutionAndFollowExecution() {
        Execution result = triggerExecutionInputsFlowExecution(false);

        List<Event<Execution>> results = sseClient
            .eventStream("/api/v1/main/executions/" + result.getId() + "/follow", Execution.class)
            .collectList()
            .block();

        assertThat(results).isNotNull();
        assertThat(results.size()).isGreaterThan(0);
        assertThat(results.getLast().getData().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(results.getFirst().getId()).isEqualTo("start");
        assertThat(results.getLast().getId()).isEqualTo("end");

        // check that a second call work: calling follow on an already terminated execution.
        results = sseClient
            .eventStream("/api/v1/main/executions/" + result.getId() + "/follow", Execution.class)
            .collectList()
            .block();

        assertThat(results).isNotNull();
        assertThat(results.size()).isGreaterThan(0);
        assertThat(results.getLast().getData().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(results.getFirst().getId()).isEqualTo("start");
        assertThat(results.getLast().getId()).isEqualTo("end");
    }

    @Test
    @LoadFlows({"flows/valids/each-sequential-nested.yaml"})
    void evalTaskRunExpression() throws TimeoutException, QueueException {
        Execution execution = runnerUtils.runOne(TENANT_ID, TESTS_FLOW_NS, "each-sequential-nested");

        ExecutionController.EvalResult result = this.evalTaskRunExpression(execution, "my simple string", 0);
        assertThat(result.getResult()).isEqualTo("my simple string");

        result = this.evalTaskRunExpression(execution, "{{ taskrun.id }}", 0);
        assertThat(result.getResult()).isEqualTo(execution.getTaskRunList().getFirst().getId());

        result = this.evalTaskRunExpression(execution, "{{ outputs['1-1_return'][taskrun.value].value }}", 21);
        assertThat(result.getResult()).contains("1-1_return");

        result = this.evalTaskRunExpression(execution, "{{ missing }}", 21);
        assertThat(result.getResult()).isNull();
        assertThat(result.getError()).contains("Unable to find `missing` used in the expression `{{ missing }}` at line 1");
        assertThat(result.getStackTrace()).contains("Unable to find `missing` used in the expression `{{ missing }}` at line 1");
    }

    @Test
    @LoadFlows({"flows/valids/inputs.yaml",
        "flows/valids/encrypted-string.yaml"})
    void evalTaskRunExpressionKeepEncryptedValues() throws TimeoutException, QueueException {
        Execution execution = runnerUtils.runOne(TENANT_ID, TESTS_FLOW_NS, "encrypted-string");

        ExecutionController.EvalResult result = this.evalTaskRunExpression(execution, "{{outputs.hello.value}}", 0);
        Map<String, Object> resultMap = null;
        try {
            resultMap = JacksonMapper.toMap(result.getResult());
        } catch (JsonProcessingException e) {
            throw new AssertionError("Evaluation result is not a map. Probably due to output decryption being performed while it shouldn't for such feature.");
        }
        assertThat(resultMap.get("type")).isEqualTo("io.kestra.datatype:aes_encrypted");
        assertThat(resultMap.get("value")).isNotNull();

        execution = runnerUtils.runOne(TENANT_ID, TESTS_FLOW_NS, "inputs", null, (flow, execution1) -> flowIO.readExecutionInputs(flow, execution1, inputs));

        result = this.evalTaskRunExpression(execution, "{{inputs.secret}}", 0);
        assertThat(result.getResult()).isNotEqualTo(inputs.get("secret"));
    }

    @Test
    @LoadFlows({"flows/valids/restart_with_inputs.yaml"})
    void restartExecutionFromUnknownTaskId() throws TimeoutException, QueueException {
        final String flowId = "restart_with_inputs";
        final String referenceTaskId = "unknownTaskId";

        // Run execution until it ends
        Execution parentExecution = runnerUtils.runOne(TENANT_ID, TESTS_FLOW_NS, flowId, null, (flow, execution1) -> flowIO.readExecutionInputs(flow, execution1, inputs));

        HttpClientResponseException e = assertThrows(HttpClientResponseException.class, () -> client.toBlocking().retrieve(
            HttpRequest
                .POST("/api/v1/main/executions/" + parentExecution.getId() + "/replay?taskRunId=" + referenceTaskId, ImmutableMap.of()),
            Execution.class
        ));

        assertThat(e.getStatus().getCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY.getCode());
        assertThat(e.getResponse().getBody(String.class).isPresent()).isTrue();
        assertThat(e.getResponse().getBody(String.class).get()).contains("No task found");
    }

    @Test
    @LoadFlows({"flows/valids/restart_with_inputs.yaml"})
    void restartExecutionWithNoFailure() throws TimeoutException, QueueException{
        final String flowId = "restart_with_inputs";

        // Run execution until it ends
        Execution parentExecution = runnerUtils.runOne(TENANT_ID, TESTS_FLOW_NS, flowId, null, (flow, execution1) -> flowIO.readExecutionInputs(flow, execution1, inputs));

        HttpClientResponseException e = assertThrows(HttpClientResponseException.class, () -> client.toBlocking().retrieve(
            HttpRequest
                .POST("/api/v1/main/executions/" + parentExecution.getId() + "/restart", ImmutableMap.of()),
            Execution.class
        ));

        assertThat(e.getStatus().getCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY.getCode());
        assertThat(e.getResponse().getBody(String.class).isPresent()).isTrue();
        assertThat(e.getResponse().getBody(String.class).get()).contains("No task found to restart");
    }

    @Test
    @LoadFlows({"flows/valids/restart_with_inputs.yaml"})
    void restartExecutionFromTaskId() throws Exception {
        final String flowId = "restart_with_inputs";
        final String referenceTaskId = "instant";

        // Run execution until it ends
        Execution parentExecution = runnerUtils.runOne(TENANT_ID, TESTS_FLOW_NS, flowId, null, (flow, execution1) -> flowIO.readExecutionInputs(flow, execution1, inputs));

        Optional<Flow> flow = flowRepositoryInterface.findById(TENANT_ID, TESTS_FLOW_NS, flowId);

        assertThat(flow.isPresent()).isTrue();

        // Run child execution starting from a specific task and wait until it finishes
        Execution finishedChildExecution = runnerUtils.awaitChildExecution(
            flow.get(),
            parentExecution, throwRunnable(() -> {
                Thread.sleep(100);

                Execution createdChidExec = client.toBlocking().retrieve(
                    HttpRequest
                        .POST("/api/v1/main/executions/" + parentExecution.getId() + "/replay?taskRunId=" + parentExecution.findTaskRunByTaskIdAndValue(referenceTaskId, List.of()).getId(), ImmutableMap.of()),
                    Execution.class
                );

            assertThat(createdChidExec).isNotNull();
            assertThat(createdChidExec.getParentId()).isEqualTo(parentExecution.getId());
            assertThat(createdChidExec.getTaskRunList().size()).isEqualTo(4);
            assertThat(createdChidExec.getState().getCurrent()).isEqualTo(State.Type.RESTARTED);

                IntStream
                    .range(0, 3)
                    .mapToObj(value -> createdChidExec.getTaskRunList().get(value))
                    .forEach(taskRun -> assertThat(taskRun.getState().getCurrent()).isEqualTo(State.Type.SUCCESS));

            assertThat(createdChidExec.getTaskRunList().get(3).getState().getCurrent()).isEqualTo(State.Type.RESTARTED);
            assertThat(createdChidExec.getTaskRunList().get(3).getAttempts().size()).isEqualTo(1);
            }),
            Duration.ofSeconds(15));

        assertThat(finishedChildExecution).isNotNull();
        assertThat(finishedChildExecution.getParentId()).isEqualTo(parentExecution.getId());
        assertThat(finishedChildExecution.getTaskRunList().size()).isEqualTo(5);

        finishedChildExecution
            .getTaskRunList()
            .stream()
            .map(TaskRun::getState)
            .forEach(state -> assertThat(state.getCurrent()).isEqualTo(State.Type.SUCCESS));
    }

    @Test
    @LoadFlows({"flows/valids/condition_with_input.yaml"})
    void restartExecutionWithNewInputs() throws Exception {
        final String flowId = "condition_with_input";

        // Run execution until it ends
        Execution parentExecution = runnerUtils.runOne(TENANT_ID, TESTS_FLOW_NS, flowId, null,
            (flow, execution1) -> flowIO.readExecutionInputs(flow, execution1, Map.of("condition", "fail")));

        assertThat(parentExecution.getState().getCurrent()).isEqualTo(Type.FAILED);

        Optional<Flow> flow = flowRepositoryInterface.findById(TENANT_ID, TESTS_FLOW_NS, flowId);

        assertThat(flow.isPresent()).isTrue();

        // Run child execution starting from a specific task and wait until it finishes
        Execution finishedChildExecution = runnerUtils.awaitChildExecution(
            flow.get(),
            parentExecution, throwRunnable(() -> {
                Thread.sleep(100);

                MultipartBody multipartBody = MultipartBody.builder()
                    .addPart("condition", "success")
                    .build();

                Execution replay = client.toBlocking().retrieve(
                    HttpRequest
                        .POST("/api/v1/main/executions/" + parentExecution.getId() + "/replay-with-inputs", multipartBody)
                        .contentType(MediaType.MULTIPART_FORM_DATA_TYPE),
                    Execution.class
                );

                assertThat(replay).isNotNull();
                assertThat(replay.getParentId()).isEqualTo(parentExecution.getId());
                assertThat(replay.getState().getCurrent()).isEqualTo(Type.CREATED);
            }),
            Duration.ofSeconds(15));

        assertThat(finishedChildExecution).isNotNull();
        assertThat(finishedChildExecution.getParentId()).isEqualTo(parentExecution.getId());
        assertThat(finishedChildExecution.getTaskRunList().size()).isEqualTo(2);

        finishedChildExecution
            .getTaskRunList()
            .stream()
            .map(TaskRun::getState)
            .forEach(state -> assertThat(state.getCurrent()).isIn(State.Type.SUCCESS, State.Type.SKIPPED));
    }

    @Test
    @LoadFlows({"flows/valids/condition_with_input.yaml"})
    void restartExecutionFromTaskIdWithInputs() throws Exception {
        final String flowId = "condition_with_input";
        final String referenceTaskId = "fail";

        // Run execution until it ends
        Execution parentExecution = runnerUtils.runOne(TENANT_ID, TESTS_FLOW_NS, flowId, null,
            (flow, execution1) -> flowIO.readExecutionInputs(flow, execution1, Map.of("condition", "fail")));

        assertThat(parentExecution.getState().getCurrent()).isEqualTo(Type.FAILED);

        Optional<Flow> flow = flowRepositoryInterface.findById(TENANT_ID, TESTS_FLOW_NS, flowId);

        assertThat(flow.isPresent()).isTrue();

        // Run child execution starting from a specific task and wait until it finishes
        Execution finishedChildExecution = runnerUtils.awaitChildExecution(
            flow.get(),
            parentExecution, throwRunnable(() -> {
                Thread.sleep(100);

                MultipartBody multipartBody = MultipartBody.builder()
                    .addPart("condition", "success")
                    .build();

                Execution replay = client.toBlocking().retrieve(
                    HttpRequest
                        .POST("/api/v1/main/executions/" + parentExecution.getId() + "/replay-with-inputs?taskRunId=" + parentExecution.findTaskRunByTaskIdAndValue(referenceTaskId, List.of()).getId(), multipartBody)
                        .contentType(MediaType.MULTIPART_FORM_DATA_TYPE),
                    Execution.class
                );

                assertThat(replay).isNotNull();
                assertThat(replay.getParentId()).isEqualTo(parentExecution.getId());
                assertThat(replay.getTaskRunList().size()).isEqualTo(2);
                assertThat(replay.getState().getCurrent()).isEqualTo(State.Type.RESTARTED);
            }),
            Duration.ofSeconds(15));

        assertThat(finishedChildExecution).isNotNull();
        assertThat(finishedChildExecution.getParentId()).isEqualTo(parentExecution.getId());
        assertThat(finishedChildExecution.getTaskRunList().size()).isEqualTo(2);

        finishedChildExecution
            .getTaskRunList()
            .stream()
            .map(TaskRun::getState)
            .forEach(state -> assertThat(state.getCurrent()).isIn(State.Type.SUCCESS, State.Type.SKIPPED));
    }

    @Test
    @LoadFlows({"flows/valids/restart-each.yaml"})
    void restartExecutionFromTaskIdWithSequential() throws Exception {
        final String flowId = "restart-each";
        final String referenceTaskId = "2_end";

        // Run execution until it ends
        Execution parentExecution = runnerUtils.runOne(TENANT_ID, TESTS_FLOW_NS, flowId, null,
            (flow, execution1) -> flowIO.readExecutionInputs(flow, execution1, inputs));

        Optional<Flow> flow = flowRepositoryInterface.findById(TENANT_ID, TESTS_FLOW_NS, flowId);
        assertThat(flow.isPresent()).isTrue();

        // Run child execution starting from a specific task and wait until it finishes
        runnerUtils.awaitChildExecution(
            flow.get(),
            parentExecution, throwRunnable(() -> {
                Thread.sleep(100);

                Execution createdChidExec = client.toBlocking().retrieve(
                    HttpRequest
                        .POST("/api/v1/main/executions/" + parentExecution.getId() + "/replay?taskRunId=" + parentExecution.findTaskRunByTaskIdAndValue(referenceTaskId, List.of()).getId(), ImmutableMap.of()),
                    Execution.class
                );

            assertThat(createdChidExec.getState().getCurrent()).isEqualTo(State.Type.RESTARTED);
            assertThat(createdChidExec.getState().getHistories()).hasSize(4);
            assertThat(createdChidExec.getTaskRunList()).hasSize(20);

            assertThat(createdChidExec.getId()).isNotEqualTo(parentExecution.getId());
            }),
            Duration.ofSeconds(30));
    }

    @Test
    @LoadFlows({"flows/valids/restart_last_failed.yaml"})
    void restartExecutionFromLastFailed() throws TimeoutException, QueueException{
        final String flowId = "restart_last_failed";

        // Run execution until it ends
        Execution firstExecution = runnerUtils.runOne(TENANT_ID, TESTS_FLOW_NS, flowId, null, null);

        assertThat(firstExecution.getTaskRunList().get(2).getState().getCurrent()).isEqualTo(State.Type.FAILED);
        assertThat(firstExecution.getState().getCurrent()).isEqualTo(State.Type.FAILED);

        // Update task's command to make second execution successful
        Optional<Flow> flow = flowRepositoryInterface.findById(TENANT_ID, TESTS_FLOW_NS, flowId);
        assertThat(flow.isPresent()).isTrue();

        // Restart execution and wait until it finishes
        Execution finishedRestartedExecution = runnerUtils.awaitExecution(
            execution -> execution.getId().equals(firstExecution.getId()) &&
                execution.getTaskRunList().size() == 4 &&
                execution.getState().isTerminated(),
            () -> {
                Execution restartedExec = client.toBlocking().retrieve(
                    HttpRequest
                        .POST("/api/v1/main/executions/" + firstExecution.getId() + "/restart", ImmutableMap.of()),
                    Execution.class
                );

                assertThat(restartedExec).isNotNull();
                assertThat(restartedExec.getId()).isEqualTo(firstExecution.getId());
                assertThat(restartedExec.getParentId()).isNull();
                assertThat(restartedExec.getTaskRunList().size()).isEqualTo(3);
                assertThat(restartedExec.getState().getCurrent()).isEqualTo(State.Type.RESTARTED);

                IntStream
                    .range(0, 2)
                    .mapToObj(value -> restartedExec.getTaskRunList().get(value)).forEach(taskRun -> {
                    assertThat(taskRun.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
                    assertThat(taskRun.getAttempts().size()).isEqualTo(1);

                    assertThat(restartedExec.getTaskRunList().get(2).getState().getCurrent()).isEqualTo(State.Type.RESTARTED);
                    assertThat(restartedExec.getTaskRunList().get(2).getAttempts().size()).isEqualTo(1);
                    });
            },
            Duration.ofSeconds(15)
        );

        assertThat(finishedRestartedExecution).isNotNull();
        assertThat(finishedRestartedExecution.getId()).isEqualTo(firstExecution.getId());
        assertThat(finishedRestartedExecution.getParentId()).isNull();
        assertThat(finishedRestartedExecution.getTaskRunList().size()).isEqualTo(4);

        assertThat(finishedRestartedExecution.getTaskRunList().getFirst().getAttempts().size()).isEqualTo(1);
        assertThat(finishedRestartedExecution.getTaskRunList().get(1).getAttempts().size()).isEqualTo(1);
        assertThat(finishedRestartedExecution.getTaskRunList().get(2).getAttempts().size()).isEqualTo(2);
        assertThat(finishedRestartedExecution.getTaskRunList().get(3).getAttempts().size()).isEqualTo(1);

        finishedRestartedExecution
            .getTaskRunList()
            .stream()
            .map(TaskRun::getState)
            .forEach(state -> assertThat(state.getCurrent()).isEqualTo(State.Type.SUCCESS));
    }

    @Test
    @LoadFlows({"flows/valids/restart_pause_last_failed.yaml"})
    void restartExecutionFromLastFailedWithPauseExecution() throws TimeoutException, QueueException{
        final String flowId = "restart_pause_last_failed";

        // Run execution until it ends
        Execution firstExecution = runnerUtils.runOne(TENANT_ID, TESTS_FLOW_NS, flowId, null, null);

        assertThat(firstExecution.getTaskRunList().get(2).getState().getCurrent()).isEqualTo(State.Type.FAILED);
        assertThat(firstExecution.getState().getCurrent()).isEqualTo(State.Type.FAILED);

        // Update task's command to make second execution successful
        Optional<Flow> flow = flowRepositoryInterface.findById(TENANT_ID, TESTS_FLOW_NS, flowId);
        assertThat(flow.isPresent()).isTrue();

        // Restart execution and wait until it finishes
        Execution finishedRestartedExecution = runnerUtils.awaitExecution(
            execution -> execution.getId().equals(firstExecution.getId()) &&
                execution.getTaskRunList().size() == 5 &&
                execution.getState().isTerminated(),
            () -> {
                Execution restartedExec = client.toBlocking().retrieve(
                    HttpRequest
                        .POST("/api/v1/main/executions/" + firstExecution.getId() + "/restart", ImmutableMap.of()),
                    Execution.class
                );

                assertThat(restartedExec).isNotNull();
                assertThat(restartedExec.getId()).isEqualTo(firstExecution.getId());
                assertThat(restartedExec.getParentId()).isNull();
                assertThat(restartedExec.getTaskRunList().size()).isEqualTo(4);
                assertThat(restartedExec.getState().getCurrent()).isEqualTo(State.Type.RESTARTED);

                IntStream
                    .range(0, 2)
                    .mapToObj(value -> restartedExec.getTaskRunList().get(value)).forEach(taskRun -> {
                    assertThat(taskRun.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
                    assertThat(taskRun.getAttempts().size()).isEqualTo(1);

                    assertThat(restartedExec.getTaskRunList().get(2).getState().getCurrent()).isEqualTo(State.Type.RUNNING);
                    assertThat(restartedExec.getTaskRunList().get(3).getState().getCurrent()).isEqualTo(State.Type.RESTARTED);
                    assertThat(restartedExec.getTaskRunList().get(2).getAttempts()).isNotNull();
                    assertThat(restartedExec.getTaskRunList().get(3).getAttempts().size()).isEqualTo(1);
                    });
            },
            Duration.ofSeconds(15)
        );

        assertThat(finishedRestartedExecution).isNotNull();
        assertThat(finishedRestartedExecution.getId()).isEqualTo(firstExecution.getId());
        assertThat(finishedRestartedExecution.getParentId()).isNull();
        assertThat(finishedRestartedExecution.getTaskRunList().size()).isEqualTo(5);

        assertThat(finishedRestartedExecution.getTaskRunList().getFirst().getAttempts().size()).isEqualTo(1);
        assertThat(finishedRestartedExecution.getTaskRunList().get(1).getAttempts().size()).isEqualTo(1);
        assertThat(finishedRestartedExecution.getTaskRunList().get(2).getAttempts()).isNotNull();
        assertThat(finishedRestartedExecution.getTaskRunList().get(2).getState().getHistories().stream().filter(state -> state.getState() == State.Type.PAUSED).count()).isEqualTo(1L);
        assertThat(finishedRestartedExecution.getTaskRunList().get(3).getAttempts().size()).isEqualTo(2);
        assertThat(finishedRestartedExecution.getTaskRunList().get(4).getAttempts().size()).isEqualTo(1);

        finishedRestartedExecution
            .getTaskRunList()
            .stream()
            .map(TaskRun::getState)
            .forEach(state -> assertThat(state.getCurrent()).isEqualTo(State.Type.SUCCESS));
    }

    @Test
    @LoadFlows({"flows/valids/inputs.yaml"})
    void downloadInternalStorageFileFromExecution() throws TimeoutException, QueueException{
        Execution execution = runnerUtils.runOne(TENANT_ID, TESTS_FLOW_NS, "inputs", null, (flow, execution1) -> flowIO.readExecutionInputs(flow, execution1, inputs));
        assertThat(execution.getTaskRunList()).hasSize(16);

        String path = (String) execution.getInputs().get("file");

        String file = client.toBlocking().retrieve(
            GET("/api/v1/main/executions/" + execution.getId() + "/file?path=" + path),
            String.class
        );

        assertThat(file).isEqualTo("hello");

        FileMetas metas = client.retrieve(
            GET("/api/v1/main/executions/" + execution.getId() + "/file/metas?path=" + path),
            FileMetas.class
        ).block();


        assertThat(metas).isNotNull();
        assertThat(metas.getSize()).isEqualTo(5L);

        String newExecutionId = IdUtils.create();

        HttpClientResponseException e = assertThrows(HttpClientResponseException.class, () -> client.toBlocking().retrieve(
            GET("/api/v1/main/executions/" + execution.getId() + "/file?path=" + path.replace(execution.getId(),
                newExecutionId
            )),
            String.class
        ));

        // we redirect to good execution (that doesn't exist, so 404)
        assertThat(e.getStatus().getCode()).isEqualTo(404);
        assertThat(e.getMessage()).contains("execution id '" + newExecutionId + "'");
    }

    @Test
    @LoadFlows({"flows/valids/inputs.yaml"})
    void previewInternalStorageFileFromExecution() throws TimeoutException, QueueException{
        Execution defaultExecution = runnerUtils.runOne(TENANT_ID, TESTS_FLOW_NS, "inputs", null, (flow, execution1) -> flowIO.readExecutionInputs(flow, execution1, inputs));
        assertThat(defaultExecution.getTaskRunList()).hasSize(16);

        String defaultPath = (String) defaultExecution.getInputs().get("file");

        String defaultFile = client.toBlocking().retrieve(
            GET("/api/v1/main/executions/" + defaultExecution.getId() + "/file/preview?path=" + defaultPath),
            String.class
        );

        assertThat(defaultFile).contains("hello");

        Map<String, Object> latin1FileInputs = ImmutableMap.<String, Object>builder()
            .put("failed", "NO")
            .put("string", "myString")
            .put("enum", "ENUM_VALUE")
            .put("int", "42")
            .put("float", "42.42")
            .put("instant", "2019-10-06T18:27:49Z")
            .put("file", Objects.requireNonNull(ExecutionControllerTest.class.getClassLoader().getResource("data/iso88591.txt")).getPath())
            .put("secret", "secret")
            .put("array", "[1, 2, 3]")
            .put("json1", "{}")
            .put("yaml1", "{}")
            .build();

        Execution latin1Execution = runnerUtils.runOne(TENANT_ID, TESTS_FLOW_NS, "inputs", null, (flow, execution1) -> flowIO.readExecutionInputs(flow, execution1, latin1FileInputs));
        assertThat(latin1Execution.getTaskRunList()).hasSize(16);

        String latin1Path = (String) latin1Execution.getInputs().get("file");

        String latin1File = client.toBlocking().retrieve(
            GET("/api/v1/main/executions/" + latin1Execution.getId() + "/file/preview?path=" + latin1Path + "&encoding=ISO-8859-1"),
            String.class
        );

        assertThat(latin1File).contains("Düsseldorf");

        HttpClientResponseException e = assertThrows(HttpClientResponseException.class, () -> client.toBlocking().retrieve(
            GET("/api/v1/main/executions/" + latin1Execution.getId() + "/file/preview?path=" + latin1Path + "&encoding=foo"),
            String.class
        ));

        assertThat(e.getStatus().getCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY.getCode());
        assertThat(e.getMessage()).contains("using encoding 'foo'");
    }

    @Test
    @LoadFlows({"flows/valids/inputs.yaml"})
    void previewLocalFileFromExecution() throws TimeoutException, QueueException, IOException {
        HashMap<String, Object> newInputs = new HashMap<>(InputsTest.inputs);
        URI file = createFile();
        newInputs.put("file", file);

        Execution execution = runnerUtils.runOne(
            MAIN_TENANT,
            "io.kestra.tests",
            "inputs",
            null,
            (flow, execution1) -> flowIO.readExecutionInputs(flow, execution1, newInputs)
        );
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

        // get the metadata of the file
        FileMetas metas = client.retrieve(
            GET("/api/v1/main/executions/" + execution.getId() + "/file/metas?path=" + file),
            FileMetas.class
        ).block();
        assertThat(metas).isNotNull();
        assertThat(metas.getSize()).isEqualTo(11L);

        // preview the file
        Map<String, Object> preview = client.toBlocking().retrieve(
            GET("/api/v1/main/executions/" + execution.getId() + "/file/preview?path=" + file),
            Map.class
        );
        assertThat(preview).isNotNull();
        assertThat(preview).containsEntry("extension", "txt");
        assertThat(preview).containsEntry("content", "Hello World");

        // download the file
        String content = client.toBlocking().retrieve(
            GET("/api/v1/main/executions/" + execution.getId() + "/file?path=" + file),
            String.class
        );
        assertThat(content).isEqualTo("Hello World");
    }

    @Test
    @LoadFlows({"flows/valids/inputs.yaml"})
    void previewNsFileFromExecution() throws TimeoutException, QueueException, IOException, URISyntaxException {
        HashMap<String, Object> newInputs = new HashMap<>(InputsTest.inputs);
        URI file = createNsFile(false);
        newInputs.put("file", file);

        Execution execution = runnerUtils.runOne(
            MAIN_TENANT,
            "io.kestra.tests",
            "inputs",
            null,
            (flow, execution1) -> flowIO.readExecutionInputs(flow, execution1, newInputs)
        );
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

        // get the metadata of the file
        FileMetas metas = client.retrieve(
            GET("/api/v1/main/executions/" + execution.getId() + "/file/metas?path=" + file),
            FileMetas.class
        ).block();
        assertThat(metas).isNotNull();
        assertThat(metas.getSize()).isEqualTo(11L);

        // preview the file
        Map<String, Object> preview = client.toBlocking().retrieve(
            GET("/api/v1/main/executions/" + execution.getId() + "/file/preview?path=" + file),
            Map.class
        );
        assertThat(preview).isNotNull();
        assertThat(preview).containsEntry("extension", "txt");
        assertThat(preview).containsEntry("content", "Hello World");

        // download the file
        String content = client.toBlocking().retrieve(
            GET("/api/v1/main/executions/" + execution.getId() + "/file?path=" + file),
            String.class
        );
        assertThat(content).isEqualTo("Hello World");
    }

    @SuppressWarnings("unchecked")
    @Test
    @LoadFlows({"flows/valids/webhook.yaml"})
    void webhook() {
        Flow webhook = flowRepositoryInterface.findById(TENANT_ID, TESTS_FLOW_NS, "webhook").orElseThrow();
        String key = ((Webhook) webhook.getTriggers().getFirst()).getKey();

        Execution execution = client.toBlocking().retrieve(
            HttpRequest
                .POST(
                    "/api/v1/main/executions/webhook/" + TESTS_FLOW_NS + "/webhook/" + key + "?name=john&age=12&age=13",
                    ImmutableMap.of("a", 1, "b", true)
                ),
            Execution.class
        );

        assertThat(((Map<String, Object>) execution.getTrigger().getVariables().get("body")).get("a")).isEqualTo(1);
        assertThat((Boolean) ((Map<String, Object>) execution.getTrigger().getVariables().get("body")).get("b")).isTrue();
        assertThat(((Map<String, Object>) execution.getTrigger().getVariables().get("parameters")).get("name")).isEqualTo(List.of("john"));
        assertThat(((Map<String, List<String>>) execution.getTrigger().getVariables().get("parameters")).get("age")).containsExactlyInAnyOrder("12", "13");
        assertThat(execution.getLabels()).containsExactlyInAnyOrder(
            new Label(Label.CORRELATION_ID, execution.getId()),
            new Label(Label.FROM, "trigger"),
            new Label("flow-label-1", "flow-label-1"),
            new Label("flow-label-2", "flow-label-2")
        );

        execution = client.toBlocking().retrieve(
            HttpRequest
                .PUT(
                    "/api/v1/main/executions/webhook/" + TESTS_FLOW_NS + "/webhook/" + key,
                    Collections.singletonList(ImmutableMap.of("a", 1, "b", true))
                ),
            Execution.class
        );

        assertThat(((List<Map<String, Object>>) execution.getTrigger().getVariables().get("body")).getFirst().get("a")).isEqualTo(1);
        assertThat((Boolean) ((List<Map<String, Object>>) execution.getTrigger().getVariables().get("body")).getFirst().get("b")).isTrue();

        execution = client.toBlocking().retrieve(
            HttpRequest
                .POST(
                    "/api/v1/main/executions/webhook/" + TESTS_FLOW_NS + "/webhook/" + key,
                    "bla"
                ),
            Execution.class
        );

        assertThat(execution.getTrigger().getVariables().get("body")).isEqualTo("bla");

        execution = client.toBlocking().retrieve(
            GET("/api/v1/main/executions/webhook/" + TESTS_FLOW_NS + "/webhook/" + key),
            Execution.class
        );
        assertThat(execution.getTrigger().getVariables().get("body")).isNull();

        execution = client.toBlocking().retrieve(
            HttpRequest
                .POST(
                    "/api/v1/main/executions/webhook/" + TESTS_FLOW_NS + "/webhook/" + key,
                    "{\\\"a\\\":\\\"\\\",\\\"b\\\":{\\\"c\\\":{\\\"d\\\":{\\\"e\\\":\\\"\\\",\\\"f\\\":\\\"1\\\"}}}}"
                ),
            Execution.class
        );
        assertThat(execution.getTrigger().getVariables().get("body")).isEqualTo("{\\\"a\\\":\\\"\\\",\\\"b\\\":{\\\"c\\\":{\\\"d\\\":{\\\"e\\\":\\\"\\\",\\\"f\\\":\\\"1\\\"}}}}");

    }

    @Test
    @LoadFlows({"flows/valids/webhook-wait.yaml"})
    void shouldWaitForWebhookAndReturnOutput() {
        Flow webhook = flowRepositoryInterface.findById(TENANT_ID, TESTS_FLOW_NS, "webhook-wait").orElseThrow();
        String key = ((Webhook) webhook.getTriggers().getFirst()).getKey();

        var execution = client.toBlocking().retrieve(
            HttpRequest
                .GET(
                    "/api/v1/main/executions/webhook/" + TESTS_FLOW_NS + "/webhook-wait/" + key
                ),
            ExecutionController.WebhookResponse.class
        );

        assertThat(execution.state().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution.url().toString()).isEqualTo("http://localhost:8081/ui/main/executions/io.kestra.tests/webhook-wait/" + execution.id());
        assertThat(execution.outputs()).hasSize(1);
        assertThat(execution.outputs()).containsEntry("output", "output");
    }

    @Test
    @LoadFlows({"flows/valids/pause-test.yaml"})
    @SuppressWarnings("unchecked")
    void resumeExecutionPaused() throws TimeoutException, QueueException {
        // Run execution until it is paused
        Execution pausedExecution = runnerUtils.runOneUntilPaused(TENANT_ID, TESTS_FLOW_NS, "pause-test");
        assertThat(pausedExecution.getState().isPaused()).isTrue();

        // resume the execution
        HttpResponse<?> resumeResponse = client.toBlocking().exchange(
            HttpRequest.POST("/api/v1/main/executions/" + pausedExecution.getId() + "/resume", null));
        assertThat(resumeResponse.getStatus().getCode()).isEqualTo(HttpStatus.NO_CONTENT.getCode());

        // check that the execution is no more paused
        Execution execution = awaitExecution(pausedExecution.getId(), exec -> !exec.getState().isPaused());
        assertThat((Map<String, Object>) execution.findTaskRunsByTaskId("pause").getFirst().getOutputs().get("resumed")).containsKey("on");
    }

    @Test
    @LoadFlows({"flows/valids/resume-validate.yaml"})
    @SuppressWarnings("unchecked")
    void resumeValidateExecutionPaused() throws TimeoutException, QueueException {
        // Run execution until it is paused
        Execution pausedExecution = runnerUtils.runOneUntilPaused(TENANT_ID, TESTS_FLOW_NS, "resume-validate");
        assertThat(pausedExecution.getState().isPaused()).isTrue();

        // validate inputs to resume a paused execution
        HttpResponse<?> resumeValidateResponse = client.toBlocking().exchange(
          HttpRequest.POST("/api/v1/main/executions/" + pausedExecution.getId() + "/resume/validate", null));
        assertThat(resumeValidateResponse.getStatus().getCode()).isEqualTo(HttpStatus.OK.getCode());

        // resume the execution
        HttpResponse<?> resumeResponse = client.toBlocking().exchange(
          HttpRequest.POST("/api/v1/main/executions/" + pausedExecution.getId() + "/resume", null));
        assertThat(resumeResponse.getStatus().getCode()).isEqualTo(HttpStatus.NO_CONTENT.getCode());

        // check that the execution is no more paused
        Execution execution = awaitExecution(pausedExecution.getId(), exec -> !exec.getState().isPaused());
        assertThat((Map<String, Object>) execution.findTaskRunsByTaskId("pause").getFirst().getOutputs().get("resumed")).containsKey("on");
    }

    @SuppressWarnings("unchecked")
    @Test
    @LoadFlows({"flows/valids/pause_on_resume.yaml"})
    void resumeExecutionPausedWithInputs() throws TimeoutException, QueueException {
        // Run execution until it is paused
        Execution pausedExecution = runnerUtils.runOneUntilPaused(TENANT_ID, TESTS_FLOW_NS, "pause_on_resume");
        assertThat(pausedExecution.getState().isPaused()).isTrue();

        File applicationFile = new File(Objects.requireNonNull(
            ExecutionControllerTest.class.getClassLoader().getResource("application-test.yml")
        ).getPath());

        MultipartBody multipartBody = MultipartBody.builder()
            .addPart("asked", "myString")
            .addPart("files", "data", MediaType.TEXT_PLAIN_TYPE, applicationFile)
            .build();

        // resume the execution
        HttpResponse<?> resumeResponse = client.toBlocking().exchange(
            HttpRequest.POST("/api/v1/main/executions/" + pausedExecution.getId() + "/resume", multipartBody)
                .contentType(MediaType.MULTIPART_FORM_DATA_TYPE)
        );
        assertThat(resumeResponse.getStatus().getCode()).isEqualTo(HttpStatus.NO_CONTENT.getCode());

        // check that the execution is no more paused
        Execution execution = awaitExecution(pausedExecution.getId(), exec -> !exec.getState().isPaused());

        Map<String, Object> outputs = (Map<String, Object>) execution.findTaskRunsByTaskId("pause").getFirst().getOutputs().get("onResume");
        assertThat(outputs.get("asked")).isEqualTo("myString");
        assertThat((String) outputs.get("data")).startsWith("kestra://");
    }

    @SuppressWarnings("unchecked")
    @Test
    @LoadFlows({"flows/valids/pause_on_resume.yaml"})
    void resumeExecutionPausedWithWrongInputs() throws TimeoutException, QueueException {
        // Run execution until it is paused
        Execution pausedExecution = runnerUtils.runOneUntilPaused(TENANT_ID, TESTS_FLOW_NS, "pause_on_resume");
        assertThat(pausedExecution.getState().isPaused()).isTrue();

        MultipartBody multipartBody = MultipartBody.builder()
            .addPart("wrong", "input")
            .build();

        // resume the execution
        HttpClientResponseException exception =  assertThrows (HttpClientResponseException.class, () ->
            client.toBlocking().exchange(
            HttpRequest.POST("/api/v1/main/executions/" + pausedExecution.getId() + "/resume", multipartBody)
                .contentType(MediaType.MULTIPART_FORM_DATA_TYPE)
        ));
        assertThat(exception.getStatus().getCode()).isEqualTo(422);
        assertThat(exception.getMessage()).isEqualTo("Invalid entity: Missing required input:asked");
    }

    @Test
    @LoadFlows({"flows/valids/pause-test.yaml"})
    void resumeExecutionByIds() throws TimeoutException, InterruptedException, QueueException {
        Execution pausedExecution1 = runnerUtils.runOneUntilPaused(TENANT_ID, TESTS_FLOW_NS, "pause-test");
        Execution pausedExecution2 = runnerUtils.runOneUntilPaused(TENANT_ID, TESTS_FLOW_NS, "pause-test");

        assertThat(pausedExecution1.getState().isPaused()).isTrue();
        assertThat(pausedExecution2.getState().isPaused()).isTrue();

        // resume executions
        BulkResponse resumeResponse = client.toBlocking().retrieve(
            HttpRequest.POST(
                "/api/v1/main/executions/resume/by-ids",
                List.of(pausedExecution1.getId(), pausedExecution2.getId())
            ),
            BulkResponse.class
        );
        assertThat(resumeResponse.getCount()).isEqualTo(2);

        // check that the executions are no more paused
        awaitExecution(pausedExecution1.getId(), exec -> !exec.getState().isPaused());
        awaitExecution(pausedExecution2.getId(), exec -> !exec.getState().isPaused());

        // attempt to resume no more paused executions
        HttpClientResponseException e = assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().retrieve(HttpRequest.POST(
                "/api/v1/main/executions/resume/by-ids",
                List.of(pausedExecution1.getId(), pausedExecution2.getId())
            ))
        );
        assertThat(e.getStatus().getCode()).isEqualTo(HttpStatus.BAD_REQUEST.getCode());
    }

    @Test
    @LoadFlows({"flows/valids/pause-test.yaml"})
    void resumeExecutionByQuery() throws TimeoutException, InterruptedException, QueueException {
        Execution pausedExecution1 = runnerUtils.runOneUntilPaused(TENANT_ID, TESTS_FLOW_NS, "pause-test");
        Execution pausedExecution2 = runnerUtils.runOneUntilPaused(TENANT_ID, TESTS_FLOW_NS, "pause-test");

        assertThat(pausedExecution1.getState().isPaused()).isTrue();
        assertThat(pausedExecution2.getState().isPaused()).isTrue();

        // resume executions
        BulkResponse resumeResponse = client.toBlocking().retrieve(
            HttpRequest.POST("/api/v1/main/executions/resume/by-query?namespace=" + TESTS_FLOW_NS, null),
            BulkResponse.class
        );
        assertThat(resumeResponse.getCount()).isEqualTo(2);

        // check that the executions are no more paused
        awaitExecution(pausedExecution1.getId(), exec -> !exec.getState().isPaused());
        awaitExecution(pausedExecution2.getId(), exec -> !exec.getState().isPaused());

        // attempt to resume no more paused executions
        HttpClientResponseException e = assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().retrieve(HttpRequest.POST(
                "/api/v1/main/executions/resume/by-query?namespace=" + TESTS_FLOW_NS, null
            ))
        );
        assertThat(e.getStatus().getCode()).isEqualTo(HttpStatus.BAD_REQUEST.getCode());
    }

    @Test
    @LoadFlows({"flows/valids/minimal.yaml"})
    void updateExecutionStatus() throws TimeoutException, QueueException {
        Execution execution = runnerUtils.runOne(TENANT_ID, TESTS_FLOW_NS, "minimal");
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

        // replay executions
        Execution changedStatus = client.toBlocking().retrieve(
            HttpRequest.POST(
                "/api/v1/main/executions/" + execution.getId() + "/change-status?status=WARNING",
                null
            ),
            Execution.class
        );
        assertThat(changedStatus.getState().getCurrent()).isEqualTo(State.Type.WARNING);
    }

    @Test
    @SuppressWarnings("unchecked")
    @LoadFlows({"flows/valids/minimal.yaml"})
    void updateExecutionStatusByIds() throws TimeoutException, QueueException {
        Execution execution1 = runnerUtils.runOne(TENANT_ID, TESTS_FLOW_NS, "minimal");
        Execution execution2 = runnerUtils.runOne(TENANT_ID, TESTS_FLOW_NS, "minimal");

        assertThat(execution1.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution2.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

        PagedResults<Execution> executions = client.toBlocking().retrieve(
            GET("/api/v1/main/executions/search"), Argument.of(PagedResults.class, Execution.class)
        );
        assertThat(executions.getTotal()).isEqualTo(2L);

        // change status of executions
        BulkResponse changeStatus = client.toBlocking().retrieve(
            HttpRequest.POST(
                "/api/v1/main/executions/change-status/by-ids?newStatus=WARNING",
                List.of(execution1.getId(), execution2.getId())
            ),
            BulkResponse.class
        );
        assertThat(changeStatus.getCount()).isEqualTo(2);

        executions = client.toBlocking().retrieve(
            GET("/api/v1/main/executions/search"), Argument.of(PagedResults.class, Execution.class)
        );
        assertThat(executions.getResults().getFirst().getState().getCurrent()).isEqualTo(State.Type.WARNING);
        assertThat(executions.getResults().get(1).getState().getCurrent()).isEqualTo(State.Type.WARNING);
    }

    @Test
    @SuppressWarnings("unchecked")
    @LoadFlows({"flows/valids/minimal.yaml"})
    void updateExecutionStatusByQuery() throws TimeoutException, QueueException {
        Execution execution1 = runnerUtils.runOne(TENANT_ID, TESTS_FLOW_NS, "minimal");
        Execution execution2 = runnerUtils.runOne(TENANT_ID, TESTS_FLOW_NS, "minimal");

        assertThat(execution1.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(execution2.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

        PagedResults<Execution> executions = client.toBlocking().retrieve(
            GET("/api/v1/main/executions/search"), Argument.of(PagedResults.class, Execution.class)
        );
        assertThat(executions.getTotal()).isEqualTo(2L);

        // change status of  executions
        BulkResponse changeStatus = client.toBlocking().retrieve(
            HttpRequest.POST("/api/v1/main/executions/change-status/by-query?namespace=io.kestra.tests&newStatus=WARNING", null),
            BulkResponse.class
        );
        assertThat(changeStatus.getCount()).isEqualTo(2);

        executions = client.toBlocking().retrieve(
            GET("/api/v1/main/executions/search"), Argument.of(PagedResults.class, Execution.class)
        );
        assertThat(executions.getResults().getFirst().getState().getCurrent()).isEqualTo(State.Type.WARNING);
        assertThat(executions.getResults().get(1).getState().getCurrent()).isEqualTo(State.Type.WARNING);;
    }

    @Test
    @LoadFlows({"flows/valids/minimal.yaml"})
    void replayExecution() throws TimeoutException, QueueException {
        Execution execution = runnerUtils.runOne(TENANT_ID, TESTS_FLOW_NS, "minimal");

        assertThat(execution.getState().isTerminated()).isTrue();

        // replay execution
        Execution replay = client.toBlocking().retrieve(
            HttpRequest.POST(
                "/api/v1/main/executions/" + execution.getId() + "/replay",
                null
            ),
            Execution.class
        );
        assertThat(replay.getState().getCurrent()).isEqualTo(State.Type.CREATED);
        assertThat(replay.getOriginalId()).isEqualTo(execution.getId());
        assertThat(replay.getLabels()).contains(new Label(Label.REPLAY, "true"));

        // load the original execution and check that it has the system.replayed label
        Execution original = client.toBlocking().retrieve(
            HttpRequest.GET("/api/v1/main/executions/" + execution.getId()),
            Execution.class
        );
        assertThat(original.getLabels()).contains(new Label(Label.REPLAYED, "true"));
    }

    @Test
    @LoadFlows({"flows/valids/minimal.yaml"})
    void replayExecutionByIds() throws TimeoutException, QueueException {
        Execution execution1 = runnerUtils.runOne(TENANT_ID, TESTS_FLOW_NS, "minimal");
        Execution execution2 = runnerUtils.runOne(TENANT_ID, TESTS_FLOW_NS, "minimal");

        assertThat(execution1.getState().isTerminated()).isTrue();
        assertThat(execution2.getState().isTerminated()).isTrue();

        PagedResults<?> executions = client.toBlocking().retrieve(
            GET("/api/v1/main/executions/search"), PagedResults.class
        );
        assertThat(executions.getTotal()).isEqualTo(2L);

        // replay executions
        BulkResponse replayResponse = client.toBlocking().retrieve(
            HttpRequest.POST(
                "/api/v1/main/executions/replay/by-ids",
                List.of(execution1.getId(), execution2.getId())
            ),
            BulkResponse.class
        );
        assertThat(replayResponse.getCount()).isEqualTo(2);

        executions = client.toBlocking().retrieve(
            GET("/api/v1/main/executions/search"), PagedResults.class
        );
        assertThat(executions.getTotal()).isEqualTo(4L);
    }

    @Test
    @LoadFlows({"flows/valids/minimal.yaml"})
    void replayExecutionByQuery() throws TimeoutException, QueueException {
        Execution execution1 = runnerUtils.runOne(TENANT_ID, TESTS_FLOW_NS, "minimal");
        Execution execution2 = runnerUtils.runOne(TENANT_ID, TESTS_FLOW_NS, "minimal");

        assertThat(execution1.getState().isTerminated()).isTrue();
        assertThat(execution2.getState().isTerminated()).isTrue();

        PagedResults<?> executions = client.toBlocking().retrieve(
            GET("/api/v1/main/executions/search"), PagedResults.class
        );
        assertThat(executions.getTotal()).isEqualTo(2L);

        // replay executions
        BulkResponse resumeResponse = client.toBlocking().retrieve(
            HttpRequest.POST("/api/v1/main/executions/replay/by-query?namespace=io.kestra.tests", null),
            BulkResponse.class
        );
        assertThat(resumeResponse.getCount()).isEqualTo(2);

        executions = client.toBlocking().retrieve(
            GET("/api/v1/main/executions/search"), PagedResults.class
        );
        assertThat(executions.getTotal()).isEqualTo(4L);
    }

    @Test
    @LoadFlows({"flows/valids/pause-test.yaml"})
    void killExecutionPaused() throws TimeoutException, QueueException {
        // Run execution until it is paused
        Execution pausedExecution = runnerUtils.runOneUntilPaused(TENANT_ID, TESTS_FLOW_NS, "pause-test");
        assertThat(pausedExecution.getState().isPaused()).isTrue();

        // kill the execution
        HttpResponse<?> killResponse = client.toBlocking().exchange(
            HttpRequest.DELETE("/api/v1/main/executions/" + pausedExecution.getId() + "/kill"));
        assertThat(killResponse.getStatus().getCode()).isEqualTo(HttpStatus.ACCEPTED.getCode());

        // check that the execution is killed
        Execution killedExecution = awaitExecution(pausedExecution.getId(), exec -> exec.getState().getCurrent().isKilled());
        assertThat(killedExecution.getTaskRunList()).hasSize(1);
    }

    @Test
    @LoadFlows({"flows/valids/sleep-long.yml"})
    void killExecution() throws TimeoutException, InterruptedException, QueueException {
        // listen to the execution queue
        AtomicReference<Execution> killedExecution = new AtomicReference<>();
        CountDownLatch killedLatch = new CountDownLatch(1);
        Flux<Execution> receiveExecutions = TestsUtils.receive(executionQueue, e -> {
            if (e.getLeft().getState().getCurrent() == State.Type.KILLED) {
                killedExecution.set(e.getLeft());
                killedLatch.countDown();
            }
        });

        // listen to the executionkilled queue
        AtomicReference<String> executionKilledId = new AtomicReference<>();
        CountDownLatch executionKilledLatch = new CountDownLatch(1);
        Flux<ExecutionKilled> receiveKilled = TestsUtils.receive(killQueue, e -> {
            executionKilledId.set(((ExecutionKilledExecution) e.getLeft()).getExecutionId());
            executionKilledLatch.countDown();
        });

        // Run execution until it is paused
        Execution runningExecution = runnerUtils.runOneUntilRunning(TENANT_ID, TESTS_FLOW_NS, "sleep-long");
        assertThat(runningExecution.getState().isRunning()).isTrue();

        // kill the execution
        HttpResponse<?> killResponse = client.toBlocking().exchange(
            HttpRequest.DELETE("/api/v1/main/executions/" + runningExecution.getId() + "/kill"));
        assertThat(killResponse.getStatus().getCode()).isEqualTo(HttpStatus.ACCEPTED.getCode());

        // check that the execution has been set to killing then killed
        assertTrue(killedLatch.await(10, TimeUnit.SECONDS));
        receiveExecutions.blockLast();
        assertThat(killedExecution.get().getId()).isEqualTo(runningExecution.getId());

        //check that an executionkilled message has been sent
        assertTrue(executionKilledLatch.await(10, TimeUnit.SECONDS));
        receiveKilled.blockLast();
        assertThat(executionKilledId.get()).isEqualTo(runningExecution.getId());

        // retrieve the execution from the API and check that the task has been set to killed
        Thread.sleep(250);
        Execution execution = client.toBlocking().retrieve(
            GET("/api/v1/main/executions/" + runningExecution.getId()),
            Execution.class);
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.KILLED);
        assertThat(execution.getTaskRunList().size()).isEqualTo(2);
        assertThat(execution.getTaskRunList().getFirst().getState().getCurrent()).isEqualTo(State.Type.KILLED);

        // check that afterExecutions has been run even if killed
        assertThat(execution.getTaskRunList().getLast().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
    }

    @Test
    @LoadFlows({"flows/valids/inputs.yaml"})
    void searchExecutions() {
        PagedResults<?> executions = client.toBlocking().retrieve(
            GET("/api/v1/main/executions/search"), PagedResults.class
        );

        assertThat(executions.getTotal()).isEqualTo(0L);

        triggerExecutionInputsFlowExecution(false);

        // + is there to simulate that a space was added (this can be the case from UI autocompletion for eg.)
        executions = client.toBlocking().retrieve(
            GET("/api/v1/main/executions/search?page=1&size=25&filters[labels][EQUALS][url]="+ENCODED_URL_LABEL_VALUE), PagedResults.class
        );

        assertThat(executions.getTotal()).isEqualTo(1L);

        executions = client.toBlocking().retrieve(
            GET("/api/v1/main/executions/search?page=1&size=25&labels=url:"+ENCODED_URL_LABEL_VALUE), PagedResults.class
        );

        assertThat(executions.getTotal()).isEqualTo(1L);

        HttpClientResponseException e = assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().retrieve(GET("/api/v1/main/executions/search?filters[startDate][EQUALS]=2024-01-07T18:43:11.248%2B01:00&filters[timeRange][EQUALS]=PT12H"))
        );

        assertThat(e.getStatus().getCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY.getCode());
        assertThat(e.getResponse().getBody(String.class).isPresent()).isTrue();
        assertThat(e.getResponse().getBody(String.class).get()).contains("are mutually exclusive");

        executions = client.toBlocking().retrieve(
            GET("/api/v1/main/executions/search?filters[timeRange][EQUALS]=PT12H"), PagedResults.class
        );

        assertThat(executions.getTotal()).isEqualTo(1L);

        executions = client.toBlocking().retrieve(
            GET("/api/v1/main/executions/search?timeRange=PT12H"), PagedResults.class
        );

        assertThat(executions.getTotal()).isEqualTo(1L);

        e = assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().retrieve(GET("/api/v1/main/executions/search?filters[timeRange][EQUALS]=P1Y"))
        );
        assertThat(e.getStatus().getCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY.getCode());

        e = assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().retrieve(GET("/api/v1/main/executions/search?timeRange=P1Y"))
        );
        assertThat(e.getStatus().getCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY.getCode());

        e = assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().retrieve(GET("/api/v1/main/executions/search?page=1&size=-1"))
        );

        assertThat(e.getStatus().getCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY.getCode());

        e = assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().retrieve(GET("/api/v1/main/executions/search?page=0"))
        );

        assertThat(e.getStatus().getCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY.getCode());
    }

    @Test
    @LoadFlows({"flows/valids/minimal.yaml"})
    void deleteExecution() throws QueueException, TimeoutException {
        Execution result = runnerUtils.runOne(TENANT_ID, TESTS_FLOW_NS, "minimal");

        var response = client.toBlocking().exchange(HttpRequest.DELETE("/api/v1/main/executions/" + result.getId()));
        assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.NO_CONTENT.getCode());

        var notFound = assertThrows(HttpClientResponseException.class, () -> client.toBlocking().exchange(HttpRequest.DELETE("/api/v1/main/executions/notfound")));
        assertThat(notFound.getStatus().getCode()).isEqualTo(HttpStatus.NOT_FOUND.getCode());
    }

    @Test
    @LoadFlows({"flows/valids/minimal.yaml"})
    void deleteExecutionByIds() throws TimeoutException, QueueException {
        Execution result1 = runnerUtils.runOne(TENANT_ID, TESTS_FLOW_NS, "minimal");
        Execution result2 = runnerUtils.runOne(TENANT_ID, TESTS_FLOW_NS, "minimal");
        Execution result3 = runnerUtils.runOne(TENANT_ID, TESTS_FLOW_NS, "minimal");

        BulkResponse response = client.toBlocking().retrieve(
            HttpRequest.DELETE("/api/v1/main/executions/by-ids", List.of(result1.getId(), result2.getId(), result3.getId())),
            BulkResponse.class
        );
        assertThat(response.getCount()).isEqualTo(3);
    }

    @Test
    @LoadFlows({"flows/valids/minimal.yaml"})
    void deleteExecutionByQuery() throws TimeoutException, QueueException {
        Execution result1 = runnerUtils.runOne(TENANT_ID, TESTS_FLOW_NS, "minimal");
        Execution result2 = runnerUtils.runOne(TENANT_ID, TESTS_FLOW_NS, "minimal");
        Execution result3 = runnerUtils.runOne(TENANT_ID, TESTS_FLOW_NS, "minimal");

        BulkResponse response = client.toBlocking().retrieve(
            HttpRequest.DELETE("/api/v1/main/executions/by-query?namespace=" + result1.getNamespace()),
            BulkResponse.class
        );
        assertThat(response.getCount()).isEqualTo(3);
    }

    @Test
    @LoadFlows({"flows/valids/minimal.yaml"})
    void setLabelsOnTerminatedExecution() throws QueueException, TimeoutException {
        // update labels on a terminated execution
        Execution result = runnerUtils.runOne(TENANT_ID, TESTS_FLOW_NS, "minimal");
        assertThat(result.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        Execution response = client.toBlocking().retrieve(
            HttpRequest.POST("/api/v1/main/executions/" + result.getId() + "/labels", List.of(new Label("existing", "updated"), new Label("newKey", "value"))),
            Execution.class
        );
        assertThat(response.getLabels()).containsExactlyInAnyOrder(
            new Label(Label.CORRELATION_ID, response.getId()),
            new Label("existing", "updated"),
            new Label("newKey", "value")
        );

        // update label on a not found execution
        var exception = assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().exchange(HttpRequest.POST("/api/v1/main/executions/notfound/labels", List.of(new Label("key", "value"))))
        );
        assertThat(exception.getStatus().getCode()).isEqualTo(HttpStatus.NOT_FOUND.getCode());

        exception = assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().exchange(HttpRequest.POST("/api/v1/main/executions/" + result.getId() + "/labels", List.of(new Label(null, null))))
        );
        assertThat(exception.getStatus().getCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY.getCode());
    }

    @Test
    @LoadFlows({"flows/valids/minimal.yaml"})
    void setLabelsOnTerminatedExecutionsByIds() throws TimeoutException, QueueException {
        Execution result1 = runnerUtils.runOne(TENANT_ID, TESTS_FLOW_NS, "minimal");
        Execution result2 = runnerUtils.runOne(TENANT_ID, TESTS_FLOW_NS, "minimal");
        Execution result3 = runnerUtils.runOne(TENANT_ID, TESTS_FLOW_NS, "minimal");

        BulkResponse response = client.toBlocking().retrieve(
            HttpRequest.POST("/api/v1/main/executions/labels/by-ids",
                new ExecutionController.SetLabelsByIdsRequest(List.of(result1.getId(), result2.getId(), result3.getId()), List.of(new Label("key", "value")))
            ),
            BulkResponse.class
        );

        assertThat(response.getCount()).isEqualTo(3);

        // load one of the executions to check that labels have been correctly updated
        Execution execution = client.toBlocking().retrieve(
            GET("/api/v1/main/executions/" + result1.getId()),
            Execution.class);
        assertThat(execution.getLabels()).hasSize(3);
        assertThat(execution.getLabels()).contains(new Label("key", "value"));
    }

    @Test
    @LoadFlows({"flows/valids/minimal.yaml"})
    void setLabelsOnTerminatedExecutionsByQuery() throws TimeoutException, QueueException {
        Execution result1 = runnerUtils.runOne(TENANT_ID, TESTS_FLOW_NS, "minimal");
        Execution result2 = runnerUtils.runOne(TENANT_ID, TESTS_FLOW_NS, "minimal");
        Execution result3 = runnerUtils.runOne(TENANT_ID, TESTS_FLOW_NS, "minimal");

        BulkResponse response = client.toBlocking().retrieve(
            HttpRequest.POST("/api/v1/main/executions/labels/by-query?namespace=" + result1.getNamespace(),
                List.of(new Label("key", "value"))
            ),
            BulkResponse.class
        );

        assertThat(response.getCount()).isEqualTo(3);

        var exception = assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().exchange(HttpRequest.POST(
                "/api/v1/main/executions/labels/by-query?namespace=" + result1.getNamespace(),
                List.of(new Label(null, null)))
            )
        );
        assertThat(exception.getStatus().getCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY.getCode());
    }

    @Test
    @LoadFlows({"flows/valids/minimal.yaml"})
    void updateExistingLabelsBySetLabelsOnTerminatedExecutionsByIds() throws TimeoutException, QueueException {
        final String statusLabelKey = "status";
        Execution resultWithLabel = runnerUtils.runOne(TENANT_ID, TESTS_FLOW_NS, "minimal", null, null, null, Label.from(Map.of(statusLabelKey, "initial")));
        Execution resultWithDifferentLabel = runnerUtils.runOne(TENANT_ID, TESTS_FLOW_NS, "minimal", null, null, null, Label.from(Map.of("foo", "bar")));
        Execution resultWithNoLabel = runnerUtils.runOne(TENANT_ID, TESTS_FLOW_NS, "minimal");

        BulkResponse response = client.toBlocking().retrieve(
            HttpRequest.POST("/api/v1/main/executions/labels/by-ids",
                new ExecutionController.SetLabelsByIdsRequest(
                    List.of(resultWithLabel.getId(), resultWithNoLabel.getId(), resultWithDifferentLabel.getId()),
                    List.of(new Label(statusLabelKey, "done"))
                )
            ),
            BulkResponse.class
        );

        assertThat(response.getCount()).isEqualTo(3);

        // check that the existing have been correctly updated
        Execution execution1 = client.toBlocking().retrieve(
            GET("/api/v1/main/executions/" + resultWithLabel.getId()),
            Execution.class);
        assertThat(execution1.getLabels()).containsExactlyInAnyOrder(
            new Label(Label.CORRELATION_ID, execution1.getId()),
            new Label("existing", "label"),
            new Label(statusLabelKey, "done")
        );

        // check that the existing have been correctly added
        Execution execution2 = client.toBlocking().retrieve(
            GET("/api/v1/main/executions/" + resultWithNoLabel.getId()),
            Execution.class);
        assertThat(execution2.getLabels()).containsExactlyInAnyOrder(
            new Label(Label.CORRELATION_ID, execution2.getId()),
            new Label("existing", "label"),
            new Label(statusLabelKey, "done")
        );

        // check that the existing have been correctly added and the existing label kept as it was
        Execution execution3 = client.toBlocking().retrieve(
            GET("/api/v1/main/executions/" + resultWithDifferentLabel.getId()),
            Execution.class);
        assertThat(execution3.getLabels()).containsExactlyInAnyOrder(
            new Label(Label.CORRELATION_ID, execution3.getId()),
            new Label("existing", "label"),
            new Label(statusLabelKey, "done"),
            new Label("foo", "bar")
        );
    }

    @Test
    @LoadFlows({"flows/valids/minimal.yaml"})
    void updateExistingLabelsBySetLabelsOnTerminatedExecutionsByQuery() throws TimeoutException, QueueException {
        final String statusLabelKey = "status";
        Execution resultWithLabel = runnerUtils.runOne(TENANT_ID, TESTS_FLOW_NS, "minimal", null, null, null, Label.from(Map.of(statusLabelKey, "initial")));
        Execution resultWithDifferentLabel = runnerUtils.runOne(TENANT_ID, TESTS_FLOW_NS, "minimal", null, null, null, Label.from(Map.of("foo", "bar")));
        Execution resultWithNoLabel = runnerUtils.runOne(TENANT_ID, TESTS_FLOW_NS, "minimal");

        BulkResponse response = client.toBlocking().retrieve(
            HttpRequest.POST("/api/v1/main/executions/labels/by-query?namespace=" + resultWithLabel.getNamespace(),
                List.of(new Label(statusLabelKey, "done"))
            ),
            BulkResponse.class
        );

        assertThat(response.getCount()).isEqualTo(3);

        var exception = assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().exchange(HttpRequest.POST(
                "/api/v1/main/executions/labels/by-query?namespace=" + resultWithLabel.getNamespace(),
                List.of(new Label(null, null)))
            )
        );
        assertThat(exception.getStatus().getCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY.getCode());

        // check that the existing have been correctly updated
        Execution execution1 = client.toBlocking().retrieve(
            GET("/api/v1/main/executions/" + resultWithLabel.getId()),
            Execution.class);
        assertThat(execution1.getLabels()).containsExactlyInAnyOrder(
            new Label(Label.CORRELATION_ID, execution1.getId()),
            new Label("existing", "label"),
            new Label(statusLabelKey, "done")
        );

        // check that the existing have been correctly added
        Execution execution2 = client.toBlocking().retrieve(
            GET("/api/v1/main/executions/" + resultWithNoLabel.getId()),
            Execution.class);
        assertThat(execution2.getLabels()).containsExactlyInAnyOrder(
            new Label(Label.CORRELATION_ID, execution2.getId()),
            new Label("existing", "label"),
            new Label(statusLabelKey, "done")
        );

        // check that the existing have been correctly added and the existing label kept as it was
        Execution execution3 = client.toBlocking().retrieve(
            GET("/api/v1/main/executions/" + resultWithDifferentLabel.getId()),
            Execution.class);
        assertThat(execution3.getLabels()).containsExactlyInAnyOrder(
            new Label(Label.CORRELATION_ID, execution3.getId()),
            new Label("existing", "label"),
            new Label(statusLabelKey, "done"),
            new Label("foo", "bar")
        );
    }

    @Test
    @LoadFlows({"flows/valids/sleep.yml",
        "flows/valids/minimal.yaml"})
    void shouldPauseExecutionARunningFlow() throws QueueException, TimeoutException {
        Execution result = runnerUtils.runOneUntilRunning(TENANT_ID, TESTS_FLOW_NS, "sleep");

        var response = client.toBlocking().exchange(HttpRequest.POST("/api/v1/main/executions/" + result.getId() + "/pause", null));
        assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.OK.getCode());

        // resume it, it should then go to completion
        response = client.toBlocking().exchange(HttpRequest.POST("/api/v1/main/executions/" + result.getId() + "/resume", null));
        assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.NO_CONTENT.getCode());

        var notFound = assertThrows(HttpClientResponseException.class, () -> client.toBlocking().exchange(HttpRequest.POST("/api/v1/main/executions/notfound/pause", null)));
        assertThat(notFound.getStatus().getCode()).isEqualTo(HttpStatus.NOT_FOUND.getCode());

        // pausing an already completed flow will result in errors
        Execution completed = runnerUtils.runOne(TENANT_ID, TESTS_FLOW_NS, "minimal");

        var notRunning = assertThrows(HttpClientResponseException.class, () -> client.toBlocking().exchange(HttpRequest.POST("/api/v1/main/executions/" + completed.getId() + "/pause", null)));
        assertThat(notRunning.getStatus().getCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY.getCode());
    }

    @Test
    @LoadFlows({"flows/valids/sleep.yml"})
    void shouldPauseExecutionByIdsRunningFlows() throws TimeoutException, QueueException {
        Execution result1 = runnerUtils.runOneUntilRunning(TENANT_ID, TESTS_FLOW_NS, "sleep");
        Execution result2 = runnerUtils.runOneUntilRunning(TENANT_ID, TESTS_FLOW_NS, "sleep");
        Execution result3 = runnerUtils.runOneUntilRunning(TENANT_ID, TESTS_FLOW_NS, "sleep");

        BulkResponse response = client.toBlocking().retrieve(
            HttpRequest.POST("/api/v1/main/executions/pause/by-ids", List.of(result1.getId(), result2.getId(), result3.getId())),
            BulkResponse.class
        );
        assertThat(response.getCount()).isEqualTo(3);
    }

    @Test
    @LoadFlows({"flows/valids/sleep-short.yml"})
    // use a dedicated Flow to avoid clash with other tests
    void shouldPauseExecutionByQueryRunningFlows() throws TimeoutException, QueueException {
        var flowId = "sleep-short";
        long start = System.currentTimeMillis();
        Execution result1 = runnerUtils.runOneUntilRunning(TENANT_ID, TESTS_FLOW_NS, flowId);
        Execution result2 = runnerUtils.runOneUntilRunning(TENANT_ID, TESTS_FLOW_NS, flowId);
        Execution result3 = runnerUtils.runOneUntilRunning(TENANT_ID, TESTS_FLOW_NS, flowId);
        long afterExec = System.currentTimeMillis();
        BulkResponse response = null;
        try {
            response = client.toBlocking().retrieve(
                HttpRequest.POST("/api/v1/main/executions/pause/by-query?flowId="+flowId+"&namespace=" + result1.getNamespace(), null),
                BulkResponse.class
            );
        } catch (HttpClientResponseException e){
            long afterException = System.currentTimeMillis();
            String errorMessage = "Duration before executions -> %d <-> duration after the exception -> %d <-> Error while pausing execution, err: %s, response: %s";
            String formatedError = String.format(errorMessage, afterExec - start, afterException - start, e.getMessage(), e.getResponse().getBody(BulkErrorResponse.class).map(BulkErrorResponse::getInvalids).orElse("errors"));
            log.error("Error while pausing execution, err: {}, response: {}", e.getMessage(), e.getResponse().getBody(BulkErrorResponse.class).map(BulkErrorResponse::getInvalids), e);
            fail(formatedError);
        }

        assertThat(response.getCount()).isEqualTo(3);
    }

    @Test
    @LoadFlows({"flows/valids/minimal.yaml"})
    void shouldRefuseSystemLabelsWhenUpdatingLabels() throws QueueException, TimeoutException {
        // update label on a terminated execution
        Execution result = runnerUtils.runOne(TENANT_ID, TESTS_FLOW_NS, "minimal");
        assertThat(result.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

        var error = assertThrows(HttpClientResponseException.class, () -> client.toBlocking().retrieve(
                HttpRequest.POST("/api/v1/main/executions/" + result.getId() + "/labels", List.of(new Label("system.label", "value"))),
                Execution.class
            )
        );

        assertThat(error.getStatus().getCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY.getCode());
    }

    @Test
    @LoadFlows({"flows/valids/flow-concurrency-queue.yml",
        "flows/valids/minimal.yaml"})
    void shouldUnqueueExecutionAQueuedFlow() throws QueueException, TimeoutException {
        // run a first flow so the second is queued
        runnerUtils.runOneUntilRunning(TENANT_ID, TESTS_FLOW_NS, "flow-concurrency-queue");
        Execution result = runUntilQueued(TESTS_FLOW_NS, "flow-concurrency-queue");

        var response = client.toBlocking().exchange(HttpRequest.POST("/api/v1/main/executions/" + result.getId() + "/unqueue", null));
        assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.OK.getCode());

        // waiting for the flow to complete successfully
        runnerUtils.awaitExecution(
            execution -> execution.getId().equals(result.getId()) && execution.getState().isSuccess(),
            () -> {},
            Duration.ofSeconds(10)
        );


        var notFound = assertThrows(HttpClientResponseException.class, () -> client.toBlocking().exchange(HttpRequest.POST("/api/v1/main/executions/notfound/unqueue", null)));
        assertThat(notFound.getStatus().getCode()).isEqualTo(HttpStatus.NOT_FOUND.getCode());

        // pausing an already completed flow will result in errors
        Execution completed = runnerUtils.runOne(TENANT_ID, TESTS_FLOW_NS, "minimal");

        var notRunning = assertThrows(HttpClientResponseException.class, () -> client.toBlocking().exchange(HttpRequest.POST("/api/v1/main/executions/" + completed.getId() + "/unqueue", null)));
        assertThat(notRunning.getStatus().getCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY.getCode());
    }

    @Test
    @LoadFlows({"flows/valids/flow-concurrency-queue.yml",
        "flows/valids/minimal.yaml"})
    void shouldUnqueueAQueuedFlowToCancelledState() throws QueueException, TimeoutException {
        // run a first flow so the second is queued
        runnerUtils.runOneUntilRunning(TENANT_ID, "io.kestra.tests", "flow-concurrency-queue");
        Execution result1 = runUntilQueued("io.kestra.tests", "flow-concurrency-queue");

        var cancelResponse = client.toBlocking().exchange(
            HttpRequest.POST("/api/v1/executions/" + result1.getId() + "/unqueue?state=CANCELLED", null)
        );
        assertThat(cancelResponse.getStatus().getCode()).isEqualTo(HttpStatus.OK.getCode());

        Optional<Execution> cancelledExecution = executionRepositoryInterface.findById(TENANT_ID, result1.getId());
        assertThat(cancelledExecution.isPresent()).isTrue();
        assertThat(cancelledExecution.get().getState().getCurrent()).isEqualTo(State.Type.CANCELLED);
    }

    @Test
    @LoadFlows({"flows/valids/flow-concurrency-queue.yml"})
    void shouldUnqueueExecutionByIdsQueuedFlows() throws TimeoutException, QueueException {
        // run a first flow so the others are queued
        runnerUtils.runOneUntilRunning(TENANT_ID, TESTS_FLOW_NS, "flow-concurrency-queue");
        Execution result1 = runUntilQueued(TESTS_FLOW_NS, "flow-concurrency-queue");
        Execution result2 = runUntilQueued(TESTS_FLOW_NS, "flow-concurrency-queue");
        Execution result3 = runUntilQueued(TESTS_FLOW_NS, "flow-concurrency-queue");

        BulkResponse response = client.toBlocking().retrieve(
            HttpRequest.POST("/api/v1/main/executions/unqueue/by-ids", List.of(result1.getId(), result2.getId(), result3.getId())),
            BulkResponse.class
        );
        assertThat(response.getCount()).isEqualTo(3);
    }

    @Test
    @LoadFlows({"flows/valids/flow-concurrency-queue.yml"})
    void shouldForceRunExecutionAQueuedFlow() throws QueueException, TimeoutException {
        // run a first flow so the second is queued
        runnerUtils.runOneUntilRunning(TENANT_ID, TESTS_FLOW_NS, "flow-concurrency-queue");
        Execution result = runUntilQueued(TESTS_FLOW_NS, "flow-concurrency-queue");

        var response = client.toBlocking().exchange(HttpRequest.POST("/api/v1/main/executions/" + result.getId() + "/force-run", null));
        assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.OK.getCode());
        Optional<Execution> forcedRun = executionRepositoryInterface.findById(TENANT_ID, result.getId());
        assertThat(forcedRun.isPresent()).isTrue();
        assertThat(forcedRun.get().getState().getCurrent()).isNotEqualTo(State.Type.QUEUED);

        // waiting for the flow to complete successfully
        runnerUtils.awaitExecution(
            execution -> execution.getId().equals(result.getId()) && execution.getState().isSuccess(),
            () -> {},
            Duration.ofSeconds(10)
        );
    }

    @Test
    @LoadFlows({"flows/valids/minimal.yaml"})
    void shouldFailToForceRunExecutionNotFoundOrTerminatedExecutions() throws QueueException, TimeoutException {
        var notFound = assertThrows(HttpClientResponseException.class, () -> client.toBlocking().exchange(HttpRequest.POST("/api/v1/main/executions/notfound/force-run", null)));
        assertThat(notFound.getStatus().getCode()).isEqualTo(HttpStatus.NOT_FOUND.getCode());

        // force run an already completed flow will result in errors
        Execution completed = runnerUtils.runOne(TENANT_ID, TESTS_FLOW_NS, "minimal");

        var notRunning = assertThrows(HttpClientResponseException.class, () -> client.toBlocking().exchange(HttpRequest.POST("/api/v1/main/executions/" + completed.getId() + "/force-run", null)));
        assertThat(notRunning.getStatus().getCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY.getCode());
    }

    @Test
    @LoadFlows({"flows/valids/minimal.yaml"})
    void shouldForceRunExecutionACreatedFlow() throws QueueException, TimeoutException {
        Execution result = this.createExecution(TESTS_FLOW_NS, "minimal");
        this.executionQueue.emit(result);

        var response = client.toBlocking().exchange(HttpRequest.POST("/api/v1/main/executions/" + result.getId() + "/force-run", null));
        assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.OK.getCode());
        Optional<Execution> forcedRun = executionRepositoryInterface.findById(TENANT_ID, result.getId());
        assertThat(forcedRun.isPresent()).isTrue();
        assertThat(forcedRun.get().getState().getCurrent()).isNotEqualTo(State.Type.CREATED);
    }

    @Test
    @LoadFlows({"flows/valids/pause-test.yaml"})
    void shouldForceRunExecutionAPausedFlow() throws QueueException, TimeoutException {
        // Run execution until it is paused
        Execution result = runnerUtils.runOneUntilPaused(TENANT_ID, TESTS_FLOW_NS, "pause-test");

        var response = client.toBlocking().exchange(HttpRequest.POST("/api/v1/main/executions/" + result.getId() + "/force-run", null));
        assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.OK.getCode());
        Optional<Execution> forcedRun = executionRepositoryInterface.findById(TENANT_ID, result.getId());
        assertThat(forcedRun.isPresent()).isTrue();
        assertThat(forcedRun.get().getState().getCurrent()).isNotEqualTo(State.Type.PAUSED);
    }


    @Test
    @LoadFlows({"flows/valids/sleep.yml"})
    void shouldForceRunExecutionARunningFlow() throws QueueException, TimeoutException {
        // Run execution until it is paused
        Execution result = runnerUtils.runOneUntilRunning(TENANT_ID, TESTS_FLOW_NS, "sleep");

        var response = client.toBlocking().exchange(HttpRequest.POST("/api/v1/main/executions/" + result.getId() + "/force-run", null));
        assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.OK.getCode());
        Optional<Execution> forcedRun = executionRepositoryInterface.findById(TENANT_ID, result.getId());
        assertThat(forcedRun.isPresent()).isTrue();
        assertThat(forcedRun.get().getState().getCurrent()).isNotEqualTo(State.Type.CREATED);
    }

    @Test
    @LoadFlows({"flows/valids/sleep.yml"})
    void shouldForRunByIdsFlows() throws TimeoutException, QueueException {
        Execution result1 =  runnerUtils.runOneUntilRunning(TENANT_ID, TESTS_FLOW_NS, "sleep");
        Execution result2 =  runnerUtils.runOneUntilRunning(TENANT_ID, TESTS_FLOW_NS, "sleep");
        Execution result3 =  runnerUtils.runOneUntilRunning(TENANT_ID, TESTS_FLOW_NS, "sleep");

        BulkResponse response = client.toBlocking().retrieve(
            HttpRequest.POST("/api/v1/main/executions/force-run/by-ids", List.of(result1.getId(), result2.getId(), result3.getId())),
            BulkResponse.class
        );
        assertThat(response.getCount()).isEqualTo(3);
    }

    @Test
    @LoadFlows({"flows/runners/sleep_medium.yml"})
    void shouldForRunByQueryFlows() throws TimeoutException, QueueException {
        String namespace = "io.kestra.forcerun.tests";
        runnerUtils.runOneUntilRunning(TENANT_ID, namespace, "sleep_medium");
        runnerUtils.runOneUntilRunning(TENANT_ID, namespace, "sleep_medium");
        runnerUtils.runOneUntilRunning(TENANT_ID, namespace, "sleep_medium");

        BulkResponse response = client.toBlocking().retrieve(
            HttpRequest.POST("/api/v1/main/executions/force-run/by-query?namespace=" + namespace, null),
            BulkResponse.class
        );
        assertThat(response.getCount()).isEqualTo(3);
    }

    @Test
    @ExecuteFlow("flows/valids/minimal.yaml")
    void shouldEvalTaskRunExpressionPebbleExpression(Execution execution) {
        ExecutionController.EvalResult evalResult = client.toBlocking().retrieve(
            HttpRequest
                .POST("/api/v1/main/executions/" + execution.getId() + "/eval/" + execution.getTaskRunList().getFirst().getId(), "{{ taskrun.id }}")
                .contentType(MediaType.TEXT_PLAIN),
            ExecutionController.EvalResult.class
        );
        assertThat(evalResult.getResult()).isNotNull();
    }

    @Test
    @ExecuteFlow("flows/valids/minimal.yaml")
    void shouldMaskSensitiveFunctionsWhenEvalTaskRunExpressionPebbleExpression(Execution execution) {
        ExecutionController.EvalResult evalResult = client.toBlocking().retrieve(
            HttpRequest
                .POST("/api/v1/main/executions/" + execution.getId() + "/eval/" + execution.getTaskRunList().getFirst().getId(), "{{ secret('MY_SECRET') }}")
                .contentType(MediaType.TEXT_PLAIN),
            ExecutionController.EvalResult.class
        );
        assertThat(evalResult.getError()).isNull();
        assertThat(evalResult.getStackTrace()).isNull();
        assertThat(evalResult.getResult()).isEqualTo("******");

        evalResult = client.toBlocking().retrieve(
            HttpRequest
                .POST("/api/v1/main/executions/" + execution.getId() + "/eval/" + execution.getTaskRunList().getFirst().getId(), "{{ secret('NON_EXISTING_KEY') }}")
                .contentType(MediaType.TEXT_PLAIN),
            ExecutionController.EvalResult.class
        );
        assertThat(evalResult.getError()).isEqualTo("io.pebbletemplates.pebble.error.PebbleException: Cannot find secret for key 'NON_EXISTING_KEY'. ({{ secret('NON_EXISTING_KEY') }}:1)");
        assertThat(evalResult.getStackTrace()).startsWith("io.kestra.core.exceptions.IllegalVariableEvaluationException: io.pebbletemplates.pebble.error.PebbleException: Cannot find secret for key 'NON_EXISTING_KEY'. ({{ secret('NON_EXISTING_KEY') }}:1)");
        assertThat(evalResult.getResult()).isNull();

        evalResult = client.toBlocking().retrieve(
            HttpRequest
                .POST("/api/v1/main/executions/" + execution.getId() + "/eval/" + execution.getTaskRunList().getFirst().getId(), "{{ http('https://dummyjson.com/todos') }}")
                .contentType(MediaType.TEXT_PLAIN),
            ExecutionController.EvalResult.class
        );
        assertThat(evalResult.getError()).isNull();
        assertThat(evalResult.getStackTrace()).isNull();
        assertThat(evalResult.getResult()).startsWith("{\"todos\":[{");

        evalResult = client.toBlocking().retrieve(
            HttpRequest
                .POST("/api/v1/main/executions/" + execution.getId() + "/eval/" + execution.getTaskRunList().getFirst().getId(), "{{ render('{{s'~'ecret(\"MY_SECRET\")}}') }}")
                .contentType(MediaType.TEXT_PLAIN),
            ExecutionController.EvalResult.class
        );
        assertThat(evalResult.getError()).isNull();
        assertThat(evalResult.getStackTrace()).isNull();
        assertThat(evalResult.getResult()).isEqualTo("******");
    }

    private ExecutionController.EvalResult evalTaskRunExpression(Execution execution, String expression, int index) {
        return client.toBlocking().retrieve(
            HttpRequest
                .POST(
                    "/api/v1/main/executions/" + execution.getId() + "/eval/" + execution.getTaskRunList().get(index).getId(),
                    expression
                )
                .contentType(MediaType.TEXT_PLAIN_TYPE),
            Argument.of(ExecutionController.EvalResult.class)
        );
    }


    private Execution triggerExecutionExecution(String namespace, String flowId, MultipartBody requestBody, Boolean wait) {
        return triggerExecutionExecution(namespace, flowId, requestBody, wait, null);
    }

    private Execution triggerExecutionExecution(String namespace, String flowId, MultipartBody requestBody, Boolean wait, String breakpoint) {
        return client.toBlocking().retrieve(
            HttpRequest
                .POST("/api/v1/main/executions/" + namespace + "/" + flowId + "?labels=a:label-1&labels=b:label-2&labels=url:" + ENCODED_URL_LABEL_VALUE + (wait ? "&wait=true" : "") + (breakpoint != null ? "&breakpoints=" + breakpoint : ""), requestBody)
                .contentType(MediaType.MULTIPART_FORM_DATA_TYPE),
            Execution.class
        );
    }

    private Execution triggerExecutionInputsFlowExecution(Boolean wait) {
        MultipartBody requestBody = createExecutionInputsFlowBody();

        return triggerExecutionExecution(TESTS_FLOW_NS, "inputs", requestBody, wait);
    }

    private MultipartBody createExecutionInputsFlowBody() {
        // Trigger execution
        File applicationFile = new File(Objects.requireNonNull(
            ExecutionControllerTest.class.getClassLoader().getResource("application-test.yml")
        ).getPath());

        File logbackFile = new File(Objects.requireNonNull(
            ExecutionControllerTest.class.getClassLoader().getResource("logback.xml")
        ).getPath());

        return MultipartBody.builder()
            .addPart("string", "myString")
            .addPart("enum", "ENUM_VALUE")
            .addPart("int", "42")
            .addPart("float", "42.42")
            .addPart("instant", "2019-10-06T18:27:49Z")
            .addPart("files", "file", MediaType.TEXT_PLAIN_TYPE, applicationFile)
            .addPart("files", "optionalFile", MediaType.TEXT_XML_TYPE, logbackFile)
            .addPart("secret", "secret")
            .addPart("array", "[1, 2, 3]")
            .addPart("json", "{}")
            .addPart("yaml", "{}")
            .build();
    }

    private Execution runUntilQueued(String namespace, String flowId) throws TimeoutException, QueueException {
        return runUntilState(namespace, flowId, State.Type.QUEUED);
    }

    private Execution createExecution(String namespace, String flowId) {
        Flow flow = flowRepositoryInterface.findById(TENANT_ID, namespace, flowId).orElseThrow();
        return Execution.newExecution(flow, null);
    }

    private Execution runUntilState(String namespace, String flowId, State.Type state) throws TimeoutException, QueueException {
        Execution execution = this.createExecution(namespace, flowId);
        return runnerUtils.awaitExecution(
            it -> execution.getId().equals(it.getId()) && it.getState().getCurrent() == state,
            throwRunnable(() -> this.executionQueue.emit(execution)),
            Duration.ofSeconds(1));
    }

    @Test
    @LoadFlows({"flows/valids/minimal.yaml"})
    void shouldRemoveLabelsFromExecutionPreservingSystemLabels() throws QueueException, TimeoutException {
        // Run initial execution
        Execution result = runnerUtils.runOne(TENANT_ID, TESTS_FLOW_NS, "minimal");
        assertThat(result.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

        Execution executionWithLabels = client.toBlocking().retrieve(
                HttpRequest.POST("/api/v1/main/executions/" + result.getId() + "/labels", List.of(
                                new Label("flow-label-1", "flow-label-1"),
                                new Label("flow-label-2", "flow-label-2"))),
                Execution.class
        );

        List<Label> allLabelsFromExecution = executionWithLabels.getLabels();
        assertLabelCounts(allLabelsFromExecution, 2, greaterThan(0));

        // Update with only one custom label
        Execution executionWithOneLabel = client.toBlocking().retrieve(
                HttpRequest.POST("/api/v1/main/executions/" + result.getId() + "/labels",
                        List.of(new Label("flow-label-1", "flow-label-1"))),
                Execution.class
        );

        allLabelsFromExecution = executionWithOneLabel.getLabels();
        assertLabelCounts(allLabelsFromExecution, 1, greaterThan(0));

        // Remove all custom labels
        Execution executionWithNoLabels = client.toBlocking().retrieve(
                HttpRequest.POST("/api/v1/main/executions/" + result.getId() + "/labels", Collections.emptyList()),
                Execution.class
        );

        allLabelsFromExecution = executionWithNoLabels.getLabels();
        assertLabelCounts(allLabelsFromExecution, 0, greaterThan(0));
    }

    @Test
    @LoadFlows({"flows/valids/minimal.yaml"})
    void shouldNotAllowAddingSystemLabels() throws QueueException, TimeoutException {
        Execution result = runnerUtils.runOne(TENANT_ID, TESTS_FLOW_NS, "minimal");
        assertThat(result.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);

        List<Label> systemLabels = List.of(new Label("system.key", "system-value"));
        HttpClientResponseException e = assertThrows(HttpClientResponseException.class, () -> client.toBlocking().retrieve(
                HttpRequest.POST("/api/v1/main/executions/" + result.getId() + "/labels", systemLabels),
                Execution.class
        ));

        assertThat(e.getStatus().getCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY.getCode());
        assertThat(e.getMessage()).contains("System labels can only be set by Kestra itself");
    }

    @Test
    @LoadFlows({"flows/valids/minimal.yaml"})
    void shouldSuspendAtBreakpointThenResume() throws QueueException, TimeoutException, InterruptedException {
        Execution execution = triggerExecutionExecution(TESTS_FLOW_NS, "minimal", null, false, "date");
        assertThat(execution).isNotNull();
        assertThat(execution.getState().getCurrent()).isEqualTo(State.Type.CREATED);

        // check that the execution is suspended
        Execution suspended = awaitExecution(execution.getId(), State.Type.BREAKPOINT);
        assertThat(suspended.getTaskRunList()).hasSize(1);
        assertThat(suspended.getTaskRunList().getFirst().getState().getCurrent()).isEqualTo(State.Type.BREAKPOINT);

        // resume the suspended execution
        HttpResponse<Void> resume = client.toBlocking().exchange(
            HttpRequest.POST("/api/v1/main/executions/" + suspended.getId() + "/resume-from-breakpoint", null),
            Void.class
        );
        assertThat(resume.getStatus().getCode()).isEqualTo(HttpStatus.OK.getCode());

        // wait for the exec to be terminated
        Execution terminated = runnerUtils.awaitExecution(
            it -> execution.getId().equals(it.getId()) && it.getState().isTerminated(),
            () -> {},
            Duration.ofSeconds(10));
        assertThat(terminated.getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
        assertThat(terminated.getTaskRunList()).hasSize(1);
        assertThat(terminated.getTaskRunList().getFirst().getState().getCurrent()).isEqualTo(State.Type.SUCCESS);
    }

    @FlakyTest
    @Test
    @LoadFlows({"flows/valids/subflow-parent.yaml", "flows/valids/subflow-child.yaml", "flows/valids/subflow-grand-child.yaml"})
    void triggerExecutionAndFollowDependencies() throws InterruptedException {
        Execution result = triggerExecutionExecution(TESTS_FLOW_NS, "subflow-parent", null, true);

        // without this slight delay, the event stream may miss some 'end' events
        Thread.sleep(500);

        List<Event<ExecutionStatusEvent>> results = sseClient
            .eventStream("/api/v1/main/executions/" + result.getId() + "/follow-dependencies?expandAll=true", ExecutionStatusEvent.class)
            .collectList()
            .block();

        assertThat(results).isNotNull();
        assertThat(results.size()).isGreaterThanOrEqualTo(5);
        assertThat(results.getFirst().getId()).isEqualTo("start");
        assertThat(results.getLast().getId()).isEqualTo("end-all");
        // check that we have 3 end events and 3 result in SUCCESS
        assertThat(results.stream().filter(event -> event.getId().equals("end"))).hasSize(3);
        assertThat(results.stream().filter(event -> event.getData().state() != null && event.getData().state().getCurrent().equals(State.Type.SUCCESS))).hasSize(3);

        // check that a second call work: calling follow on an already terminated execution.
        results = sseClient
            .eventStream("/api/v1/main/executions/" + result.getId() + "/follow-dependencies?expandAll=true", ExecutionStatusEvent.class)
            .collectList()
            .block();

        assertThat(results).isNotNull();
        assertThat(results.size()).isGreaterThan(1);
        assertThat(results.getFirst().getId()).isEqualTo("start");
        assertThat(results.getLast().getId()).isEqualTo("end-all");
        // check that we have 3 end events and 3 results in SUCCESS
        assertThat(results.stream().filter(event -> event.getId().equals("end"))).hasSize(3);
        assertThat(results.stream().filter(event -> event.getData().state() != null && event.getData().state().getCurrent().equals(State.Type.SUCCESS))).hasSize(3);

        // check that a without expandAll it would return only the immediate dependencies.
        results = sseClient
            .eventStream("/api/v1/main/executions/" + result.getId() + "/follow-dependencies", ExecutionStatusEvent.class)
            .collectList()
            .block();

        assertThat(results).isNotNull();
        assertThat(results.size()).isGreaterThan(1);
        assertThat(results.getFirst().getId()).isEqualTo("start");
        assertThat(results.getLast().getId()).isEqualTo("end-all");
        // check that we have 2 end events and 2 results in SUCCESS
        assertThat(results.stream().filter(event -> event.getId().equals("end"))).hasSize(2);
        assertThat(results.stream().filter(event -> event.getData().state() != null && event.getData().state().getCurrent().equals(State.Type.SUCCESS))).hasSize(2);

    }

    @Test
    @LoadFlows({"flows/valids/logs.yaml"})
    void restartExecutionByIdShouldFailed() throws InterruptedException {
        Execution execution = client.toBlocking().retrieve(
            POST(
                "/api/v1/main/executions/" + TESTS_FLOW_NS + "/logs",
                null
            ),
            Execution.class
        );

        // EXECUTION NOT FAILED STATE
        HttpClientResponseException e = assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().retrieve(
                POST("/api/v1/main/executions/restart/by-ids",
                    List.of(execution.getId())
                ),
                MutableHttpResponse.class
            ));

        assertThat(e.getStatus().getCode()).isEqualTo(HttpStatus.BAD_REQUEST.getCode());
        assertThat(e.getMessage()).contains("invalid bulk restart");

        // EXECUTION NOT FOUND
        e = assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().retrieve(
                POST("/api/v1/main/executions/restart/by-ids",
                    List.of("NotExists")
                ),
                MutableHttpResponse.class
            ));

        assertThat(e.getStatus().getCode()).isEqualTo(HttpStatus.BAD_REQUEST.getCode());
        assertThat(e.getMessage()).contains("invalid bulk restart");
    }

    @Test
    @LoadFlows({"flows/valids/failed-first.yaml"})
    void restartExecutionByIdShouldSucceed() throws InterruptedException {
        Execution execution = client.toBlocking().retrieve(
            POST(
                "/api/v1/main/executions/" + TESTS_FLOW_NS + "/failed-first",
                null
            ),
            Execution.class
        );

        Thread.sleep(250);

        BulkResponse result = client.toBlocking().retrieve(
            POST("/api/v1/main/executions/restart/by-ids",
                List.of(execution.getId())
            ),
            BulkResponse.class
        );

        assertThat(result).isNotNull();
        assertThat(result.getCount()).isEqualTo(1);
    }

    @Test
    @LoadFlows({"flows/valids/logs.yaml"})
    void killByIdShouldFailed() {
        Execution execution = client.toBlocking().retrieve(
            POST(
                "/api/v1/main/executions/" + TESTS_FLOW_NS + "/logs",
                null
            ),
            Execution.class
        );

        awaitExecution(execution.getId());

        // EXECUTION TERMINATED STATE
        HttpClientResponseException e = assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().retrieve(
                DELETE("/api/v1/main/executions/kill/by-ids",
                    List.of(execution.getId())
                ),
                MutableHttpResponse.class
            ));

        assertThat(e.getStatus().getCode()).isEqualTo(HttpStatus.BAD_REQUEST.getCode());
        assertThat(e.getMessage()).contains("invalid bulk kill");

        // EXECUTION NOT FOUND
        e = assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().retrieve(
                DELETE("/api/v1/main/executions/kill/by-ids",
                    List.of("NotExists")
                ),
                MutableHttpResponse.class
            ));

        assertThat(e.getStatus().getCode()).isEqualTo(HttpStatus.BAD_REQUEST.getCode());
        assertThat(e.getMessage()).contains("invalid bulk kill");
    }

    @Test
    @LoadFlows({"flows/valids/sleep-long.yml"})
    void killExecutionByIdShouldSucceed() throws InterruptedException {
        Execution execution = client.toBlocking().retrieve(
            POST(
                "/api/v1/main/executions/" + TESTS_FLOW_NS + "/sleep-long",
                null
            ),
            Execution.class
        );

        Thread.sleep(250);

        BulkResponse result = client.toBlocking().retrieve(
            DELETE("/api/v1/main/executions/kill/by-ids",
                List.of(execution.getId())
            ),
            BulkResponse.class
        );

        assertThat(result).isNotNull();
        assertThat(result.getCount()).isEqualTo(1);
    }

    @Test
    @LoadFlows("flows/valids/webhook-outputs.yaml")
    void webhookWithOutputs() {
        Map<String, Object> outputs = client.toBlocking().retrieve(
            GET(
                "/api/v1/main/executions/webhook/" + ExecutionControllerTest.TESTS_FLOW_NS + "/webhook-outputs/webhook-outputs"
            ),
            Argument.mapOf(String.class, Object.class)
        );

        assertThat(outputs).hasFieldOrPropertyWithValue("status", "ok");
        assertThat(outputs).containsKey("executionId");
    }

    private List<Label> getExecutionNonSystemLabels(List<Label> labels) {
        return labels == null ? List.of() :
            labels.stream()
                .filter(l -> !l.key().startsWith(Label.SYSTEM_PREFIX))
                .collect(Collectors.toList());
    }

    private List<Label> getExecutionSystemLabels(List<Label> allLabelsFromExecution) {
        return allLabelsFromExecution.stream()
                .filter(label -> label.key().startsWith(Label.SYSTEM_PREFIX))
                .collect(Collectors.toList());
    }

    private void assertLabelCounts(List<Label> allLabels, int expectedCustomCount, Matcher<Integer> expectedSystemMatcher) {
        List<Label> customLabels = getExecutionNonSystemLabels(allLabels);
        List<Label> systemLabels = getExecutionSystemLabels(allLabels);
        assertThat(customLabels).as("Custom label count").hasSize(expectedCustomCount);
        assertThat("System label count", systemLabels, hasSize(expectedSystemMatcher));
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

    private Execution awaitExecution(String executionId) {
        return Awaitility.await()
            .atMost(Duration.ofSeconds(10))
            .with().pollDelay(Duration.ofMillis(100)).pollInterval(Duration.ofMillis(250))
            .until(
                () -> client.toBlocking().retrieve(GET("/api/v1/main/executions/" + executionId), Execution.class),
                execution -> execution.getState().isTerminated()
            );
    }

    private Execution awaitExecution(String executionId, State.Type state) {
        return Awaitility.await()
            .atMost(Duration.ofSeconds(10))
            .with().pollDelay(Duration.ofMillis(100)).pollInterval(Duration.ofMillis(250))
            .until(
                () -> client.toBlocking().retrieve(GET("/api/v1/main/executions/" + executionId), Execution.class),
                execution -> execution.getState().getCurrent() == state
            );
    }

    private Execution awaitExecution(String executionId, Predicate<Execution> predicate) {
        return Awaitility.await()
            .atMost(Duration.ofSeconds(10))
            .with().pollDelay(Duration.ofMillis(100)).pollInterval(Duration.ofMillis(250))
            .until(
                () -> client.toBlocking().retrieve(GET("/api/v1/main/executions/" + executionId), Execution.class),
                predicate
            );
    }
}
