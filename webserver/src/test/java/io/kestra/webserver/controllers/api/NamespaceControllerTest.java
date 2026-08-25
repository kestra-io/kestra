package io.kestra.webserver.controllers.api;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.devskiller.friendly_id.FriendlyId;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.GenericFlow;
import io.kestra.core.models.namespaces.Namespace;
import io.kestra.core.models.topologies.FlowNode;
import io.kestra.core.models.topologies.FlowRelation;
import io.kestra.core.models.topologies.FlowTopology;
import io.kestra.core.models.topologies.FlowTopologyGraph;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.repositories.FlowTopologyRepositoryInterface;
import io.kestra.plugin.core.log.Log;
import io.kestra.webserver.responses.PagedResults;

import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
            HttpRequest.GET("/api/v1/main/namespaces/my.ns"),
            Namespace.class
        );

        assertThat(namespace.getId()).isEqualTo("my.ns");
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldKeepRouteDeclaredParamsWorkingAlongsideFilters() {
        // GIVEN — `existingOnly` is both a filter field name and a real argument of this route (bound as `existing`),
        // so the legacy-flat-param safeguard must not reject it. See kestra-io/kestra-ee#10326.
        flow("my.ns");

        // WHEN
        PagedResults<Namespace> list = client.toBlocking().retrieve(
            HttpRequest.GET("/api/v1/main/namespaces/search?existing=true"),
            Argument.of(PagedResults.class, Namespace.class)
        );

        // THEN
        assertThat(list.getTotal()).isNotNull();

        // AND — the argument name is NOT a second way in: `existingOnly` is never read by Micronaut, so it must be
        // rejected like any other silently-ignored flat param rather than spared as "route-declared"
        HttpClientResponseException e = assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().retrieve(
                HttpRequest.GET("/api/v1/main/namespaces/search?existingOnly=true"),
                Argument.of(PagedResults.class, Namespace.class)
            )
        );
        assertThat(e.getStatus().getCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY.getCode());
        assertThat(e.getMessage()).contains("[existingOnly]");
    }

    @SuppressWarnings("unchecked")
    @Test
    void list() {
        flow("my.ns");
        flow("my.ns.flow");
        flow("another.ns");

        PagedResults<Namespace> list = client.toBlocking().retrieve(
            HttpRequest.GET("/api/v1/main/namespaces/search"),
            Argument.of(PagedResults.class, Namespace.class)
        );
        assertThat(list.getTotal()).isEqualTo(6L);
        assertThat(list.getResults().size()).isEqualTo(6);
        assertThat(list.getResults().stream().map(Namespace::getId).toList()).containsExactlyInAnyOrder("my", "my.ns", "my.ns.flow", "another", "another.ns", "system");

        list = client.toBlocking().retrieve(
            HttpRequest.GET("/api/v1/main/namespaces/search?size=2&sort=id:desc"),
            Argument.of(PagedResults.class, Namespace.class)
        );
        assertThat(list.getTotal()).isEqualTo(6L);
        assertThat(list.getResults().size()).isEqualTo(2);
        assertThat(list.getResults().getFirst().getId()).isEqualTo("system");
        assertThat(list.getResults().get(1).getId()).isEqualTo("my.ns.flow");

        list = client.toBlocking().retrieve(
            HttpRequest.GET("/api/v1/main/namespaces/search?page=2&size=2&sort=id:desc"),
            Argument.of(PagedResults.class, Namespace.class)
        );
        assertThat(list.getTotal()).isEqualTo(6L);
        assertThat(list.getResults().size()).isEqualTo(2);
        assertThat(list.getResults().getFirst().getId()).isEqualTo("my.ns");
        assertThat(list.getResults().get(1).getId()).isEqualTo("my");

        list = client.toBlocking().retrieve(
            HttpRequest.GET("/api/v1/main/namespaces/search?filters[q][EQUALS]=ns"),
            Argument.of(PagedResults.class, Namespace.class)
        );
        assertThat(list.getTotal()).isEqualTo(3L);
        assertThat(list.getResults().size()).isEqualTo(3);

        list = client.toBlocking().retrieve(
            HttpRequest.GET("/api/v1/main/namespaces/search?page=4&size=2&sort=id:desc"),
            Argument.of(PagedResults.class, Namespace.class)
        );
        assertThat(list.getTotal()).isEqualTo(6L);
        assertThat(list.getResults()).isEmpty();
    }

    @Test
    void namespaceTopology() {
        flowTopologyRepository.save(createSimpleFlowTopology("flow-a", "flow-b"));
        flowTopologyRepository.save(createSimpleFlowTopology("flow-a", "flow-c"));

        FlowTopologyGraph retrieve = client.toBlocking().retrieve(
            HttpRequest.GET("/api/v1/main/namespaces/topology.namespace/dependencies"),
            Argument.of(FlowTopologyGraph.class)
        );

        assertThat(retrieve.getNodes().size()).isEqualTo(3);
        assertThat(retrieve.getEdges().size()).isEqualTo(2);
    }

    @Test
    void inheritedSecrets() {
        Map<String, Set<String>> parentInheritedSecrets = client.toBlocking().retrieve(
            HttpRequest.GET("/api/v1/main/namespaces/any.ns/inherited-secrets"),
            Argument.mapOf(Argument.of(String.class), Argument.setOf(String.class))
        );
        assertThat(parentInheritedSecrets.size()).isEqualTo(1);
        assertThat(parentInheritedSecrets.get("any.ns")).isEqualTo(Set.of("WEBHOOK_KEY", "PASSWORD", "NEW_LINE", "MY_SECRET"));
    }

    protected Flow flow(String namespace) {
        Flow flow = Flow.builder()
            .id("flow-" + FriendlyId.createFriendlyId())
            .namespace(namespace)
            .tenantId("main")
            .tasks(
                List.of(
                    Log.builder()
                        .id("log")
                        .type(Log.class.getName())
                        .message("Hello")
                        .build()
                )
            )
            .build();
        return flowRepository.create(GenericFlow.of(flow));
    }

    protected FlowTopology createSimpleFlowTopology(String flowA, String flowB) {
        return FlowTopology.builder()
            .relation(FlowRelation.FLOW_TASK)
            .source(
                FlowNode.builder()
                    .id(flowA)
                    .namespace("topology.namespace")
                    .tenantId("main")
                    .uid(flowA)
                    .build()
            )
            .destination(
                FlowNode.builder()
                    .id(flowB)
                    .namespace("topology.namespace")
                    .tenantId("main")
                    .uid(flowB)
                    .build()
            )
            .build();
    }

}
