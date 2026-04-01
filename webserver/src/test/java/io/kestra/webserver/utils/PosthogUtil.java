package io.kestra.webserver.utils;

import java.nio.charset.StandardCharsets;

import com.github.tomakehurst.wiremock.http.Body;
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;

public class PosthogUtil {
    public static void mockPosthog(WireMockRuntimeInfo wmRuntimeInfo) {
        stubFor(
            get(urlEqualTo("/v1/config"))
                .willReturn(
                    aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withResponseBody(
                            Body.fromJsonBytes(
                                """
                                    {
                                        "posthog": {
                                            "apiHost": "%s"
                                        }
                                    }""".formatted(wmRuntimeInfo.getHttpBaseUrl()).getBytes(StandardCharsets.UTF_8)
                            )
                        )
                )
        );

        stubFor(
            post(urlEqualTo("/batch"))
                .willReturn(aResponse())
        );
    }
}
