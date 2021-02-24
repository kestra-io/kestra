package org.kestra.webserver.controllers;

import com.google.common.collect.ImmutableMap;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.RxHttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.client.multipart.MultipartBody;
import io.micronaut.http.client.sse.RxSseClient;
import io.micronaut.http.hateoas.JsonError;
import io.micronaut.http.sse.Event;
import io.micronaut.runtime.server.EmbeddedServer;
import org.junit.jupiter.api.Test;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.models.executions.TaskRun;
import org.kestra.core.models.flows.Flow;
import org.kestra.core.models.flows.State;
import org.kestra.core.models.storage.FileMetas;
import org.kestra.core.models.triggers.types.Webhook;
import org.kestra.core.queues.QueueFactoryInterface;
import org.kestra.core.queues.QueueInterface;
import org.kestra.core.repositories.FlowRepositoryInterface;
import org.kestra.core.runners.AbstractMemoryRunnerTest;
import org.kestra.core.runners.InputsTest;
import org.kestra.core.utils.IdUtils;
import org.kestra.webserver.responses.PagedResults;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.File;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeoutException;
import java.util.stream.IntStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.kestra.core.utils.Rethrow.throwRunnable;

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

    public static final String TESTS_FLOW_NS = "org.kestra.tests";

    public static Map<String, String> inputs = ImmutableMap.of(
        "string", "myString",
        "int", "42",
        "float", "42.42",
        "instant", "2019-10-06T18:27:49Z",
        "file", Objects.requireNonNull(InputsTest.class.getClassLoader().getResource("application.yml")).getPath()
    );

    @Test
    void getNotFound() {
        HttpClientResponseException e = assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().retrieve(HttpRequest.GET("/api/v1/executions/exec_id_not_found"))
        );

        assertThat(e.getStatus(), is(HttpStatus.NOT_FOUND));
    }

    private Execution triggerExecution(String namespace, String flowId, MultipartBody requestBody) {
        return client.toBlocking().retrieve(
            HttpRequest
                .POST("/api/v1/executions/trigger/" + namespace + "/" + flowId, requestBody)
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

    private Execution triggerInputsFlowExecution() {
        MultipartBody requestBody = createInputsFlowBody();

        return triggerExecution(TESTS_FLOW_NS, "inputs", requestBody);
    }

    @Test
    void trigger() {
        Execution result = triggerInputsFlowExecution();

        assertThat(result.getState().getCurrent(), is(State.Type.CREATED));
        assertThat(result.getFlowId(), is("inputs"));
        assertThat(result.getInputs().get("float"), is(42.42));
        assertThat(result.getInputs().get("file").toString(), startsWith("kestra:///org/kestra/tests/inputs/executions/"));
        assertThat(result.getInputs().get("file").toString(), startsWith("kestra:///org/kestra/tests/inputs/executions/"));
    }

    @Test
    void get() {
        Execution result = triggerInputsFlowExecution();

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
        String namespace = "org.kestra.tests.minimal.bis";
        String flowId = "minimal-bis";

        PagedResults<Execution> executionsBefore = client.toBlocking().retrieve(
            HttpRequest.GET("/api/v1/executions?namespace=" + namespace + "&flowId=" + flowId),
            Argument.of(PagedResults.class, Execution.class)
        );

        assertThat(executionsBefore.getTotal(), is(0L));

        triggerExecution(namespace, flowId, MultipartBody.builder().addPart("string", "myString").build());

        PagedResults<Execution> executionsAfter = client.toBlocking().retrieve(
            HttpRequest.GET("/api/v1/executions?namespace=" + namespace + "&flowId=" + flowId),
            Argument.of(PagedResults.class, Execution.class)
        );

        assertThat(executionsAfter.getTotal(), is(1L));
    }

    @Test
    void triggerAndFollow() {
        Execution result = triggerInputsFlowExecution();

        RxSseClient sseClient = embeddedServer.getApplicationContext().createBean(RxSseClient.class, embeddedServer.getURL());

        List<Event<Execution>> results = sseClient
            .eventStream("/api/v1/executions/" + result.getId() + "/follow", Execution.class)
            .toList()
            .blockingGet();

        assertThat(results.size(), is(greaterThan(0)));
        assertThat(results.get(results.size() - 1).getData().getState().getCurrent(), is(State.Type.SUCCESS));
    }

    @Test
    void restartFromUnknownTaskId() throws TimeoutException {
        final String flowId = "restart_with_inputs";
        final String referenceTaskId = "unknownTaskId";

        // Run execution until it ends
        Execution parentExecution = runnerUtils.runOne(TESTS_FLOW_NS, flowId, null, (flow, execution1) -> runnerUtils.typedInputs(flow, execution1, inputs));

        HttpClientResponseException e = assertThrows(HttpClientResponseException.class, () -> client.toBlocking().retrieve(
            HttpRequest
                .POST("/api/v1/executions/" + parentExecution.getId() + "/restart?taskId=" + referenceTaskId, MultipartBody.builder().addPart("string", "myString").build())
                .contentType(MediaType.MULTIPART_FORM_DATA_TYPE),
            Execution.class
        ));

        assertThat(e.getStatus(), is(HttpStatus.UNPROCESSABLE_ENTITY));
        assertThat(e.getResponse().getBody(JsonError.class).isPresent(), is(true));
        assertThat(e.getResponse().getBody(JsonError.class).get().getMessage(), containsString("Task [" + referenceTaskId + "] does not exist !"));
    }

    @Test
    void restartWithNoFailure() throws TimeoutException {
        final String flowId = "restart_with_inputs";

        // Run execution until it ends
        Execution parentExecution = runnerUtils.runOne(TESTS_FLOW_NS, flowId, null, (flow, execution1) -> runnerUtils.typedInputs(flow, execution1, inputs));

        HttpClientResponseException e = assertThrows(HttpClientResponseException.class, () -> client.toBlocking().retrieve(
            HttpRequest
                .POST("/api/v1/executions/" + parentExecution.getId() + "/restart", MultipartBody.builder().addPart("string", "myString").build())
                .contentType(MediaType.MULTIPART_FORM_DATA_TYPE),
            Execution.class
        ));

        assertThat(e.getStatus(), is(HttpStatus.UNPROCESSABLE_ENTITY));
        assertThat(e.getResponse().getBody(JsonError.class).isPresent(), is(true));
        assertThat(e.getResponse().getBody(JsonError.class).get().getMessage(), containsString("No failed task found to restart execution from !"));
    }

    @Test
    void restartFromTaskId() throws TimeoutException, InterruptedException {
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
                        .POST("/api/v1/executions/" + parentExecution.getId() + "/restart?taskId=" + referenceTaskId, MultipartBody.builder().addPart("string", "myString").build())
                        .contentType(MediaType.MULTIPART_FORM_DATA_TYPE),
                    Execution.class
                );

                assertThat(createdChidExec, notNullValue());
                assertThat(createdChidExec.getParentId(), is(parentExecution.getId()));
                assertThat(createdChidExec.getTaskRunList().size(), is(4));
                assertThat(createdChidExec.getState().getCurrent(), is(State.Type.CREATED));

                IntStream
                    .range(0, 3)
                    .mapToObj(value -> createdChidExec.getTaskRunList().get(value))
                    .forEach(taskRun -> assertThat(taskRun.getState().getCurrent(), is(State.Type.SUCCESS)));

                assertThat(createdChidExec.getTaskRunList().get(3).getState().getCurrent(), is(State.Type.CREATED));
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
    void restartFromTaskIdWithSequential() throws TimeoutException, InterruptedException {
        final String flowId = "restart_with_sequential";
        final String referenceTaskId = "a-3-2-2_end";

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
                        .POST("/api/v1/executions/" + parentExecution.getId() + "/restart?taskId=" + referenceTaskId, MultipartBody.builder().addPart("string", "myString").build())
                        .contentType(MediaType.MULTIPART_FORM_DATA_TYPE),
                    Execution.class
                );

                assertThat(createdChidExec, notNullValue());
                assertThat(createdChidExec.getParentId(), is(parentExecution.getId()));
                assertThat(createdChidExec.getTaskRunList().size(), is(8));
                assertThat(createdChidExec.getState().getCurrent(), is(State.Type.CREATED));

                assertThat(createdChidExec.getTaskRunList().get(0).getState().getCurrent(), is(State.Type.RUNNING));
                assertThat(createdChidExec.getTaskRunList().get(1).getState().getCurrent(), is(State.Type.SUCCESS));
                assertThat(createdChidExec.getTaskRunList().get(2).getState().getCurrent(), is(State.Type.SUCCESS));
                assertThat(createdChidExec.getTaskRunList().get(3).getState().getCurrent(), is(State.Type.RUNNING));
                assertThat(createdChidExec.getTaskRunList().get(4).getState().getCurrent(), is(State.Type.SUCCESS));
                assertThat(createdChidExec.getTaskRunList().get(5).getState().getCurrent(), is(State.Type.RUNNING));
                assertThat(createdChidExec.getTaskRunList().get(6).getState().getCurrent(), is(State.Type.SUCCESS));
                assertThat(createdChidExec.getTaskRunList().get(7).getState().getCurrent(), is(State.Type.CREATED));
                assertThat(createdChidExec.getTaskRunList().get(7).getAttempts().size(), is(1));
            }),
            Duration.ofSeconds(30000));
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
                        .POST("/api/v1/executions/" + firstExecution.getId() + "/restart", MultipartBody.builder().addPart("string", "myString").build())
                        .contentType(MediaType.MULTIPART_FORM_DATA_TYPE),
                    Execution.class
                );

                assertThat(restartedExec, notNullValue());
                assertThat(restartedExec.getId(), is(firstExecution.getId()));
                assertThat(restartedExec.getParentId(), nullValue());
                assertThat(restartedExec.getTaskRunList().size(), is(3));
                assertThat(restartedExec.getState().getCurrent(), is(State.Type.RUNNING));

                IntStream
                    .range(0, 2)
                    .mapToObj(value -> restartedExec.getTaskRunList().get(value)).forEach(taskRun -> {
                    assertThat(taskRun.getState().getCurrent(), is(State.Type.SUCCESS));
                    assertThat(taskRun.getAttempts().size(), is(1));

                    assertThat(restartedExec.getTaskRunList().get(2).getState().getCurrent(), is(State.Type.CREATED));
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

        assertThat(metas.getSize(), equalTo(288L));

        HttpClientResponseException e = assertThrows(HttpClientResponseException.class, () -> client.toBlocking().retrieve(
            HttpRequest.GET("/api/v1/executions/" + execution.getId() + "/file?path=" + path.replace(execution.getId(),
                IdUtils.create()
            )),
            String.class
        ));

        assertThat(e.getStatus().getCode(), is(422));
    }

    @SuppressWarnings("unchecked")
    @Test
    void webhook() {
        Flow webhook = flowRepositoryInterface.findById(TESTS_FLOW_NS, "webhook").orElseThrow();
        String key = ((Webhook) webhook.getTriggers().get(0)).getKey();

        Execution execution = client.toBlocking().retrieve(
            HttpRequest
                .POST(
                    "/api/v1/executions/webhook/" + TESTS_FLOW_NS + "/webhook/" + key,
                    ImmutableMap.of("a", 1, "b", true)
                ),
            Execution.class
        );

        assertThat(((Map<String, Object>) execution.getTrigger().getVariables().get("body")).get("a"), is(1));
        assertThat(((Map<String, Object>) execution.getTrigger().getVariables().get("body")).get("b"), is(true));

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
    }
}
