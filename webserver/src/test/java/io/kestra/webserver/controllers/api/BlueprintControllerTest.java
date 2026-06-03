package io.kestra.webserver.controllers.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;

import io.kestra.core.utils.VersionProvider;
import io.kestra.webserver.responses.PagedResults;

import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertThrows;

@MicronautTest
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
    // GET "/v1/blueprints/kinds/{kind}/versions/{version}/tags"
    private static final String API_BLUEPRINT_GET_TAGS_PATH = "/v1/blueprints/kinds/%s/versions/%s/tags";
    private static final String KIND_FLOW = BlueprintController.Kind.FLOW.val();
    public static final String API_V1_BLUEPRINT_COMMUNITY_FLOW_PATH = "/api/v1/main/blueprints/community/flow";

    @Inject
    @Client("/")
    HttpClient client;

    @Inject
    VersionProvider versionProvider;

    @SuppressWarnings("unchecked")
    @Test
    void shouldFindSearchBlueprintsTranslatesFiltersToLegacyQueryParams(WireMockRuntimeInfo wmRuntimeInfo) {
        stubFor(
            get(urlMatching("/v1/blueprints.*"))
                .willReturn(
                    aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBodyFile("blueprints.json")
                )
        );

        PagedResults<BlueprintController.ApiBlueprintItem> blueprintsWithTotal = client.toBlocking().retrieve(
            HttpRequest.GET(API_V1_BLUEPRINT_COMMUNITY_FLOW_PATH + "?page=1&size=5&sort=title:asc&filters[q][EQUALS]=someTitle&filters[tags][CONTAINS]=3"),
            Argument.of(PagedResults.class, BlueprintController.ApiBlueprintItem.class)
        );

        assertThat(blueprintsWithTotal.getTotal()).isEqualTo(2L);
        assertThat(blueprintsWithTotal.getResults()).hasSize(2);

        WireMock wireMock = wmRuntimeInfo.getWireMock();
        wireMock.verifyThat(
            getRequestedFor(urlPathEqualTo(String.format(API_BLUEPRINT_SEARCH_KIND_FLOW, KIND_FLOW, versionProvider.getVersion())))
                .withQueryParam("q", equalTo("someTitle"))
                .withQueryParam("tags", equalTo("3"))
                .withQueryParam("ee", equalTo("false"))
                .withQueryParam("page", equalTo("1"))
                .withQueryParam("size", equalTo("5"))
                .withQueryParam("sort", equalTo("title:asc"))
        );
    }

    @Test
    void shouldRejectUnsupportedOperationForTagsFilter() {
        HttpClientResponseException e = assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().retrieve(
                HttpRequest.GET(API_V1_BLUEPRINT_COMMUNITY_FLOW_PATH + "?filters[tags][EQUALS]=foo"),
                Argument.of(PagedResults.class, BlueprintController.ApiBlueprintItem.class)
            )
        );
        assertThat(e.getMessage()).contains("Operation EQUALS is not supported for field TAGS");
    }

    @Test
    void shouldRejectLogicalFilterGroupsInsteadOfSilentlyDroppingThem() {
        HttpClientResponseException e = assertThrows(
            HttpClientResponseException.class,
            () -> client.toBlocking().retrieve(
                HttpRequest.GET(API_V1_BLUEPRINT_COMMUNITY_FLOW_PATH + "?filters[or][0][q][EQUALS]=foo&filters[or][1][q][EQUALS]=bar"),
                Argument.of(PagedResults.class, BlueprintController.ApiBlueprintItem.class)
            )
        );
        assertThat(e.getMessage()).contains("does not support logical (AND/OR) or nested filter groups");
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldFindSearchBlueprintsWithoutFiltersOmitsLegacyParams(WireMockRuntimeInfo wmRuntimeInfo) {
        stubFor(
            get(urlMatching("/v1/blueprints.*"))
                .willReturn(
                    aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBodyFile("blueprints.json")
                )
        );

        client.toBlocking().retrieve(
            HttpRequest.GET(API_V1_BLUEPRINT_COMMUNITY_FLOW_PATH + "?page=1&size=5"),
            Argument.of(PagedResults.class, BlueprintController.ApiBlueprintItem.class)
        );

        WireMock wireMock = wmRuntimeInfo.getWireMock();
        wireMock.verifyThat(
            getRequestedFor(urlPathEqualTo(String.format(API_BLUEPRINT_SEARCH_KIND_FLOW, KIND_FLOW, versionProvider.getVersion())))
                .withQueryParam("ee", equalTo("false"))
                .withoutQueryParam("q")
                .withoutQueryParam("tags")
        );
    }

    @Test
    void shouldGetSourceForExistingGetBlueprint(WireMockRuntimeInfo wmRuntimeInfo) {
        stubFor(
            get(urlMatching("/v1/blueprints/kinds/.*/id_1/.*/source.*"))
                .willReturn(
                    aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBodyFile("blueprint-flow.yaml")
                )
        );

        String blueprintFlow = client.toBlocking().retrieve(
            HttpRequest.GET(API_V1_BLUEPRINT_COMMUNITY_FLOW_PATH + "/id_1/source"),
            String.class
        );

        assertThat(blueprintFlow, not(emptyOrNullString()));

        WireMock wireMock = wmRuntimeInfo.getWireMock();
        wireMock.verifyThat(getRequestedFor(urlEqualTo(String.format(API_BLUEPRINT_GET_SOURCE, KIND_FLOW, "id_1", versionProvider.getVersion()))));
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldGetGraphForExistingGetBlueprint(WireMockRuntimeInfo wmRuntimeInfo) {
        stubFor(
            get(urlMatching("/v1/blueprints/kinds/.*/id_1/.*/graph.*"))
                .willReturn(
                    aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBodyFile("blueprint-graph.json")
                )
        );

        Map<String, Object> graph = client.toBlocking().retrieve(
            HttpRequest.GET(API_V1_BLUEPRINT_COMMUNITY_FLOW_PATH + "/id_1/graph"),
            Argument.mapOf(String.class, Object.class)
        );

        List<Map<String, Object>> nodes = (List<Map<String, Object>>) graph.get("nodes");
        List<Map<String, Object>> edges = (List<Map<String, Object>>) graph.get("edges");
        List<Map<String, Object>> clusters = (List<Map<String, Object>>) graph.get("clusters");
        assertThat(nodes.size()).isEqualTo(12);
        assertThat(nodes.stream().filter(abstractGraph -> abstractGraph.get("uid").equals("3mTDtNoUxYIFaQtgjEg28_root")).count()).isEqualTo(1L);
        assertThat(edges.size()).isEqualTo(16);
        assertThat(clusters.size()).isEqualTo(1);

        WireMock wireMock = wmRuntimeInfo.getWireMock();
        wireMock.verifyThat(getRequestedFor(urlEqualTo(String.format(API_BLUEPRINT_GET_GRAPH, KIND_FLOW, "id_1", versionProvider.getVersion()))));
    }

    @Test
    void shouldGetDetailsForExistingGetBlueprint(WireMockRuntimeInfo wmRuntimeInfo) {
        stubFor(
            get(urlMatching("/v1/blueprints/kinds/.*/id_1.*"))
                .willReturn(
                    aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBodyFile("blueprint.json")
                )
        );

        BlueprintController.ApiBlueprintItemWithSource blueprint = client.toBlocking().retrieve(
            HttpRequest.GET(API_V1_BLUEPRINT_COMMUNITY_FLOW_PATH + "/id_1"),
            BlueprintController.ApiBlueprintItemWithSource.class
        );

        assertThat(blueprint.getId()).isEqualTo("1");
        assertThat(blueprint.getTitle()).isEqualTo("GCS Trigger");
        assertThat(blueprint.getDescription()).isEqualTo("GCS trigger flow");
        assertThat(blueprint.getSource(), not(emptyOrNullString()));
        assertThat(blueprint.getPublishedAt()).isEqualTo(Instant.parse("2023-06-01T08:37:34.661Z"));
        assertThat(blueprint.getTags().size()).isEqualTo(2);
        assertThat(blueprint.getTags()).containsExactly("3", "2");

        WireMock wireMock = wmRuntimeInfo.getWireMock();
        wireMock.verifyThat(getRequestedFor(urlEqualTo(String.format(API_BLUEPRINT_GET, KIND_FLOW, "id_1", versionProvider.getVersion()))));
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldGetTagsTranslatesFilterToLegacyQueryParam(WireMockRuntimeInfo wmRuntimeInfo) {
        stubFor(
            get(urlMatching("/v1/blueprints/.*/tags.*"))
                .willReturn(
                    aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBodyFile("blueprint-tags.json")
                )
        );

        List<BlueprintController.ApiBlueprintTagItem> blueprintTags = client.toBlocking().retrieve(
            HttpRequest.GET(API_V1_BLUEPRINT_COMMUNITY_FLOW_PATH + "/tags?filters[q][EQUALS]=someQuery"),
            Argument.of(List.class, BlueprintController.ApiBlueprintTagItem.class)
        );

        assertThat(blueprintTags.size()).isEqualTo(3);
        assertThat(blueprintTags.getFirst().getId()).isEqualTo("3");

        WireMock wireMock = wmRuntimeInfo.getWireMock();
        wireMock.verifyThat(
            getRequestedFor(urlPathEqualTo(String.format(API_BLUEPRINT_GET_TAGS_PATH, KIND_FLOW, versionProvider.getVersion())))
                .withQueryParam("q", equalTo("someQuery"))
        );
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldGetTagsWithoutFiltersOmitsLegacyQueryParam(WireMockRuntimeInfo wmRuntimeInfo) {
        stubFor(
            get(urlMatching("/v1/blueprints/.*/tags.*"))
                .willReturn(
                    aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBodyFile("blueprint-tags.json")
                )
        );

        client.toBlocking().retrieve(
            HttpRequest.GET(API_V1_BLUEPRINT_COMMUNITY_FLOW_PATH + "/tags"),
            Argument.of(List.class, BlueprintController.ApiBlueprintTagItem.class)
        );

        WireMock wireMock = wmRuntimeInfo.getWireMock();
        wireMock.verifyThat(
            getRequestedFor(urlPathEqualTo(String.format(API_BLUEPRINT_GET_TAGS_PATH, KIND_FLOW, versionProvider.getVersion())))
                .withoutQueryParam("q")
        );
    }
}
