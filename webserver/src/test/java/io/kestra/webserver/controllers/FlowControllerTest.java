package io.kestra.webserver.controllers;

import com.google.common.collect.ImmutableList;
import io.kestra.core.Helpers;
import io.kestra.core.exceptions.InternalException;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.flows.Input;
import io.kestra.core.models.flows.input.StringInput;
import io.kestra.core.models.hierarchies.FlowGraph;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.validations.ValidateConstraintViolation;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.serializers.YamlFlowParser;
import io.kestra.core.tasks.debugs.Return;
import io.kestra.core.tasks.flows.Sequential;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.TestsUtils;
import io.kestra.jdbc.repository.AbstractJdbcFlowRepository;
import io.kestra.webserver.controllers.domain.IdWithNamespace;
import io.kestra.webserver.controllers.h2.JdbcH2ControllerTest;
import io.kestra.webserver.responses.BulkResponse;
import io.kestra.webserver.responses.PagedResults;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.client.multipart.MultipartBody;
import io.micronaut.http.hateoas.JsonError;
import io.micronaut.rxjava2.http.client.RxHttpClient;
import jakarta.inject.Inject;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.ZipFile;

import static io.micronaut.http.HttpRequest.*;
import static io.micronaut.http.HttpStatus.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FlowControllerTest extends JdbcH2ControllerTest {
    @Inject
    @Client("/")
    RxHttpClient client;

    @Inject
    AbstractJdbcFlowRepository jdbcFlowRepository;

    @BeforeEach
    protected void init() {
        jdbcFlowRepository.findAll()
            .forEach(jdbcFlowRepository::delete);

        super.setup();
    }

    @Test
    void id() {
        String result = client.toBlocking().retrieve(HttpRequest.GET("/api/v1/flows/io.kestra.tests/full"), String.class);
        Flow flow = new YamlFlowParser().parse(result, Flow.class);
        assertThat(flow.getId(), is("full"));
        assertThat(flow.getTasks().size(), is(5));
    }

    @Test
    void idNoSource() {
        Map<String, Object> map = client.toBlocking().retrieve(HttpRequest.GET("/api/v1/flows/io.kestra.tests/full"), Argument.mapOf(String.class, Object.class));
        assertThat(map.get("source"), is(nullValue()));

        FlowWithSource result = client.toBlocking().retrieve(HttpRequest.GET("/api/v1/flows/io.kestra.tests/full?source=true"), FlowWithSource.class);
        assertThat(result.getSource(), containsString("#triggers:"));
    }

    @Test
    void task() {
        Task result = client.toBlocking().retrieve(HttpRequest.GET("/api/v1/flows/io.kestra.tests/each-object/tasks/not-json"), Task.class);

        assertThat(result.getId(), is("not-json"));
        assertThat(result.getType(), is("io.kestra.core.tasks.debugs.Return"));
    }

    @Test
    void taskNotFound() {
        HttpClientResponseException e = assertThrows(HttpClientResponseException.class, () -> {
            client.toBlocking().retrieve(HttpRequest.GET("/api/v1/flows/io.kestra.tests/full/tasks/notFound"));
        });

        assertThat(e.getStatus(), is(HttpStatus.NOT_FOUND));
    }

    @Test
    void graph() {
        FlowGraph result = client.toBlocking().retrieve(HttpRequest.GET("/api/v1/flows/io.kestra.tests/all-flowable/graph"), FlowGraph.class);

        assertThat(result.getNodes().size(), is(38));
        assertThat(result.getEdges().size(), is(42));
        assertThat(result.getClusters().size(), is(7));
        assertThat(result.getClusters().stream().map(FlowGraph.Cluster::getCluster).toList(), Matchers.everyItem(
            Matchers.hasProperty("uid", Matchers.not(Matchers.startsWith("cluster_cluster_")))
        ));
    }

    @Test
    void graph_FlowNotFound() {
        HttpClientResponseException exception = assertThrows(HttpClientResponseException.class, () -> client.toBlocking().retrieve(GET("/api/v1/flows/io.kestra.tests/unknown-flow/graph")));

        assertThat(exception.getStatus(), is(NOT_FOUND));
        assertThat(exception.getMessage(), is("Not Found: Unable to find flow io.kestra.tests_unknown-flow"));
    }

    @Test
    void idNotFound() {
        HttpClientResponseException e = assertThrows(HttpClientResponseException.class, () -> {
            client.toBlocking().retrieve(HttpRequest.GET("/api/v1/flows/io.kestra.tests/notFound"));
        });

        assertThat(e.getStatus(), is(HttpStatus.NOT_FOUND));
    }

    @SuppressWarnings("unchecked")
    @Test
    void findAll() {
        PagedResults<Flow> flows = client.toBlocking().retrieve(HttpRequest.GET("/api/v1/flows/search?q=*"), Argument.of(PagedResults.class, Flow.class));
        assertThat(flows.getTotal(), equalTo(Helpers.FLOWS_COUNT));
    }

    @SuppressWarnings("OptionalGetWithoutIsPresent")
    @Test
    void updateNamespace() {
        // initial création
        List<Flow> flows = Arrays.asList(
            generateFlow("f1", "io.kestra.updatenamespace", "1"),
            generateFlow("f2", "io.kestra.updatenamespace", "2"),
            generateFlow("f3", "io.kestra.updatenamespace", "3")
        );

        List<Flow> updated = client.toBlocking().retrieve(HttpRequest.POST("/api/v1/flows/io.kestra.updatenamespace", flows), Argument.listOf(Flow.class));
        assertThat(updated.size(), is(3));

        Flow retrieve = parseFlow(client.toBlocking().retrieve(GET("/api/v1/flows/io.kestra.updatenamespace/f1"), String.class));
        assertThat(retrieve.getId(), is("f1"));

        // update
        flows = Arrays.asList(
            generateFlow("f3", "io.kestra.updatenamespace", "3-3"),
            generateFlow("f4", "io.kestra.updatenamespace", "4")
        );

        // f3 & f4 must be updated
        updated = client.toBlocking().retrieve(HttpRequest.POST("/api/v1/flows/io.kestra.updatenamespace", flows), Argument.listOf(Flow.class));
        assertThat(updated.size(), is(4));
        assertThat(updated.get(2).getInputs().get(0).getName(), is("3-3"));
        assertThat(updated.get(3).getInputs().get(0).getName(), is("4"));

        // f1 & f2 must be deleted
        assertThrows(HttpClientResponseException.class, () -> {
            client.toBlocking().retrieve(HttpRequest.GET("/api/v1/flows/io.kestra.updatenamespace/f1"), Flow.class);
        });

        assertThrows(HttpClientResponseException.class, () -> {
            client.toBlocking().retrieve(HttpRequest.GET("/api/v1/flows/io.kestra.updatenamespace/f2"), Flow.class);
        });

        // create a flow in another namespace
        Flow invalid = generateFlow("invalid1", "io.kestra.othernamespace", "1");
        client.toBlocking().retrieve(POST("/api/v1/flows", invalid), Flow.class);

        HttpClientResponseException e = assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().retrieve(
                POST("/api/v1/flows/io.kestra.updatenamespace", Arrays.asList(
                    invalid,
                    generateFlow("f4", "io.kestra.updatenamespace", "5"),
                    generateFlow("f6", "io.kestra.another", "5")
                )),
                Argument.listOf(Flow.class)
            )
        );
        String jsonError = e.getResponse().getBody(String.class).get();
        assertThat(e.getStatus(), is(UNPROCESSABLE_ENTITY));
        assertThat(jsonError, containsString("flow.namespace"));

        // flow is not created
        assertThrows(HttpClientResponseException.class, () -> {
            client.toBlocking().retrieve(HttpRequest.GET("/api/v1/flows/io.kestra.another/f6"), Flow.class);
        });

        // flow is not updated
        retrieve = parseFlow(client.toBlocking().retrieve(GET("/api/v1/flows/io.kestra.updatenamespace/f4"), String.class));
        assertThat(retrieve.getInputs().get(0).getName(), is("4"));

        // send 2 same id
        e = assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().retrieve(
                POST("/api/v1/flows/io.kestra.same", Arrays.asList(
                    generateFlow("f7", "io.kestra.same", "1"),
                    generateFlow("f7", "io.kestra.same", "5")
                )),
                Argument.listOf(Flow.class)
            )
        );
        jsonError = e.getResponse().getBody(String.class).get();
        assertThat(e.getStatus(), is(UNPROCESSABLE_ENTITY));
        assertThat(jsonError, containsString("flow.id: Duplicate"));

        // cleanup
        try {
            client.toBlocking().exchange(DELETE("/api/v1/flows/io.kestra.othernamespace/invalid1"));
            for (int i = 1; i <= 7; i++) {
                client.toBlocking().exchange(DELETE("/api/v1/flows/io.kestra.updatenamespace/f1"));
            }
        } catch (Exception ignored) {

        }
    }

    @Test
    void updateNamespaceAsString() {
        // initial création
        String flows = String.join("---\n", Arrays.asList(
            generateFlowAsString("flow1","io.kestra.updatenamespace","a"),
            generateFlowAsString("flow2","io.kestra.updatenamespace","a"),
            generateFlowAsString("flow3","io.kestra.updatenamespace","a")
        ));

        List<FlowWithSource> updated = client.toBlocking()
            .retrieve(
                HttpRequest.POST("/api/v1/flows/io.kestra.updatenamespace", flows)
                    .contentType(MediaType.APPLICATION_YAML),
                Argument.listOf(FlowWithSource.class)
            );
        assertThat(updated.size(), is(3));

        client.toBlocking().exchange(DELETE("/api/v1/flows/io.kestra.updatenamespace/flow1"));
        client.toBlocking().exchange(DELETE("/api/v1/flows/io.kestra.updatenamespace/flow2"));
        client.toBlocking().exchange(DELETE("/api/v1/flows/io.kestra.updatenamespace/flow3"));
    }

    @Test
    void createFlow() {
        Flow flow = generateFlow("io.kestra.unittest", "a");

        Flow result = parseFlow(client.toBlocking().retrieve(POST("/api/v1/flows", flow), String.class));

        assertThat(result.getId(), is(flow.getId()));
        assertThat(result.getInputs().get(0).getName(), is("a"));

        Flow get = parseFlow(client.toBlocking().retrieve(HttpRequest.GET("/api/v1/flows/" + flow.getNamespace() + "/" + flow.getId()), String.class));
        assertThat(get.getId(), is(flow.getId()));
        assertThat(get.getInputs().get(0).getName(), is("a"));
    }

    @Test
    void createFlowWithJsonLabels() {
        Map<String, Object> flow = JacksonMapper.toMap(generateFlow("io.kestra.unittest", "a"));
        flow.put("labels", Map.of("a", "b"));

        Flow result = parseFlow(client.toBlocking().retrieve(POST("/api/v1/flows", flow), String.class));

        assertThat(result.getId(), is(flow.get("id")));
        assertThat(result.getLabels().get(0).key(), is("a"));
        assertThat(result.getLabels().get(0).value(), is("b"));
    }

    @Test
    void deletedFlow() {
        Flow flow = generateFlow("io.kestra.unittest", "a");

        FlowWithSource result = client.toBlocking().retrieve(POST("/api/v1/flows", flow), FlowWithSource.class);
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

        String deletedResult = client.toBlocking().retrieve(HttpRequest.GET("/api/v1/flows/" + flow.getNamespace() + "/" + flow.getId() + "?allowDeleted=true"), String.class);
        Flow deletedFlow = new YamlFlowParser().parse(deletedResult, Flow.class);

        assertThat(deletedFlow.isDeleted(), is(true));
    }

    @Test
    void updateFlow() {
        String flowId = IdUtils.create();

        Flow flow = generateFlow(flowId, "io.kestra.unittest", "a");

        Flow result = client.toBlocking().retrieve(POST("/api/v1/flows", flow), Flow.class);

        assertThat(result.getId(), is(flow.getId()));
        assertThat(result.getInputs().get(0).getName(), is("a"));

        flow = generateFlow(flowId, "io.kestra.unittest", "b");

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
    void updateFlowMultilineJson() {
        String flowId = IdUtils.create();

        Flow flow = generateFlowWithFlowable(flowId, "io.kestra.unittest", "\n \n a         \nb\nc");

        Flow result = client.toBlocking().retrieve(POST("/api/v1/flows", flow), Flow.class);
        assertThat(result.getId(), is(flow.getId()));

        FlowWithSource withSource = client.toBlocking().retrieve(GET("/api/v1/flows/" + flow.getNamespace() + "/" + flow.getId() + "?source=true").contentType(MediaType.APPLICATION_YAML), FlowWithSource.class);
        assertThat(withSource.getId(), is(flow.getId()));
        assertThat(withSource.getSource(), containsString("format: |2-"));
    }

    @Test
    void updateTaskFlow() throws InternalException {
        String flowId = IdUtils.create();

        Flow flow = generateFlowWithFlowable(flowId, "io.kestra.unittest", "a");

        Flow result = client.toBlocking().retrieve(POST("/api/v1/flows", flow), Flow.class);
        assertThat(result.getId(), is(flow.getId()));

        Task task = generateTask("test2", "updated task");

        Flow get = client.toBlocking().retrieve(
            PATCH("/api/v1/flows/" + flow.getNamespace() + "/" + flow.getId() + "/" + task.getId(), task),
            Flow.class
        );

        assertThat(get.getId(), is(flow.getId()));
        assertThat(((Return) get.findTaskByTaskId("test2")).getFormat(), is("updated task"));

        HttpClientResponseException e = assertThrows(HttpClientResponseException.class, () -> {
            client.toBlocking().retrieve(
                PATCH("/api/v1/flows/" + flow.getNamespace() + "/" + flow.getId() + "/test6", task),
                Flow.class
            );
        });
        assertThat(e.getStatus(), is(UNPROCESSABLE_ENTITY));

        e = assertThrows(HttpClientResponseException.class, () -> {
            client.toBlocking().retrieve(
                PATCH("/api/v1/flows/" + flow.getNamespace() + "/" + flow.getId() + "/test6", generateTask("test6", "updated task")),
                Flow.class
            );
        });
        assertThat(e.getStatus(), is(NOT_FOUND));
    }

    @SuppressWarnings("OptionalGetWithoutIsPresent")
    @Test
    void invalidUpdateFlow() {
        String flowId = IdUtils.create();

        Flow flow = generateFlow(flowId, "io.kestra.unittest", "a");
        Flow result = client.toBlocking().retrieve(POST("/api/v1/flows", flow), Flow.class);

        assertThat(result.getId(), is(flow.getId()));

        Flow finalFlow = generateFlow(IdUtils.create(), "io.kestra.unittest2", "b");
        ;

        HttpClientResponseException e = assertThrows(HttpClientResponseException.class, () -> {
            client.toBlocking().exchange(
                PUT("/api/v1/flows/" + flow.getNamespace() + "/" + flowId, finalFlow),
                Argument.of(Flow.class),
                Argument.of(JsonError.class)
            );
        });

        String jsonError = e.getResponse().getBody(String.class).get();

        assertThat(e.getStatus(), is(UNPROCESSABLE_ENTITY));
        assertThat(jsonError, containsString("flow.id"));
        assertThat(jsonError, containsString("flow.namespace"));
    }

    @Test
    void listDistinctNamespace() {
        List<String> namespaces = client.toBlocking().retrieve(
            HttpRequest.GET("/api/v1/flows/distinct-namespaces"), Argument.listOf(String.class));

        assertThat(namespaces.size(), is(4));
    }

    @Test
    void createFlowFromString() {
        String flow = generateFlowAsString("io.kestra.unittest","a");
        Flow assertFlow = parseFlow(flow);

        FlowWithSource result = client.toBlocking().retrieve(POST("/api/v1/flows", flow).contentType(MediaType.APPLICATION_YAML), FlowWithSource.class);

        assertThat(result.getId(), is(assertFlow.getId()));
        assertThat(result.getInputs().get(0).getName(), is("a"));

        FlowWithSource get = client.toBlocking().retrieve(HttpRequest.GET("/api/v1/flows/io.kestra.unittest/" + assertFlow.getId() + "?source=true"), FlowWithSource.class);
        assertThat(get.getId(), is(assertFlow.getId()));
        assertThat(get.getInputs().get(0).getName(), is("a"));
        assertThat(get.getSource(), containsString(" Comment i added"));
    }

    @Test
    void createInvalidFlowFromString() throws IOException {
        URL resource = TestsUtils.class.getClassLoader().getResource("flows/simpleInvalidFlow.yaml");
        assert resource != null;

        String flow = Files.readString(Path.of(resource.getPath()), Charset.defaultCharset());

        HttpClientResponseException e = assertThrows(HttpClientResponseException.class, () -> {
            client.toBlocking().retrieve(
                POST("/api/v1/flows", flow).contentType(MediaType.APPLICATION_YAML),
                Flow.class
            );
        });
        assertThat(e.getStatus(), is(UNPROCESSABLE_ENTITY));
    }

    @Test
    void updateFlowFromString() throws IOException {
        String flow = generateFlowAsString("updatedFlow","io.kestra.unittest","a");
        Flow assertFlow = parseFlow(flow);

        FlowWithSource result = client.toBlocking().retrieve(POST("/api/v1/flows", flow).contentType(MediaType.APPLICATION_YAML), FlowWithSource.class);

        assertThat(result.getId(), is(assertFlow.getId()));
        assertThat(result.getInputs().get(0).getName(), is("a"));

        flow = generateFlowAsString("updatedFlow","io.kestra.unittest","b");

        FlowWithSource get = client.toBlocking().retrieve(
            PUT("/api/v1/flows/io.kestra.unittest/updatedFlow", flow).contentType(MediaType.APPLICATION_YAML),
            FlowWithSource.class
        );

        assertThat(get.getId(), is(assertFlow.getId()));
        assertThat(get.getInputs().get(0).getName(), is("b"));

        String finalFlow = flow;
        HttpClientResponseException e = assertThrows(HttpClientResponseException.class, () -> {
            HttpResponse<Void> response = client.toBlocking().exchange(
                PUT("/api/v1/flows/io.kestra.unittest/" + IdUtils.create(), finalFlow).contentType(MediaType.APPLICATION_YAML)
            );
        });
        assertThat(e.getStatus(), is(NOT_FOUND));
    }

    @Test
    void updateInvalidFlowFromString() throws IOException {
        URL resource = TestsUtils.class.getClassLoader().getResource("flows/simpleFlow.yaml");
        assert resource != null;

        String flow = Files.readString(Path.of(resource.getPath()), Charset.defaultCharset());

        FlowWithSource result = client.toBlocking().retrieve(POST("/api/v1/flows", flow).contentType(MediaType.APPLICATION_YAML), FlowWithSource.class);

        assertThat(result.getId(), is("test-flow"));

        resource = TestsUtils.class.getClassLoader().getResource("flows/simpleInvalidFlowUpdate.yaml");
        assert resource != null;

        String finalFlow = Files.readString(Path.of(resource.getPath()), Charset.defaultCharset());

        HttpClientResponseException e = assertThrows(HttpClientResponseException.class, () -> {
            client.toBlocking().exchange(
                PUT("/api/v1/flows/io.kestra.unittest/test-flow", finalFlow).contentType(MediaType.APPLICATION_YAML),
                Argument.of(Flow.class),
                Argument.of(JsonError.class)
            );
        });

        String jsonError = e.getResponse().getBody(String.class).get();

        assertThat(e.getStatus(), is(UNPROCESSABLE_ENTITY));
        assertThat(jsonError, containsString("flow.id"));
        assertThat(jsonError, containsString("flow.namespace"));
    }

    @Test
    void exportByQuery() throws IOException {
        byte[] zip = client.toBlocking().retrieve(HttpRequest.GET("/api/v1/flows/export/by-query?namespace=io.kestra.tests"),
            Argument.of(byte[].class));
        File file = File.createTempFile("flows", ".zip");
        Files.write(file.toPath(), zip);

        try (ZipFile zipFile = new ZipFile(file)) {
            assertThat(zipFile.stream().count(), is(Helpers.FLOWS_COUNT -1));
        }

        file.delete();
    }

    @Test
    void exportByIds() throws IOException {
        List<IdWithNamespace> ids = List.of(
            new IdWithNamespace("io.kestra.tests", "each-object"),
            new IdWithNamespace("io.kestra.tests", "webhook"),
            new IdWithNamespace("io.kestra.tests", "task-flow"));
        byte[] zip = client.toBlocking().retrieve(HttpRequest.POST("/api/v1/flows/export/by-ids", ids),
            Argument.of(byte[].class));
        File file = File.createTempFile("flows", ".zip");
        Files.write(file.toPath(), zip);

        try(ZipFile zipFile = new ZipFile(file)) {
            assertThat(zipFile.stream().count(), is(3L));
        }

        file.delete();
    }

    @Test
    void importFlowsWithYaml() throws IOException {
        var yaml = generateFlowAsString("io.kestra.unittest","a") + "---" +
            generateFlowAsString("io.kestra.unittest","b") + "---" +
            generateFlowAsString("io.kestra.unittest","c");

        var temp = File.createTempFile("flows", ".yaml");
        Files.writeString(temp.toPath(), yaml);
        var body = MultipartBody.builder()
            .addPart("fileUpload", "flows.yaml", temp)
            .build();
        var response = client.toBlocking().exchange(POST("/api/v1/flows/import", body).contentType(MediaType.MULTIPART_FORM_DATA));

        assertThat(response.getStatus(), is(NO_CONTENT));
        temp.delete();
    }

    @Test
    void importFlowsWithZip() throws IOException {
        // create a ZIP file using the extract endpoint
        byte[] zip = client.toBlocking().retrieve(HttpRequest.GET("/api/v1/flows/export/by-query?namespace=io.kestra.tests"),
            Argument.of(byte[].class));
        File temp = File.createTempFile("flows", ".zip");
        Files.write(temp.toPath(), zip);

        var body = MultipartBody.builder()
            .addPart("fileUpload", "flows.zip", temp)
            .build();
        var response = client.toBlocking().exchange(POST("/api/v1/flows/import", body).contentType(MediaType.MULTIPART_FORM_DATA));

        assertThat(response.getStatus(), is(NO_CONTENT));
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
            .exchange(POST("/api/v1/flows/disable/by-ids", ids), BulkResponse.class);

        assertThat(response.getBody().get().getCount(), is(3));

        Flow eachObject = parseFlow(client.toBlocking().retrieve(HttpRequest.GET("/api/v1/flows/io.kestra.tests/each-object"), String.class));
        Flow webhook = parseFlow(client.toBlocking().retrieve(HttpRequest.GET("/api/v1/flows/io.kestra.tests/webhook"), String.class));
        Flow taskFlow = parseFlow(client.toBlocking().retrieve(HttpRequest.GET("/api/v1/flows/io.kestra.tests/task-flow"), String.class));

        assertThat(eachObject.isDisabled(), is(true));
        assertThat(webhook.isDisabled(), is(true));
        assertThat(taskFlow.isDisabled(), is(true));

        response = client
            .toBlocking()
            .exchange(POST("/api/v1/flows/enable/by-ids", ids), BulkResponse.class);

        assertThat(response.getBody().get().getCount(), is(3));

        eachObject = parseFlow(client.toBlocking().retrieve(HttpRequest.GET("/api/v1/flows/io.kestra.tests/each-object"), String.class));
        webhook = parseFlow(client.toBlocking().retrieve(HttpRequest.GET("/api/v1/flows/io.kestra.tests/webhook"), String.class));
        taskFlow = parseFlow(client.toBlocking().retrieve(HttpRequest.GET("/api/v1/flows/io.kestra.tests/task-flow"), String.class));

        assertThat(eachObject.isDisabled(), is(false));
        assertThat(webhook.isDisabled(), is(false));
        assertThat(taskFlow.isDisabled(), is(false));
    }

    @Test
    void disableEnableFlowsByQuery() throws InterruptedException {
        Flow flow = generateFlow("toDisable","io.kestra.unittest.disabled", "a");
        client.toBlocking().retrieve(POST("/api/v1/flows", flow), String.class);

        HttpResponse<BulkResponse> response = client
            .toBlocking()
            .exchange(POST("/api/v1/flows/disable/by-query?namespace=io.kestra.unittest.disabled", Map.of()), BulkResponse.class);

        assertThat(response.getBody().get().getCount(), is(1));

        Flow toDisable = parseFlow(client.toBlocking().retrieve(HttpRequest.GET("/api/v1/flows/io.kestra.unittest.disabled/toDisable"), String.class));

        assertThat(toDisable.isDisabled(), is(true));

        response = client
            .toBlocking()
            .exchange(POST("/api/v1/flows/enable/by-query?namespace=io.kestra.unittest.disabled", Map.of()), BulkResponse.class);

        assertThat(response.getBody().get().getCount(), is(1));

        toDisable = parseFlow(client.toBlocking().retrieve(HttpRequest.GET("/api/v1/flows/io.kestra.unittest.disabled/toDisable"), String.class));

        assertThat(toDisable.isDisabled(), is(false));
    }

    @Test
    void deleteFlowsByQuery(){
        postFlow("flow-a","io.kestra.tests.delete", "a");
        postFlow("flow-b","io.kestra.tests.delete", "b");
        postFlow("flow-c","io.kestra.tests.delete", "c");

        List<IdWithNamespace> ids = List.of(
            new IdWithNamespace("io.kestra.tests.delete", "flow-a"),
            new IdWithNamespace("io.kestra.tests.delete", "flow-b"),
            new IdWithNamespace("io.kestra.tests.delete", "flow-c")
        );

        HttpResponse<BulkResponse> response = client
            .toBlocking()
            .exchange(DELETE("/api/v1/flows/delete/by-ids", ids), BulkResponse.class);

        assertThat(response.getBody().get().getCount(), is(3));

        HttpClientResponseException flowA = assertThrows(HttpClientResponseException.class, () -> {
            client.toBlocking().retrieve(HttpRequest.GET("/api/v1/flows/io.kestra.unittest.disabled/flow-a"));
        });
        HttpClientResponseException flowB = assertThrows(HttpClientResponseException.class, () -> {
            client.toBlocking().retrieve(HttpRequest.GET("/api/v1/flows/io.kestra.unittest.disabled/flow-b"));
        });
        HttpClientResponseException flowC = assertThrows(HttpClientResponseException.class, () -> {
            client.toBlocking().retrieve(HttpRequest.GET("/api/v1/flows/io.kestra.unittest.disabled/flow-c"));
        });

        assertThat(flowA.getStatus(), is(HttpStatus.NOT_FOUND));
        assertThat(flowB.getStatus(), is(HttpStatus.NOT_FOUND));
        assertThat(flowC.getStatus(), is(HttpStatus.NOT_FOUND));
    }

    @Test
    void deleteFlowsByIds(){
        Flow flow = generateFlow("toDelete","io.kestra.unittest.delete", "a");
        client.toBlocking().retrieve(POST("/api/v1/flows", flow), String.class);

        client.toBlocking().exchange(HttpRequest.DELETE("/api/v1/flows/delete/by-query?namespace=io.kestra.unittest.delete"));

        HttpClientResponseException e = assertThrows(HttpClientResponseException.class, () -> {
            client.toBlocking().retrieve(HttpRequest.GET("/api/v1/flows/io.kestra.unittest.disabled/toDelete"));
        });

        assertThat(e.getStatus(), is(HttpStatus.NOT_FOUND));
    }

    @Test
    void validateFlows() throws IOException {
        URL resource = TestsUtils.class.getClassLoader().getResource("flows/validateMultipleValidFlows.yaml");
        String flow = Files.readString(Path.of(Objects.requireNonNull(resource).getPath()), Charset.defaultCharset());

        HttpResponse<List<ValidateConstraintViolation>> response = client.toBlocking().exchange(POST("/api/v1/flows/validate", flow).contentType(MediaType.APPLICATION_YAML), Argument.listOf(ValidateConstraintViolation.class));

        List<ValidateConstraintViolation> body = response.body();
        assertThat(body.size(), is(2));
        assertThat(body.get(0).getDeprecationPaths(), hasSize(3));
        assertThat(body.get(0).getDeprecationPaths(), containsInAnyOrder("tasks[1]", "tasks[1].additionalProperty", "listeners"));
        assertThat(body.get(1).getDeprecationPaths(), empty());
        assertThat(body, everyItem(
            Matchers.hasProperty("constraints", is(nullValue()))
        ));

        resource = TestsUtils.class.getClassLoader().getResource("flows/validateMultipleInvalidFlows.yaml");
        flow = Files.readString(Path.of(Objects.requireNonNull(resource).getPath()), Charset.defaultCharset());

        response = client.toBlocking().exchange(POST("/api/v1/flows/validate", flow).contentType(MediaType.APPLICATION_YAML), Argument.listOf(ValidateConstraintViolation.class));

        body = response.body();
        assertThat(body.size(), is(2));
        assertThat(body.get(0).getConstraints(), containsString("Unrecognized field \"unknownProp\""));
        assertThat(body.get(1).getConstraints(), containsString("Invalid type: io.kestra.core.tasks.debugs.UnknownTask"));
    }

    private Flow generateFlow(String namespace, String inputName) {
        return generateFlow(IdUtils.create(), namespace, inputName);
    }

    private Flow generateFlow(String friendlyId, String namespace, String inputName) {
        return Flow.builder()
            .id(friendlyId)
            .namespace(namespace)
            .inputs(ImmutableList.of(StringInput.builder().type(Input.Type.STRING).name(inputName).build()))
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
            .format(format)
            .build();
    }

    private Flow parseFlow(String flow) {
        return new YamlFlowParser().parse(flow, Flow.class);
    }

    private String generateFlowAsString(String friendlyId, String namespace, String format) {
        return String.format("id: %s\n" +
            "# Comment i added\n" +
            "namespace: %s\n" +
            "inputs:\n" +
            "  - name: %s\n" +
            "    type: STRING\n" +
            "tasks:\n" +
            "  - id: test\n" +
            "    type: io.kestra.core.tasks.debugs.Return\n" +
            "    format: test\n" +
            "disabled: false\n" +
            "deleted: false", friendlyId,namespace, format);

    }
    private String generateFlowAsString(String namespace, String format) {
        return String.format("id: %s\n" +
            "# Comment i added\n" +
            "namespace: %s\n" +
            "inputs:\n" +
            "  - name: %s\n" +
            "    type: STRING\n" +
            "tasks:\n" +
            "  - id: test\n" +
            "    type: io.kestra.core.tasks.debugs.Return\n" +
            "    format: test\n" +
            "disabled: false\n" +
            "deleted: false", IdUtils.create(),namespace, format);

    }

    private String postFlow(String friendlyId, String namespace, String format) {
        return client.toBlocking().retrieve(POST("/api/v1/flows", generateFlow(friendlyId, namespace, format)), String.class);
    }
}
