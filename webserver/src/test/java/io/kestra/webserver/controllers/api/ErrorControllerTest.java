package io.kestra.webserver.controllers.api;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.plugin.core.log.Log;
import io.kestra.core.utils.IdUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.hateoas.JsonError;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import io.kestra.core.junit.annotations.KestraTest;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;
import jakarta.inject.Inject;

import static io.micronaut.http.HttpRequest.POST;
import static io.micronaut.http.HttpStatus.UNPROCESSABLE_ENTITY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SuppressWarnings("OptionalGetWithoutIsPresent")
@KestraTest
class ErrorControllerTest {
    @Inject
    @Client("/")
    ReactorHttpClient client;

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

        HttpClientResponseException exception = assertThrows(HttpClientResponseException.class, () ->
            client.toBlocking().retrieve(POST("/api/v1/main/flows", flow), Argument.of(Flow.class), Argument.of(Object.class))
        );

        assertThat(exception.getStatus().getCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY.getCode());

        String response = exception.getResponse().getBody(String.class).get();
        assertThat(response).contains("Invalid type: io.kestra.invalid");
        assertThat(response).contains("\"path\":\"io.kestra.core.models.flows.Flow[\\\"tasks\\\"] > java.util.ArrayList[0]\"");
        assertThat(response).contains("Failed to convert argument");

        // missing getter & setter on JsonError
        // assertThat(exception.getResponse().getBody(JsonError.class).get().getEmbedded().get("errors").get().getFirst().getPath(), containsInAnyOrder("tasks"));
    }

    @Test
    void unknownProperties() {
        Map<String, Object> flow =  ImmutableMap.of(
            "id", IdUtils.create(),
            "namespace", "io.kestra.test",
            "unknown", "properties",
            "tasks", Collections.singletonList(ImmutableMap.of(
                "id", IdUtils.create(),
                "type", Log.class.getName(),
                "message", "logging"
            ))
        );

        HttpClientResponseException exception = assertThrows(HttpClientResponseException.class, () -> client.toBlocking().retrieve(
                POST("/api/v1/main/flows", JacksonMapper.ofYaml().writeValueAsString(flow)).contentType(MediaType.APPLICATION_YAML),
                Argument.of(String.class),
                Argument.of(JsonError.class)
            )
        );

        assertThat(exception.getStatus().getCode()).isEqualTo(UNPROCESSABLE_ENTITY.getCode());

        String response = exception.getResponse().getBody(String.class).get();
        assertThat(response).contains("Invalid entity: Unrecognized field \\\"unknown\\\" (class io.kestra.core.models.flows.FlowWithSource), not marked as ignorable");
        assertThat(response).contains("\"path\":\"io.kestra.core.models.flows.FlowWithSource[\\\"unknown\\\"]\"");
    }

    @Disabled("Test disabled: no exception thrown when converting to dynamic properties")
    @Test
    void invalidEnum() {
        Map<String, Object> flow = ImmutableMap.of(
            "id", IdUtils.create(),
            "namespace", "io.kestra.test",
            "tasks", Collections.singletonList(ImmutableMap.of(
                "id", IdUtils.create(),
                "type", Log.class.getName(),
                "message", "Yeah !",
                "level", "WRONG"
            ))
        );

        HttpClientResponseException exception = assertThrows(HttpClientResponseException.class, () ->
            client.toBlocking().retrieve(POST("/api/v1/main/flows", flow), Argument.of(Flow.class), Argument.of(JsonError.class))
        );

        assertThat(exception.getStatus().getCode()).isEqualTo(UNPROCESSABLE_ENTITY.getCode());

        String response = exception.getResponse().getBody(String.class).get();
        assertThat(response).contains("Cannot deserialize value of type `org.slf4j.event.Level` from String \\\"WRONG\\\"");
        assertThat(response).contains("\"path\":\"io.kestra.core.models.flows.Flow[\\\"tasks\\\"] > java.util.ArrayList[0] > io.kestra.plugin.core.log.Log[\\\"level\\\"]\"");
    }

}