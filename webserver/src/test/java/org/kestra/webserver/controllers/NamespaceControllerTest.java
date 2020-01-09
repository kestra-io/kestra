package org.kestra.webserver.controllers;

import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.RxHttpClient;
import io.micronaut.http.client.annotation.Client;
import org.junit.jupiter.api.Test;
import org.kestra.core.runners.AbstractMemoryRunnerTest;

import javax.inject.Inject;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class NamespaceControllerTest extends AbstractMemoryRunnerTest {
    @Inject
    @Client("/")
    RxHttpClient client;

    @Test
    void listDistinctNamespace() {
        List<String> namespaces = client.toBlocking().retrieve(
            HttpRequest.GET("/api/v1/namespaces"), Argument.listOf(String.class));

        assertThat(namespaces.size(), is(2));
    }

    @Test
    void listDistinctNamespaceWithPrefix() {
        String prefix = "org.kestra.tests.minimal";
        List<String> namespaces = client.toBlocking().retrieve(
            HttpRequest.GET("/api/v1/namespaces?prefix=" + prefix), Argument.listOf(String.class));

        assertThat(namespaces.size(), is(1));
    }
}
