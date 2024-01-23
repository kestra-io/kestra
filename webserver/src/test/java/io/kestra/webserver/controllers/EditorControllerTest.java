package io.kestra.webserver.controllers;

import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.kestra.webserver.controllers.domain.MarketplaceRequestType;
import io.kestra.webserver.controllers.h2.JdbcH2ControllerTest;
import io.kestra.webserver.services.MarketplaceRequestMapper;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpRequest;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.rxjava2.http.client.RxHttpClient;
import io.micronaut.test.annotation.MockBean;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Objects;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

// For this controller tests, we replace every Marketplace URLs with http://localhost:8081/{previous-host} to target Wiremock server
@WireMockTest(httpPort = 28181)
class EditorControllerTest extends JdbcH2ControllerTest {
    @Inject
    @Client("/")
    private RxHttpClient client;

    @Inject
    private EmbeddedServer embeddedServer;

    @MockBean(MarketplaceRequestMapper.class)
    private MarketplaceRequestMapper marketplaceRequestMapper() {
        return new MarketplaceRequestMapper() {
            @Override
            public String url(MarketplaceRequestType type) {
                return type.getUrl().replaceFirst("https?://", "http://localhost:28181/");
            }

            @Override
            public String resourceBaseUrl(String publisher) {
                return super.resourceBaseUrl(publisher).replaceFirst("https?://", "http://localhost:28181/");
            }
        };
    }

    @Test
    void getWithType() {
        stubFor(hasGoodHeaders(
                get(urlMatching("/vscode-unpkg.net/_lp/test"))
            ).willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withHeader("Access-Control-Allow-Origin", "*")
                .withBody("{\"works\": true}"))
        );

        assertThat(
            client.toBlocking().retrieve(withBadHeaders(HttpRequest.GET("/api/v1/editor/marketplace/nls/test"))),
            is("{\"works\": true}")
        );
    }

    @Test
    void postWithType() throws IOException {
        String query = this.fileContent("/__files/extension-query.json");
        stubFor(hasGoodHeaders(
                post(urlMatching("/marketplace.visualstudio.com/_apis/public/gallery/searchrelevancy/extensionquery/something"))
                    .withRequestBody(equalToJson(query))
            ).willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withHeader("Access-Control-Allow-Origin", "*")
                .withBody("{\"works\": true}"))
        );

        assertThat(
            client.toBlocking().exchange(withBadHeaders(HttpRequest.POST("/api/v1/editor/marketplace/search/something", query)), String.class).body(),
            is("{\"works\": true}")
        );
    }

    @Test
    void getResource() {
        String publisher = "my-publisher";
        String trailingPath = "/my-extension/1.0.0/path/to/some-resource.json";
        stubFor(hasGoodHeaders(
            get(urlMatching("/" + publisher + ".vscode-unpkg.net/" + publisher + trailingPath))
                .willReturn(aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withHeader("Access-Control-Allow-Origin", "*")
                    .withBody("{\"works\": true}")))
        );

        assertThat(
            client.toBlocking().retrieve(withBadHeaders(HttpRequest.GET("/api/v1/editor/marketplace/resource/" + publisher + trailingPath))),
            is("{\"works\": true}")
        );
    }

    // "/extension" path is an index path which gives back every resources of an extension through their URLs
    // the goal of this test is to ensure that every URLs' host of such path are replaced with our server API
    // this allows us to proxy the requests to the real URLs while overriding our Origin not to get blocked
    @Test
    void getResource_ReplaceUrlsForExtensionPath() throws IOException {
        String publisher = "my-publisher";
        String trailingPath = "/my-extension/1.0.0/extension";
        stubFor(hasGoodHeaders(
            get(urlMatching("/" + publisher + ".vscode-unpkg.net/" + publisher + trailingPath))
                .willReturn(aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withHeader("Access-Control-Allow-Origin", "*")
                    .withBodyFile("extension.json")))
        );

        assertThat(
            client.toBlocking().retrieve(withBadHeaders(HttpRequest.GET("/api/v1/editor/marketplace/resource/" + publisher + trailingPath))),
            is(this.fileContent("/__files/extension-with-url-rewrites.json")
                .replace("{port}", String.valueOf(embeddedServer.getPort()))
            )
        );
    }

    private MappingBuilder hasGoodHeaders(MappingBuilder mappingBuilder) {
        return mappingBuilder.withHeader("Origin", equalTo("http://localhost:8080"))
            .withHeader("Host", equalTo("localhost:28181"))
            .withHeader("Cookie", absent())
            .withHeader("Access-Control-Allow-Origin", absent());
    }

    private MutableHttpRequest<?> withBadHeaders(MutableHttpRequest<?> httpRequest) {
        return httpRequest.headers(headers -> {
            headers.set("Origin", "http://bad-origin");
            headers.set("Cookie", "bad-cookie");
        });
    }

    private String fileContent(String resourcePath) throws IOException {
        return new String(
            Objects.requireNonNull(EditorControllerTest.class.getResourceAsStream(resourcePath)).readAllBytes()
        );
    }
}