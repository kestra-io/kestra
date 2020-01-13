package org.kestra.webserver.controllers;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.RxHttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.multipart.MultipartBody;
import io.micronaut.http.client.sse.RxSseClient;
import io.micronaut.http.sse.Event;
import io.micronaut.runtime.server.EmbeddedServer;
import org.kestra.core.runners.AbstractMemoryRunnerTest;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.models.flows.State;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import javax.inject.Inject;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;

class ExecutionControllerTest extends AbstractMemoryRunnerTest {
    @Inject
    EmbeddedServer embeddedServer;

    @Inject
    @Client("/")
    RxHttpClient client;

    @SuppressWarnings("unchecked")
    @Test
    void trigger() {
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

        Execution result = client.toBlocking().retrieve(
            HttpRequest
                .POST("/api/v1/executions/trigger/org.kestra.tests/inputs", requestBody)
                .contentType(MediaType.MULTIPART_FORM_DATA_TYPE),
            Execution.class
        );

        assertThat(result.getState().getCurrent(), is(State.Type.CREATED));
        assertThat(result.getFlowId(), is("inputs"));
        assertThat(result.getInputs().get("float"), is(42.42));
        assertThat(((Map<String, String>) result.getInputs().get("file")).get("uri"), startsWith("kestra:///org/kestra/tests/inputs/executions/"));
        assertThat(((Map<String, String>) result.getInputs().get("optionalFile")).get("uri"), startsWith("kestra:///org/kestra/tests/inputs/executions/"));
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

        List<Event<Execution>> results = sseClient.eventStream("executions/" + execution.getId() + "/follow", Execution.class).toList().blockingGet();

        assertThat(results.size(), is(13));
    }
}