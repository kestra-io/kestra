package io.kestra.webserver.controllers;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.webserver.responses.PagedResults;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@MicronautTest
@WireMockTest(httpPort = 8081)
class BlueprintControllerTest {
    @Inject
    @Client("/")
    HttpClient client;

    @Test
    void blueprints(WireMockRuntimeInfo wmRuntimeInfo) {
        stubFor(get(urlMatching("/v1/blueprints.*"))
            .willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBodyFile("blueprints.json"))
        );

        PagedResults<BlueprintController.BlueprintItem> blueprintsWithTotal = client.toBlocking().retrieve(
            HttpRequest.GET("/api/v1/blueprints?page=1&size=5&q=someTitle&sort=title:asc&tagIds=3"),
            Argument.of(PagedResults.class, BlueprintController.BlueprintItem.class)
        );

        assertThat(blueprintsWithTotal.getTotal(), is(2L));
        ArrayListTotal<BlueprintController.BlueprintItem> blueprints = blueprintsWithTotal.getResults();
        assertThat(blueprints.size(), is(2));
        assertThat(blueprints.get(0).getId(), is("1"));
        assertThat(blueprints.get(0).getTitle(), is("GCS Trigger"));
        assertThat(blueprints.get(0).getDescription(), is("GCS trigger flow"));
        assertThat(blueprints.get(0).getPublishedAt(), is(Instant.parse("2023-06-01T08:37:34.661Z")));
        assertThat(blueprints.get(0).getTags().size(), is(2));
        assertThat(blueprints.get(0).getTags(), contains("3", "2"));
        assertThat(blueprints.get(1).getId(), is("2"));

        WireMock wireMock = wmRuntimeInfo.getWireMock();
        wireMock.verifyThat(getRequestedFor(urlEqualTo("/v1/blueprints?page=1&size=5&q=someTitle&sort=title%3Aasc&tagIds=3")));
    }

    @Test
    void blueprintFlow(WireMockRuntimeInfo wmRuntimeInfo) {
        stubFor(get(urlMatching("/v1/blueprints/id_1/flow.*"))
            .willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBodyFile("blueprint-flow.yaml"))
        );

        String blueprintFlow = client.toBlocking().retrieve(
            HttpRequest.GET("/api/v1/blueprints/id_1/flow"),
            String.class
        );

        assertThat(blueprintFlow, not(emptyOrNullString()));

        WireMock wireMock = wmRuntimeInfo.getWireMock();
        wireMock.verifyThat(getRequestedFor(urlEqualTo("/v1/blueprints/id_1/flow"
        )));
    }

    @Test
    void blueprint(WireMockRuntimeInfo wmRuntimeInfo) {
        stubFor(get(urlMatching("/v1/blueprints/id_1.*"))
            .willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBodyFile("blueprint.json"))
        );

        BlueprintController.BlueprintItemWithFlow blueprint = client.toBlocking().retrieve(
            HttpRequest.GET("/api/v1/blueprints/id_1"),
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
        wireMock.verifyThat(getRequestedFor(urlEqualTo("/v1/blueprints/id_1")));
    }

    @SuppressWarnings("unchecked")
    @Test
    void blueprintTags(WireMockRuntimeInfo wmRuntimeInfo) {
        stubFor(get(urlMatching("/v1/blueprints/tags.*"))
            .willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBodyFile("blueprint-tags.json"))
        );

        List<BlueprintController.BlueprintTagItem> blueprintTags = client.toBlocking().retrieve(
            HttpRequest.GET("/api/v1/blueprints/tags"),
            Argument.of(List.class, BlueprintController.BlueprintTagItem.class)
        );

        assertThat(blueprintTags.size(), is(3));
        assertThat(blueprintTags.get(0).getId(), is("3"));
        assertThat(blueprintTags.get(0).getName(), is("Cloud"));
        assertThat(blueprintTags.get(0).getPublishedAt(), is(Instant.parse("2023-06-01T08:37:10.171Z")));

        WireMock wireMock = wmRuntimeInfo.getWireMock();
        wireMock.verifyThat(getRequestedFor(urlEqualTo("/v1/blueprints/tags")));
    }
}
