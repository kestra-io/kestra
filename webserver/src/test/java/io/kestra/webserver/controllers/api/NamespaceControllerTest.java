package io.kestra.webserver.controllers.api;

import com.devskiller.friendly_id.FriendlyId;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.namespaces.Namespace;
import io.kestra.core.models.topologies.FlowNode;
import io.kestra.core.models.topologies.FlowRelation;
import io.kestra.core.models.topologies.FlowTopology;
import io.kestra.core.models.topologies.FlowTopologyGraph;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.repositories.FlowTopologyRepositoryInterface;
import io.kestra.plugin.core.log.Log;
import io.kestra.webserver.models.namespaces.NamespaceWithDisabled;
import io.kestra.webserver.responses.PagedResults;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@KestraTest
public class NamespaceControllerTest {
    @Inject
    @Client("/")
    private ReactorHttpClient client;

    @Inject
    private FlowRepositoryInterface flowRepository;

    @Inject
    private FlowTopologyRepositoryInterface flowTopologyRepository;

    @BeforeEach
    void reset() {
        flowRepository.findAllWithSourceForAllTenants().forEach(flowRepository::delete);
    }

    @Test
    void get() {
        flow("my.ns");
        Namespace namespace = client.toBlocking().retrieve(
            HttpRequest.GET("/api/v1/namespaces/my.ns"),
            Namespace.class
        );

        assertThat(namespace.getId(), is("my.ns"));
        assertThat(namespace.isDeleted(), is(false));
    }

    @SuppressWarnings("unchecked")
    @Test
    void list() {
        flow("my.ns");
        flow("my.ns.flow");
        flow("another.ns");

        PagedResults<NamespaceWithDisabled> list = client.toBlocking().retrieve(
            HttpRequest.GET("/api/v1/namespaces/search"),
            Argument.of(PagedResults.class, NamespaceWithDisabled.class)
        );
        assertThat(list.getTotal(), is(6L));
        assertThat(list.getResults().size(), is(6));
        assertThat(list.getResults(), everyItem(hasProperty("disabled", is(true))));
        assertThat(list.getResults().stream().map(NamespaceWithDisabled::getId).toList(), containsInAnyOrder(
            "my", "my.ns", "my.ns.flow",
            "another", "another.ns", "system"
        ));


        list = client.toBlocking().retrieve(
            HttpRequest.GET("/api/v1/namespaces/search?size=2&sort=id:desc"),
            Argument.of(PagedResults.class, NamespaceWithDisabled.class)
        );
        assertThat(list.getTotal(), is(6L));
        assertThat(list.getResults().size(), is(2));
        assertThat(list.getResults().getFirst().getId(), is("system"));
        assertThat(list.getResults().get(1).getId(), is("my.ns.flow"));

        list = client.toBlocking().retrieve(
            HttpRequest.GET("/api/v1/namespaces/search?page=2&size=2&sort=id:desc"),
            Argument.of(PagedResults.class, NamespaceWithDisabled.class)
        );
        assertThat(list.getTotal(), is(6L));
        assertThat(list.getResults().size(), is(2));
        assertThat(list.getResults().getFirst().getId(), is("my.ns"));
        assertThat(list.getResults().get(1).getId(), is("my"));

        list = client.toBlocking().retrieve(
            HttpRequest.GET("/api/v1/namespaces/search?q=ns"),
            Argument.of(PagedResults.class, NamespaceWithDisabled.class)
        );
        assertThat(list.getTotal(), is(3L));
        assertThat(list.getResults().size(), is(3));
    }

    @Test
    void namespaceTopology() {
        flowTopologyRepository.save(createSimpleFlowTopology("flow-a", "flow-b"));
        flowTopologyRepository.save(createSimpleFlowTopology("flow-a", "flow-c"));

        FlowTopologyGraph retrieve = client.toBlocking().retrieve(
            HttpRequest.GET("/api/v1/namespaces/topology.namespace/dependencies"),
            Argument.of(FlowTopologyGraph.class)
        );

        assertThat(retrieve.getNodes().size(), is(3));
        assertThat(retrieve.getEdges().size(), is(2));
    }

    protected Flow flow(String namespace) {
        Flow flow = Flow.builder()
            .id("flow-" + FriendlyId.createFriendlyId())
            .namespace(namespace)
            .tasks(List.of(
                Log.builder()
                    .id("log")
                    .type(Log.class.getName())
                    .message("Hello")
                    .build()
            ))
            .build();
        return flowRepository.create(flow, flow.generateSource(), flow);
    }

    protected FlowTopology createSimpleFlowTopology(String flowA, String flowB) {
        return FlowTopology.builder()
            .relation(FlowRelation.FLOW_TASK)
            .source(FlowNode.builder()
                .id(flowA)
                .namespace("topology.namespace")
                .uid(flowA)
                .build()
            )
            .destination(FlowNode.builder()
                .id(flowB)
                .namespace("topology.namespace")
                .uid(flowB)
                .build()
            )
            .build();
    }

}
