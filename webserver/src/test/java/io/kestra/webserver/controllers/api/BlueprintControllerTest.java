package io.kestra.webserver.controllers.api;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.kestra.core.utils.VersionProvider;
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

    // GET "/v1/blueprints/kinds/{kind}/versions/{version}"
    private static final String API_BLUEPRINT_SEARCH_KIND_FLOW = "/v1/blueprints/kinds/%s/versions/%s";
    // GET "/v1/blueprints/kinds/{kind}/{id}/versions/{version}"
    private static final String API_BLUEPRINT_GET = "/v1/blueprints/kinds/%s/%s/versions/%s";
    // GET "/v1/blueprints/kinds/{kind}/{id}/versions/{version}/source"
    private static final String API_BLUEPRINT_GET_SOURCE = API_BLUEPRINT_GET + "/source";
    // GET "/v1/blueprints/kinds/{kind}/{id}/versions/{version}/graph"
    private static final String API_BLUEPRINT_GET_GRAPH = API_BLUEPRINT_GET + "/graph";
    // GET "/v1/blueprints/kinds/{kind}/{id}/versions/{version}/graph"
    private static final String API_BLUEPRINT_GET_TAGS = "/v1/blueprints/kinds/%s/versions/%s/tags?q=%s";
    private static final String KIND_FLOW = BlueprintController.Kind.FLOW.val();
    public static final String API_V1_BLUEPRINT_COMMUNITY_FLOW_PATH = "/api/v1/blueprints/community/flow";

    @Inject
    @Client("/")
    HttpClient client;

    @Inject
    VersionProvider versionProvider;

    @SuppressWarnings("unchecked")
    @Test
    void shouldFindBlueprints(WireMockRuntimeInfo wmRuntimeInfo) {
        stubFor(get(urlMatching("/v1/blueprints.*"))
            .willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBodyFile("blueprints.json"))
        );

        PagedResults<BlueprintController.ApiBlueprintItem> blueprintsWithTotal = client.toBlocking().retrieve(
            HttpRequest.GET(API_V1_BLUEPRINT_COMMUNITY_FLOW_PATH + "?page=1&size=5&q=someTitle&sort=title:asc&tags=3"),
            Argument.of(PagedResults.class, BlueprintController.ApiBlueprintItem.class)
        );

        assertThat(blueprintsWithTotal.getTotal(), is(2L));
        List<BlueprintController.ApiBlueprintItem> blueprints = blueprintsWithTotal.getResults();
        assertThat(blueprints.size(), is(2));
        assertThat(blueprints.getFirst().getId(), is("1"));
        assertThat(blueprints.getFirst().getTitle(), is("GCS Trigger"));
        assertThat(blueprints.getFirst().getDescription(), is("GCS trigger flow"));
        assertThat(blueprints.getFirst().getPublishedAt(), is(Instant.parse("2023-06-01T08:37:34.661Z")));
        assertThat(blueprints.getFirst().getTags().size(), is(2));
        assertThat(blueprints.getFirst().getTags(), contains("3", "2"));
        assertThat(blueprints.get(1).getId(), is("2"));

        WireMock wireMock = wmRuntimeInfo.getWireMock();
        wireMock.verifyThat(getRequestedFor(urlEqualTo(String.format(API_BLUEPRINT_SEARCH_KIND_FLOW , KIND_FLOW, versionProvider.getVersion()) + "?page=1&size=5&q=someTitle&sort=title%3Aasc&tags=3&ee=false")));
    }

    @Test
    void shouldGetSourceForExistingBlueprint(WireMockRuntimeInfo wmRuntimeInfo) {
        stubFor(get(urlMatching("/v1/blueprints/kinds/.*/id_1/.*/source.*"))
            .willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBodyFile("blueprint-flow.yaml"))
        );

        String blueprintFlow = client.toBlocking().retrieve(
            HttpRequest.GET(API_V1_BLUEPRINT_COMMUNITY_FLOW_PATH + "/id_1/source"),
            String.class
        );

        assertThat(blueprintFlow, not(emptyOrNullString()));

        WireMock wireMock = wmRuntimeInfo.getWireMock();
        wireMock.verifyThat(getRequestedFor(urlEqualTo(String.format(API_BLUEPRINT_GET_SOURCE, KIND_FLOW,  "id_1", versionProvider.getVersion()))));
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldGetGraphForExistingBlueprint(WireMockRuntimeInfo wmRuntimeInfo) {
        stubFor(get(urlMatching("/v1/blueprints/kinds/.*/id_1/.*/graph.*"))
            .willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBodyFile("blueprint-graph.json"))
        );

        Map<String, Object> graph = client.toBlocking().retrieve(
            HttpRequest.GET(API_V1_BLUEPRINT_COMMUNITY_FLOW_PATH + "/id_1/graph"),
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
        wireMock.verifyThat(getRequestedFor(urlEqualTo(String.format(API_BLUEPRINT_GET_GRAPH, KIND_FLOW, "id_1", versionProvider.getVersion()))));
    }

    @Test
    void shouldGetDetailsForExistingBlueprint(WireMockRuntimeInfo wmRuntimeInfo) {
        stubFor(get(urlMatching("/v1/blueprints/kinds/.*/id_1.*"))
            .willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBodyFile("blueprint.json"))
        );

        BlueprintController.ApiBlueprintItemWithSource blueprint = client.toBlocking().retrieve(
            HttpRequest.GET(API_V1_BLUEPRINT_COMMUNITY_FLOW_PATH + "/id_1"),
            BlueprintController.ApiBlueprintItemWithSource.class
        );

        assertThat(blueprint.getId(), is("1"));
        assertThat(blueprint.getTitle(), is("GCS Trigger"));
        assertThat(blueprint.getDescription(), is("GCS trigger flow"));
        assertThat(blueprint.getSource(), not(emptyOrNullString()));
        assertThat(blueprint.getPublishedAt(), is(Instant.parse("2023-06-01T08:37:34.661Z")));
        assertThat(blueprint.getTags().size(), is(2));
        assertThat(blueprint.getTags(), contains("3", "2"));

        WireMock wireMock = wmRuntimeInfo.getWireMock();
        wireMock.verifyThat(getRequestedFor(urlEqualTo(String.format(API_BLUEPRINT_GET, KIND_FLOW, "id_1", versionProvider.getVersion()))));
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldGetTags(WireMockRuntimeInfo wmRuntimeInfo) {
        stubFor(get(urlMatching("/v1/blueprints/.*/tags.*"))
            .willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBodyFile("blueprint-tags.json"))
        );

        List<BlueprintController.ApiBlueprintTagItem> blueprintTags = client.toBlocking().retrieve(
            HttpRequest.GET(API_V1_BLUEPRINT_COMMUNITY_FLOW_PATH + "/tags?q=someQuery"),
            Argument.of(List.class, BlueprintController.ApiBlueprintTagItem.class)
        );

        assertThat(blueprintTags.size(), is(3));
        assertThat(blueprintTags.getFirst().getId(), is("3"));
        assertThat(blueprintTags.getFirst().getName(), is("Cloud"));
        assertThat(blueprintTags.getFirst().getPublishedAt(), is(Instant.parse("2023-06-01T08:37:10.171Z")));

        WireMock wireMock = wmRuntimeInfo.getWireMock();
        wireMock.verifyThat(getRequestedFor(urlEqualTo(String.format(API_BLUEPRINT_GET_TAGS, KIND_FLOW, versionProvider.getVersion(), "someQuery"))));
    }
}
