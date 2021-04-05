package io.kestra.webserver.controllers;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.utils.IdUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.RxHttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.hateoas.JsonError;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;
import javax.inject.Inject;

import static io.micronaut.http.HttpRequest.POST;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

@MicronautTest
class ErrorControllerTest {
    @Inject
    @Client("/")
    RxHttpClient client;

    @Test
    void type() {
        Map<String, Object> flow = ImmutableMap.of(
            "id", IdUtils.create(),
            "namespace", "io.kestra.test",
            "tasks", Collections.singletonList(ImmutableMap.of(
                "id", IdUtils.create(),
                "type", "io.kestra.invalid"
            ))
        );

        HttpClientResponseException exception = assertThrows(HttpClientResponseException.class, () -> {
            client.toBlocking().retrieve(POST("/api/v1/flows", flow), Argument.of(Flow.class), Argument.of(JsonError.class));
        });

        assertThat(exception.getStatus(), is(HttpStatus.UNPROCESSABLE_ENTITY));

        String response = exception.getResponse().getBody(String.class).get();
        assertThat(response, containsString("Invalid type: io.kestra.invalid"));
        assertThat(response, containsString("\"path\":\"io.kestra.core.models.flows.Flow[\\\"tasks\\\"] > java.util.ArrayList[0]\""));
        assertThat(response, containsString("Failed to convert argument"));

        // missing getter & setter on JsonError
        // assertThat(exception.getResponse().getBody(JsonError.class).get().getEmbedded().get("errors").get().get(0).getPath(), containsInAnyOrder("tasks"));
    }
}