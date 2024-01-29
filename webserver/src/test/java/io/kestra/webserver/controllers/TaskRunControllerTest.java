package io.kestra.webserver.controllers;

import io.kestra.webserver.controllers.h2.JdbcH2ControllerTest;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.*;

class TaskRunControllerTest extends JdbcH2ControllerTest {
    @Inject
    @Client("/")
    private ReactorHttpClient client;

    @Test
    void search() {
        HttpClientResponseException e = assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().retrieve(HttpRequest.GET("/api/v1/taskruns/search"))
        );

        assertThat(e.getStatus(), is(HttpStatus.NOT_FOUND));
    }

    @Test
    void maxTaskRunSetting() {
        HttpClientResponseException e = assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().retrieve(HttpRequest.GET("/api/v1/taskruns/maxTaskRunSetting"))
        );

        assertThat(e.getStatus(), is(HttpStatus.NOT_FOUND));
    }
}