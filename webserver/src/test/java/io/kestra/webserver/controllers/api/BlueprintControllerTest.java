package io.kestra.webserver.controllers.api;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.utils.VersionProvider;
import io.kestra.webserver.controllers.api.BlueprintController;
import io.kestra.webserver.responses.PagedResults;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.kestra.core.junit.annotations.KestraTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@KestraTest
@WireMockTest(httpPort = 28181)
class BlueprintControllerTest {
    @Inject
    @Client("/")
    HttpClient client;

    @Inject
    VersionProvider versionProvider;

    @SuppressWarnings("unchecked")
    @Test
    void blueprints(WireMockRuntimeInfo wmRuntimeInfo) {
        stubFor(get(urlMatching("/v1/blueprints.*"))
            .willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBodyFile("blueprints.json"))
        );

        PagedResults<BlueprintController.BlueprintItem> blueprintsWithTotal = client.toBlocking().retrieve(
            HttpRequest.GET("/api/v1/blueprints/community?page=1&size=5&q=someTitle&sort=title:asc&tags=3"),
            Argument.of(PagedResults.class, BlueprintController.BlueprintItem.class)
        );

        assertThat(blueprintsWithTotal.getTotal(), is(2L));
        ArrayListTotal<BlueprintController.BlueprintItem> blueprints = blueprintsWithTotal.getResults();
        assertThat(blueprints.size(), is(2));
        assertThat(blueprints.getFirst().getId(), is("1"));
        assertThat(blueprints.getFirst().getTitle(), is("GCS Trigger"));
        assertThat(blueprints.getFirst().getDescription(), is("GCS trigger flow"));
        assertThat(blueprints.getFirst().getPublishedAt(), is(Instant.parse("2023-06-01T08:37:34.661Z")));
        assertThat(blueprints.getFirst().getTags().size(), is(2));
        assertThat(blueprints.getFirst().getTags(), contains("3", "2"));
        assertThat(blueprints.get(1).getId(), is("2"));

        WireMock wireMock = wmRuntimeInfo.getWireMock();
        wireMock.verifyThat(getRequestedFor(urlEqualTo("/v1/blueprints/versions/" + versionProvider.getVersion() + "?page=1&size=5&q=someTitle&sort=title%3Aasc&tags=3&ee=false")));
    }

    @Test
    void blueprintFlow(WireMockRuntimeInfo wmRuntimeInfo) {
        stubFor(get(urlMatching("/v1/blueprints/id_1/.*/flow.*"))
            .willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBodyFile("blueprint-flow.yaml"))
        );

        String blueprintFlow = client.toBlocking().retrieve(
            HttpRequest.GET("/api/v1/blueprints/community/id_1/flow"),
            String.class
        );

        assertThat(blueprintFlow, not(emptyOrNullString()));

        WireMock wireMock = wmRuntimeInfo.getWireMock();
        wireMock.verifyThat(getRequestedFor(urlEqualTo("/v1/blueprints/id_1/versions/" + versionProvider.getVersion() + "/flow")));
    }

    @SuppressWarnings("unchecked")
    @Test
    void blueprintGraph(WireMockRuntimeInfo wmRuntimeInfo) {
        stubFor(get(urlMatching("/v1/blueprints/id_1/.*/graph.*"))
            .willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBodyFile("blueprint-graph.json"))
        );

        Map<String, Object> graph = client.toBlocking().retrieve(
            HttpRequest.GET("/api/v1/blueprints/community/id_1/graph"),
            Argument.mapOf(String.class, Object.class)
        );

        List<Map<String, Object>> nodes = (List<Map<String, Object>>) graph.get("nodes");
        List<Map<String, Object>> edges = (List<Map<String, Object>>) graph.get("edges");
        List<Map<String, Object>> clusters = (List<Map<String, Object>>) graph.get("clusters");
        assertThat(nodes.size(), is(12));
        assertThat(nodes.stream().filter(abstractGraph -> abstractGraph.get("uid").equals("3mTDtNoUxYIFaQtgjEg28_root")).count(), is(1L));
        assertThat(edges.size(), is(16));
        assertThat(clusters.size(), is(1));

        WireMock wireMock = wmRuntimeInfo.getWireMock();
        wireMock.verifyThat(getRequestedFor(urlEqualTo("/v1/blueprints/id_1/versions/" + versionProvider.getVersion() + "/graph")));
    }

    @Test
    void blueprint(WireMockRuntimeInfo wmRuntimeInfo) {
        stubFor(get(urlMatching("/v1/blueprints/id_1.*"))
            .willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBodyFile("blueprint.json"))
        );

        BlueprintController.BlueprintItemWithFlow blueprint = client.toBlocking().retrieve(
            HttpRequest.GET("/api/v1/blueprints/community/id_1"),
            BlueprintController.BlueprintItemWithFlow.class
        );

        assertThat(blueprint.getId(), is("1"));
        assertThat(blueprint.getTitle(), is("GCS Trigger"));
        assertThat(blueprint.getDescription(), is("GCS trigger flow"));
        assertThat(blueprint.getFlow(), not(emptyOrNullString()));
        assertThat(blueprint.getPublishedAt(), is(Instant.parse("2023-06-01T08:37:34.661Z")));
        assertThat(blueprint.getTags().size(), is(2));
        assertThat(blueprint.getTags(), contains("3", "2"));

        WireMock wireMock = wmRuntimeInfo.getWireMock();
        wireMock.verifyThat(getRequestedFor(urlEqualTo("/v1/blueprints/id_1/versions/" + versionProvider.getVersion())));
    }

    @SuppressWarnings("unchecked")
    @Test
    void blueprintTags(WireMockRuntimeInfo wmRuntimeInfo) {
        stubFor(get(urlMatching("/v1/blueprints/.*/tags.*"))
            .willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBodyFile("blueprint-tags.json"))
        );

        List<BlueprintController.BlueprintTagItem> blueprintTags = client.toBlocking().retrieve(
            HttpRequest.GET("/api/v1/blueprints/community/tags?q=someQuery"),
            Argument.of(List.class, BlueprintController.BlueprintTagItem.class)
        );

        assertThat(blueprintTags.size(), is(3));
        assertThat(blueprintTags.getFirst().getId(), is("3"));
        assertThat(blueprintTags.getFirst().getName(), is("Cloud"));
        assertThat(blueprintTags.getFirst().getPublishedAt(), is(Instant.parse("2023-06-01T08:37:10.171Z")));

        WireMock wireMock = wmRuntimeInfo.getWireMock();
        wireMock.verifyThat(getRequestedFor(urlEqualTo("/v1/blueprints/versions/" + versionProvider.getVersion() + "/tags?q=someQuery")));
    }
}
