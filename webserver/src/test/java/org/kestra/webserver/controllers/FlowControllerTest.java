package org.kestra.webserver.controllers;

import com.devskiller.friendly_id.FriendlyId;
import com.google.common.collect.ImmutableList;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.RxHttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import org.junit.jupiter.api.Test;
import org.kestra.core.Helpers;
import org.kestra.core.models.flows.Flow;
import org.kestra.core.models.flows.Input;
import org.kestra.core.runners.AbstractMemoryRunnerTest;
import org.kestra.webserver.responses.FlowResponse;
import org.kestra.webserver.responses.PagedResults;

import javax.inject.Inject;

import static io.micronaut.http.HttpRequest.*;
import static io.micronaut.http.HttpStatus.NOT_FOUND;
import static io.micronaut.http.HttpStatus.NO_CONTENT;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FlowControllerTest extends AbstractMemoryRunnerTest {
    @Inject
    @Client("/")
    RxHttpClient client;

    @Test
    void id() {
        Flow result = client.toBlocking().retrieve(HttpRequest.GET("/api/v1/flows/org.kestra.tests/full"), Flow.class);

        assertThat(result.getId(), is("full"));
        assertThat(result.getTasks().size(), is(5));
    }

    @Test
    void idNotFound() {
        HttpClientResponseException e = assertThrows(HttpClientResponseException.class, () -> {
            client.toBlocking().retrieve(HttpRequest.GET("/api/v1/flows/org.kestra.tests/notFound"));
        });

        assertThat(e.getStatus(), is(HttpStatus.NOT_FOUND));
    }

    @Test
    void findAll() {
        PagedResults<Flow> flows = client.toBlocking().retrieve(HttpRequest.GET("/api/v1/flows/search?q=*"), Argument.of(PagedResults.class, Flow.class));
        assertThat(flows.getTotal(), is(Helpers.FLOWS_COUNT));
    }

    @Test
    void findNoResult() {
        PagedResults<Flow> flows = client.toBlocking().retrieve(HttpRequest.GET("/api/v1/flows/org.kestra.testsnotfound"), Argument.of(PagedResults.class, Flow.class));
        assertThat(flows.getTotal(), is(0L));
    }

    @Test
    void createFlow() {
        Flow flow = Flow.builder()
            .id(FriendlyId.createFriendlyId())
            .namespace("org.kestra.unittest")
            .inputs(ImmutableList.of(Input.builder().type(Input.Type.STRING).name("a").build()))
            .build();

        FlowResponse result = client.toBlocking().retrieve(
            POST("/api/v1/flows", flow),
            FlowResponse.class
        );
        assertThat(result.getFlow().getId(), is(flow.getId()));
        assertThat(result.getFlow().getInputs().get(0).getName(), is("a"));

        Flow get = client.toBlocking().retrieve(HttpRequest.GET("/api/v1/flows/" + flow.getNamespace() + "/" + flow.getId()), Flow.class);
        assertThat(get.getId(), is(flow.getId()));
        assertThat(get.getInputs().get(0).getName(), is("a"));

    }

    @Test
    void deleteFlow() {
        Flow flow = Flow.builder()
            .id(FriendlyId.createFriendlyId())
            .namespace("org.kestra.unittest")
            .build();

        FlowResponse result = client.toBlocking().retrieve(
            POST("/api/v1/flows", flow),
            FlowResponse.class
        );
        assertThat(result.getFlow().getId(), is(flow.getId()));
        assertThat(result.getFlow().getRevision(), is(1));

        HttpResponse<Void> deleteResult = client.toBlocking().exchange(
            DELETE("/api/v1/flows/" + flow.getNamespace() + "/" + flow.getId())
        );
        assertThat(deleteResult.getStatus(), is(NO_CONTENT));

        HttpClientResponseException e = assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<Void> response = client.toBlocking().exchange(
                DELETE("/api/v1/flows/" + flow.getNamespace() + "/" + flow.getId())
            );
        });

        assertThat(e.getStatus(), is(NOT_FOUND));
    }

    @Test
    void updateFlow() {
        String flowId = FriendlyId.createFriendlyId();

        Flow flow = Flow.builder()
            .id(flowId)
            .namespace("org.kestra.unittest")
            .inputs(ImmutableList.of(Input.builder().type(Input.Type.STRING).name("a").build()))
            .build();

        FlowResponse result = client.toBlocking().retrieve(
            POST("/api/v1/flows", flow),
            FlowResponse.class
        );

        assertThat(result.getFlow().getId(), is(flow.getId()));
        assertThat(result.getFlow().getInputs().get(0).getName(), is("a"));

        flow = Flow.builder()
            .id(flowId)
            .namespace("org.kestra.unittest")
            .inputs(ImmutableList.of(Input.builder().type(Input.Type.STRING).name("b").build()))
            .build();

        Flow get = client.toBlocking().retrieve(
            PUT("/api/v1/flows/" + flow.getNamespace() + "/" + flow.getId(), flow),
            Flow.class
        );

        assertThat(get.getId(), is(flow.getId()));
        assertThat(get.getInputs().get(0).getName(), is("b"));

        Flow finalFlow = flow;
        HttpClientResponseException e = assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<Void> response = client.toBlocking().exchange(
                PUT("/api/v1/flows/" + finalFlow.getNamespace() + "/" + FriendlyId.createFriendlyId(), finalFlow)
            );
        });
        assertThat(e.getStatus(), is(NOT_FOUND));


    }
}
