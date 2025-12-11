package io.kestra.webserver.controllers.api;

import com.google.common.collect.ImmutableList;
import io.kestra.core.Helpers;
import io.kestra.core.exceptions.InternalException;
import io.kestra.core.junit.annotations.FlakyTest;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.flows.*;
import io.kestra.core.models.flows.input.StringInput;
import io.kestra.core.models.hierarchies.FlowGraph;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.topologies.FlowNode;
import io.kestra.core.models.topologies.FlowRelation;
import io.kestra.core.models.topologies.FlowTopology;
import io.kestra.core.models.topologies.FlowTopologyGraph;
import io.kestra.core.models.validations.ValidateConstraintViolation;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.repositories.FlowTopologyRepositoryInterface;
import io.kestra.core.repositories.LocalFlowRepositoryLoader;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.serializers.YamlParser;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.TestsUtils;
import io.kestra.jdbc.JdbcTestUtils;
import io.kestra.jdbc.repository.AbstractJdbcFlowRepository;
import io.kestra.plugin.core.debug.Return;
import io.kestra.plugin.core.flow.Sequential;
import io.kestra.webserver.controllers.domain.IdWithNamespace;
import io.kestra.webserver.responses.BulkResponse;
import io.kestra.webserver.responses.PagedResults;
import io.kestra.webserver.utils.RequestUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.http.*;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.client.multipart.MultipartBody;
import io.micronaut.http.hateoas.JsonError;
import io.micronaut.http.uri.UriBuilder;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import jakarta.inject.Inject;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.event.Level;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.zip.ZipFile;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static io.micronaut.http.HttpRequest.*;
import static io.micronaut.http.HttpStatus.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest
class FlowControllerTest {
    private static final String TEST_NAMESPACE = "io.kestra.unittest";

    public static final String FLOW_PATH = "/api/v1/main/flows";

    @Inject
    @Client("/")
    ReactorHttpClient client;

    @Inject
    AbstractJdbcFlowRepository jdbcFlowRepository;

    @Inject
    private JdbcTestUtils jdbcTestUtils;

    @Inject
    protected LocalFlowRepositoryLoader repositoryLoader;

    @Inject
    private FlowTopologyRepositoryInterface flowTopologyRepository;

    @BeforeAll
    public static void beforeAll() {
        Helpers.loadExternalPluginsFromClasspath();
    }

    @BeforeEach
    protected void init() throws IOException, URISyntaxException {
        jdbcTestUtils.drop();
        jdbcTestUtils.migrate();

        TestsUtils.loads(MAIN_TENANT, repositoryLoader);
    }

    @Test
    void id() {
        String result = client.toBlocking().retrieve(HttpRequest.GET("/api/v1/main/flows/io.kestra.tests/full"), String.class);
        Flow flow = YamlParser.parse(result, Flow.class);
        assertThat(flow.getId()).isEqualTo("full");
        assertThat(flow.getTasks().size()).isEqualTo(5);
    }

    @Test
    void idNoSource() {
        Map<String, Object> map = client.toBlocking().retrieve(HttpRequest.GET("/api/v1/main/flows/io.kestra.tests/full"), Argument.mapOf(String.class, Object.class));
        assertThat(map.get("source")).isNull();

        FlowWithSource result = client.toBlocking().retrieve(HttpRequest.GET("/api/v1/main/flows/io.kestra.tests/full?source=true"), FlowWithSource.class);
        assertThat(result.getSource()).contains("#triggers:");
    }

    @Test
    void task() {
        Task result = client.toBlocking().retrieve(HttpRequest.GET("/api/v1/main/flows/io.kestra.tests/each-object/tasks/not-json"), Task.class);

        assertThat(result.getId()).isEqualTo("not-json");
        assertThat(result.getType()).isEqualTo("io.kestra.plugin.core.debug.Return");
    }

    @Test
    void taskNotFound() {
        HttpClientResponseException e = assertThrows(HttpClientResponseException.class, () -> {
            client.toBlocking().retrieve(HttpRequest.GET("/api/v1/main/flows/io.kestra.tests/full/tasks/notFound"));
        });

        assertThat(e.getStatus().getCode()).isEqualTo(HttpStatus.NOT_FOUND.getCode());
    }

    @Test
    void graph() {
        FlowGraph result = client.toBlocking().retrieve(HttpRequest.GET("/api/v1/main/flows/io.kestra.tests/all-flowable/graph"), FlowGraph.class);

        assertThat(result.getNodes().size()).isEqualTo(38);
        assertThat(result.getEdges().size()).isEqualTo(42);
        assertThat(result.getClusters().size()).isEqualTo(7);
        assertThat(result.getClusters().stream().map(FlowGraph.Cluster::getCluster).toList(), Matchers.everyItem(
            Matchers.hasProperty("uid", Matchers.not(Matchers.startsWith("cluster_cluster_")))
        ));
    }

    @Test
    void graph_FlowNotFound() {
        HttpClientResponseException exception = assertThrows(HttpClientResponseException.class, () -> client.toBlocking().retrieve(GET("/api/v1/main/flows/io.kestra.tests/unknown-flow/graph")));

        assertThat(exception.getStatus().getCode()).isEqualTo(NOT_FOUND.getCode());
        assertThat(exception.getMessage()).isEqualTo("Not Found: Unable to find flow main_io.kestra.tests_unknown-flow");
    }

    @Test
    void idNotFound() {
        HttpClientResponseException e = assertThrows(HttpClientResponseException.class, () -> {
            client.toBlocking().retrieve(HttpRequest.GET("/api/v1/main/flows/io.kestra.tests/notFound"));
        });

        assertThat(e.getStatus().getCode()).isEqualTo(HttpStatus.NOT_FOUND.getCode());
    }

    @SuppressWarnings("unchecked")
    @Test
    void searchFlowsAll() {
        PagedResults<Flow> flows = client.toBlocking().retrieve(HttpRequest.GET("/api/v1/main/flows/search?filters[q][EQUALS]=*"), Argument.of(PagedResults.class, Flow.class));
        assertThat(flows.getTotal()).isEqualTo(Helpers.FLOWS_COUNT);

        PagedResults<Flow> flows_oldParameters = client.toBlocking().retrieve(HttpRequest.GET("/api/v1/main/flows/search?q=*"), Argument.of(PagedResults.class, Flow.class));
        assertThat(flows_oldParameters.getTotal()).isEqualTo(Helpers.FLOWS_COUNT);
    }

    @SuppressWarnings("unchecked")
    @Test
    void searchFlowsMatch() {
        PagedResults<Flow> flows = client.toBlocking().retrieve(HttpRequest.GET("/api/v1/main/flows/search?filters[q][EQUALS]=io.kestra.tests2"), Argument.of(PagedResults.class, Flow.class));
        assertThat(flows.getTotal()).isEqualTo(1L);
    }

    @SuppressWarnings("unchecked")
    @Test
    void searchFlowsNotEqualsQuery() {
        PagedResults<Flow> flows = client.toBlocking().retrieve(HttpRequest.GET("/api/v1/main/flows/search?filters[q][NOT_EQUALS]=io.kestra.tests2"), Argument.of(PagedResults.class, Flow.class));
        assertThat(flows.getTotal()).isEqualTo(Helpers.FLOWS_COUNT - 1);
    }

    @SuppressWarnings("unchecked")
    @Test
    void searchFlows_shouldReturnNothingForOppositeQuery() {
        PagedResults<Flow> flows = client.toBlocking().retrieve(HttpRequest.GET("/api/v1/main/flows/search?filters[q][EQUALS]=io.kestra.tests2&filters[q][NOT_EQUALS]=io.kestra.tests2"), Argument.of(PagedResults.class, Flow.class));
        assertThat(flows.getTotal()).isEqualTo(0L);
    }

    @SuppressWarnings("unchecked")
    @Test
    void searchFlowsByNamespacePrefix() {
        assertThat(client.toBlocking().retrieve(HttpRequest.GET("/api/v1/main/flows/search?filters[namespace][PREFIX]=io.kestra.tests2"), Argument.of(PagedResults.class, Flow.class))
            .getTotal())
            .isEqualTo(1L);

        assertThat(client.toBlocking().retrieve(HttpRequest.GET("/api/v1/main/flows/search?filters[namespace][PREFIX]=io.kestra.tests"), Argument.of(PagedResults.class, Flow.class))
            .getTotal())
            .isEqualTo(Helpers.FLOWS_COUNT);
    }

    @Test
    void getFlowFlowsByNamespace() throws IOException, URISyntaxException {
        TestsUtils.loads(MAIN_TENANT, repositoryLoader, FlowControllerTest.class.getClassLoader().getResource("flows/getflowsbynamespace"));

        List<Flow> flows = client.toBlocking().retrieve(HttpRequest.GET("/api/v1/main/flows/io.kestra.unittest.flowsbynamespace"), Argument.listOf(Flow.class));
        assertThat(flows.size()).isEqualTo(2);
        assertThat(flows.stream().map(Flow::getId).toList()).containsExactlyInAnyOrder("getbynamespace-test-flow", "getbynamespace-test-flow2");
    }

    @SuppressWarnings("OptionalGetWithoutIsPresent")
    @Test
    void updateFlowFromJsonFlowsInNamespace() {
        // initial création
        List<Flow> flows = Arrays.asList(
            generateFlow("f1", "io.kestra.updatenamespace", "1"),
            generateFlow("f2", "io.kestra.updatenamespace", "2"),
            generateFlow("f3", "io.kestra.updatenamespace", "3")
        );

        List<Flow> updated = client.toBlocking().retrieve(HttpRequest.POST("/api/v1/main/flows/io.kestra.updatenamespace", flows), Argument.listOf(Flow.class));
        assertThat(updated.size()).isEqualTo(3);

        Flow retrieve = parseFlow(client.toBlocking().retrieve(GET("/api/v1/main/flows/io.kestra.updatenamespace/f1"), String.class));
        assertThat(retrieve.getId()).isEqualTo("f1");

        // update
        flows = Arrays.asList(
            generateFlow("f3", "io.kestra.updatenamespace", "3-3"),
            generateFlow("f4", "io.kestra.updatenamespace", "4")
        );

        // f3 & f4 must be updated
        updated = client.toBlocking().retrieve(HttpRequest.POST("/api/v1/main/flows/io.kestra.updatenamespace", flows), Argument.listOf(Flow.class));
        assertThat(updated.size()).isEqualTo(4);
        assertThat(updated.get(2).getInputs().getFirst().getId()).isEqualTo("3-3");
        assertThat(updated.get(3).getInputs().getFirst().getId()).isEqualTo("4");

        // f1 & f2 must be deleted
        assertThrows(HttpClientResponseException.class, () -> {
            client.toBlocking().retrieve(HttpRequest.GET("/api/v1/main/flows/io.kestra.updatenamespace/f1"), Flow.class);
        });

        assertThrows(HttpClientResponseException.class, () -> {
            client.toBlocking().retrieve(HttpRequest.GET("/api/v1/main/flows/io.kestra.updatenamespace/f2"), Flow.class);
        });

        // create a flow in another namespace
        Flow invalid = generateFlow("invalid1", "io.kestra.othernamespace", "1");
        client.toBlocking().retrieve(POST("/api/v1/main/flows", invalid), Flow.class);

        HttpClientResponseException e = assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().retrieve(
                POST("/api/v1/main/flows/io.kestra.updatenamespace", Arrays.asList(
                    invalid,
                    generateFlow("f4", "io.kestra.updatenamespace", "5"),
                    generateFlow("f6", "io.kestra.another", "5")
                )),
                Argument.listOf(Flow.class)
            )
        );
        String jsonError = e.getResponse().getBody(String.class).get();
        assertThat(e.getStatus().getCode()).isEqualTo(UNPROCESSABLE_ENTITY.getCode());
        assertThat(jsonError).contains("flow.namespace");

        // flow is not created
        assertThrows(HttpClientResponseException.class, () -> {
            client.toBlocking().retrieve(HttpRequest.GET("/api/v1/main/flows/io.kestra.another/f6"), Flow.class);
        });

        // flow is not updated
        retrieve = parseFlow(client.toBlocking().retrieve(GET("/api/v1/main/flows/io.kestra.updatenamespace/f4"), String.class));
        assertThat(retrieve.getInputs().getFirst().getId()).isEqualTo("4");

        // send 2 same id
        e = assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().retrieve(
                POST("/api/v1/main/flows/io.kestra.same", Arrays.asList(
                    generateFlow("f7", "io.kestra.same", "1"),
                    generateFlow("f7", "io.kestra.same", "5")
                )),
                Argument.listOf(Flow.class)
            )
        );
        jsonError = e.getResponse().getBody(String.class).get();
        assertThat(e.getStatus().getCode()).isEqualTo(UNPROCESSABLE_ENTITY.getCode());
        assertThat(jsonError).contains("flow.id: Duplicate");

        // cleanup
        try {
            client.toBlocking().exchange(DELETE("/api/v1/main/flows/io.kestra.othernamespace/invalid1"));
            for (int i = 1; i <= 7; i++) {
                client.toBlocking().exchange(DELETE("/api/v1/main/flows/io.kestra.updatenamespace/f1"));
            }
        } catch (Exception ignored) {

        }
    }

    @Test
    void updateFlowFlowsInNamespaceAsString() {
        // initial création
        String flows = String.join("---\n", Arrays.asList(
            generateFlowAsString("flow1","io.kestra.updatenamespace","a"),
            generateFlowAsString("flow2","io.kestra.updatenamespace","a"),
            generateFlowAsString("flow3","io.kestra.updatenamespace","a")
        ));

        List<FlowWithSource> updated = client.toBlocking()
            .retrieve(
                HttpRequest.POST("/api/v1/main/flows/io.kestra.updatenamespace", flows)
                    .contentType(MediaType.APPLICATION_YAML),
                Argument.listOf(FlowWithSource.class)
            );
        assertThat(updated.size()).isEqualTo(3);

        client.toBlocking().exchange(DELETE("/api/v1/main/flows/io.kestra.updatenamespace/flow1"));
        client.toBlocking().exchange(DELETE("/api/v1/main/flows/io.kestra.updatenamespace/flow2"));
        client.toBlocking().exchange(DELETE("/api/v1/main/flows/io.kestra.updatenamespace/flow3"));
    }

    @Test
    void bulk() {
        // initial création
        String flows = String.join("---\n", Arrays.asList(
            generateFlowAsString("flow1","io.kestra.bulk","a"),
            generateFlowAsString("flow2","io.kestra.bulk","a"),
            generateFlowAsString("flow3","io.kestra.bulk","a")
        ));

        List<FlowWithSource> updated = client.toBlocking()
            .retrieve(
                HttpRequest.POST("/api/v1/main/flows/bulk?namespace=io.kestra.bulk", flows)
                    .contentType(MediaType.APPLICATION_YAML),
                Argument.listOf(FlowWithSource.class)
            );
        assertThat(updated.size()).isEqualTo(3);

        // resend the same request, should not add revision
        updated = client.toBlocking()
            .retrieve(
                HttpRequest.POST("/api/v1/main/flows/bulk?namespace=io.kestra.bulk", flows)
                    .contentType(MediaType.APPLICATION_YAML),
                Argument.listOf(FlowWithSource.class)
            );
        assertThat(updated.size()).isEqualTo(3);

        assertThat(updated.stream().map(AbstractFlow::getRevision).distinct().toList().size()).isEqualTo(1);
        assertThat(updated.stream().map(AbstractFlow::getRevision).distinct().toList().getFirst()).isEqualTo(1);

        client.toBlocking().exchange(DELETE("/api/v1/main/flows/io.kestra.bulk/flow1"));
        client.toBlocking().exchange(DELETE("/api/v1/main/flows/io.kestra.bulk/flow2"));
        client.toBlocking().exchange(DELETE("/api/v1/main/flows/io.kestra.bulk/flow3"));
    }

    @Test
    void createFlowFromJsonFlow() {
        Flow flow = generateFlow(TEST_NAMESPACE, "a");

        Flow result = parseFlow(client.toBlocking().retrieve(POST("/api/v1/main/flows", flow), String.class));

        assertThat(result.getId()).isEqualTo(flow.getId());
        assertThat(result.getInputs().getFirst().getId()).isEqualTo("a");

        Flow get = parseFlow(client.toBlocking().retrieve(HttpRequest.GET("/api/v1/main/flows/" + flow.getNamespace() + "/" + flow.getId()), String.class));
        assertThat(get.getId()).isEqualTo(flow.getId());
        assertThat(get.getInputs().getFirst().getId()).isEqualTo("a");
    }

    @Test
    void createFlowFromJsonFlowWithJsonLabels() {
        Map<String, Object> flow = JacksonMapper.toMap(generateFlow(TEST_NAMESPACE, "a"));
        flow.put("labels", Map.of("a", "b"));

        Flow result = parseFlow(client.toBlocking().retrieve(POST("/api/v1/main/flows", flow), String.class));

        assertThat(result.getId()).isEqualTo(flow.get("id"));
        assertThat(result.getLabels().getFirst().key()).isEqualTo("a");
        assertThat(result.getLabels().getFirst().value()).isEqualTo("b");
    }

    @Test
    void deletedFlow() {
        Flow flow = generateFlow(TEST_NAMESPACE, "a");

        FlowWithSource result = client.toBlocking().retrieve(POST("/api/v1/main/flows", flow), FlowWithSource.class);
        assertThat(result.getId()).isEqualTo(flow.getId());
        assertThat(result.getRevision()).isEqualTo(1);

        HttpResponse<Void> deleteResult = client.toBlocking().exchange(
            DELETE("/api/v1/main/flows/" + flow.getNamespace() + "/" + flow.getId())
        );
        assertThat(deleteResult.getStatus().getCode()).isEqualTo(NO_CONTENT.getCode());

        HttpClientResponseException e = assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<Void> response = client.toBlocking().exchange(
                DELETE("/api/v1/main/flows/" + flow.getNamespace() + "/" + flow.getId())
            );
        });

        assertThat(e.getStatus().getCode()).isEqualTo(NOT_FOUND.getCode());

        String deletedResult = client.toBlocking().retrieve(HttpRequest.GET("/api/v1/main/flows/" + flow.getNamespace() + "/" + flow.getId() + "?allowDeleted=true"), String.class);
        Flow deletedFlow = YamlParser.parse(deletedResult, Flow.class);

        assertThat(deletedFlow.isDeleted()).isTrue();
    }

    @Test
    void updateFlowFlowFromJson() {
        String flowId = IdUtils.create();

        Flow flow = generateFlow(flowId, TEST_NAMESPACE, "a");

        Flow result = client.toBlocking().retrieve(POST("/api/v1/main/flows", flow), Flow.class);

        assertThat(result.getId()).isEqualTo(flow.getId());
        assertThat(result.getInputs().getFirst().getId()).isEqualTo("a");

        flow = generateFlow(flowId, TEST_NAMESPACE, "b");

        Flow get = client.toBlocking().retrieve(
            PUT("/api/v1/main/flows/" + flow.getNamespace() + "/" + flow.getId(), flow),
            Flow.class
        );

        assertThat(get.getId()).isEqualTo(flow.getId());
        assertThat(get.getInputs().getFirst().getId()).isEqualTo("b");

        Flow finalFlow = flow;
        HttpClientResponseException e = assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<Void> response = client.toBlocking().exchange(
                PUT("/api/v1/main/flows/" + finalFlow.getNamespace() + "/" + IdUtils.create(), finalFlow)
            );
        });
        assertThat(e.getStatus().getCode()).isEqualTo(NOT_FOUND.getCode());
    }

    @Test
    void updateFlowFlowFromJsonMultilineJson() {
        String flowId = IdUtils.create();

        Flow flow = generateFlowWithFlowable(flowId, TEST_NAMESPACE, "\n \n a         \nb\nc");

        Flow result = client.toBlocking().retrieve(POST("/api/v1/main/flows", flow), Flow.class);
        assertThat(result.getId()).isEqualTo(flow.getId());

        FlowWithSource withSource = client.toBlocking().retrieve(GET("/api/v1/main/flows/" + flow.getNamespace() + "/" + flow.getId() + "?source=true").contentType(MediaType.APPLICATION_YAML), FlowWithSource.class);
        assertThat(withSource.getId()).isEqualTo(flow.getId());
        assertThat(withSource.getSource()).contains("format: |2-");
    }

    @Test
    void updateFlowTaskFlowFromJson() throws InternalException {
        String flowId = IdUtils.create();

        Flow flow = generateFlowWithFlowable(flowId, TEST_NAMESPACE, "a");

        Flow result = client.toBlocking().retrieve(POST("/api/v1/main/flows", flow), Flow.class);
        assertThat(result.getId()).isEqualTo(flow.getId());

        Task task = generateTask("test2", "updated task");

        Flow get = client.toBlocking().retrieve(
            PATCH("/api/v1/main/flows/" + flow.getNamespace() + "/" + flow.getId() + "/" + task.getId(), task),
            Flow.class
        );

        assertThat(get.getId()).isEqualTo(flow.getId());
        assertThat(((Return) get.findTaskByTaskId("test2")).getFormat().toString()).isEqualTo("updated task");

        HttpClientResponseException e = assertThrows(HttpClientResponseException.class, () -> {
            client.toBlocking().retrieve(
                PATCH("/api/v1/main/flows/" + flow.getNamespace() + "/" + flow.getId() + "/test6", task),
                Flow.class
            );
        });
        assertThat(e.getStatus().getCode()).isEqualTo(UNPROCESSABLE_ENTITY.getCode());

        e = assertThrows(HttpClientResponseException.class, () -> {
            client.toBlocking().retrieve(
                PATCH("/api/v1/main/flows/" + flow.getNamespace() + "/" + flow.getId() + "/test6", generateTask("test6", "updated task")),
                Flow.class
            );
        });
        assertThat(e.getStatus().getCode()).isEqualTo(NOT_FOUND.getCode());
    }

    @SuppressWarnings("OptionalGetWithoutIsPresent")
    @Test
    void invalidUpdateFlowFlowFromJson() {
        String flowId = IdUtils.create();

        Flow flow = generateFlow(flowId, TEST_NAMESPACE, "a");
        Flow result = client.toBlocking().retrieve(POST("/api/v1/main/flows", flow), Flow.class);

        assertThat(result.getId()).isEqualTo(flow.getId());

        Flow finalFlow = generateFlow(IdUtils.create(), "io.kestra.unittest2", "b");
        ;

        HttpClientResponseException e = assertThrows(HttpClientResponseException.class, () -> {
            client.toBlocking().exchange(
                PUT("/api/v1/main/flows/" + flow.getNamespace() + "/" + flowId, finalFlow),
                Argument.of(Flow.class),
                Argument.of(JsonError.class)
            );
        });

        String jsonError = e.getResponse().getBody(String.class).get();

        assertThat(e.getStatus().getCode()).isEqualTo(UNPROCESSABLE_ENTITY.getCode());
        assertThat(jsonError).contains("flow.id");
        assertThat(jsonError).contains("flow.namespace");
    }

    @Test
    void listDistinctNamespaces() {
        List<String> namespaces = client.toBlocking().retrieve(
            HttpRequest.GET("/api/v1/main/flows/distinct-namespaces"), Argument.listOf(String.class));

        assertThat(namespaces.size()).isEqualTo(13);
    }

    @Test
    void createFlowFromJsonFlowFromString() {
        String flow = generateFlowAsString(TEST_NAMESPACE,"a");
        Flow assertFlow = parseFlow(flow);

        FlowWithSource result = client.toBlocking().retrieve(POST("/api/v1/main/flows", flow).contentType(MediaType.APPLICATION_YAML), FlowWithSource.class);

        assertThat(result.getId()).isEqualTo(assertFlow.getId());
        assertThat(result.getInputs().getFirst().getId()).isEqualTo("a");

        FlowWithSource get = client.toBlocking().retrieve(HttpRequest.GET("/api/v1/main/flows/io.kestra.unittest/" + assertFlow.getId() + "?source=true"), FlowWithSource.class);
        assertThat(get.getId()).isEqualTo(assertFlow.getId());
        assertThat(get.getInputs().getFirst().getId()).isEqualTo("a");
        assertThat(get.getSource()).contains(" Comment i added");
    }

    @Test
    void createFlowFromJsonInvalidFlowFromString() throws IOException {
        URL resource = TestsUtils.class.getClassLoader().getResource("flows/simpleInvalidFlow.yaml");
        assert resource != null;

        String flow = Files.readString(Path.of(resource.getPath()), Charset.defaultCharset());

        HttpClientResponseException e = assertThrows(HttpClientResponseException.class, () -> {
            client.toBlocking().retrieve(
                POST("/api/v1/main/flows", flow).contentType(MediaType.APPLICATION_YAML),
                Flow.class
            );
        });
        assertThat(e.getStatus().getCode()).isEqualTo(UNPROCESSABLE_ENTITY.getCode());
    }

    @Test
    @FlakyTest
    void updateFlowFlowFromJsonFromString() throws IOException {
        String flow = generateFlowAsString("updatedFlow", TEST_NAMESPACE,"a");
        Flow assertFlow = parseFlow(flow);

        FlowWithSource result = client.toBlocking().retrieve(POST("/api/v1/main/flows", flow).contentType(MediaType.APPLICATION_YAML), FlowWithSource.class);

        assertThat(result.getId()).isEqualTo(assertFlow.getId());
        assertThat(result.getInputs().getFirst().getId()).isEqualTo("a");

        flow = generateFlowAsString("updatedFlow", TEST_NAMESPACE,"b");

        FlowWithSource get = client.toBlocking().retrieve(
            PUT("/api/v1/main/flows/io.kestra.unittest/updatedFlow", flow).contentType(MediaType.APPLICATION_YAML),
            FlowWithSource.class
        );

        assertThat(get.getId()).isEqualTo(assertFlow.getId());
        assertThat(get.getInputs().getFirst().getId()).isEqualTo("b");

        String finalFlow = flow;
        HttpClientResponseException e = assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<Void> response = client.toBlocking().exchange(
                PUT("/api/v1/main/flows/io.kestra.unittest/" + IdUtils.create(), finalFlow).contentType(MediaType.APPLICATION_YAML)
            );
        });
        assertThat(e.getStatus().getCode()).isEqualTo(NOT_FOUND.getCode());
    }

    @Test
    void updateFlowInvalidFlowFromJsonFromString() throws IOException {
        URL resource = TestsUtils.class.getClassLoader().getResource("flows/simpleFlow.yaml");
        assert resource != null;

        String flow = Files.readString(Path.of(resource.getPath()), Charset.defaultCharset());

        FlowWithSource result = client.toBlocking().retrieve(POST("/api/v1/main/flows", flow).contentType(MediaType.APPLICATION_YAML), FlowWithSource.class);

        assertThat(result.getId()).isEqualTo("test-flow");

        resource = TestsUtils.class.getClassLoader().getResource("flows/simpleInvalidFlowUpdate.yaml");
        assert resource != null;

        String finalFlow = Files.readString(Path.of(resource.getPath()), Charset.defaultCharset());

        HttpClientResponseException e = assertThrows(HttpClientResponseException.class, () -> {
            client.toBlocking().exchange(
                PUT("/api/v1/main/flows/io.kestra.unittest/test-flow", finalFlow).contentType(MediaType.APPLICATION_YAML),
                Argument.of(Flow.class),
                Argument.of(JsonError.class)
            );
        });

        String jsonError = e.getResponse().getBody(String.class).get();

        assertThat(e.getStatus().getCode()).isEqualTo(UNPROCESSABLE_ENTITY.getCode());
        assertThat(jsonError).contains("flow.id");
        assertThat(jsonError).contains("flow.namespace");
    }

    /**
     * this is testing legacy > new filters /by-query endpoints, related file is {@link RequestUtils#getFiltersOrDefaultToLegacyMapping(List, String, String, String, String, Level, ZonedDateTime, ZonedDateTime, List, List, Duration, ExecutionRepositoryInterface.ChildFilter, List, String, String)}
     */
    @Test
    void exportFlowsByQueryForANamespace() throws IOException {
        byte[] zip = client.toBlocking().retrieve(HttpRequest.GET("/api/v1/main/flows/export/by-query?namespace=io.kestra.tests"),
            Argument.of(byte[].class));
        File file = File.createTempFile("flows", ".zip");
        Files.write(file.toPath(), zip);

        try (ZipFile zipFile = new ZipFile(file)) {
            assertThat(zipFile.stream().count())
                .describedAs("by default /by-query endpoints should use specific PREFIX in legacy filter mapping, " +
                    "in this test, we should get all Flow when querying with namespace=io.kestra.tests, io.kestra.tests.subnamespace are accepted, but not io.kestra.tests2")
                .isEqualTo(Helpers.FLOWS_COUNT);
        }

        file.delete();
    }

    @Test
    void exportByIds() throws IOException {
        List<IdWithNamespace> ids = List.of(
            new IdWithNamespace("io.kestra.tests", "each-object"),
            new IdWithNamespace("io.kestra.tests", "webhook"),
            new IdWithNamespace("io.kestra.tests", "task-flow"));
        byte[] zip = client.toBlocking().retrieve(HttpRequest.POST("/api/v1/main/flows/export/by-ids", ids),
            Argument.of(byte[].class));
        File file = File.createTempFile("flows", ".zip");
        Files.write(file.toPath(), zip);

        try(ZipFile zipFile = new ZipFile(file)) {
            assertThat(zipFile.stream().count()).isEqualTo(3L);
        }

        file.delete();
    }

    @Test
    void importFlowsWithYaml() throws IOException {
        var yaml = generateFlowAsString(TEST_NAMESPACE,"a") + "---" +
            generateFlowAsString(TEST_NAMESPACE,"b") + "---" +
            generateFlowAsString(TEST_NAMESPACE,"c");

        var temp = File.createTempFile("flows", ".yaml");
        Files.writeString(temp.toPath(), yaml);
        var body = MultipartBody.builder()
            .addPart("fileUpload", "flows.yaml", temp)
            .build();
        var response = client.toBlocking().exchange(POST("/api/v1/main/flows/import", body).contentType(MediaType.MULTIPART_FORM_DATA));

        assertThat(response.getStatus().getCode()).isEqualTo(OK.getCode());
        temp.delete();
    }

    @Test
    void importFlowsWithZip() throws IOException {
        // create a ZIP file using the extract endpoint
        byte[] zip = client.toBlocking().retrieve(HttpRequest.GET("/api/v1/main/flows/export/by-query?namespace=io.kestra.tests"),
            Argument.of(byte[].class));
        File temp = File.createTempFile("flows", ".zip");
        Files.write(temp.toPath(), zip);

        var body = MultipartBody.builder()
            .addPart("fileUpload", "flows.zip", temp)
            .build();
        var response = client.toBlocking().exchange(POST("/api/v1/main/flows/import", body).contentType(MediaType.MULTIPART_FORM_DATA));

        assertThat(response.getStatus().getCode()).isEqualTo(OK.getCode());
        temp.delete();
    }

    @Test
    void importFlowsWithInvalidButAllowed() throws IOException {
        var yaml = generateFlowAsString(TEST_NAMESPACE,"a") + "---" +
            generateInvalidFlowAsString("importFlowsWithInvalidButAllowed",TEST_NAMESPACE);
        var temp = File.createTempFile("flows", ".yaml");
        Files.writeString(temp.toPath(), yaml);

        var body = MultipartBody.builder()
            .addPart("fileUpload", "flows.yaml", temp)
            .build();
        var response = client.toBlocking().exchange(POST("/api/v1/main/flows/import", body).contentType(MediaType.MULTIPART_FORM_DATA));
        assertThat(response.getStatus().getCode()).isEqualTo(OK.getCode());
        temp.delete();
    }

    @Test
    void importFlowsWithInvalidNotAllowed() throws IOException {
        var yaml = generateFlowAsString(TEST_NAMESPACE,"a") + "---" +
            generateInvalidFlowAsString("importFlowsWithInvalidNotAllowed",TEST_NAMESPACE);
        var temp = File.createTempFile("flows", ".yaml");
        Files.writeString(temp.toPath(), yaml);

        var body = MultipartBody.builder()
            .addPart("fileUpload", "flows.yaml", temp)
            .build();
        var exception = assertThrows(HttpClientResponseException.class, () -> {
            client.toBlocking().exchange(POST("/api/v1/main/flows/import?failOnError=true", body).contentType(MediaType.MULTIPART_FORM_DATA));
        });

        assertThat(exception.getStatus().getCode()).isEqualTo(UNPROCESSABLE_ENTITY.getCode());
        temp.delete();
    }

    @Test
    void importFlowsWithInvalidFile() throws IOException {
        var temp = File.createTempFile("flows", ".txt");
        Files.writeString(temp.toPath(), "this is not a valid file");

        var body = MultipartBody.builder()
            .addPart("fileUpload", "flows.txt", temp)
            .build();
        var exception = assertThrows(HttpClientResponseException.class, () -> {
            client.toBlocking().exchange(POST("/api/v1/main/flows/import?failOnError=false", body).contentType(MediaType.MULTIPART_FORM_DATA));
        });

        assertThat(exception.getStatus().getCode()).isEqualTo(UNPROCESSABLE_ENTITY.getCode());
        temp.delete();
    }

    @Test
    void disableEnableFlowsByIds() {
        List<IdWithNamespace> ids = List.of(
            new IdWithNamespace("io.kestra.tests", "each-object"),
            new IdWithNamespace("io.kestra.tests", "webhook"),
            new IdWithNamespace("io.kestra.tests", "task-flow")
        );

        HttpResponse<BulkResponse> response = client
            .toBlocking()
            .exchange(POST("/api/v1/main/flows/disable/by-ids", ids), BulkResponse.class);

        assertThat(response.getBody().get().getCount()).isEqualTo(3);

        Flow eachObject = parseFlow(client.toBlocking().retrieve(HttpRequest.GET("/api/v1/main/flows/io.kestra.tests/each-object"), String.class));
        Flow webhook = parseFlow(client.toBlocking().retrieve(HttpRequest.GET("/api/v1/main/flows/io.kestra.tests/webhook"), String.class));
        Flow taskFlow = parseFlow(client.toBlocking().retrieve(HttpRequest.GET("/api/v1/main/flows/io.kestra.tests/task-flow"), String.class));

        assertThat(eachObject.isDisabled()).isTrue();
        assertThat(webhook.isDisabled()).isTrue();
        assertThat(taskFlow.isDisabled()).isTrue();

        response = client
            .toBlocking()
            .exchange(POST("/api/v1/main/flows/enable/by-ids", ids), BulkResponse.class);

        assertThat(response.getBody().get().getCount()).isEqualTo(3);

        eachObject = parseFlow(client.toBlocking().retrieve(HttpRequest.GET("/api/v1/main/flows/io.kestra.tests/each-object"), String.class));
        webhook = parseFlow(client.toBlocking().retrieve(HttpRequest.GET("/api/v1/main/flows/io.kestra.tests/webhook"), String.class));
        taskFlow = parseFlow(client.toBlocking().retrieve(HttpRequest.GET("/api/v1/main/flows/io.kestra.tests/task-flow"), String.class));

        assertThat(eachObject.isDisabled()).isFalse();
        assertThat(webhook.isDisabled()).isFalse();
        assertThat(taskFlow.isDisabled()).isFalse();
    }

    @Test
    void disableEnableFlowsByQuery() throws InterruptedException {
        Flow flow = generateFlow("toDisable","io.kestra.unittest.disabled", "a");
        client.toBlocking().retrieve(POST("/api/v1/main/flows", flow), String.class);

        HttpResponse<BulkResponse> response = client
            .toBlocking()
            .exchange(POST("/api/v1/main/flows/disable/by-query?namespace=io.kestra.unittest.disabled", Map.of()), BulkResponse.class);

        assertThat(response.getBody().get().getCount()).isEqualTo(1);

        Flow toDisable = parseFlow(client.toBlocking().retrieve(HttpRequest.GET("/api/v1/main/flows/io.kestra.unittest.disabled/toDisable"), String.class));

        assertThat(toDisable.isDisabled()).isTrue();

        response = client
            .toBlocking()
            .exchange(POST("/api/v1/main/flows/enable/by-query?namespace=io.kestra.unittest.disabled", Map.of()), BulkResponse.class);

        assertThat(response.getBody().get().getCount()).isEqualTo(1);

        toDisable = parseFlow(client.toBlocking().retrieve(HttpRequest.GET("/api/v1/main/flows/io.kestra.unittest.disabled/toDisable"), String.class));

        assertThat(toDisable.isDisabled()).isFalse();
    }

    @Test
    void deleteFlowFlowsByQuery(){
        postFlow("flowIdA","io.kestra.tests.delete", "a");
        postFlow("flowIdB","io.kestra.tests.delete", "b");
        postFlow("flowIdC","io.kestra.tests.delete", "c");

        UriBuilder uriBuilder = UriBuilder.of("/api/v1/main/flows/delete/by-query");
        uriBuilder.queryParam("q", "flowId");
        uriBuilder.queryParam("namespace", "io.kestra.tests.delete");
        URI uri = uriBuilder.build();

        HttpResponse<BulkResponse> response = client
            .toBlocking()
            .exchange(DELETE(uri), BulkResponse.class);

        assertThat(response.getBody().get().getCount()).isEqualTo(3);

        HttpClientResponseException flowA = assertThrows(HttpClientResponseException.class, () -> {
            client.toBlocking().retrieve(HttpRequest.GET("/api/v1/main/flows/io.kestra.unittest.disabled/flow-a"));
        });
        HttpClientResponseException flowB = assertThrows(HttpClientResponseException.class, () -> {
            client.toBlocking().retrieve(HttpRequest.GET("/api/v1/main/flows/io.kestra.unittest.disabled/flow-b"));
        });
        HttpClientResponseException flowC = assertThrows(HttpClientResponseException.class, () -> {
            client.toBlocking().retrieve(HttpRequest.GET("/api/v1/main/flows/io.kestra.unittest.disabled/flow-c"));
        });

        assertThat(flowA.getStatus().getCode()).isEqualTo(HttpStatus.NOT_FOUND.getCode());
        assertThat(flowB.getStatus().getCode()).isEqualTo(HttpStatus.NOT_FOUND.getCode());
        assertThat(flowC.getStatus().getCode()).isEqualTo(HttpStatus.NOT_FOUND.getCode());
    }

    @Test
    void deleteFlowFlowsByIds(){
        Flow flow = generateFlow("toDelete","io.kestra.unittest.delete", "a");
        client.toBlocking().retrieve(POST("/api/v1/main/flows", flow), String.class);

        client.toBlocking().exchange(HttpRequest.DELETE("/api/v1/main/flows/delete/by-query?namespace=io.kestra.unittest.delete"));

        HttpClientResponseException e = assertThrows(HttpClientResponseException.class, () -> {
            client.toBlocking().retrieve(HttpRequest.GET("/api/v1/main/flows/io.kestra.unittest.disabled/toDelete"));
        });

        assertThat(e.getStatus().getCode()).isEqualTo(HttpStatus.NOT_FOUND.getCode());
    }

    @Test
    void validateFlows() throws IOException {
        URL resource = TestsUtils.class.getClassLoader().getResource("flows/validateMultipleValidFlows.yaml");
        String flow = Files.readString(Path.of(Objects.requireNonNull(resource).getPath()), Charset.defaultCharset());

        String firstFlowSource = flow.split("(?m)^---")[0];
        jdbcFlowRepository.create(GenericFlow.fromYaml("main", firstFlowSource));

        HttpResponse<List<ValidateConstraintViolation>> response = client.toBlocking().exchange(POST("/api/v1/main/flows/validate", flow).contentType(MediaType.APPLICATION_YAML), Argument.listOf(ValidateConstraintViolation.class));

        List<ValidateConstraintViolation> body = response.body();
        assertThat(body.size()).isEqualTo(2);
        // We don't send any revision while the flow already exists so it's outdated
        assertThat(body.getFirst().isOutdated()).isTrue();
        assertThat(body.getFirst().getDeprecationPaths()).hasSize(3);
        assertThat(body.getFirst().getDeprecationPaths()).containsExactlyInAnyOrder("tasks[1]", "tasks[1].additionalProperty", "listeners");
        assertThat(body.getFirst().getWarnings().size()).isZero();
        assertThat(body.getFirst().getInfos().size()).isZero();
        assertThat(body.get(1).isOutdated()).isFalse();
        assertThat(body.get(1).getDeprecationPaths()).containsExactlyInAnyOrder("tasks[0]", "tasks[1]");
        assertThat(body, everyItem(
            Matchers.hasProperty("constraints", nullValue())
        ));

        resource = TestsUtils.class.getClassLoader().getResource("flows/validateMultipleInvalidFlows.yaml");
        flow = Files.readString(Path.of(Objects.requireNonNull(resource).getPath()), Charset.defaultCharset());

        response = client.toBlocking().exchange(POST("/api/v1/main/flows/validate", flow).contentType(MediaType.APPLICATION_YAML), Argument.listOf(ValidateConstraintViolation.class));

        body = response.body();
        assertThat(body.size()).isEqualTo(2);
        assertThat(body.getFirst().getConstraints()).contains("Unrecognized field \"unknownProp\"");
        assertThat(body.get(1).getConstraints()).contains("Invalid type: io.kestra.plugin.core.debug.UnknownTask");
    }

    @Test
    void shouldValidateFlowWithWarningsAndInfos() throws IOException {
        URL resource = TestsUtils.class.getClassLoader().getResource("flows/warningsAndInfos.yaml");
        String source = Files.readString(Path.of(Objects.requireNonNull(resource).getPath()), Charset.defaultCharset());

        jdbcFlowRepository.create(GenericFlow.fromYaml(MAIN_TENANT, source));

        HttpResponse<List<ValidateConstraintViolation>> response = client.toBlocking().exchange(POST("/api/v1/main/flows/validate", source).contentType(MediaType.APPLICATION_YAML), Argument.listOf(ValidateConstraintViolation.class));

        List<ValidateConstraintViolation> body = response.body();
        assertThat(body.size()).isEqualTo(1);
        assertThat(body.getFirst().getDeprecationPaths()).hasSize(1);
        assertThat(body.getFirst().getDeprecationPaths().getFirst()).isEqualTo("tasks[0]");
        assertThat(body.getFirst().getInfos().size()).isEqualTo(1);
        assertThat(body.getFirst().getInfos().getFirst()).isEqualTo("io.kestra.core.tasks.log.Log is replaced by io.kestra.plugin.core.log.Log");
    }

    @Test
    void commaInSingleLabelsValue() {
        String encodedCommaWithinLabel = URLEncoder.encode("project:foo,bar", StandardCharsets.UTF_8);

        MutableHttpRequest<Object> searchRequest = HttpRequest
            .GET("/api/v1/main/flows/search?filters[labels][EQUALS][project]=foo,bar");
        assertDoesNotThrow(() -> client.toBlocking().retrieve(searchRequest, PagedResults.class));

        MutableHttpRequest<Object> searchRequest_oldParameters = HttpRequest
            .GET("/api/v1/main/flows/search?labels=project:foo,bar");
        assertDoesNotThrow(() -> client.toBlocking().retrieve(searchRequest_oldParameters, PagedResults.class));

        MutableHttpRequest<Object> exportRequest = HttpRequest
            .GET("/api/v1/main/flows/export/by-query?labels=" + encodedCommaWithinLabel);
        assertDoesNotThrow(() -> client.toBlocking().retrieve(exportRequest, byte[].class));

        MutableHttpRequest<List<Object>> deleteRequest = HttpRequest
            .DELETE("/api/v1/main/flows/delete/by-query?labels=" + encodedCommaWithinLabel);
        assertDoesNotThrow(() -> client.toBlocking().retrieve(deleteRequest, BulkResponse.class));

        MutableHttpRequest<List<Object>> disableRequest = HttpRequest
            .POST("/api/v1/main/flows/disable/by-query?labels=" + encodedCommaWithinLabel, List.of());
        assertDoesNotThrow(() -> client.toBlocking().retrieve(disableRequest, BulkResponse.class));

        MutableHttpRequest<List<Object>> enableRequest = HttpRequest
            .POST("/api/v1/main/flows/enable/by-query?labels=" + encodedCommaWithinLabel, List.of());
        assertDoesNotThrow(() -> client.toBlocking().retrieve(enableRequest, BulkResponse.class));
    }

    @Test
    void commaInOneOfMultiLabels() {

        Map<String, Object> flow = JacksonMapper.toMap(generateFlow(TEST_NAMESPACE, "a"));
        flow.put("labels", Map.of("project", "foo,bar", "status", "test"));

        parseFlow(client.toBlocking().retrieve(POST("/api/v1/main/flows", flow), String.class));

        var flows = client.toBlocking().retrieve(GET("/api/v1/main/flows/search?filters[labels][EQUALS][project]=foo,bar" + "&filters[labels][EQUALS][status]=test"), Argument.of(PagedResults.class, Flow.class));
        assertThat(flows.getTotal()).isEqualTo(1L);

        flows = client.toBlocking().retrieve(GET("/api/v1/main/flows/search?labels=project:foo,bar" + "&labels=status:test"), Argument.of(PagedResults.class, Flow.class));
        assertThat(flows.getTotal()).isEqualTo(1L);

    }

    @Test
    void validateTask() throws IOException {
        URL resource = TestsUtils.class.getClassLoader().getResource("tasks/validTask.json");

        String task = Files.readString(Path.of(Objects.requireNonNull(resource).getPath()), Charset.defaultCharset());

        HttpResponse<List<ValidateConstraintViolation>> response = client.toBlocking().exchange(POST("/api/v1/main/flows/validate/task", task).contentType(MediaType.APPLICATION_JSON), Argument.listOf(ValidateConstraintViolation.class));

        List<ValidateConstraintViolation> body = response.body();
        assertThat(body.size()).isEqualTo(1);
        assertThat(body, everyItem(
            Matchers.hasProperty("constraints", nullValue())
        ));

        resource = TestsUtils.class.getClassLoader().getResource("tasks/invalidTaskUnknownType.json");
        task = Files.readString(Path.of(Objects.requireNonNull(resource).getPath()), Charset.defaultCharset());

        response = client.toBlocking().exchange(POST("/api/v1/main/flows/validate/task", task).contentType(MediaType.APPLICATION_JSON), Argument.listOf(ValidateConstraintViolation.class));

        body = response.body();

        assertThat(body.size()).isEqualTo(1);
        assertThat(body.get(0).getConstraints()).contains("Invalid type: io.kestra.plugin.core.debug.UnknownTask");

        resource = TestsUtils.class.getClassLoader().getResource("tasks/invalidTaskUnknownProp.json");
        task = Files.readString(Path.of(Objects.requireNonNull(resource).getPath()), Charset.defaultCharset());

        response = client.toBlocking().exchange(POST("/api/v1/main/flows/validate/task", task).contentType(MediaType.APPLICATION_JSON), Argument.listOf(ValidateConstraintViolation.class));

        body = response.body();

        assertThat(body.size()).isEqualTo(1);
        assertThat(body.get(0).getConstraints()).contains("Unrecognized field \"unknownProp\"");

        resource = TestsUtils.class.getClassLoader().getResource("tasks/invalidTaskMissingProp.json");
        task = Files.readString(Path.of(Objects.requireNonNull(resource).getPath()), Charset.defaultCharset());

        response = client.toBlocking().exchange(POST("/api/v1/main/flows/validate/task", task).contentType(MediaType.APPLICATION_JSON), Argument.listOf(ValidateConstraintViolation.class));

        body = response.body();

        assertThat(body.size()).isEqualTo(1);
        assertThat(body.get(0).getConstraints()).contains("message: must not be null");
    }

    @Test
    void validateTrigger() throws IOException {
        URL resource = TestsUtils.class.getClassLoader().getResource("triggers/validTrigger.json");

        String task = Files.readString(Path.of(Objects.requireNonNull(resource).getPath()), Charset.defaultCharset());

        HttpResponse<List<ValidateConstraintViolation>> response = client.toBlocking().exchange(POST("/api/v1/main/flows/validate/trigger", task).contentType(MediaType.APPLICATION_JSON), Argument.listOf(ValidateConstraintViolation.class));

        List<ValidateConstraintViolation> body = response.body();
        assertThat(body.size()).isEqualTo(1);
        assertThat(body, everyItem(
            Matchers.hasProperty("constraints", nullValue())
        ));

        resource = TestsUtils.class.getClassLoader().getResource("triggers/invalidTriggerUnknownType.json");
        task = Files.readString(Path.of(Objects.requireNonNull(resource).getPath()), Charset.defaultCharset());

        response = client.toBlocking().exchange(POST("/api/v1/main/flows/validate/trigger", task).contentType(MediaType.APPLICATION_JSON), Argument.listOf(ValidateConstraintViolation.class));

        body = response.body();

        assertThat(body.size()).isEqualTo(1);
        assertThat(body.get(0).getConstraints()).contains("Invalid type: io.kestra.plugin.core.debug.UnknownTrigger");

        resource = TestsUtils.class.getClassLoader().getResource("triggers/invalidTriggerUnknownProp.json");
        task = Files.readString(Path.of(Objects.requireNonNull(resource).getPath()), Charset.defaultCharset());

        response = client.toBlocking().exchange(POST("/api/v1/main/flows/validate/trigger", task).contentType(MediaType.APPLICATION_JSON), Argument.listOf(ValidateConstraintViolation.class));

        body = response.body();

        assertThat(body.size()).isEqualTo(1);
        assertThat(body.get(0).getConstraints()).contains("Unrecognized field \"unknownProp\"");

        resource = TestsUtils.class.getClassLoader().getResource("triggers/invalidTriggerMissingProp.json");
        task = Files.readString(Path.of(Objects.requireNonNull(resource).getPath()), Charset.defaultCharset());

        response = client.toBlocking().exchange(POST("/api/v1/main/flows/validate/trigger", task).contentType(MediaType.APPLICATION_JSON), Argument.listOf(ValidateConstraintViolation.class));

        body = response.body();

        assertThat(body.size()).isEqualTo(1);
        assertThat(body.get(0).getConstraints()).contains("cron: must not be null");
    }

    @Test
    void dependencies() {
        flowTopologyRepository.save(createSimpleFlowTopology("flow-a", "flow-b"));
        flowTopologyRepository.save(createSimpleFlowTopology("flow-b", "flow-c"));
        flowTopologyRepository.save(createSimpleFlowTopology("flow-c", "flow-d"));
        flowTopologyRepository.save(createSimpleFlowTopology("flow-d", "flow-e"));

        FlowTopologyGraph result = client.toBlocking().retrieve(HttpRequest.GET("/api/v1/main/flows/io.kestra.tests/flow-a/dependencies"), FlowTopologyGraph.class);
        assertThat(result.getNodes().size()).isEqualTo(2);
        assertThat(result.getEdges().size()).isEqualTo(1);
        assertThat(result.getNodes()).extracting(node -> node.getId()).contains("flow-a", "flow-b");
        assertThat(result.getEdges()).extracting(edge -> edge.getSource()).contains("flow-a");
        assertThat(result.getEdges()).extracting(edge -> edge.getTarget()).contains("flow-b");

        result = client.toBlocking().retrieve(HttpRequest.GET("/api/v1/main/flows/io.kestra.tests/flow-a/dependencies?expandAll=true"), FlowTopologyGraph.class);
        assertThat(result.getNodes().size()).isEqualTo(5);
        assertThat(result.getEdges().size()).isEqualTo(4);
        assertThat(result.getNodes()).extracting(node -> node.getId()).contains("flow-a", "flow-b", "flow-c", "flow-d", "flow-e");
        assertThat(result.getEdges()).extracting(edge -> edge.getSource()).contains("flow-a", "flow-b", "flow-c", "flow-d");
        assertThat(result.getEdges()).extracting(edge -> edge.getTarget()).contains("flow-b", "flow-c", "flow-d", "flow-e");
    }

    @Test
    void shouldIncludeUpstreamDependencies() {
        flowTopologyRepository.save(createSimpleFlowTopology("flow-a", "flow-b"));
        flowTopologyRepository.save(createSimpleFlowTopology("flow-a", "flow-c"));
        flowTopologyRepository.save(createSimpleFlowTopology("flow-c", "flow-d"));
        flowTopologyRepository.save(createSimpleFlowTopology("flow-b", "flow-e"));

        FlowTopologyGraph result = client.toBlocking().retrieve(HttpRequest.GET("/api/v1/main/flows/io.kestra.tests/flow-a/dependencies?expandAll=true"), FlowTopologyGraph.class);
        assertThat(result.getNodes().size()).isEqualTo(5);
        assertThat(result.getEdges().size()).isEqualTo(4);
        assertThat(result.getNodes()).extracting(node -> node.getId()).contains("flow-a", "flow-b", "flow-c", "flow-d", "flow-e");
        assertThat(result.getEdges()).extracting(edge -> edge.getSource()).contains("flow-c", "flow-a", "flow-b", "flow-a");
        assertThat(result.getEdges()).extracting(edge -> edge.getTarget()).contains("flow-b", "flow-c", "flow-d", "flow-e");

        // check that each subnode include all upstream dependencies
        result = client.toBlocking().retrieve(HttpRequest.GET("/api/v1/main/flows/io.kestra.tests/flow-b/dependencies?expandAll=true"), FlowTopologyGraph.class);
        assertThat(result.getNodes().size()).isEqualTo(5);
        assertThat(result.getEdges().size()).isEqualTo(4);

        result = client.toBlocking().retrieve(HttpRequest.GET("/api/v1/main/flows/io.kestra.tests/flow-c/dependencies?expandAll=true"), FlowTopologyGraph.class);
        assertThat(result.getNodes().size()).isEqualTo(5);
        assertThat(result.getEdges().size()).isEqualTo(4);

        result = client.toBlocking().retrieve(HttpRequest.GET("/api/v1/main/flows/io.kestra.tests/flow-d/dependencies?expandAll=true"), FlowTopologyGraph.class);
        assertThat(result.getNodes().size()).isEqualTo(5);
        assertThat(result.getEdges().size()).isEqualTo(4);

        result = client.toBlocking().retrieve(HttpRequest.GET("/api/v1/main/flows/io.kestra.tests/flow-e/dependencies?expandAll=true"), FlowTopologyGraph.class);
        assertThat(result.getNodes().size()).isEqualTo(5);
        assertThat(result.getEdges().size()).isEqualTo(4);
    }

    @Test
    void exportFlows() {
        Flow f1 = generateFlow("flow_export_1", "io.kestra.export", "a");
        Flow f2 = generateFlow("flow_export_2", "io.kestra.export", "b");

        client.toBlocking().retrieve(
            HttpRequest.POST(FLOW_PATH, f1),
            Flow.class
        );
        client.toBlocking().retrieve(
            HttpRequest.POST(FLOW_PATH, f2),
            Flow.class
        );

        HttpResponse<byte[]> response = client.toBlocking().exchange(
            HttpRequest.GET(FLOW_PATH + "/export/by-query/csv"),
            byte[].class
        );

        assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.OK.getCode());
        assertThat(response.getHeaders().get("Content-Disposition")).contains("attachment; filename=flows.csv");

        String csv = new String(response.body());
        assertThat(csv).contains("id");
        assertThat(csv).contains(f1.getId());
        assertThat(csv).contains(f2.getId());
    }


    private Flow generateFlow(String namespace, String inputName) {
        return generateFlow(IdUtils.create(), namespace, inputName);
    }

    private Flow generateFlow(String friendlyId, String namespace, String inputName) {
        return Flow.builder()
            .id(friendlyId)
            .namespace(namespace)
            .inputs(ImmutableList.of(StringInput.builder().type(Type.STRING).id(inputName).build()))
            .tasks(Collections.singletonList(generateTask("test", "test")))
            .build();
    }

    private Flow generateFlowWithFlowable(String friendlyId, String namespace, String format) {
        return Flow.builder()
            .id(friendlyId)
            .namespace(namespace)
            .tasks(Collections.singletonList(
                Sequential.builder()
                    .id("seq")
                    .type(Sequential.class.getName())
                    .tasks(Arrays.asList(
                        generateTask("test1", "test"),
                        generateTask("test2", format)
                    ))
                    .build()
            ))
            .build();
    }

    private Task generateTask(String id, String format) {
        return Return.builder()
            .id(id)
            .type(Return.class.getName())
            .format(Property.ofValue(format))
            .build();
    }

    private Flow parseFlow(String flow) {
        return YamlParser.parse(flow, Flow.class);
    }

    private String generateFlowAsString(String id, String namespace, String format) {
        return """
            id: %s
            # Comment i added
            namespace: %s
            inputs:
              - id: %s
                type: STRING
            tasks:
              - id: test
                type: io.kestra.plugin.core.debug.Return
                format: test
            disabled: false
            deleted: false
            """.formatted(id, namespace, format);
    }
    private String generateFlowAsString(String namespace, String format) {
        return generateFlowAsString(IdUtils.create(), namespace, format);

    }

    private String generateInvalidFlowAsString(String id, String namespace) {
        return """
            id: %s
            # Comment i added
            namespace: %s
            tasks:
              - id: test
                type: io.kestra.plugin.core.debug.Invalid
                format: test
            disabled: false
            deleted: false
            """.formatted(id, namespace);
    }

    private String postFlow(String friendlyId, String namespace, String format) {
        return client.toBlocking().retrieve(POST("/api/v1/main/flows", generateFlow(friendlyId, namespace, format)), String.class);
    }

    protected FlowTopology createSimpleFlowTopology(String flowA, String flowB) {
        return FlowTopology.builder()
            .relation(FlowRelation.FLOW_TASK)
            .source(FlowNode.builder()
                .id(flowA)
                .namespace("io.kestra.tests")
                .tenantId("main")
                .uid(flowA)
                .build()
            )
            .destination(FlowNode.builder()
                .id(flowB)
                .namespace("io.kestra.tests")
                .tenantId("main")
                .uid(flowB)
                .build()
            )
            .build();
    }
}
