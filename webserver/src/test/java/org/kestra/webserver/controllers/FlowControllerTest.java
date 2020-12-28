package org.kestra.webserver.controllers;

import com.google.common.collect.ImmutableList;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.RxHttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.hateoas.JsonError;
import org.junit.jupiter.api.Test;
import org.kestra.core.Helpers;
import org.kestra.core.models.flows.Flow;
import org.kestra.core.models.flows.Input;
import org.kestra.core.models.hierarchies.FlowGraph;
import org.kestra.core.models.tasks.Task;
import org.kestra.core.runners.AbstractMemoryRunnerTest;
import org.kestra.core.tasks.debugs.Return;
import org.kestra.core.utils.IdUtils;
import org.kestra.webserver.responses.PagedResults;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.inject.Inject;

import static io.micronaut.http.HttpRequest.*;
import static io.micronaut.http.HttpStatus.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
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
    void graph() {
        FlowGraph result = client.toBlocking().retrieve(HttpRequest.GET("/api/v1/flows/org.kestra" +
            ".tests/all-flowable/graph"), FlowGraph.class);

        assertThat(result.getNodes().size(), is(28));
        assertThat(result.getEdges().size(), is(31));
        assertThat(result.getClusters().size(), is(6));
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


        // send 2 same id
        e = assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().retrieve(
                POST("/api/v1/flows/org.kestra.same", Arrays.asList(
                    generateFlow("f7", "org.kestra.same", "1"),
                    generateFlow("f7", "org.kestra.same", "5")
                )),
                Argument.listOf(Flow.class)
            )
        );
        jsonError = e.getResponse().getBody(JsonError.class).get();
        assertThat(e.getStatus(), is(UNPROCESSABLE_ENTITY));
        assertThat(jsonError.getMessage(), containsString("flow.id: Duplicate"));
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
        String flowId = IdUtils.create();

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
                PUT("/api/v1/flows/" + finalFlow.getNamespace() + "/" + IdUtils.create(), finalFlow)
            );
        });
        assertThat(e.getStatus(), is(NOT_FOUND));
    }

    @Test
    void updateTaskFlow() {
        String flowId = IdUtils.create();

        Flow flow = generateFlow(flowId, "org.kestra.unittest", "a");

        Flow result = client.toBlocking().retrieve(POST("/api/v1/flows", flow), Flow.class);

        assertThat(result.getId(), is(flow.getId()));
        assertThat(result.getInputs().get(0).getName(), is("a"));

        Task task = generateTask("test", "updated task");

        Flow get = client.toBlocking().retrieve(
            PATCH("/api/v1/flows/" + flow.getNamespace() + "/" + flow.getId() + "/" + task.getId(), task),
            Flow.class
        );

        assertThat(get.getId(), is(flow.getId()));
        assertThat(((Return) get.getTasks().get(0)).getFormat(), is("updated task"));

        HttpClientResponseException e = assertThrows(HttpClientResponseException.class, () -> {
            client.toBlocking().retrieve(
                PATCH("/api/v1/flows/" + flow.getNamespace() + "/" + flow.getId() + "/test2", task),
                Flow.class
            );
        });
        assertThat(e.getStatus(), is(NOT_FOUND));
    }

    @SuppressWarnings("OptionalGetWithoutIsPresent")
    @Test
    void invalidUpdateFlow() {
        String flowId = IdUtils.create();

        Flow flow = generateFlow(flowId, "org.kestra.unittest", "a");
        Flow result = client.toBlocking().retrieve(POST("/api/v1/flows", flow), Flow.class);

        assertThat(result.getId(), is(flow.getId()));

        Flow finalFlow = generateFlow(IdUtils.create(), "org.kestra.unittest2", "b");;

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
    void listDistinctNamespace() {
        List<String> namespaces = client.toBlocking().retrieve(
            HttpRequest.GET("/api/v1/flows/distinct-namespaces"), Argument.listOf(String.class));

        assertThat(namespaces.size(), is(2));
    }

    private Flow generateFlow(String namespace, String inputName) {
        return generateFlow(IdUtils.create(), namespace, inputName);
    }

    private Flow generateFlow(String friendlyId, String namespace, String inputName) {
        return Flow.builder()
            .id(friendlyId)
            .namespace(namespace)
            .inputs(ImmutableList.of(Input.builder().type(Input.Type.STRING).name(inputName).build()))
            .tasks(Collections.singletonList(generateTask("test", "test")))
            .build();
    }

    private Task generateTask(String id, String format) {
        return Return.builder()
            .id(id)
            .type(Return.class.getName())
            .format(format)
            .build();
    }
}
