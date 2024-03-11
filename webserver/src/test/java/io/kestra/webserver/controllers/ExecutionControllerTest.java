package io.kestra.webserver.controllers;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.models.Label;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionKilled;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.storage.FileMetas;
import io.kestra.core.models.triggers.types.Webhook;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.runners.InputsTest;
import io.kestra.core.utils.Await;
import io.kestra.core.utils.IdUtils;
import io.kestra.webserver.controllers.h2.JdbcH2ControllerTest;
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
import io.micronaut.runtime.server.EmbeddedServer;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junitpioneer.jupiter.RetryingTest;

import java.io.File;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.IntStream;

import static io.kestra.core.utils.Rethrow.throwRunnable;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

class ExecutionControllerTest extends JdbcH2ControllerTest {
    public static final String URL_LABEL_VALUE = "https://some-url.com";
    public static final String ENCODED_URL_LABEL_VALUE = URL_LABEL_VALUE.replace("/", URLEncoder.encode("/", StandardCharsets.UTF_8));
    @Inject
    EmbeddedServer embeddedServer;
    @Inject
    ExecutionController executionController;

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

    public static final String TESTS_FLOW_NS = "io.kestra.tests";

    public static Map<String, Object> inputs = ImmutableMap.<String, Object>builder()
        .put("failed", "NO")
        .put("string", "myString")
        .put("enum", "ENUM_VALUE")
        .put("int", "42")
        .put("float", "42.42")
        .put("instant", "2019-10-06T18:27:49Z")
        .put("file", Objects.requireNonNull(InputsTest.class.getClassLoader().getResource("data/hello.txt")).getPath())
        .put("secret", "secret")
        .build();

    @Test
    void getNotFound() {
        HttpClientResponseException e = assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().retrieve(HttpRequest.GET("/api/v1/executions/exec_id_not_found"))
        );

        assertThat(e.getStatus(), is(HttpStatus.NOT_FOUND));
    }

    private Execution triggerExecution(String namespace, String flowId, MultipartBody requestBody, Boolean wait) {
        return client.toBlocking().retrieve(
            HttpRequest
                .POST("/api/v1/executions/" + namespace + "/" + flowId + "?labels=a:label-1,b:label-2,url:" + ENCODED_URL_LABEL_VALUE + (wait ? "&wait=true" : ""), requestBody)
                .contentType(MediaType.MULTIPART_FORM_DATA_TYPE),
            Execution.class
        );
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
            .build();
    }

    private Execution triggerInputsFlowExecution(Boolean wait) {
        MultipartBody requestBody = createInputsFlowBody();

        return triggerExecution(TESTS_FLOW_NS, "inputs", requestBody, wait);
    }

    @Test
    void trigger() {
        Execution result = triggerInputsFlowExecution(false);

        assertThat(result.getState().getCurrent(), is(State.Type.CREATED));
        assertThat(result.getFlowId(), is("inputs"));
        assertThat(result.getInputs().get("float"), is(42.42));
        assertThat(result.getInputs().get("file").toString(), startsWith("kestra:///io/kestra/tests/inputs/executions/"));
        assertThat(result.getInputs().get("file").toString(), startsWith("kestra:///io/kestra/tests/inputs/executions/"));
        assertThat(result.getInputs().containsKey("bool"), is(true));
        assertThat(result.getInputs().get("bool"), nullValue());
        assertThat(result.getLabels().size(), is(5));
        assertThat(result.getLabels().get(0), is(new Label("flow-label-1", "flow-label-1")));
        assertThat(result.getLabels().get(1), is(new Label("flow-label-2", "flow-label-2")));
        assertThat(result.getLabels().get(2), is(new Label("a", "label-1")));
        assertThat(result.getLabels().get(3), is(new Label("b", "label-2")));
        assertThat(result.getLabels().get(4), is(new Label("url", URL_LABEL_VALUE)));
    }

    @Test
    void triggerAndWait() {
        Execution result = triggerInputsFlowExecution(true);

        assertThat(result.getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(result.getTaskRunList().size(), is(6));
    }

    @Test
    void get() {
        Execution result = triggerInputsFlowExecution(false);

        // Get the triggered execution by execution id
        Execution foundExecution = client.retrieve(
            HttpRequest.GET("/api/v1/executions/" + result.getId()),
            Execution.class
        ).block();

        assertThat(foundExecution.getId(), is(result.getId()));
        assertThat(foundExecution.getNamespace(), is(result.getNamespace()));
    }

    @SuppressWarnings("unchecked")
    @Test
    void findByFlowId() {
        String namespace = "io.kestra.tests.minimal.bis";
        String flowId = "minimal-bis";

        PagedResults<Execution> executionsBefore = client.toBlocking().retrieve(
            HttpRequest.GET("/api/v1/executions?namespace=" + namespace + "&flowId=" + flowId),
            Argument.of(PagedResults.class, Execution.class)
        );

        assertThat(executionsBefore.getTotal(), is(0L));

        triggerExecution(namespace, flowId, MultipartBody.builder().addPart("string", "myString").build(), false);

        // Wait for execution indexation
        Await.until(() -> executionRepositoryInterface.findByFlowId(null, namespace, flowId, Pageable.from(1)).size() == 1);
        PagedResults<Execution> executionsAfter = client.toBlocking().retrieve(
            HttpRequest.GET("/api/v1/executions?namespace=" + namespace + "&flowId=" + flowId),
            Argument.of(PagedResults.class, Execution.class)
        );

        assertThat(executionsAfter.getTotal(), is(1L));
    }

    @Test
    void triggerAndFollow() {
        Execution result = triggerInputsFlowExecution(false);

        List<Event<Execution>> results = sseClient
            .eventStream("/api/v1/executions/" + result.getId() + "/follow", Execution.class)
            .collectList()
            .block();

        assertThat(results.size(), is(greaterThan(0)));
        assertThat(results.get(results.size() - 1).getData().getState().getCurrent(), is(State.Type.SUCCESS));
    }

    private ExecutionController.EvalResult eval(Execution execution, String expression, int index) {
        ExecutionController.EvalResult eval = client.toBlocking().retrieve(
            HttpRequest
                .POST(
                    "/api/v1/executions/" + execution.getId() + "/eval/" + execution.getTaskRunList().get(index).getId(),
                    expression
                )
                .contentType(MediaType.TEXT_PLAIN_TYPE),
            Argument.of(ExecutionController.EvalResult.class)
        );

        return eval;
    }

    @Test
    void eval() throws TimeoutException {
        Execution execution = runnerUtils.runOne(null, "io.kestra.tests", "each-sequential-nested");

        ExecutionController.EvalResult result = this.eval(execution, "my simple string", 0);
        assertThat(result.getResult(), is("my simple string"));

        result = this.eval(execution, "{{ taskrun.id }}", 0);
        assertThat(result.getResult(), is(execution.getTaskRunList().get(0).getId()));

        result = this.eval(execution, "{{ outputs['1-1_return'][taskrun.value].value }}", 21);
        assertThat(result.getResult(), containsString("1-1_return"));

        result = this.eval(execution, "{{ missing }}", 21);
        assertThat(result.getResult(), is(nullValue()));
        assertThat(result.getError(), containsString("Missing variable: 'missing' on '{{ missing }}' at line 1"));
        assertThat(result.getStackTrace(), containsString("Missing variable: 'missing' on '{{ missing }}' at line 1"));
    }

    @Test
    void restartFromUnknownTaskId() throws TimeoutException {
        final String flowId = "restart_with_inputs";
        final String referenceTaskId = "unknownTaskId";

        // Run execution until it ends
        Execution parentExecution = runnerUtils.runOne(null, TESTS_FLOW_NS, flowId, null, (flow, execution1) -> runnerUtils.typedInputs(flow, execution1, inputs));

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
    void restartWithNoFailure() throws TimeoutException {
        final String flowId = "restart_with_inputs";

        // Run execution until it ends
        Execution parentExecution = runnerUtils.runOne(null, TESTS_FLOW_NS, flowId, null, (flow, execution1) -> runnerUtils.typedInputs(flow, execution1, inputs));

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
    void restartFromTaskId() throws Exception {
        final String flowId = "restart_with_inputs";
        final String referenceTaskId = "instant";

        // Run execution until it ends
        Execution parentExecution = runnerUtils.runOne(null, TESTS_FLOW_NS, flowId, null, (flow, execution1) -> runnerUtils.typedInputs(flow, execution1, inputs));

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
    void restartFromTaskIdWithSequential() throws Exception {
        final String flowId = "restart-each";
        final String referenceTaskId = "2_end";

        // Run execution until it ends
        Execution parentExecution = runnerUtils.runOne(null, TESTS_FLOW_NS, flowId, null,
            (flow, execution1) -> runnerUtils.typedInputs(flow, execution1, inputs));

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
    void restartFromLastFailed() throws TimeoutException {
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

        assertThat(finishedRestartedExecution.getTaskRunList().get(0).getAttempts().size(), is(1));
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
    void restartFromLastFailedWithPause() throws TimeoutException {
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

        assertThat(finishedRestartedExecution.getTaskRunList().get(0).getAttempts().size(), is(1));
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
    void downloadFile() throws TimeoutException {
        Execution execution = runnerUtils.runOne(null, TESTS_FLOW_NS, "inputs", null, (flow, execution1) -> runnerUtils.typedInputs(flow, execution1, inputs));
        assertThat(execution.getTaskRunList(), hasSize(6));

        String path = (String) execution.getInputs().get("file");

        String file = client.toBlocking().retrieve(
            HttpRequest.GET("/api/v1/executions/" + execution.getId() + "/file?path=" + path),
            String.class
        );

        assertThat(file, is("hello"));

        FileMetas metas = client.retrieve(
            HttpRequest.GET("/api/v1/executions/" + execution.getId() + "/file/metas?path=" + path),
            FileMetas.class
        ).block();

        assertThat(metas.getSize(), is(5L));

        String newExecutionId = IdUtils.create();

        HttpClientResponseException e = assertThrows(HttpClientResponseException.class, () -> client.toBlocking().retrieve(
            HttpRequest.GET("/api/v1/executions/" + execution.getId() + "/file?path=" + path.replace(execution.getId(),
                newExecutionId
            )),
            String.class
        ));

        // we redirect to good execution (that doesn't exist, so 404)
        assertThat(e.getStatus().getCode(), is(404));
        assertThat(e.getMessage(), containsString("execution id '" +  newExecutionId + "'"));
    }

    @Test
    void filePreview() throws TimeoutException {
        Execution defaultExecution = runnerUtils.runOne(null, TESTS_FLOW_NS, "inputs", null, (flow, execution1) -> runnerUtils.typedInputs(flow, execution1, inputs));
        assertThat(defaultExecution.getTaskRunList(), hasSize(6));

        String defaultPath = (String) defaultExecution.getInputs().get("file");

        String defaultFile = client.toBlocking().retrieve(
            HttpRequest.GET("/api/v1/executions/" + defaultExecution.getId() + "/file/preview?path=" + defaultPath),
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
            .build();

        Execution latin1Execution = runnerUtils.runOne(null, TESTS_FLOW_NS, "inputs", null, (flow, execution1) -> runnerUtils.typedInputs(flow, execution1, latin1FileInputs));
        assertThat(latin1Execution.getTaskRunList(), hasSize(6));

        String latin1Path = (String) latin1Execution.getInputs().get("file");

        String latin1File = client.toBlocking().retrieve(
            HttpRequest.GET("/api/v1/executions/" + latin1Execution.getId() + "/file/preview?path=" + latin1Path + "&encoding=ISO-8859-1"),
            String.class
        );

        assertThat(latin1File, containsString("Düsseldorf"));

        HttpClientResponseException e = assertThrows(HttpClientResponseException.class, () -> client.toBlocking().retrieve(
            HttpRequest.GET("/api/v1/executions/" + latin1Execution.getId() + "/file/preview?path=" + latin1Path + "&encoding=foo"),
            String.class
        ));

        assertThat(e.getStatus(), is(HttpStatus.UNPROCESSABLE_ENTITY));
        assertThat(e.getMessage(), containsString("using encoding 'foo'"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void webhook() {
        Flow webhook = flowRepositoryInterface.findById(null, TESTS_FLOW_NS, "webhook").orElseThrow();
        String key = ((Webhook) webhook.getTriggers().get(0)).getKey();

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
        assertThat(execution.getLabels().get(0), is(new Label("flow-label-1", "flow-label-1")));
        assertThat(execution.getLabels().get(1), is(new Label("flow-label-2", "flow-label-2")));

        execution = client.toBlocking().retrieve(
            HttpRequest
                .PUT(
                    "/api/v1/executions/webhook/" + TESTS_FLOW_NS + "/webhook/" + key,
                    Collections.singletonList(ImmutableMap.of("a", 1, "b", true))
                ),
            Execution.class
        );

        assertThat(((List<Map<String, Object>>) execution.getTrigger().getVariables().get("body")).get(0).get("a"), is(1));
        assertThat(((List<Map<String, Object>>) execution.getTrigger().getVariables().get("body")).get(0).get("b"), is(true));

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
            HttpRequest
                .GET("/api/v1/executions/webhook/" + TESTS_FLOW_NS + "/webhook/" + key),
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
    void webhookDynamicKey() {
        Execution execution = client.toBlocking().retrieve(
            HttpRequest
                .GET(
                    "/api/v1/executions/webhook/" + TESTS_FLOW_NS + "/webhook-dynamic-key/webhook-dynamic-key"
                ),
            Execution.class
        );

        assertThat(execution, notNullValue());
        assertThat(execution.getId(), notNullValue());
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "SECRET_WEBHOOK_KEY", matches = ".*")
    void webhookDynamicKeyFromASecret() {
        Execution execution = client.toBlocking().retrieve(
            HttpRequest
                .GET(
                    "/api/v1/executions/webhook/" + TESTS_FLOW_NS + "/webhook-secret-key/secretKey"
                ),
            Execution.class
        );

        assertThat(execution, notNullValue());
        assertThat(execution.getId(), notNullValue());
    }

    @Test
    void webhookWithCondition() {
        record Hello(String hello) {}

        Execution execution = client.toBlocking().retrieve(
            HttpRequest
                .POST(
                    "/api/v1/executions/webhook/" + TESTS_FLOW_NS + "/webhook-with-condition/webhookKey",
                    new Hello("world")
                ),
            Execution.class
        );

        assertThat(execution, notNullValue());
        assertThat(execution.getId(), notNullValue());

        HttpResponse<Execution> response = client.toBlocking().exchange(
            HttpRequest
                .POST(
                    "/api/v1/executions/webhook/" + TESTS_FLOW_NS + "/webhook-with-condition/webhookKey",
                    new Hello("webhook")
                ),
            Execution.class
        );
        assertThat(response.getStatus(), is(HttpStatus.NO_CONTENT));
        assertThat(response.body(), nullValue());
    }

    @Test
    void resumePaused() throws TimeoutException, InterruptedException {
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
            HttpRequest.GET("/api/v1/executions/" + pausedExecution.getId()),
            Execution.class);
        assertThat(execution.getState().isPaused(), is(false));
    }

    @Test
    void resumeByIds() throws TimeoutException, InterruptedException {
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
            HttpRequest.GET("/api/v1/executions/" + pausedExecution1.getId()),
            Execution.class
        );
        Execution resumedExecution2 = client.toBlocking().retrieve(
            HttpRequest.GET("/api/v1/executions/" + pausedExecution2.getId()),
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
    void resumeByQuery() throws TimeoutException, InterruptedException {
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
            HttpRequest.GET("/api/v1/executions/" + pausedExecution1.getId()),
            Execution.class
        );
        Execution resumedExecution2 = client.toBlocking().retrieve(
            HttpRequest.GET("/api/v1/executions/" + pausedExecution2.getId()),
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

    @RetryingTest(5)
    void killPaused() throws TimeoutException, InterruptedException {
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
            HttpRequest.GET("/api/v1/executions/" + pausedExecution.getId()),
            Execution.class);
        assertThat(execution.getState().isPaused(), is(false));
    }

    @Test
    void find() {
        PagedResults<?> executions = client.toBlocking().retrieve(
            HttpRequest.GET("/api/v1/executions/search"), PagedResults.class
        );

        assertThat(executions.getTotal(), is(0L));

        triggerInputsFlowExecution(false);

        // + is there to simulate that a space was added (this can be the case from UI autocompletion for eg.)
        executions = client.toBlocking().retrieve(
            HttpRequest.GET("/api/v1/executions/search?page=1&size=25&labels=url:+"+ENCODED_URL_LABEL_VALUE), PagedResults.class
        );

        assertThat(executions.getTotal(), is(1L));

        HttpClientResponseException e = assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().retrieve(HttpRequest.GET("/api/v1/executions/search?startDate=2024-01-07T18:43:11.248%2B01:00&timeRange=PT12H"))
        );

        assertThat(e.getStatus(), is(HttpStatus.UNPROCESSABLE_ENTITY));
        assertThat(e.getResponse().getBody(String.class).get(), containsString("are mutually exclusive"));

        executions = client.toBlocking().retrieve(
            HttpRequest.GET("/api/v1/executions/search?timeRange=PT12H"), PagedResults.class
        );

        assertThat(executions.getTotal(), is(1L));

        e = assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().retrieve(HttpRequest.GET("/api/v1/executions/search?timeRange=P1Y"))
        );

        assertThat(e.getStatus(), is(HttpStatus.UNPROCESSABLE_ENTITY));
    }

    @Test
    void kill() throws TimeoutException, InterruptedException {
        // Run execution until it is paused
        Execution runningExecution = runnerUtils.runOneUntilRunning(null, TESTS_FLOW_NS, "sleep");
        assertThat(runningExecution.getState().isRunning(), is(true));

        // listen to the execution queue
        CountDownLatch killingLatch = new CountDownLatch(1);
        CountDownLatch killedLatch = new CountDownLatch(1);
        executionQueue.receive(e -> {
            if (e.getLeft().getId().equals(runningExecution.getId()) && e.getLeft().getState().getCurrent() == State.Type.KILLING) {
                killingLatch.countDown();
            }
            if (e.getLeft().getId().equals(runningExecution.getId()) && e.getLeft().getState().getCurrent() == State.Type.KILLED) {
                killedLatch.countDown();
            }
        });

        // listen to the executionkilled queue
        CountDownLatch executionKilledLatch = new CountDownLatch(1);
        killQueue.receive(e -> {
            if (e.getLeft().getExecutionId().equals(runningExecution.getId())) {
                executionKilledLatch.countDown();
            }
        });

        // kill the execution
        HttpResponse<?> killResponse = client.toBlocking().exchange(
            HttpRequest.DELETE("/api/v1/executions/" + runningExecution.getId() + "/kill"));
        assertThat(killResponse.getStatus(), is(HttpStatus.ACCEPTED));

        // check that the execution has been set to killing then killed
        assertTrue(killingLatch.await(10, TimeUnit.SECONDS));
        assertTrue(killedLatch.await(10, TimeUnit.SECONDS));
        //check that an executionkilled message has been sent
        assertTrue(executionKilledLatch.await(10, TimeUnit.SECONDS));

        // retrieve the execution from the API and check that the task has been set to killed
        Thread.sleep(500);
        Execution execution = client.toBlocking().retrieve(
            HttpRequest.GET("/api/v1/executions/" + runningExecution.getId()),
            Execution.class);
        assertThat(execution.getState().getCurrent(), is(State.Type.KILLED));
        assertThat(execution.getTaskRunList().size(), is(1));
        assertThat(execution.getTaskRunList().get(0).getState().getCurrent(), is(State.Type.KILLED));
    }

    @Test
    void resolveAbsoluteDateTime() {
        final ZonedDateTime absoluteTimestamp = ZonedDateTime.of(2023, 2, 3, 4, 6,10, 0, ZoneId.systemDefault());
        final Duration offset = Duration.ofSeconds(5L);
        final ZonedDateTime baseTimestamp = ZonedDateTime.of(2024, 2, 3, 5, 6,10, 0, ZoneId.systemDefault());

        assertThat(executionController.resolveAbsoluteDateTime(absoluteTimestamp, null, null), is(absoluteTimestamp));
        assertThat(executionController.resolveAbsoluteDateTime(null, offset, baseTimestamp), is(baseTimestamp.minus(offset)));
        assertThrows(IllegalArgumentException.class, () -> executionController.resolveAbsoluteDateTime(absoluteTimestamp, offset, baseTimestamp));
    }

    @Test
    void delete() {
        Execution result = triggerInputsFlowExecution(true);

        var response = client.toBlocking().exchange(HttpRequest.DELETE("/api/v1/executions/" + result.getId()));
        assertThat(response.getStatus(), is(HttpStatus.NO_CONTENT));

        var notFound = assertThrows(HttpClientResponseException.class, () -> client.toBlocking().exchange(HttpRequest.DELETE("/api/v1/executions/notfound")));
        assertThat(notFound.getStatus(), is(HttpStatus.NOT_FOUND));
    }

    @Test
    void deleteByIds() {
        Execution result1 = triggerInputsFlowExecution(true);
        Execution result2 = triggerInputsFlowExecution(true);
        Execution result3 = triggerInputsFlowExecution(true);

        BulkResponse response = client.toBlocking().retrieve(
            HttpRequest.DELETE("/api/v1/executions/by-ids", List.of(result1.getId(), result2.getId(), result3.getId())),
            BulkResponse.class
        );
        assertThat(response.getCount(), is(3));
    }

    @Test
    void deleteByQuery() {
        Execution result1 = triggerInputsFlowExecution(true);
        Execution result2 = triggerInputsFlowExecution(true);
        Execution result3 = triggerInputsFlowExecution(true);

        BulkResponse response = client.toBlocking().retrieve(
            HttpRequest.DELETE("/api/v1/executions/by-query?namespace=" + result1.getNamespace()),
            BulkResponse.class
        );
        assertThat(response.getCount(), is(3));
    }

    @Test
    void setLabels() {
        // update label on a terminated execution
        Execution result = triggerInputsFlowExecution(true);
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
    }

    @Test
    void setLabelsByIds() {
        Execution result1 = triggerInputsFlowExecution(true);
        Execution result2 = triggerInputsFlowExecution(true);
        Execution result3 = triggerInputsFlowExecution(true);

        BulkResponse response = client.toBlocking().retrieve(
            HttpRequest.POST("/api/v1/executions/labels/by-ids",
                new ExecutionController.SetLabelsByIdsRequest(List.of(result1.getId(), result2.getId(), result3.getId()), List.of(new Label("key", "value")))
            ),
            BulkResponse.class
        );

        assertThat(response.getCount(), is(3));
    }

    @Test
    void setLabelsByQuery() {
        Execution result1 = triggerInputsFlowExecution(true);
        Execution result2 = triggerInputsFlowExecution(true);
        Execution result3 = triggerInputsFlowExecution(true);

        BulkResponse response = client.toBlocking().retrieve(
            HttpRequest.POST("/api/v1/executions/labels/by-query?namespace=" + result1.getNamespace(),
                List.of(new Label("key", "value"))
            ),
            BulkResponse.class
        );

        assertThat(response.getCount(), is(3));
    }
}
