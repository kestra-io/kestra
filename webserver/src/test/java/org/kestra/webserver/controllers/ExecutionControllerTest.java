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
import io.reactivex.Maybe;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.models.executions.TaskRun;
import org.kestra.core.models.flows.Flow;
import org.kestra.core.models.flows.State;
import org.kestra.core.queues.QueueFactoryInterface;
import org.kestra.core.queues.QueueInterface;
import org.kestra.core.repositories.FlowRepositoryInterface;
import org.kestra.core.runners.AbstractMemoryRunnerTest;
import org.kestra.core.runners.InputsTest;
import org.kestra.core.tasks.scripts.Bash;
import org.kestra.webserver.responses.PagedResults;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.File;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.stream.IntStream;

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
        HttpClientResponseException e = assertThrows(HttpClientResponseException.class, () -> {
            client.toBlocking().retrieve(HttpRequest.GET("/api/v1/executions/exec_id_not_found"));
        });

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

        MultipartBody requestBody = MultipartBody.builder()
            .addPart("string", "myString")
            .addPart("int", "42")
            .addPart("float", "42.42")
            .addPart("instant", "2019-10-06T18:27:49Z")
            .addPart("files", "file", MediaType.TEXT_PLAIN_TYPE, applicationFile)
            .addPart("files", "optionalFile", MediaType.TEXT_XML_TYPE, logbackFile)
            .build();

        return requestBody;
    }

    private Execution triggerInputsFlowExecution() {
        MultipartBody requestBody = createInputsFlowBody();

        return triggerExecution(TESTS_FLOW_NS, "inputs", requestBody);
    }

    @SuppressWarnings("unchecked")
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
        Maybe<Execution> foundExecution = client.retrieve(
            HttpRequest.GET("/api/v1/executions/" + result.getId()),
            Execution.class
        ).firstElement();

        assertThat(foundExecution.isEmpty().blockingGet(), is(Boolean.FALSE));
        assertThat(foundExecution.blockingGet().getId(), is(result.getId()));
        assertThat(foundExecution.blockingGet().getNamespace(), is(result.getNamespace()));
    }

    @SuppressWarnings("unchecked")
    @Disabled("TODO: this test is flakky since the execution can be terminated before the second call")
    @Test
    void findByFlowId() throws TimeoutException {
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
    @Disabled("TODO: don't work")
    void triggerAndFollow() {
        RxSseClient sseClient = embeddedServer.getApplicationContext().createBean(RxSseClient.class, embeddedServer.getURL());

        Execution execution = client.toBlocking().retrieve(
            HttpRequest
                .POST("/api/v1/executions/trigger/org.kestra.tests/full", MultipartBody.builder().addPart("string", "myString").build())
                .contentType(MediaType.MULTIPART_FORM_DATA_TYPE),
            Execution.class
        );

        List<Event<Execution>> results = sseClient
            .eventStream("executions/" + execution.getId() + "/follow", Execution.class)
            .toList()
            .blockingGet();

        assertThat(results.size(), is(13));
    }

    @Test
    void restartFromUnknownTaskId() throws TimeoutException {
        final String flowId = "restart_with_inputs";
        final String referenceTaskId = "unknownTaskId";

        // Run execution until it ends
        Execution parentExecution = runnerUtils.runOne(TESTS_FLOW_NS, flowId, null, (flow, execution1) -> runnerUtils.typedInputs(flow, execution1, inputs));

        HttpClientResponseException e = assertThrows(HttpClientResponseException.class, () -> {
            Execution createdChidExec = client.toBlocking().retrieve(
                HttpRequest
                    .POST("/api/v1/executions/" + parentExecution.getId() + "/restart?taskId=" + referenceTaskId, MultipartBody.builder().addPart("string", "myString").build())
                    .contentType(MediaType.MULTIPART_FORM_DATA_TYPE),
                Execution.class
            );
        });

        assertThat(e.getStatus(), is(HttpStatus.UNPROCESSABLE_ENTITY));
        assertThat(e.getResponse().getBody(JsonError.class).get().getMessage(), containsString("Task [" + referenceTaskId + "] does not exist !"));
    }

    @Test
    void restartWithNoFailure() throws TimeoutException {
        final String flowId = "restart_with_inputs";

        // Run execution until it ends
        Execution parentExecution = runnerUtils.runOne(TESTS_FLOW_NS, flowId, null, (flow, execution1) -> runnerUtils.typedInputs(flow, execution1, inputs));

        HttpClientResponseException e = assertThrows(HttpClientResponseException.class, () -> {
            Execution createdChidExec = client.toBlocking().retrieve(
                HttpRequest
                    .POST("/api/v1/executions/" + parentExecution.getId() + "/restart", MultipartBody.builder().addPart("string", "myString").build())
                    .contentType(MediaType.MULTIPART_FORM_DATA_TYPE),
                Execution.class
            );
        });

        assertThat(e.getStatus(), is(HttpStatus.UNPROCESSABLE_ENTITY));
        assertThat(e.getResponse().getBody(JsonError.class).get().getMessage(), containsString("No failed task found to restart execution from !"));
    }

    @Test
    void restartFromTaskId() throws TimeoutException {
        final String flowId = "restart_with_inputs";
        final String referenceTaskId = "instant";

        // Run execution until it ends
        Execution parentExecution = runnerUtils.runOne(TESTS_FLOW_NS, flowId, null, (flow, execution1) -> runnerUtils.typedInputs(flow, execution1, inputs));

        Optional<Flow> flow = flowRepositoryInterface.findById(TESTS_FLOW_NS, flowId);

        // Run child execution starting from a specific task and wait until it finishes
        Execution finishedChildExecution = runnerUtils.awaitChildExecution(
            flow.get(),
            parentExecution, () -> {
                Execution createdChidExec = client.toBlocking().retrieve(
                    HttpRequest
                        .POST("/api/v1/executions/" + parentExecution.getId() + "/restart?taskId=" + referenceTaskId, MultipartBody.builder().addPart("string", "myString").build())
                        .contentType(MediaType.MULTIPART_FORM_DATA_TYPE),
                    Execution.class
                );

                assertThat(createdChidExec, notNullValue());
                assertThat(createdChidExec.getParentId(), is(parentExecution.getId()));
                assertThat(createdChidExec.getTaskRunList().size(), is(4));
                assertThat(createdChidExec.getState().getCurrent(), is(State.Type.RUNNING));

                IntStream
                    .range(0, 3)
                    .mapToObj(value -> {
                        return createdChidExec.getTaskRunList().get(value);
                    }).forEach(taskRun -> {
                    assertThat(taskRun.getState().getCurrent(), is(State.Type.SUCCESS));
                });

                assertThat(createdChidExec.getTaskRunList().get(3).getState().getCurrent(), is(State.Type.CREATED));
                assertThat(createdChidExec.getTaskRunList().get(3).getAttempts().size(), is(1));
            },
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
    void restartFromTaskIdWithSequential() throws TimeoutException {
        final String flowId = "restart_with_sequential";
        final String referenceTaskId = "a-3-2-2_end";

        // Run execution until it ends
        Execution parentExecution = runnerUtils.runOne(TESTS_FLOW_NS, flowId, null,
            (flow, execution1) -> runnerUtils.typedInputs(flow, execution1, inputs));

        Optional<Flow> flow = flowRepositoryInterface.findById(TESTS_FLOW_NS, flowId);

        // Run child execution starting from a specific task and wait until it finishes
        Execution finishedChildExecution = runnerUtils.awaitChildExecution(
            flow.get(),
            parentExecution, () -> {
                Execution createdChidExec = client.toBlocking().retrieve(
                    HttpRequest
                        .POST("/api/v1/executions/" + parentExecution.getId() + "/restart?taskId=" + referenceTaskId, MultipartBody.builder().addPart("string", "myString").build())
                        .contentType(MediaType.MULTIPART_FORM_DATA_TYPE),
                    Execution.class
                );

                assertThat(createdChidExec, notNullValue());
                assertThat(createdChidExec.getParentId(), is(parentExecution.getId()));
                assertThat(createdChidExec.getTaskRunList().size(), is(8));
                assertThat(createdChidExec.getState().getCurrent(), is(State.Type.RUNNING));

                assertThat(createdChidExec.getTaskRunList().get(0).getState().getCurrent(), is(State.Type.RUNNING));
                assertThat(createdChidExec.getTaskRunList().get(1).getState().getCurrent(), is(State.Type.SUCCESS));
                assertThat(createdChidExec.getTaskRunList().get(2).getState().getCurrent(), is(State.Type.SUCCESS));
                assertThat(createdChidExec.getTaskRunList().get(3).getState().getCurrent(), is(State.Type.RUNNING));
                assertThat(createdChidExec.getTaskRunList().get(4).getState().getCurrent(), is(State.Type.SUCCESS));
                assertThat(createdChidExec.getTaskRunList().get(5).getState().getCurrent(), is(State.Type.RUNNING));
                assertThat(createdChidExec.getTaskRunList().get(6).getState().getCurrent(), is(State.Type.SUCCESS));
                assertThat(createdChidExec.getTaskRunList().get(7).getState().getCurrent(), is(State.Type.CREATED));
                assertThat(createdChidExec.getTaskRunList().get(7).getAttempts().size(), is(1));
            },
            Duration.ofSeconds(30000));
    }

    @Test
    void restartFromLastFailed() throws TimeoutException {
        final String flowId = "restart_last_failed";

        // Run execution until it ends
        Execution firstExecution = runnerUtils.runOne(TESTS_FLOW_NS, flowId, null, null);

        assertThat(firstExecution.getTaskRunList().get(2).getState().getCurrent(), is(State.Type.FAILED));
        assertThat(firstExecution.getState().getCurrent(), is(State.Type.FAILED));

        // Update task's command to make second execution successful
        Optional<Flow> flow = flowRepositoryInterface.findById(TESTS_FLOW_NS, flowId);

        Bash task = (Bash) flow.get().getTasks().get(2);
        Bash b = Bash.builder()
            .id(task.getId())
            .type(task.getType())
            .commands(new String[]{"exit 0"})
            .build();

        flow.get().getTasks().set(2, b);

        flowRepositoryInterface.create(flow.get());

        // Restart execution and wait until it finishes
        Execution finishedRestartedExecution = runnerUtils.awaitExecution(
            flow.get(),
            firstExecution, () -> {
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
                    .mapToObj(value -> {
                        return restartedExec.getTaskRunList().get(value);
                    }).forEach(taskRun -> {
                    assertThat(taskRun.getState().getCurrent(), is(State.Type.SUCCESS));
                    assertThat(taskRun.getAttempts().size(), is(1));

                    assertThat(restartedExec.getTaskRunList().get(2).getState().getCurrent(), is(State.Type.CREATED));
                    assertThat(restartedExec.getTaskRunList().get(2).getAttempts().size(), is(1));
                });
            },
            Duration.ofSeconds(15));

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
    void restartFromLastFailedWithErrorsTwoTimes() throws TimeoutException {
        final String flowId = "sequential-with-global-errors";

        // Run execution until it ends
        Execution firstExecution = runnerUtils.runOne(TESTS_FLOW_NS, flowId, null, null);
        assertThat(firstExecution.getTaskRunList().size(), is(6));
        // Execution status
        assertThat(firstExecution.getState().getCurrent(), is(State.Type.FAILED));

        // Main task fails
        assertThat(firstExecution.getTaskRunList().get(0).getState().getCurrent(), is(State.Type.FAILED));
        assertThat(firstExecution.getTaskRunList().get(0).getAttempts(), nullValue());
        assertThat(firstExecution.getTaskRunList().get(1).getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(firstExecution.getTaskRunList().get(1).getAttempts().size(), is(1));
        assertThat(firstExecution.getTaskRunList().get(2).getState().getCurrent(), is(State.Type.FAILED));
        assertThat(firstExecution.getTaskRunList().get(2).getAttempts(), nullValue());
        assertThat(firstExecution.getTaskRunList().get(3).getState().getCurrent(), is(State.Type.FAILED));
        assertThat(firstExecution.getTaskRunList().get(3).getAttempts().size(), is(1));

        // Errors tasks are successful
        assertThat(firstExecution.getTaskRunList().get(4).getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(firstExecution.getTaskRunList().get(4).getAttempts().size(), is(1));
        assertThat(firstExecution.getTaskRunList().get(5).getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(firstExecution.getTaskRunList().get(5).getAttempts().size(), is(1));

        // Update task's command to make second execution successful
        Optional<Flow> flow = flowRepositoryInterface.findById(TESTS_FLOW_NS, flowId);

        // Restart execution and wait until it finishes
        Execution finishedRestartedExecution = runnerUtils.awaitExecution(
            flow.get(),
            firstExecution, () -> {
                Execution restartedExec = client.toBlocking().retrieve(
                    HttpRequest
                        .POST("/api/v1/executions/" + firstExecution.getId() + "/restart", MultipartBody.builder().addPart("string", "myString").build())
                        .contentType(MediaType.MULTIPART_FORM_DATA_TYPE),
                    Execution.class
                );

                assertThat(restartedExec, notNullValue());
                assertThat(restartedExec.getId(), is(firstExecution.getId()));
                assertThat(restartedExec.getParentId(), nullValue());
                assertThat(restartedExec.getTaskRunList().size(), is(4));
                assertThat(restartedExec.getState().getCurrent(), is(State.Type.RUNNING));

                // Tasks
                assertThat(restartedExec.getTaskRunList().get(0).getState().getCurrent(), is(State.Type.RUNNING));
                assertThat(restartedExec.getTaskRunList().get(1).getState().getCurrent(), is(State.Type.SUCCESS));
                assertThat(restartedExec.getTaskRunList().get(2).getState().getCurrent(), is(State.Type.RUNNING));
                // Last failed task
                assertThat(restartedExec.getTaskRunList().get(3).getState().getCurrent(), is(State.Type.CREATED));
            },
            Duration.ofSeconds(15));

        assertThat(finishedRestartedExecution, notNullValue());
        assertThat(finishedRestartedExecution.getId(), is(firstExecution.getId()));
        assertThat(finishedRestartedExecution.getParentId(), nullValue());
        assertThat(finishedRestartedExecution.getTaskRunList().size(), is(6));

        // Tasks
        assertThat(finishedRestartedExecution.getTaskRunList().get(0).getState().getCurrent(), is(State.Type.FAILED));
        assertThat(finishedRestartedExecution.getTaskRunList().get(1).getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(finishedRestartedExecution.getTaskRunList().get(2).getState().getCurrent(), is(State.Type.FAILED));
        // Last failed task
        assertThat(finishedRestartedExecution.getTaskRunList().get(3).getState().getCurrent(), is(State.Type.FAILED));
        // Errors tasks
        assertThat(finishedRestartedExecution.getTaskRunList().get(4).getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(finishedRestartedExecution.getTaskRunList().get(5).getState().getCurrent(), is(State.Type.SUCCESS));
    }
}
