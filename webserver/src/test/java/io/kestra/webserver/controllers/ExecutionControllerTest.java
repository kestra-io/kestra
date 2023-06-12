package io.kestra.webserver.controllers;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.TaskRun;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.State;
import io.kestra.core.models.storage.FileMetas;
import io.kestra.core.models.triggers.types.Webhook;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.runners.AbstractMemoryRunnerTest;
import io.kestra.core.runners.InputsTest;
import io.kestra.core.utils.IdUtils;
import io.kestra.webserver.responses.PagedResults;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.client.multipart.MultipartBody;
import io.micronaut.http.sse.Event;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.rxjava2.http.client.RxHttpClient;
import io.micronaut.rxjava2.http.client.sse.RxSseClient;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeoutException;
import java.util.stream.IntStream;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import static io.kestra.core.utils.Rethrow.throwRunnable;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExecutionControllerTest extends AbstractMemoryRunnerTest {
    @Inject
    EmbeddedServer embeddedServer;

    @Inject
    @Named(QueueFactoryInterface.EXECUTION_NAMED)
    protected QueueInterface<Execution> executionQueue;

    @Inject
    FlowRepositoryInterface flowRepositoryInterface;

    @Inject
    @Client("/")
    RxHttpClient client;

    public static final String TESTS_FLOW_NS = "io.kestra.tests";

    public static Map<String, String> inputs = ImmutableMap.<String, String>builder()
        .put("failed", "NO")
        .put("string", "myString")
        .put("int", "42")
        .put("float", "42.42")
        .put("instant", "2019-10-06T18:27:49Z")
        .put("file", Objects.requireNonNull(InputsTest.class.getClassLoader().getResource("application.yml")).getPath())
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
                .POST("/api/v1/executions/trigger/" + namespace + "/" + flowId + "?labels=a:label-1,b:label-2" + (wait ? "&wait=true" : ""), requestBody)
                .contentType(MediaType.MULTIPART_FORM_DATA_TYPE),
            Execution.class
        );
    }


    private MultipartBody createInputsFlowBody() {
        // Trigger execution
        File applicationFile = new File(Objects.requireNonNull(
            ExecutionControllerTest.class.getClassLoader().getResource("application.yml")
        ).getPath());

        File logbackFile = new File(Objects.requireNonNull(
            ExecutionControllerTest.class.getClassLoader().getResource("logback.xml")
        ).getPath());

        return MultipartBody.builder()
            .addPart("string", "myString")
            .addPart("int", "42")
            .addPart("float", "42.42")
            .addPart("instant", "2019-10-06T18:27:49Z")
            .addPart("files", "file", MediaType.TEXT_PLAIN_TYPE, applicationFile)
            .addPart("files", "optionalFile", MediaType.TEXT_XML_TYPE, logbackFile)
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
        assertThat(result.getLabels().get("a"), is("label-1"));
        assertThat(result.getLabels().get("b"), is("label-2"));
    }

    @Test
    void triggerAndWait() {
        Execution result = triggerInputsFlowExecution(true);

        assertThat(result.getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(result.getTaskRunList().size(), is(5));
    }

    @Test
    void get() {
        Execution result = triggerInputsFlowExecution(false);

        // Get the triggered execution by execution id
        Execution foundExecution = client.retrieve(
            HttpRequest.GET("/api/v1/executions/" + result.getId()),
            Execution.class
        ).blockingFirst();

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

        PagedResults<Execution> executionsAfter = client.toBlocking().retrieve(
            HttpRequest.GET("/api/v1/executions?namespace=" + namespace + "&flowId=" + flowId),
            Argument.of(PagedResults.class, Execution.class)
        );

        assertThat(executionsAfter.getTotal(), is(1L));
    }

    @Test
    void triggerAndFollow() {
        Execution result = triggerInputsFlowExecution(false);

        RxSseClient sseClient = embeddedServer.getApplicationContext().createBean(RxSseClient.class, embeddedServer.getURL());

        List<Event<Execution>> results = sseClient
            .eventStream("/api/v1/executions/" + result.getId() + "/follow", Execution.class)
            .toList()
            .blockingGet();

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
        Execution execution = runnerUtils.runOne("io.kestra.tests", "each-sequential-nested");

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
        Execution parentExecution = runnerUtils.runOne(TESTS_FLOW_NS, flowId, null, (flow, execution1) -> runnerUtils.typedInputs(flow, execution1, inputs));

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
        Execution parentExecution = runnerUtils.runOne(TESTS_FLOW_NS, flowId, null, (flow, execution1) -> runnerUtils.typedInputs(flow, execution1, inputs));

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
        Execution parentExecution = runnerUtils.runOne(TESTS_FLOW_NS, flowId, null, (flow, execution1) -> runnerUtils.typedInputs(flow, execution1, inputs));

        Optional<Flow> flow = flowRepositoryInterface.findById(TESTS_FLOW_NS, flowId);

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
        Execution parentExecution = runnerUtils.runOne(TESTS_FLOW_NS, flowId, null,
            (flow, execution1) -> runnerUtils.typedInputs(flow, execution1, inputs));

        Optional<Flow> flow = flowRepositoryInterface.findById(TESTS_FLOW_NS, flowId);
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
    void restartFromLastFailed() throws TimeoutException, InterruptedException {
        final String flowId = "restart_last_failed";

        // Run execution until it ends
        Execution firstExecution = runnerUtils.runOne(TESTS_FLOW_NS, flowId, null, null);

        assertThat(firstExecution.getTaskRunList().get(2).getState().getCurrent(), is(State.Type.FAILED));
        assertThat(firstExecution.getState().getCurrent(), is(State.Type.FAILED));

        // Update task's command to make second execution successful
        Optional<Flow> flow = flowRepositoryInterface.findById(TESTS_FLOW_NS, flowId);
        assertThat(flow.isPresent(), is(true));

        // Restart execution and wait until it finishes
        Execution finishedRestartedExecution = runnerUtils.awaitExecution(
            flow.get(),
            firstExecution, throwRunnable(() -> {
                Thread.sleep(1000);

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
            }),
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
    void downloadFile() throws TimeoutException {
        Execution execution = runnerUtils.runOne(TESTS_FLOW_NS, "inputs", null, (flow, execution1) -> runnerUtils.typedInputs(flow, execution1, inputs));
        assertThat(execution.getTaskRunList(), hasSize(5));

        String path = (String) execution.getInputs().get("file");

        String file = client.toBlocking().retrieve(
            HttpRequest.GET("/api/v1/executions/" + execution.getId() + "/file?path=" + path),
            String.class
        );

        assertThat(file, containsString("micronaut:"));

        FileMetas metas = client.retrieve(
            HttpRequest.GET("/api/v1/executions/" + execution.getId() + "/file/metas?path=" + path),
            FileMetas.class
        ).blockingFirst();

        assertThat(metas.getSize(), equalTo(356L));

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

    @SuppressWarnings("unchecked")
    @Test
    void webhook() {
        Flow webhook = flowRepositoryInterface.findById(TESTS_FLOW_NS, "webhook").orElseThrow();
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
}
