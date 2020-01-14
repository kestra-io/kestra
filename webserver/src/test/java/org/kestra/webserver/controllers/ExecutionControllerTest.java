package org.kestra.webserver.controllers;

import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.RxHttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.client.multipart.MultipartBody;
import io.micronaut.http.client.sse.RxSseClient;
import io.micronaut.http.sse.Event;
import io.micronaut.runtime.server.EmbeddedServer;
import io.reactivex.Maybe;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.models.flows.State;
import org.kestra.core.runners.AbstractMemoryRunnerTest;
import org.kestra.webserver.responses.PagedResults;

import javax.inject.Inject;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExecutionControllerTest extends AbstractMemoryRunnerTest {
    @Inject
    EmbeddedServer embeddedServer;

    @Inject
    @Client("/")
    RxHttpClient client;

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

    private Execution triggerInputsExecution() {
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

        return triggerExecution("org.kestra.tests", "inputs", requestBody);
    }

    @SuppressWarnings("unchecked")
    @Test
    void trigger() {
        Execution result = triggerInputsExecution();

        assertThat(result.getState().getCurrent(), is(State.Type.CREATED));
        assertThat(result.getFlowId(), is("inputs"));
        assertThat(result.getInputs().get("float"), is(42.42));
        assertThat(((Map<String, String>) result.getInputs().get("file")).get("uri"), startsWith("kestra:///org/kestra/tests/inputs/executions/"));
        assertThat(((Map<String, String>) result.getInputs().get("optionalFile")).get("uri"), startsWith("kestra:///org/kestra/tests/inputs/executions/"));
    }

    @Test
    void get() {
        Execution result = triggerInputsExecution();

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
}