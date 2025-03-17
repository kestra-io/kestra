package io.kestra.webserver.controllers.api;

import static io.kestra.core.utils.Rethrow.throwRunnable;
import static io.micronaut.http.HttpRequest.GET;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.collect.ImmutableMap;
import io.kestra.core.junit.annotations.ExecuteFlow;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.junit.annotations.LoadFlows;
import io.kestra.core.models.Label;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionKilled;
import io.kestra.core.models.executions.ExecutionKilledExecution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.storage.FileMetas;
import io.kestra.core.queues.QueueException;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.repositories.LocalFlowRepositoryLoader;
import io.kestra.core.runners.FlowInputOutput;
import io.kestra.core.runners.InputsTest;
import io.kestra.core.runners.RunnerUtils;
import io.kestra.core.runners.StandAloneRunner;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.utils.Await;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.TestsUtils;
import io.kestra.jdbc.JdbcTestUtils;
import io.kestra.plugin.core.trigger.Webhook;
import io.kestra.webserver.responses.BulkResponse;
import io.kestra.webserver.responses.PagedResults;
import io.micronaut.core.type.Argument;
import io.micronaut.data.model.Pageable;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.client.multipart.MultipartBody;
import io.micronaut.http.sse.Event;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import io.micronaut.reactor.http.client.ReactorSseClient;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.io.File;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import lombok.extern.slf4j.Slf4j;
import org.hamcrest.Matcher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.RetryingTest;
import reactor.core.publisher.Flux;

@Slf4j
@KestraTest(startRunner = true)
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
    protected LocalFlowRepositoryLoader repositoryLoader;

    @Inject
    protected RunnerUtils runnerUtils;

    @Inject
    protected StandAloneRunner runner;

    public static final String TESTS_FLOW_NS = "io.kestra.tests";
    public static final String TESTS_WEBHOOK_KEY = "a-secret-key";

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
        .put("json", "{}")
        .put("yaml", """
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
    void trigger() {
        Execution result = triggerInputsFlowExecution(false);

        assertThat(result.getState().getCurrent(), is(State.Type.CREATED));
        assertThat(result.getFlowId(), is("inputs"));
        assertThat(result.getInputs().get("float"), is(42.42));
        assertThat(result.getInputs().get("file").toString(), startsWith("kestra:///io/kestra/tests/inputs/executions/"));
        assertThat(result.getInputs().get("file").toString(), startsWith("kestra:///io/kestra/tests/inputs/executions/"));
        assertThat(result.getInputs().containsKey("bool"), is(true));
        assertThat(result.getInputs().get("bool"), nullValue());
        assertThat(result.getLabels().size(), is(6));
        assertThat(result.getLabels().getFirst(), is(new Label("flow-label-1", "flow-label-1")));
        assertThat(result.getLabels().get(1), is(new Label("flow-label-2", "flow-label-2")));
        assertThat(result.getLabels().get(2), is(new Label("a", "label-1")));
        assertThat(result.getLabels().get(3), is(new Label("b", "label-2")));
        assertThat(result.getLabels().get(4), is(new Label("url", URL_LABEL_VALUE)));

        var notFound = assertThrows(HttpClientResponseException.class, () -> client.toBlocking().exchange(
            HttpRequest
                .POST("/api/v1/executions/foo/bar", createInputsFlowBody())
                .contentType(MediaType.MULTIPART_FORM_DATA_TYPE),
            HttpResponse.class
        ));
        assertThat(notFound.getStatus(), is(HttpStatus.NOT_FOUND));
    }

    @Test
    @LoadFlows({"flows/valids/inputs-small-files.yaml"})
    void triggerInputSmall() {
        File applicationFile = new File(Objects.requireNonNull(
            ExecutionControllerTest.class.getClassLoader().getResource("application-test.yml")
        ).getPath());

        MultipartBody requestBody = MultipartBody.builder()
            .addPart("files", "f", MediaType.TEXT_PLAIN_TYPE, applicationFile)
            .build();

        Execution execution = triggerExecution(TESTS_FLOW_NS, "inputs-small-files", requestBody, true);

        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat((String) execution.getOutputs().get("o"), startsWith("kestra://"));
    }

    @Test
    @LoadFlows({"flows/valids/inputs.yaml"})
    void invalidInputs() {
        MultipartBody.Builder builder = MultipartBody.builder()
            .addPart("validatedString", "B-failed");
        inputs.forEach((s, o) -> builder.addPart(s, o instanceof String str ? str : null));

        HttpClientResponseException e = assertThrows(
            HttpClientResponseException.class,
            () -> triggerExecution(TESTS_FLOW_NS, "inputs", builder.build(), false)
        );

        String response = e.getResponse().getBody(String.class).orElseThrow();

        assertThat(response, containsString("Invalid entity"));
        assertThat(response, containsString("Invalid input for `validatedString`"));
    }

    @Test
    @LoadFlows({"flows/valids/inputs.yaml"})
    void triggerAndWait() {
        Execution result = triggerInputsFlowExecution(true);

        assertThat(result.getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(result.getTaskRunList().size(), is(14));
    }

    @Test
    @LoadFlows({"flows/valids/inputs.yaml"})
    void get() {
        Execution result = triggerInputsFlowExecution(false);

        // Get the triggered execution by execution id
        Execution foundExecution = client.retrieve(
            GET("/api/v1/executions/" + result.getId()),
            Execution.class
        ).block();

        assertThat(foundExecution, is(notNullValue()));
        assertThat(foundExecution.getId(), is(result.getId()));
        assertThat(foundExecution.getNamespace(), is(result.getNamespace()));
    }

    @SuppressWarnings("unchecked")
    @Test
    @LoadFlows({"flows/valids/minimal-bis.yaml"})
    void findByFlowId() {
        String namespace = "io.kestra.tests.minimal.bis";
        String flowId = "minimal-bis";

        PagedResults<Execution> executionsBefore = client.toBlocking().retrieve(
            GET("/api/v1/executions?namespace=" + namespace + "&flowId=" + flowId),
            Argument.of(PagedResults.class, Execution.class)
        );

        assertThat(executionsBefore.getTotal(), is(0L));

        triggerExecution(namespace, flowId, MultipartBody.builder().addPart("string", "myString").build(), false);

        // Wait for execution indexation
        Await.until(() -> executionRepositoryInterface.findByFlowId(null, namespace, flowId, Pageable.from(1)).size() == 1);
        PagedResults<Execution> executionsAfter = client.toBlocking().retrieve(
            GET("/api/v1/executions?namespace=" + namespace + "&flowId=" + flowId),
            Argument.of(PagedResults.class, Execution.class)
        );

        assertThat(executionsAfter.getTotal(), is(1L));
    }

    @Test
    @LoadFlows({"flows/valids/inputs.yaml"})
    void triggerAndFollow() {
        Execution result = triggerInputsFlowExecution(false);

        List<Event<Execution>> results = sseClient
            .eventStream("/api/v1/executions/" + result.getId() + "/follow", Execution.class)
            .collectList()
            .block();

        assertThat(results, is(notNullValue()));
        assertThat(results.size(), is(greaterThan(0)));
        assertThat(results.getLast().getData().getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(results.getFirst().getId(), is("start"));
        assertThat(results.getLast().getId(), is("end"));
    }

    @Test
    @LoadFlows({"flows/valids/each-sequential-nested.yaml"})
    void eval() throws TimeoutException, QueueException {
        Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "each-sequential-nested");

        ExecutionController.EvalResult result = this.eval(execution, "my simple string", 0);
        assertThat(result.getResult(), is("my simple string"));

        result = this.eval(execution, "{{ taskrun.id }}", 0);
        assertThat(result.getResult(), is(execution.getTaskRunList().getFirst().getId()));

        result = this.eval(execution, "{{ outputs['1-1_return'][taskrun.value].value }}", 21);
        assertThat(result.getResult(), containsString("1-1_return"));

        result = this.eval(execution, "{{ missing }}", 21);
        assertThat(result.getResult(), is(nullValue()));
        assertThat(result.getError(), containsString("Unable to find `missing` used in the expression `{{ missing }}` at line 1"));
        assertThat(result.getStackTrace(), containsString("Unable to find `missing` used in the expression `{{ missing }}` at line 1"));
    }

    @Test
    @LoadFlows({"flows/valids/inputs.yaml",
        "flows/valids/encrypted-string.yaml"})
    void evalKeepEncryptedValues() throws TimeoutException, QueueException {
        Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "encrypted-string");

        ExecutionController.EvalResult result = this.eval(execution, "{{outputs.hello.value}}", 0);
        Map<String, Object> resultMap = null;
        try {
            resultMap = JacksonMapper.toMap(result.getResult());
        } catch (JsonProcessingException e) {
            throw new AssertionError("Evaluation result is not a map. Probably due to output decryption being performed while it shouldn't for such feature.");
        }
        assertThat(resultMap.get("type"), is("io.kestra.datatype:aes_encrypted"));
        assertThat(resultMap.get("value"), notNullValue());

        execution = runnerUtils.runOne(null, "io.kestra.tests", "inputs", null, (flow, execution1) -> flowIO.readExecutionInputs(flow, execution1, inputs));

        result = this.eval(execution, "{{inputs.secret}}", 0);
        assertThat(result.getResult(), not(inputs.get("secret")));
    }

    @Test
    @LoadFlows({"flows/valids/restart_with_inputs.yaml"})
    void restartFromUnknownTaskId() throws TimeoutException, QueueException {
        final String flowId = "restart_with_inputs";
        final String referenceTaskId = "unknownTaskId";

        // Run execution until it ends
        Execution parentExecution = runnerUtils.runOne(null, TESTS_FLOW_NS, flowId, null, (flow, execution1) -> flowIO.readExecutionInputs(flow, execution1, inputs));

        HttpClientResponseException e = assertThrows(HttpClientResponseException.class, () -> client.toBlocking().retrieve(
            HttpRequest
                .POST("/api/v1/executions/" + parentExecution.getId() + "/replay?taskRunId=" + referenceTaskId, ImmutableMap.of()),
            Execution.class
        ));

        assertThat(e.getStatus(), is(HttpStatus.UNPROCESSABLE_ENTITY));
        assertThat(e.getResponse().getBody(String.class).isPresent(), is(true));
        assertThat(e.getResponse().getBody(String.class).get(), containsString("No task found"));
    }

    @Test
    @LoadFlows({"flows/valids/restart_with_inputs.yaml"})
    void restartWithNoFailure() throws TimeoutException, QueueException{
        final String flowId = "restart_with_inputs";

        // Run execution until it ends
        Execution parentExecution = runnerUtils.runOne(null, TESTS_FLOW_NS, flowId, null, (flow, execution1) -> flowIO.readExecutionInputs(flow, execution1, inputs));

        HttpClientResponseException e = assertThrows(HttpClientResponseException.class, () -> client.toBlocking().retrieve(
            HttpRequest
                .POST("/api/v1/executions/" + parentExecution.getId() + "/restart", ImmutableMap.of()),
            Execution.class
        ));

        assertThat(e.getStatus(), is(HttpStatus.UNPROCESSABLE_ENTITY));
        assertThat(e.getResponse().getBody(String.class).isPresent(), is(true));
        assertThat(e.getResponse().getBody(String.class).get(), containsString("No task found to restart"));
    }

    @Test
    @LoadFlows({"flows/valids/restart_with_inputs.yaml"})
    void restartFromTaskId() throws Exception {
        final String flowId = "restart_with_inputs";
        final String referenceTaskId = "instant";

        // Run execution until it ends
        Execution parentExecution = runnerUtils.runOne(null, TESTS_FLOW_NS, flowId, null, (flow, execution1) -> flowIO.readExecutionInputs(flow, execution1, inputs));

        Optional<Flow> flow = flowRepositoryInterface.findById(null, TESTS_FLOW_NS, flowId);

        assertThat(flow.isPresent(), is(true));

        // Run child execution starting from a specific task and wait until it finishes
        Execution finishedChildExecution = runnerUtils.awaitChildExecution(
            flow.get(),
            parentExecution, throwRunnable(() -> {
                Thread.sleep(100);

                Execution createdChidExec = client.toBlocking().retrieve(
                    HttpRequest
                        .POST("/api/v1/executions/" + parentExecution.getId() + "/replay?taskRunId=" + parentExecution.findTaskRunByTaskIdAndValue(referenceTaskId, List.of()).getId(), ImmutableMap.of()),
                    Execution.class
                );

                assertThat(createdChidExec, notNullValue());
                assertThat(createdChidExec.getParentId(), is(parentExecution.getId()));
                assertThat(createdChidExec.getTaskRunList().size(), is(4));
                assertThat(createdChidExec.getState().getCurrent(), is(State.Type.RESTARTED));

                IntStream
                    .range(0, 3)
                    .mapToObj(value -> createdChidExec.getTaskRunList().get(value))
                    .forEach(taskRun -> assertThat(taskRun.getState().getCurrent(), is(State.Type.SUCCESS)));

                assertThat(createdChidExec.getTaskRunList().get(3).getState().getCurrent(), is(State.Type.RESTARTED));
                assertThat(createdChidExec.getTaskRunList().get(3).getAttempts().size(), is(1));
            }),
            Duration.ofSeconds(15));

        assertThat(finishedChildExecution, notNullValue());
        assertThat(finishedChildExecution.getParentId(), is(parentExecution.getId()));
        assertThat(finishedChildExecution.getTaskRunList().size(), is(5));

        finishedChildExecution
            .getTaskRunList()
            .stream()
            .map(TaskRun::getState)
            .forEach(state -> assertThat(state.getCurrent(), is(State.Type.SUCCESS)));
    }

    @Test
    @LoadFlows({"flows/valids/restart-each.yaml"})
    void restartFromTaskIdWithSequential() throws Exception {
        final String flowId = "restart-each";
        final String referenceTaskId = "2_end";

        // Run execution until it ends
        Execution parentExecution = runnerUtils.runOne(null, TESTS_FLOW_NS, flowId, null,
            (flow, execution1) -> flowIO.readExecutionInputs(flow, execution1, inputs));

        Optional<Flow> flow = flowRepositoryInterface.findById(null, TESTS_FLOW_NS, flowId);
        assertThat(flow.isPresent(), is(true));

        // Run child execution starting from a specific task and wait until it finishes
        runnerUtils.awaitChildExecution(
            flow.get(),
            parentExecution, throwRunnable(() -> {
                Thread.sleep(100);

                Execution createdChidExec = client.toBlocking().retrieve(
                    HttpRequest
                        .POST("/api/v1/executions/" + parentExecution.getId() + "/replay?taskRunId=" + parentExecution.findTaskRunByTaskIdAndValue(referenceTaskId, List.of()).getId(), ImmutableMap.of()),
                    Execution.class
                );

                assertThat(createdChidExec.getState().getCurrent(), is(State.Type.RESTARTED));
                assertThat(createdChidExec.getState().getHistories(), hasSize(4));
                assertThat(createdChidExec.getTaskRunList(), hasSize(20));

                assertThat(createdChidExec.getId(), not(parentExecution.getId()));
            }),
            Duration.ofSeconds(30));
    }

    @Test
    @LoadFlows({"flows/valids/restart_last_failed.yaml"})
    void restartFromLastFailed() throws TimeoutException, QueueException{
        final String flowId = "restart_last_failed";

        // Run execution until it ends
        Execution firstExecution = runnerUtils.runOne(null, TESTS_FLOW_NS, flowId, null, null);

        assertThat(firstExecution.getTaskRunList().get(2).getState().getCurrent(), is(State.Type.FAILED));
        assertThat(firstExecution.getState().getCurrent(), is(State.Type.FAILED));

        // Update task's command to make second execution successful
        Optional<Flow> flow = flowRepositoryInterface.findById(null, TESTS_FLOW_NS, flowId);
        assertThat(flow.isPresent(), is(true));

        // Restart execution and wait until it finishes
        Execution finishedRestartedExecution = runnerUtils.awaitExecution(
            execution -> execution.getId().equals(firstExecution.getId()) &&
                execution.getTaskRunList().size() == 4 &&
                execution.getState().isTerminated(),
            () -> {
                Execution restartedExec = client.toBlocking().retrieve(
                    HttpRequest
                        .POST("/api/v1/executions/" + firstExecution.getId() + "/restart", ImmutableMap.of()),
                    Execution.class
                );

                assertThat(restartedExec, notNullValue());
                assertThat(restartedExec.getId(), is(firstExecution.getId()));
                assertThat(restartedExec.getParentId(), nullValue());
                assertThat(restartedExec.getTaskRunList().size(), is(3));
                assertThat(restartedExec.getState().getCurrent(), is(State.Type.RESTARTED));

                IntStream
                    .range(0, 2)
                    .mapToObj(value -> restartedExec.getTaskRunList().get(value)).forEach(taskRun -> {
                        assertThat(taskRun.getState().getCurrent(), is(State.Type.SUCCESS));
                        assertThat(taskRun.getAttempts().size(), is(1));

                        assertThat(restartedExec.getTaskRunList().get(2).getState().getCurrent(), is(State.Type.RESTARTED));
                        assertThat(restartedExec.getTaskRunList().get(2).getAttempts().size(), is(1));
                    });
            },
            Duration.ofSeconds(15)
        );

        assertThat(finishedRestartedExecution, notNullValue());
        assertThat(finishedRestartedExecution.getId(), is(firstExecution.getId()));
        assertThat(finishedRestartedExecution.getParentId(), nullValue());
        assertThat(finishedRestartedExecution.getTaskRunList().size(), is(4));

        assertThat(finishedRestartedExecution.getTaskRunList().getFirst().getAttempts().size(), is(1));
        assertThat(finishedRestartedExecution.getTaskRunList().get(1).getAttempts().size(), is(1));
        assertThat(finishedRestartedExecution.getTaskRunList().get(2).getAttempts().size(), is(2));
        assertThat(finishedRestartedExecution.getTaskRunList().get(3).getAttempts().size(), is(1));

        finishedRestartedExecution
            .getTaskRunList()
            .stream()
            .map(TaskRun::getState)
            .forEach(state -> assertThat(state.getCurrent(), is(State.Type.SUCCESS)));
    }

    @Test
    @LoadFlows({"flows/valids/restart_pause_last_failed.yaml"})
    void restartFromLastFailedWithPause() throws TimeoutException, QueueException{
        final String flowId = "restart_pause_last_failed";

        // Run execution until it ends
        Execution firstExecution = runnerUtils.runOne(null, TESTS_FLOW_NS, flowId, null, null);

        assertThat(firstExecution.getTaskRunList().get(2).getState().getCurrent(), is(State.Type.FAILED));
        assertThat(firstExecution.getState().getCurrent(), is(State.Type.FAILED));

        // Update task's command to make second execution successful
        Optional<Flow> flow = flowRepositoryInterface.findById(null, TESTS_FLOW_NS, flowId);
        assertThat(flow.isPresent(), is(true));

        // Restart execution and wait until it finishes
        Execution finishedRestartedExecution = runnerUtils.awaitExecution(
            execution -> execution.getId().equals(firstExecution.getId()) &&
                execution.getTaskRunList().size() == 5 &&
                execution.getState().isTerminated(),
            () -> {
                Execution restartedExec = client.toBlocking().retrieve(
                    HttpRequest
                        .POST("/api/v1/executions/" + firstExecution.getId() + "/restart", ImmutableMap.of()),
                    Execution.class
                );

                assertThat(restartedExec, notNullValue());
                assertThat(restartedExec.getId(), is(firstExecution.getId()));
                assertThat(restartedExec.getParentId(), nullValue());
                assertThat(restartedExec.getTaskRunList().size(), is(4));
                assertThat(restartedExec.getState().getCurrent(), is(State.Type.RESTARTED));

                IntStream
                    .range(0, 2)
                    .mapToObj(value -> restartedExec.getTaskRunList().get(value)).forEach(taskRun -> {
                        assertThat(taskRun.getState().getCurrent(), is(State.Type.SUCCESS));
                        assertThat(taskRun.getAttempts().size(), is(1));

                        assertThat(restartedExec.getTaskRunList().get(2).getState().getCurrent(), is(State.Type.RUNNING));
                        assertThat(restartedExec.getTaskRunList().get(3).getState().getCurrent(), is(State.Type.RESTARTED));
                        assertThat(restartedExec.getTaskRunList().get(2).getAttempts(), nullValue());
                        assertThat(restartedExec.getTaskRunList().get(3).getAttempts().size(), is(1));
                    });
            },
            Duration.ofSeconds(15)
        );

        assertThat(finishedRestartedExecution, notNullValue());
        assertThat(finishedRestartedExecution.getId(), is(firstExecution.getId()));
        assertThat(finishedRestartedExecution.getParentId(), nullValue());
        assertThat(finishedRestartedExecution.getTaskRunList().size(), is(5));

        assertThat(finishedRestartedExecution.getTaskRunList().getFirst().getAttempts().size(), is(1));
        assertThat(finishedRestartedExecution.getTaskRunList().get(1).getAttempts().size(), is(1));
        assertThat(finishedRestartedExecution.getTaskRunList().get(2).getAttempts(), nullValue());
        assertThat(finishedRestartedExecution.getTaskRunList().get(2).getState().getHistories().stream().filter(state -> state.getState() == State.Type.PAUSED).count(), is(1L));
        assertThat(finishedRestartedExecution.getTaskRunList().get(3).getAttempts().size(), is(2));
        assertThat(finishedRestartedExecution.getTaskRunList().get(4).getAttempts().size(), is(1));

        finishedRestartedExecution
            .getTaskRunList()
            .stream()
            .map(TaskRun::getState)
            .forEach(state -> assertThat(state.getCurrent(), is(State.Type.SUCCESS)));
    }

    @Test
    @LoadFlows({"flows/valids/inputs.yaml"})
    void downloadFile() throws TimeoutException, QueueException{
        Execution execution = runnerUtils.runOne(null, TESTS_FLOW_NS, "inputs", null, (flow, execution1) -> flowIO.readExecutionInputs(flow, execution1, inputs));
        assertThat(execution.getTaskRunList(), hasSize(14));

        String path = (String) execution.getInputs().get("file");

        String file = client.toBlocking().retrieve(
            GET("/api/v1/executions/" + execution.getId() + "/file?path=" + path),
            String.class
        );

        assertThat(file, is("hello"));

        FileMetas metas = client.retrieve(
            GET("/api/v1/executions/" + execution.getId() + "/file/metas?path=" + path),
            FileMetas.class
        ).block();


        assertThat(metas, is(notNullValue()));
        assertThat(metas.getSize(), is(5L));

        String newExecutionId = IdUtils.create();

        HttpClientResponseException e = assertThrows(HttpClientResponseException.class, () -> client.toBlocking().retrieve(
            GET("/api/v1/executions/" + execution.getId() + "/file?path=" + path.replace(execution.getId(),
                newExecutionId
            )),
            String.class
        ));

        // we redirect to good execution (that doesn't exist, so 404)
        assertThat(e.getStatus().getCode(), is(404));
        assertThat(e.getMessage(), containsString("execution id '" +  newExecutionId + "'"));
    }

    @Test
    @LoadFlows({"flows/valids/inputs.yaml"})
    void filePreview() throws TimeoutException, QueueException{
        Execution defaultExecution = runnerUtils.runOne(null, TESTS_FLOW_NS, "inputs", null, (flow, execution1) -> flowIO.readExecutionInputs(flow, execution1, inputs));
        assertThat(defaultExecution.getTaskRunList(), hasSize(14));

        String defaultPath = (String) defaultExecution.getInputs().get("file");

        String defaultFile = client.toBlocking().retrieve(
            GET("/api/v1/executions/" + defaultExecution.getId() + "/file/preview?path=" + defaultPath),
            String.class
        );

        assertThat(defaultFile, containsString("hello"));

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
            .put("json", "{}")
            .put("yaml", "{}")
            .build();

        Execution latin1Execution = runnerUtils.runOne(null, TESTS_FLOW_NS, "inputs", null, (flow, execution1) -> flowIO.readExecutionInputs(flow, execution1, latin1FileInputs));
        assertThat(latin1Execution.getTaskRunList(), hasSize(14));

        String latin1Path = (String) latin1Execution.getInputs().get("file");

        String latin1File = client.toBlocking().retrieve(
            GET("/api/v1/executions/" + latin1Execution.getId() + "/file/preview?path=" + latin1Path + "&encoding=ISO-8859-1"),
            String.class
        );

        assertThat(latin1File, containsString("Düsseldorf"));

        HttpClientResponseException e = assertThrows(HttpClientResponseException.class, () -> client.toBlocking().retrieve(
            GET("/api/v1/executions/" + latin1Execution.getId() + "/file/preview?path=" + latin1Path + "&encoding=foo"),
            String.class
        ));

        assertThat(e.getStatus(), is(HttpStatus.UNPROCESSABLE_ENTITY));
        assertThat(e.getMessage(), containsString("using encoding 'foo'"));
    }

    @SuppressWarnings("unchecked")
    @Test
    @LoadFlows({"flows/valids/webhook.yaml"})
    void webhook() {
        Flow webhook = flowRepositoryInterface.findById(null, TESTS_FLOW_NS, "webhook").orElseThrow();
        String key = ((Webhook) webhook.getTriggers().getFirst()).getKey();

        Execution execution = client.toBlocking().retrieve(
            HttpRequest
                .POST(
                    "/api/v1/executions/webhook/" + TESTS_FLOW_NS + "/webhook/" + key + "?name=john&age=12&age=13",
                    ImmutableMap.of("a", 1, "b", true)
                ),
            Execution.class
        );

        assertThat(((Map<String, Object>) execution.getTrigger().getVariables().get("body")).get("a"), is(1));
        assertThat(((Map<String, Object>) execution.getTrigger().getVariables().get("body")).get("b"), is(true));
        assertThat(((Map<String, Object>) execution.getTrigger().getVariables().get("parameters")).get("name"), is(List.of("john")));
        assertThat(((Map<String, List<Integer>>) execution.getTrigger().getVariables().get("parameters")).get("age"), containsInAnyOrder("12", "13"));
        assertThat(execution.getLabels().getFirst(), is(new Label("flow-label-1", "flow-label-1")));
        assertThat(execution.getLabels().get(1), is(new Label("flow-label-2", "flow-label-2")));

        execution = client.toBlocking().retrieve(
            HttpRequest
                .PUT(
                    "/api/v1/executions/webhook/" + TESTS_FLOW_NS + "/webhook/" + key,
                    Collections.singletonList(ImmutableMap.of("a", 1, "b", true))
                ),
            Execution.class
        );

        assertThat(((List<Map<String, Object>>) execution.getTrigger().getVariables().get("body")).getFirst().get("a"), is(1));
        assertThat(((List<Map<String, Object>>) execution.getTrigger().getVariables().get("body")).getFirst().get("b"), is(true));

        execution = client.toBlocking().retrieve(
            HttpRequest
                .POST(
                    "/api/v1/executions/webhook/" + TESTS_FLOW_NS + "/webhook/" + key,
                    "bla"
                ),
            Execution.class
        );

        assertThat(execution.getTrigger().getVariables().get("body"), is("bla"));

        execution = client.toBlocking().retrieve(
            GET("/api/v1/executions/webhook/" + TESTS_FLOW_NS + "/webhook/" + key),
            Execution.class
        );
        assertThat(execution.getTrigger().getVariables().get("body"), is(nullValue()));

        execution = client.toBlocking().retrieve(
            HttpRequest
                .POST(
                    "/api/v1/executions/webhook/" + TESTS_FLOW_NS + "/webhook/" + key,
                    "{\\\"a\\\":\\\"\\\",\\\"b\\\":{\\\"c\\\":{\\\"d\\\":{\\\"e\\\":\\\"\\\",\\\"f\\\":\\\"1\\\"}}}}"
                ),
            Execution.class
        );
        assertThat(execution.getTrigger().getVariables().get("body"), is("{\\\"a\\\":\\\"\\\",\\\"b\\\":{\\\"c\\\":{\\\"d\\\":{\\\"e\\\":\\\"\\\",\\\"f\\\":\\\"1\\\"}}}}"));

    }

    @Test
    @LoadFlows({"flows/valids/pause.yaml"})
    void resumePaused() throws TimeoutException, InterruptedException, QueueException {
        // Run execution until it is paused
        Execution pausedExecution = runnerUtils.runOneUntilPaused(null, TESTS_FLOW_NS, "pause");
        assertThat(pausedExecution.getState().isPaused(), is(true));

        // resume the execution
        HttpResponse<?> resumeResponse = client.toBlocking().exchange(
            HttpRequest.POST("/api/v1/executions/" + pausedExecution.getId() + "/resume", null));
        assertThat(resumeResponse.getStatus(), is(HttpStatus.NO_CONTENT));

        // check that the execution is no more paused
        Thread.sleep(100);
        Execution execution = client.toBlocking().retrieve(
            GET("/api/v1/executions/" + pausedExecution.getId()),
            Execution.class);
        assertThat(execution.getState().isPaused(), is(false));
    }

    @SuppressWarnings("unchecked")
    @Test
    @LoadFlows({"flows/valids/pause_on_resume.yaml"})
    void resumePausedWithInputs() throws TimeoutException, InterruptedException, QueueException {
        // Run execution until it is paused
        Execution pausedExecution = runnerUtils.runOneUntilPaused(null, TESTS_FLOW_NS, "pause_on_resume");
        assertThat(pausedExecution.getState().isPaused(), is(true));

        File applicationFile = new File(Objects.requireNonNull(
            ExecutionControllerTest.class.getClassLoader().getResource("application-test.yml")
        ).getPath());

        MultipartBody multipartBody = MultipartBody.builder()
            .addPart("asked", "myString")
            .addPart("files", "data", MediaType.TEXT_PLAIN_TYPE, applicationFile)
            .build();

        // resume the execution
        HttpResponse<?> resumeResponse = client.toBlocking().exchange(
            HttpRequest.POST("/api/v1/executions/" + pausedExecution.getId() + "/resume", multipartBody)
                .contentType(MediaType.MULTIPART_FORM_DATA_TYPE)
        );
        assertThat(resumeResponse.getStatus(), is(HttpStatus.NO_CONTENT));

        // check that the execution is no more paused
        Thread.sleep(100);
        Execution execution = client.toBlocking().retrieve(
            GET("/api/v1/executions/" + pausedExecution.getId()),
            Execution.class);
        assertThat(execution.getState().isPaused(), is(false));

        Map<String, Object> outputs = (Map<String, Object>) execution.findTaskRunsByTaskId("pause").getFirst().getOutputs().get("onResume");
        assertThat(outputs.get("asked"), is("myString"));
        assertThat((String) outputs.get("data"), startsWith("kestra://"));
    }

    @Test
    @LoadFlows({"flows/valids/pause.yaml"})
    void resumeByIds() throws TimeoutException, InterruptedException, QueueException {
        Execution pausedExecution1 = runnerUtils.runOneUntilPaused(null, TESTS_FLOW_NS, "pause");
        Execution pausedExecution2 = runnerUtils.runOneUntilPaused(null, TESTS_FLOW_NS, "pause");

        assertThat(pausedExecution1.getState().isPaused(), is(true));
        assertThat(pausedExecution2.getState().isPaused(), is(true));

        // resume executions
        BulkResponse resumeResponse = client.toBlocking().retrieve(
            HttpRequest.POST(
                "/api/v1/executions/resume/by-ids",
                List.of(pausedExecution1.getId(), pausedExecution2.getId())
            ),
            BulkResponse.class
        );
        assertThat(resumeResponse.getCount(), is(2));

        // check that the executions are no more paused
        Thread.sleep(100);
        Execution resumedExecution1 = client.toBlocking().retrieve(
            GET("/api/v1/executions/" + pausedExecution1.getId()),
            Execution.class
        );
        Execution resumedExecution2 = client.toBlocking().retrieve(
            GET("/api/v1/executions/" + pausedExecution2.getId()),
            Execution.class
        );
        assertThat(resumedExecution1.getState().isPaused(), is(false));
        assertThat(resumedExecution2.getState().isPaused(), is(false));

        // attempt to resume no more paused executions
        HttpClientResponseException e = assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().retrieve(HttpRequest.POST(
                "/api/v1/executions/resume/by-ids",
                List.of(pausedExecution1.getId(), pausedExecution2.getId())
            ))
        );
        assertThat(e.getStatus(), is(HttpStatus.BAD_REQUEST));
    }

    @Test
    @LoadFlows({"flows/valids/pause.yaml"})
    void resumeByQuery() throws TimeoutException, InterruptedException, QueueException {
        Execution pausedExecution1 = runnerUtils.runOneUntilPaused(null, TESTS_FLOW_NS, "pause");
        Execution pausedExecution2 = runnerUtils.runOneUntilPaused(null, TESTS_FLOW_NS, "pause");

        assertThat(pausedExecution1.getState().isPaused(), is(true));
        assertThat(pausedExecution2.getState().isPaused(), is(true));

        // resume executions
        BulkResponse resumeResponse = client.toBlocking().retrieve(
            HttpRequest.POST("/api/v1/executions/resume/by-query?namespace=" + TESTS_FLOW_NS, null),
            BulkResponse.class
        );
        assertThat(resumeResponse.getCount(), is(2));

        // check that the executions are no more paused
        Thread.sleep(100);
        Execution resumedExecution1 = client.toBlocking().retrieve(
            GET("/api/v1/executions/" + pausedExecution1.getId()),
            Execution.class
        );
        Execution resumedExecution2 = client.toBlocking().retrieve(
            GET("/api/v1/executions/" + pausedExecution2.getId()),
            Execution.class
        );
        assertThat(resumedExecution1.getState().isPaused(), is(false));
        assertThat(resumedExecution2.getState().isPaused(), is(false));

        // attempt to resume no more paused executions
        HttpClientResponseException e = assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().retrieve(HttpRequest.POST(
                "/api/v1/executions/resume/by-query?namespace=" + TESTS_FLOW_NS, null
            ))
        );
        assertThat(e.getStatus(), is(HttpStatus.BAD_REQUEST));
    }

    @Test
    @LoadFlows({"flows/valids/minimal.yaml"})
    void changeStatus() throws TimeoutException, QueueException {
        Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "minimal");
        assertThat(execution.getState().getCurrent(), is(State.Type.SUCCESS));

        // replay executions
        Execution changedStatus = client.toBlocking().retrieve(
            HttpRequest.POST(
                "/api/v1/executions/" + execution.getId() + "/change-status?status=WARNING",
                null
            ),
            Execution.class
        );
        assertThat(changedStatus.getState().getCurrent(), is (State.Type.WARNING));
    }

    @Test
    @SuppressWarnings("unchecked")
    @LoadFlows({"flows/valids/minimal.yaml"})
    void changeStatusByIds() throws TimeoutException, QueueException {
        Execution execution1 = runnerUtils.runOne(null, "io.kestra.tests", "minimal");
        Execution execution2 = runnerUtils.runOne(null, "io.kestra.tests", "minimal");

        assertThat(execution1.getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution2.getState().getCurrent(), is(State.Type.SUCCESS));

        PagedResults<Execution> executions = client.toBlocking().retrieve(
            GET("/api/v1/executions/search"), Argument.of(PagedResults.class, Execution.class)
        );
        assertThat(executions.getTotal(), is(2L));

        // change status of executions
        BulkResponse changeStatus = client.toBlocking().retrieve(
            HttpRequest.POST(
                "/api/v1/executions/change-status/by-ids?newStatus=WARNING",
                List.of(execution1.getId(), execution2.getId())
            ),
            BulkResponse.class
        );
        assertThat(changeStatus.getCount(), is(2));

        executions = client.toBlocking().retrieve(
            GET("/api/v1/executions/search"), Argument.of(PagedResults.class, Execution.class)
        );
        assertThat(executions.getResults().getFirst().getState().getCurrent(), is(State.Type.WARNING));
        assertThat(executions.getResults().get(1).getState().getCurrent(), is(State.Type.WARNING));
    }

    @Test
    @SuppressWarnings("unchecked")
    @LoadFlows({"flows/valids/minimal.yaml"})
    void changeStatusByQuery() throws TimeoutException, QueueException {
        Execution execution1 = runnerUtils.runOne(null, "io.kestra.tests", "minimal");
        Execution execution2 = runnerUtils.runOne(null, "io.kestra.tests", "minimal");

        assertThat(execution1.getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(execution2.getState().getCurrent(), is(State.Type.SUCCESS));

        PagedResults<Execution> executions = client.toBlocking().retrieve(
            GET("/api/v1/executions/search"), Argument.of(PagedResults.class, Execution.class)
        );
        assertThat(executions.getTotal(), is(2L));

        // change status of  executions
        BulkResponse changeStatus = client.toBlocking().retrieve(
            HttpRequest.POST("/api/v1/executions/change-status/by-query?namespace=io.kestra.tests&newStatus=WARNING", null),
            BulkResponse.class
        );
        assertThat(changeStatus.getCount(), is(2));

        executions = client.toBlocking().retrieve(
            GET("/api/v1/executions/search"), Argument.of(PagedResults.class, Execution.class)
        );
        assertThat(executions.getResults().getFirst().getState().getCurrent(), is(State.Type.WARNING));
        assertThat(executions.getResults().get(1).getState().getCurrent(), is(State.Type.WARNING));;
    }

    @Test
    @LoadFlows({"flows/valids/minimal.yaml"})
    void replay() throws TimeoutException, QueueException {
        Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "minimal");

        assertThat(execution.getState().isTerminated(), is(true));

        // replay execution
        Execution replay = client.toBlocking().retrieve(
            HttpRequest.POST(
                "/api/v1/executions/" + execution.getId() + "/replay",
                null
            ),
            Execution.class
        );
        assertThat(replay.getState().getCurrent(), is(State.Type.CREATED));
        assertThat(replay.getOriginalId(), is(execution.getId()));
        assertThat(replay.getLabels(), hasItem(new Label(Label.REPLAY, "true")));

        // load the original execution and check that it has the system.replayed label
        Execution original = client.toBlocking().retrieve(
            HttpRequest.GET("/api/v1/executions/" + execution.getId()),
            Execution.class
        );
        assertThat(original.getLabels(), hasItem(new Label(Label.REPLAYED, "true")));
    }

    @Test
    @LoadFlows({"flows/valids/minimal.yaml"})
    void replayByIds() throws TimeoutException, QueueException {
        Execution execution1 = runnerUtils.runOne(null, "io.kestra.tests", "minimal");
        Execution execution2 = runnerUtils.runOne(null, "io.kestra.tests", "minimal");

        assertThat(execution1.getState().isTerminated(), is(true));
        assertThat(execution2.getState().isTerminated(), is(true));

        PagedResults<?> executions = client.toBlocking().retrieve(
            GET("/api/v1/executions/search"), PagedResults.class
        );
        assertThat(executions.getTotal(), is(2L));

        // replay executions
        BulkResponse replayResponse = client.toBlocking().retrieve(
            HttpRequest.POST(
                "/api/v1/executions/replay/by-ids",
                List.of(execution1.getId(), execution2.getId())
            ),
            BulkResponse.class
        );
        assertThat(replayResponse.getCount(), is(2));

        executions = client.toBlocking().retrieve(
            GET("/api/v1/executions/search"), PagedResults.class
        );
        assertThat(executions.getTotal(), is(4L));
    }

    @Test
    @LoadFlows({"flows/valids/minimal.yaml"})
    void replayByQuery() throws TimeoutException, QueueException {
        Execution execution1 = runnerUtils.runOne(null, "io.kestra.tests", "minimal");
        Execution execution2 = runnerUtils.runOne(null, "io.kestra.tests", "minimal");

        assertThat(execution1.getState().isTerminated(), is(true));
        assertThat(execution2.getState().isTerminated(), is(true));

        PagedResults<?> executions = client.toBlocking().retrieve(
            GET("/api/v1/executions/search"), PagedResults.class
        );
        assertThat(executions.getTotal(), is(2L));

        // replay executions
        BulkResponse resumeResponse = client.toBlocking().retrieve(
            HttpRequest.POST("/api/v1/executions/replay/by-query?namespace=io.kestra.tests", null),
            BulkResponse.class
        );
        assertThat(resumeResponse.getCount(), is(2));

        executions = client.toBlocking().retrieve(
            GET("/api/v1/executions/search"), PagedResults.class
        );
        assertThat(executions.getTotal(), is(4L));
    }

    @RetryingTest(5)
    @LoadFlows({"flows/valids/pause.yaml"})
    void killPaused() throws TimeoutException, InterruptedException, QueueException {
        // Run execution until it is paused
        Execution pausedExecution = runnerUtils.runOneUntilPaused(null, TESTS_FLOW_NS, "pause");
        assertThat(pausedExecution.getState().isPaused(), is(true));

        // resume the execution
        HttpResponse<?> resumeResponse = client.toBlocking().exchange(
            HttpRequest.DELETE("/api/v1/executions/" + pausedExecution.getId() + "/kill"));
        assertThat(resumeResponse.getStatus(), is(HttpStatus.ACCEPTED));

        // check that the execution is no more paused
        Thread.sleep(100);
        Execution execution = client.toBlocking().retrieve(
            GET("/api/v1/executions/" + pausedExecution.getId()),
            Execution.class);
        assertThat(execution.getState().isPaused(), is(false));
    }

    // This test is flaky on CI as the flow may be already SUCCESS when we kill it if CI is super slow
    @RetryingTest(5)
    @LoadFlows({"flows/valids/sleep-long.yml"})
    void kill() throws TimeoutException, InterruptedException, QueueException {
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
        Execution runningExecution = runnerUtils.runOneUntilRunning(null, TESTS_FLOW_NS, "sleep-long");
        assertThat(runningExecution.getState().isRunning(), is(true));

        // kill the execution
        HttpResponse<?> killResponse = client.toBlocking().exchange(
            HttpRequest.DELETE("/api/v1/executions/" + runningExecution.getId() + "/kill"));
        assertThat(killResponse.getStatus(), is(HttpStatus.ACCEPTED));

        // check that the execution has been set to killing then killed
        assertTrue(killedLatch.await(10, TimeUnit.SECONDS));
        receiveExecutions.blockLast();
        assertThat(killedExecution.get().getId(), is(runningExecution.getId()));

        //check that an executionkilled message has been sent
        assertTrue(executionKilledLatch.await(10, TimeUnit.SECONDS));
        receiveKilled.blockLast();
        assertThat(executionKilledId.get(), is(runningExecution.getId()));

        // retrieve the execution from the API and check that the task has been set to killed
        Thread.sleep(1000);
        Execution execution = client.toBlocking().retrieve(
            GET("/api/v1/executions/" + runningExecution.getId()),
            Execution.class);
        assertThat(execution.getState().getCurrent(), is(State.Type.KILLED));
        assertThat(execution.getTaskRunList().size(), is(1));
        assertThat(execution.getTaskRunList().getFirst().getState().getCurrent(), is(State.Type.KILLED));
    }

    @Test
    @LoadFlows({"flows/valids/inputs.yaml"})
    void find() {
        PagedResults<?> executions = client.toBlocking().retrieve(
            GET("/api/v1/executions/search"), PagedResults.class
        );

        assertThat(executions.getTotal(), is(0L));

        triggerInputsFlowExecution(false);

        // + is there to simulate that a space was added (this can be the case from UI autocompletion for eg.)
        executions = client.toBlocking().retrieve(
            GET("/api/v1/executions/search?page=1&size=25&filters[labels][$eq][url]="+ENCODED_URL_LABEL_VALUE), PagedResults.class
        );

        assertThat(executions.getTotal(), is(1L));

        executions = client.toBlocking().retrieve(
            GET("/api/v1/executions/search?page=1&size=25&labels=url:"+ENCODED_URL_LABEL_VALUE), PagedResults.class
        );

        assertThat(executions.getTotal(), is(1L));

        HttpClientResponseException e = assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().retrieve(GET("/api/v1/executions/search?filters[startDate][$eq]=2024-01-07T18:43:11.248%2B01:00&filters[timeRange][$eq]=PT12H"))
        );

        assertThat(e.getStatus(), is(HttpStatus.UNPROCESSABLE_ENTITY));
        assertThat(e.getResponse().getBody(String.class).isPresent(), is(true));
        assertThat(e.getResponse().getBody(String.class).get(), containsString("are mutually exclusive"));

        executions = client.toBlocking().retrieve(
            GET("/api/v1/executions/search?filters[timeRange][$eq]=PT12H"), PagedResults.class
        );

        assertThat(executions.getTotal(), is(1L));

        executions = client.toBlocking().retrieve(
            GET("/api/v1/executions/search?timeRange=PT12H"), PagedResults.class
        );

        assertThat(executions.getTotal(), is(1L));

        e = assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().retrieve(GET("/api/v1/executions/search?filters[timeRange][$eq]=P1Y"))
        );
        assertThat(e.getStatus(), is(HttpStatus.UNPROCESSABLE_ENTITY));

        e = assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().retrieve(GET("/api/v1/executions/search?timeRange=P1Y"))
        );
        assertThat(e.getStatus(), is(HttpStatus.UNPROCESSABLE_ENTITY));

        e = assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().retrieve(GET("/api/v1/executions/search?page=1&size=-1"))
        );

        assertThat(e.getStatus(), is(HttpStatus.UNPROCESSABLE_ENTITY));

        e = assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().retrieve(GET("/api/v1/executions/search?page=0"))
        );

        assertThat(e.getStatus(), is(HttpStatus.UNPROCESSABLE_ENTITY));
    }

    @Test
    @LoadFlows({"flows/valids/minimal.yaml"})
    void delete() throws QueueException, TimeoutException {
        Execution result = runnerUtils.runOne(null, "io.kestra.tests", "minimal");

        var response = client.toBlocking().exchange(HttpRequest.DELETE("/api/v1/executions/" + result.getId()));
        assertThat(response.getStatus(), is(HttpStatus.NO_CONTENT));

        var notFound = assertThrows(HttpClientResponseException.class, () -> client.toBlocking().exchange(HttpRequest.DELETE("/api/v1/executions/notfound")));
        assertThat(notFound.getStatus(), is(HttpStatus.NOT_FOUND));
    }

    @Test
    @LoadFlows({"flows/valids/minimal.yaml"})
    void deleteByIds() throws TimeoutException, QueueException {
        Execution result1 = runnerUtils.runOne(null, "io.kestra.tests", "minimal");
        Execution result2 = runnerUtils.runOne(null, "io.kestra.tests", "minimal");
        Execution result3 = runnerUtils.runOne(null, "io.kestra.tests", "minimal");

        BulkResponse response = client.toBlocking().retrieve(
            HttpRequest.DELETE("/api/v1/executions/by-ids", List.of(result1.getId(), result2.getId(), result3.getId())),
            BulkResponse.class
        );
        assertThat(response.getCount(), is(3));
    }

    @Test
    @LoadFlows({"flows/valids/minimal.yaml"})
    void deleteByQuery() throws TimeoutException, QueueException {
        Execution result1 = runnerUtils.runOne(null, "io.kestra.tests", "minimal");
        Execution result2 = runnerUtils.runOne(null, "io.kestra.tests", "minimal");
        Execution result3 = runnerUtils.runOne(null, "io.kestra.tests", "minimal");

        BulkResponse response = client.toBlocking().retrieve(
            HttpRequest.DELETE("/api/v1/executions/by-query?namespace=" + result1.getNamespace()),
            BulkResponse.class
        );
        assertThat(response.getCount(), is(3));
    }

    @Test
    @LoadFlows({"flows/valids/minimal.yaml"})
    void setLabels() throws QueueException, TimeoutException {
        // update label on a terminated execution
        Execution result = runnerUtils.runOne(null, "io.kestra.tests", "minimal");
        assertThat(result.getState().getCurrent(), is(State.Type.SUCCESS));
        Execution response = client.toBlocking().retrieve(
            HttpRequest.POST("/api/v1/executions/" + result.getId() + "/labels", List.of(new Label("key", "value"))),
            Execution.class
        );
        assertThat(response.getLabels(), hasItem(new Label("key", "value")));

        // update label on a not found execution
        var exception = assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().exchange(HttpRequest.POST("/api/v1/executions/notfound/labels", List.of(new Label("key", "value"))))
        );
        assertThat(exception.getStatus(), is(HttpStatus.NOT_FOUND));

        exception = assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().exchange(HttpRequest.POST("/api/v1/executions/" + result.getId() + "/labels", List.of(new Label(null, null))))
        );
        assertThat(exception.getStatus(), is(HttpStatus.UNPROCESSABLE_ENTITY));
    }

    @Test
    @LoadFlows({"flows/valids/minimal.yaml"})
    void setLabelsByIds() throws TimeoutException, QueueException {
        Execution result1 = runnerUtils.runOne(null, "io.kestra.tests", "minimal");
        Execution result2 = runnerUtils.runOne(null, "io.kestra.tests", "minimal");
        Execution result3 = runnerUtils.runOne(null, "io.kestra.tests", "minimal");

        BulkResponse response = client.toBlocking().retrieve(
            HttpRequest.POST("/api/v1/executions/labels/by-ids",
                new ExecutionController.SetLabelsByIdsRequest(List.of(result1.getId(), result2.getId(), result3.getId()), List.of(new Label("key", "value")))
            ),
            BulkResponse.class
        );

        assertThat(response.getCount(), is(3));
    }

    @Test
    @LoadFlows({"flows/valids/minimal.yaml"})
    void setLabelsByQuery() throws TimeoutException, QueueException {
        Execution result1 = runnerUtils.runOne(null, "io.kestra.tests", "minimal");
        Execution result2 = runnerUtils.runOne(null, "io.kestra.tests", "minimal");
        Execution result3 = runnerUtils.runOne(null, "io.kestra.tests", "minimal");

        BulkResponse response = client.toBlocking().retrieve(
            HttpRequest.POST("/api/v1/executions/labels/by-query?namespace=" + result1.getNamespace(),
                List.of(new Label("key", "value"))
            ),
            BulkResponse.class
        );

        assertThat(response.getCount(), is(3));

        var exception = assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().exchange(HttpRequest.POST(
                "/api/v1/executions/labels/by-query?namespace=" + result1.getNamespace(),
                List.of(new Label(null, null)))
            )
        );
        assertThat(exception.getStatus(), is(HttpStatus.UNPROCESSABLE_ENTITY));
    }

    @Test
    @LoadFlows({"flows/valids/sleep.yml",
        "flows/valids/minimal.yaml"})
    void shouldPauseARunningFlow() throws QueueException, TimeoutException {
        Execution result = runnerUtils.runOneUntilRunning(null, "io.kestra.tests", "sleep");

        var response = client.toBlocking().exchange(HttpRequest.POST("/api/v1/executions/" + result.getId() + "/pause", null));
        assertThat(response.getStatus(), is(HttpStatus.OK));

        // resume it, it should then go to completion
        response = client.toBlocking().exchange(HttpRequest.POST("/api/v1/executions/" + result.getId() + "/resume", null));
        assertThat(response.getStatus(), is(HttpStatus.NO_CONTENT));

        var notFound = assertThrows(HttpClientResponseException.class, () -> client.toBlocking().exchange(HttpRequest.POST("/api/v1/executions/notfound/pause", null)));
        assertThat(notFound.getStatus(), is(HttpStatus.NOT_FOUND));

        // pausing an already completed flow will result in errors
        Execution completed = runnerUtils.runOne(null, "io.kestra.tests", "minimal");

        var notRunning = assertThrows(HttpClientResponseException.class, () -> client.toBlocking().exchange(HttpRequest.POST("/api/v1/executions/" + completed.getId() + "/pause", null)));
        assertThat(notRunning.getStatus(), is(HttpStatus.UNPROCESSABLE_ENTITY));
    }

    @Test
    @LoadFlows({"flows/valids/sleep.yml"})
    void shouldPauseByIdsRunningFlows() throws TimeoutException, QueueException {
        Execution result1 = runnerUtils.runOneUntilRunning(null, "io.kestra.tests", "sleep");
        Execution result2 = runnerUtils.runOneUntilRunning(null, "io.kestra.tests", "sleep");
        Execution result3 = runnerUtils.runOneUntilRunning(null, "io.kestra.tests", "sleep");

        BulkResponse response = client.toBlocking().retrieve(
            HttpRequest.POST("/api/v1/executions/pause/by-ids", List.of(result1.getId(), result2.getId(), result3.getId())),
            BulkResponse.class
        );
        assertThat(response.getCount(), is(3));
    }

    @Test
    @LoadFlows({"flows/valids/sleep.yml"})
    void shouldPauseByQueryRunningFlows() throws TimeoutException, QueueException {
        Execution result1 = runnerUtils.runOneUntilRunning(null, "io.kestra.tests", "sleep");
        Execution result2 = runnerUtils.runOneUntilRunning(null, "io.kestra.tests", "sleep");
        Execution result3 = runnerUtils.runOneUntilRunning(null, "io.kestra.tests", "sleep");

        BulkResponse response = client.toBlocking().retrieve(
            HttpRequest.POST("/api/v1/executions/pause/by-query?namespace=" + result1.getNamespace(), null),
            BulkResponse.class
        );
        assertThat(response.getCount(), is(3));
    }

    @Test
    @LoadFlows({"flows/valids/minimal.yaml"})
    void shouldRefuseSystemLabelsWhenUpdatingLabels() throws QueueException, TimeoutException {
        // update label on a terminated execution
        Execution result = runnerUtils.runOne(null, "io.kestra.tests", "minimal");
        assertThat(result.getState().getCurrent(), is(State.Type.SUCCESS));

        var error = assertThrows(HttpClientResponseException.class, () -> client.toBlocking().retrieve(
                HttpRequest.POST("/api/v1/executions/" + result.getId() + "/labels", List.of(new Label("system.label", "value"))),
                Execution.class
            )
        );

        assertThat(error.getStatus(), is(HttpStatus.UNPROCESSABLE_ENTITY));
    }

    @Test
    @LoadFlows({"flows/valids/flow-concurrency-queue.yml",
        "flows/valids/minimal.yaml"})
    void shouldUnqueueAQueuedFlow() throws QueueException, TimeoutException {
        // run a first flow so the second is queued
        runnerUtils.runOneUntilRunning(null, "io.kestra.tests", "flow-concurrency-queue");
        Execution result = runUntilQueued("io.kestra.tests", "flow-concurrency-queue");

        var response = client.toBlocking().exchange(HttpRequest.POST("/api/v1/executions/" + result.getId() + "/unqueue", null));
        assertThat(response.getStatus(), is(HttpStatus.OK));

        var notFound = assertThrows(HttpClientResponseException.class, () -> client.toBlocking().exchange(HttpRequest.POST("/api/v1/executions/notfound/unqueue", null)));
        assertThat(notFound.getStatus(), is(HttpStatus.NOT_FOUND));

        // pausing an already completed flow will result in errors
        Execution completed = runnerUtils.runOne(null, "io.kestra.tests", "minimal");

        var notRunning = assertThrows(HttpClientResponseException.class, () -> client.toBlocking().exchange(HttpRequest.POST("/api/v1/executions/" + completed.getId() + "/unqueue", null)));
        assertThat(notRunning.getStatus(), is(HttpStatus.UNPROCESSABLE_ENTITY));
    }

    @Test
    @LoadFlows({"flows/valids/flow-concurrency-queue.yml"})
    void shouldUnqueueByIdsQueuedFlows() throws TimeoutException, QueueException {
        // run a first flow so the others are queued
        runnerUtils.runOneUntilRunning(null, "io.kestra.tests", "flow-concurrency-queue");
        Execution result1 = runUntilQueued("io.kestra.tests", "flow-concurrency-queue");
        Execution result2 = runUntilQueued("io.kestra.tests", "flow-concurrency-queue");
        Execution result3 = runUntilQueued("io.kestra.tests", "flow-concurrency-queue");

        BulkResponse response = client.toBlocking().retrieve(
            HttpRequest.POST("/api/v1/executions/unqueue/by-ids", List.of(result1.getId(), result2.getId(), result3.getId())),
            BulkResponse.class
        );
        assertThat(response.getCount(), is(3));
    }

    @Test
    @LoadFlows({"flows/valids/flow-concurrency-queue.yml"})
    void shouldForceRunAQueuedFlow() throws QueueException, TimeoutException {
        // run a first flow so the second is queued
        runnerUtils.runOneUntilRunning(null, "io.kestra.tests", "flow-concurrency-queue");
        Execution result = runUntilQueued("io.kestra.tests", "flow-concurrency-queue");

        var response = client.toBlocking().exchange(HttpRequest.POST("/api/v1/executions/" + result.getId() + "/force-run", null));
        assertThat(response.getStatus(), is(HttpStatus.OK));
        Optional<Execution> forcedRun = executionRepositoryInterface.findById(null, result.getId());
        assertThat(forcedRun.isPresent(), is(true));
        assertThat(forcedRun.get().getState().getCurrent(), not(State.Type.QUEUED));
    }

    @Test
    @LoadFlows({"flows/valids/minimal.yaml"})
    void shouldFailToForceRunNotFoundOrTerminatedExecutions() throws QueueException, TimeoutException {
        var notFound = assertThrows(HttpClientResponseException.class, () -> client.toBlocking().exchange(HttpRequest.POST("/api/v1/executions/notfound/force-run", null)));
        assertThat(notFound.getStatus(), is(HttpStatus.NOT_FOUND));

        // force run an already completed flow will result in errors
        Execution completed = runnerUtils.runOne(null, "io.kestra.tests", "minimal");

        var notRunning = assertThrows(HttpClientResponseException.class, () -> client.toBlocking().exchange(HttpRequest.POST("/api/v1/executions/" + completed.getId() + "/force-run", null)));
        assertThat(notRunning.getStatus(), is(HttpStatus.UNPROCESSABLE_ENTITY));
    }

    @Test
    @LoadFlows({"flows/valids/minimal.yaml"})
    void shouldForceRunACreatedFlow() throws QueueException, TimeoutException {
        Execution result = runUntilCreated("io.kestra.tests", "minimal");

        var response = client.toBlocking().exchange(HttpRequest.POST("/api/v1/executions/" + result.getId() + "/force-run", null));
        assertThat(response.getStatus(), is(HttpStatus.OK));
        Optional<Execution> forcedRun = executionRepositoryInterface.findById(null, result.getId());
        assertThat(forcedRun.isPresent(), is(true));
        assertThat(forcedRun.get().getState().getCurrent(), not(State.Type.CREATED));
    }

    @Test
    @LoadFlows({"flows/valids/pause.yaml"})
    void shouldForceRunAPausedFlow() throws QueueException, TimeoutException {
        // Run execution until it is paused
        Execution result = runnerUtils.runOneUntilPaused(null, TESTS_FLOW_NS, "pause");

        var response = client.toBlocking().exchange(HttpRequest.POST("/api/v1/executions/" + result.getId() + "/force-run", null));
        assertThat(response.getStatus(), is(HttpStatus.OK));
        Optional<Execution> forcedRun = executionRepositoryInterface.findById(null, result.getId());
        assertThat(forcedRun.isPresent(), is(true));
        assertThat(forcedRun.get().getState().getCurrent(), not(State.Type.PAUSED));
    }


    @Test
    @LoadFlows({"flows/valids/sleep.yml"})
    void shouldForceRunARunningFlow() throws QueueException, TimeoutException {
        // Run execution until it is paused
        Execution result = runnerUtils.runOneUntilRunning(null, TESTS_FLOW_NS, "sleep");

        var response = client.toBlocking().exchange(HttpRequest.POST("/api/v1/executions/" + result.getId() + "/force-run", null));
        assertThat(response.getStatus(), is(HttpStatus.OK));
        Optional<Execution> forcedRun = executionRepositoryInterface.findById(null, result.getId());
        assertThat(forcedRun.isPresent(), is(true));
        assertThat(forcedRun.get().getState().getCurrent(), not(State.Type.CREATED));
    }

    @Test
    @LoadFlows({"flows/valids/sleep.yml"})
    void shouldForRunByIdsFlows() throws TimeoutException, QueueException {
        Execution result1 =  runnerUtils.runOneUntilRunning(null, TESTS_FLOW_NS, "sleep");
        Execution result2 =  runnerUtils.runOneUntilRunning(null, TESTS_FLOW_NS, "sleep");
        Execution result3 =  runnerUtils.runOneUntilRunning(null, TESTS_FLOW_NS, "sleep");

        BulkResponse response = client.toBlocking().retrieve(
            HttpRequest.POST("/api/v1/executions/force-run/by-ids", List.of(result1.getId(), result2.getId(), result3.getId())),
            BulkResponse.class
        );
        assertThat(response.getCount(), is(3));
    }

    @Test
    @LoadFlows({"flows/runners/sleep_medium.yml"})
    void shouldForRunByQueryFlows() throws TimeoutException, QueueException {
        String namespace = "io.kestra.forcerun.tests";
        runnerUtils.runOneUntilRunning(null, namespace, "sleep_medium");
        runnerUtils.runOneUntilRunning(null, namespace, "sleep_medium");
        runnerUtils.runOneUntilRunning(null, namespace, "sleep_medium");

        BulkResponse response = client.toBlocking().retrieve(
            HttpRequest.POST("/api/v1/executions/force-run/by-query?namespace=" + namespace, null),
            BulkResponse.class
        );
        assertThat(response.getCount(), is(3));
    }

    @Test
    @ExecuteFlow("flows/valids/minimal.yaml")
    void shouldEvalPebbleExpression(Execution execution) {
        ExecutionController.EvalResult evalResult = client.toBlocking().retrieve(
            HttpRequest
                .POST("/api/v1/executions/" + execution.getId() + "/eval/" + execution.getTaskRunList().getFirst().getId(), "{{ taskrun.id }}")
                .contentType(MediaType.TEXT_PLAIN),
            ExecutionController.EvalResult.class
        );
        assertThat(evalResult.getResult(), notNullValue());
    }

    @Test
    @ExecuteFlow("flows/valids/minimal.yaml")
    void shouldMaskSecretWhenEvalPebbleExpression(Execution execution) {
        ExecutionController.EvalResult evalResult = client.toBlocking().retrieve(
            HttpRequest
                .POST("/api/v1/executions/" + execution.getId() + "/eval/" + execution.getTaskRunList().getFirst().getId(), "{{ secret('KEY') }}")
                .contentType(MediaType.TEXT_PLAIN),
            ExecutionController.EvalResult.class
        );
        assertThat(evalResult.getResult(), is("******"));
    }

    private ExecutionController.EvalResult eval(Execution execution, String expression, int index) {
        return client.toBlocking().retrieve(
            HttpRequest
                .POST(
                    "/api/v1/executions/" + execution.getId() + "/eval/" + execution.getTaskRunList().get(index).getId(),
                    expression
                )
                .contentType(MediaType.TEXT_PLAIN_TYPE),
            Argument.of(ExecutionController.EvalResult.class)
        );
    }


    private Execution triggerExecution(String namespace, String flowId, MultipartBody requestBody, Boolean wait) {
        return client.toBlocking().retrieve(
            HttpRequest
                .POST("/api/v1/executions/" + namespace + "/" + flowId + "?labels=a:label-1&labels=b:label-2&labels=url:" + ENCODED_URL_LABEL_VALUE + (wait ? "&wait=true" : ""), requestBody)
                .contentType(MediaType.MULTIPART_FORM_DATA_TYPE),
            Execution.class
        );
    }

    private Execution triggerInputsFlowExecution(Boolean wait) {
        MultipartBody requestBody = createInputsFlowBody();

        return triggerExecution(TESTS_FLOW_NS, "inputs", requestBody, wait);
    }

    private MultipartBody createInputsFlowBody() {
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

    private Execution runUntilCreated(String namespace, String flowId) throws TimeoutException, QueueException {
        return runUntilState(namespace, flowId, State.Type.CREATED);
    }

    private Execution runUntilState(String namespace, String flowId, State.Type state) throws TimeoutException, QueueException {
        Flow flow = flowRepositoryInterface.findById(null, namespace, flowId).orElseThrow();
        Execution execution = Execution.newExecution(flow, null);
        return runnerUtils.awaitExecution(
            it -> it.getState().getCurrent() == state,
            throwRunnable(() -> this.executionQueue.emit(execution)),
            Duration.ofSeconds(1));
    }

    @Test
    @LoadFlows({"flows/valids/minimal.yaml"})
    void shouldRemoveLabelsFromExecutionPreservingSystemLabels() throws QueueException, TimeoutException {
        // Run initial execution
        Execution result = runnerUtils.runOne(null, "io.kestra.tests", "minimal");
        assertThat(result.getState().getCurrent(), is(State.Type.SUCCESS));

        Execution executionWithLabels = client.toBlocking().retrieve(
                HttpRequest.POST("/api/v1/executions/" + result.getId() + "/labels", List.of(
                                new Label("flow-label-1", "flow-label-1"),
                                new Label("flow-label-2", "flow-label-2"))),
                Execution.class
        );

        List<Label> allLabelsFromExecution = executionWithLabels.getLabels();
        assertLabelCounts(allLabelsFromExecution, 2, greaterThan(0));

        // Update with only one custom label
        Execution executionWithOneLabel = client.toBlocking().retrieve(
                HttpRequest.POST("/api/v1/executions/" + result.getId() + "/labels",
                        List.of(new Label("flow-label-1", "flow-label-1"))),
                Execution.class
        );

        allLabelsFromExecution = executionWithOneLabel.getLabels();
        assertLabelCounts(allLabelsFromExecution, 1, greaterThan(0));

        // Remove all custom labels
        Execution executionWithNoLabels = client.toBlocking().retrieve(
                HttpRequest.POST("/api/v1/executions/" + result.getId() + "/labels", Collections.emptyList()),
                Execution.class
        );

        allLabelsFromExecution = executionWithNoLabels.getLabels();
        assertLabelCounts(allLabelsFromExecution, 0, greaterThan(0));
    }

    @Test
    @LoadFlows({"flows/valids/minimal.yaml"})
    void shouldNotAllowAddingSystemLabels() throws QueueException, TimeoutException {
        Execution result = runnerUtils.runOne(null, "io.kestra.tests", "minimal");
        assertThat(result.getState().getCurrent(), is(State.Type.SUCCESS));

        List<Label> systemLabels = List.of(new Label("system.key", "system-value"));
        HttpClientResponseException e = assertThrows(HttpClientResponseException.class, () -> client.toBlocking().retrieve(
                HttpRequest.POST("/api/v1/executions/" + result.getId() + "/labels", systemLabels),
                Execution.class
        ));

        assertThat(e.getStatus(), is(HttpStatus.UNPROCESSABLE_ENTITY));
        assertThat(e.getMessage(), containsString("System labels can only be set by Kestra itself"));
    }

    private List<Label> getNonSystemLabels(List<Label> labels) {
        return labels == null ? List.of() :
            labels.stream()
                .filter(l -> !l.key().startsWith(Label.SYSTEM_PREFIX))
                .collect(Collectors.toList());
    }

    private List<Label> getSystemLabels(List<Label> allLabelsFromExecution) {
        return allLabelsFromExecution.stream()
                .filter(label -> label.key().startsWith(Label.SYSTEM_PREFIX))
                .collect(Collectors.toList());
    }

    private void assertLabelCounts(List<Label> allLabels, int expectedCustomCount, Matcher<Integer> expectedSystemMatcher) {
        List<Label> customLabels = getNonSystemLabels(allLabels);
        List<Label> systemLabels = getSystemLabels(allLabels);
        assertThat("Custom label count", customLabels, hasSize(expectedCustomCount));
        assertThat("System label count", systemLabels, hasSize(expectedSystemMatcher));
    }
}
