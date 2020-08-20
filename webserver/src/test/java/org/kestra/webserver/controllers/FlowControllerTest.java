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
import io.micronaut.http.hateoas.JsonError;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.kestra.core.Helpers;
import org.kestra.core.models.executions.Execution;
import org.kestra.core.models.executions.metrics.ExecutionMetricsAggregation;
import org.kestra.core.models.flows.Flow;
import org.kestra.core.models.flows.Input;
import org.kestra.core.models.flows.State;
import org.kestra.core.models.hierarchies.FlowTree;
import org.kestra.core.runners.AbstractMemoryRunnerTest;
import org.kestra.core.tasks.debugs.Return;
import org.kestra.webserver.responses.PagedResults;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeoutException;
import javax.inject.Inject;

import static io.micronaut.http.HttpRequest.*;
import static io.micronaut.http.HttpStatus.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FlowControllerTest extends AbstractMemoryRunnerTest {
    @Inject
    @Client("/")
    RxHttpClient client;

    public static final String TESTS_FLOW_NS = "org.kestra.tests";

    @Test
    void id() {
        Flow result = client.toBlocking().retrieve(HttpRequest.GET("/api/v1/flows/org.kestra.tests/full"), Flow.class);

        assertThat(result.getId(), is("full"));
        assertThat(result.getTasks().size(), is(5));
    }

    @Test
    void tree() {
        FlowTree result = client.toBlocking().retrieve(HttpRequest.GET("/api/v1/flows/org.kestra" +
            ".tests/all-flowable/tree"), FlowTree.class);

        assertThat(result.getTasks().size(), is(20));
    }

    @Test
    void idNotFound() {
        HttpClientResponseException e = assertThrows(HttpClientResponseException.class, () -> {
            client.toBlocking().retrieve(HttpRequest.GET("/api/v1/flows/org.kestra.tests/notFound"));
        });

        assertThat(e.getStatus(), is(HttpStatus.NOT_FOUND));
    }

    @SuppressWarnings("unchecked")
    @Test
    void findAll() {
        PagedResults<Flow> flows = client.toBlocking().retrieve(HttpRequest.GET("/api/v1/flows/search?q=*"), Argument.of(PagedResults.class, Flow.class));
        assertThat(flows.getTotal(), is(Helpers.FLOWS_COUNT));
    }

    @SuppressWarnings("OptionalGetWithoutIsPresent")
    @Test
    void updateNamespace() {
        // initial création
        List<Flow> flows = Arrays.asList(
            generateFlow("f1", "org.kestra.updatenamespace", "1"),
            generateFlow("f2", "org.kestra.updatenamespace", "2"),
            generateFlow("f3", "org.kestra.updatenamespace", "3")
        );

        List<Flow> updated = client.toBlocking().retrieve(HttpRequest.POST("/api/v1/flows/org.kestra.updatenamespace", flows), Argument.listOf(Flow.class));
        assertThat(updated.size(), is(3));

        Flow retrieve = client.toBlocking().retrieve(GET("/api/v1/flows/org.kestra.updatenamespace/f1"), Flow.class);
        assertThat(retrieve.getId(), is("f1"));

        // update
        flows = Arrays.asList(
            generateFlow("f3", "org.kestra.updatenamespace", "3-3"),
            generateFlow("f4", "org.kestra.updatenamespace", "4")
        );

        // f3 & f4 must be updated
        updated = client.toBlocking().retrieve(HttpRequest.POST("/api/v1/flows/org.kestra.updatenamespace", flows), Argument.listOf(Flow.class));
        assertThat(updated.size(), is(2));
        assertThat(updated.get(0).getInputs().get(0).getName(), is("3-3"));
        assertThat(updated.get(1).getInputs().get(0).getName(), is("4"));

        // f1 & f2 must be deleted
        assertThrows(HttpClientResponseException.class, () -> {
            client.toBlocking().retrieve(HttpRequest.GET("/api/v1/flows/org.kestra.updatenamespace/f1"), Flow.class);
        });

        assertThrows(HttpClientResponseException.class, () -> {
            client.toBlocking().retrieve(HttpRequest.GET("/api/v1/flows/org.kestra.updatenamespace/f2"), Flow.class);
        });

        // create a flow in another namespace
        Flow invalid = generateFlow("invalid1", "org.kestra.othernamespace", "1");
        client.toBlocking().retrieve(POST("/api/v1/flows", invalid), Flow.class);

        HttpClientResponseException e = assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().retrieve(
                POST("/api/v1/flows/org.kestra.updatenamespace", Arrays.asList(
                    invalid,
                    generateFlow("f4", "org.kestra.updatenamespace", "5"),
                    generateFlow("f6", "org.kestra.another", "5")
                )),
                Argument.listOf(Flow.class)
            )
        );
        JsonError jsonError = e.getResponse().getBody(JsonError.class).get();
        assertThat(e.getStatus(), is(UNPROCESSABLE_ENTITY));
        assertThat(jsonError.getMessage(), containsString("flow.namespace"));

        // flow is not created
        assertThrows(HttpClientResponseException.class, () -> {
            client.toBlocking().retrieve(HttpRequest.GET("/api/v1/flows/org.kestra.another/f6"), Flow.class);
        });

        // flow is not updated
        retrieve = client.toBlocking().retrieve(GET("/api/v1/flows/org.kestra.updatenamespace/f4"), Flow.class);
        assertThat(retrieve.getInputs().get(0).getName(), is("4"));
    }

    @Test
    void createFlow() {
        Flow flow = generateFlow("org.kestra.unittest", "a");

        Flow result = client.toBlocking().retrieve(POST("/api/v1/flows", flow), Flow.class);

        assertThat(result.getId(), is(flow.getId()));
        assertThat(result.getInputs().get(0).getName(), is("a"));

        Flow get = client.toBlocking().retrieve(HttpRequest.GET("/api/v1/flows/" + flow.getNamespace() + "/" + flow.getId()), Flow.class);
        assertThat(get.getId(), is(flow.getId()));
        assertThat(get.getInputs().get(0).getName(), is("a"));

    }

    @Test
    void deleteFlow() {
        Flow flow = generateFlow("org.kestra.unittest", "a");

        Flow result = client.toBlocking().retrieve(POST("/api/v1/flows", flow), Flow.class);
        assertThat(result.getId(), is(flow.getId()));
        assertThat(result.getRevision(), is(1));

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

        Flow flow = generateFlow(flowId, "org.kestra.unittest", "a");

        Flow result = client.toBlocking().retrieve(POST("/api/v1/flows", flow), Flow.class);

        assertThat(result.getId(), is(flow.getId()));
        assertThat(result.getInputs().get(0).getName(), is("a"));

        flow = generateFlow(flowId, "org.kestra.unittest", "b");

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

    @SuppressWarnings("OptionalGetWithoutIsPresent")
    @Test
    void invalidUpdateFlow() {
        String flowId = FriendlyId.createFriendlyId();

        Flow flow = generateFlow(flowId, "org.kestra.unittest", "a");
        Flow result = client.toBlocking().retrieve(POST("/api/v1/flows", flow), Flow.class);

        assertThat(result.getId(), is(flow.getId()));

        Flow finalFlow = generateFlow(FriendlyId.createFriendlyId(), "org.kestra.unittest2", "b");;

        HttpClientResponseException e = assertThrows(HttpClientResponseException.class, () -> {
            client.toBlocking().exchange(
                PUT("/api/v1/flows/" + flow.getNamespace() + "/" + flowId, finalFlow),
                Argument.of(Flow.class),
                Argument.of(JsonError.class)
            );
        });

        JsonError jsonError = e.getResponse().getBody(JsonError.class).get();

        assertThat(e.getStatus(), is(UNPROCESSABLE_ENTITY));
        assertThat(jsonError.getMessage(), containsString("flow.id"));
        assertThat(jsonError.getMessage(), containsString("flow.namespace"));
    }

    @Test
    @SuppressWarnings("unchecked")
    @Disabled("This test can only be run against elastic search, not in memory mode")
    void testSearchAndAggregate() throws TimeoutException {
        // Run several execution
        Execution full = runnerUtils.runOne(TESTS_FLOW_NS, "full");
        Execution minimal = runnerUtils.runOne(TESTS_FLOW_NS, "minimal");
        Execution logs = runnerUtils.runOne(TESTS_FLOW_NS, "logs");
        Execution seqWithLocalErrors = runnerUtils.runOne(TESTS_FLOW_NS, "sequential-with-local-errors");
        Execution seqWithGlobalErrors = runnerUtils.runOne(TESTS_FLOW_NS, "sequential-with-global-errors");

        assertThat(full.getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(minimal.getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(logs.getState().getCurrent(), is(State.Type.SUCCESS));
        assertThat(seqWithLocalErrors.getState().getCurrent(), is(State.Type.FAILED));
        assertThat(seqWithGlobalErrors.getState().getCurrent(), is(State.Type.FAILED));

        final String query = "namespace:org.kestra.tests";

        PagedResults<Execution> aggFind = client.toBlocking().retrieve(
            HttpRequest.GET("/api/v1/flows/searchAndAggregate?q=*&startDate=2020-01-26T15:30:33.243Z" + query),
            Argument.of(PagedResults.class, ExecutionMetricsAggregation.class)
        );

        assertThat(aggFind.getTotal(), greaterThanOrEqualTo(5L));
    }

    @Test
    void listDistinctNamespace() {
        List<String> namespaces = client.toBlocking().retrieve(
            HttpRequest.GET("/api/v1/flows/distinct-namespaces"), Argument.listOf(String.class));

        assertThat(namespaces.size(), is(2));
    }

    private Flow generateFlow(String namespace, String inputName) {
        return generateFlow(FriendlyId.createFriendlyId(), namespace, inputName);
    }

    private Flow generateFlow(String friendlyId, String namespace, String inputName) {
        return Flow.builder()
            .id(friendlyId)
            .namespace(namespace)
            .inputs(ImmutableList.of(Input.builder().type(Input.Type.STRING).name(inputName).build()))
            .tasks(Collections.singletonList(Return.builder()
                .id("test")
                .type(Return.class.getName())
                .format("test")
                .build()))
            .build();
    }
}
