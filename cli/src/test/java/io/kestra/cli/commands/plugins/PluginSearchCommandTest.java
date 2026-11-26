package io.kestra.cli.commands.plugins;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;

import io.kestra.cli.ConsoleCapture;

import io.micronaut.configuration.picocli.PicocliRunner;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

@WireMockTest
class PluginSearchCommandTest {
    @Test
    void searchWithExactMatch(WireMockRuntimeInfo wmInfo) {
        stubFor(
            get(urlEqualTo("/v1/plugins"))
                .willReturn(
                    aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                [
                                    {
                                        "name": "plugin-notifications",
                                        "title": "Notifications",
                                        "group": "io.kestra.plugin",
                                        "version": "0.6.0"
                                    },
                                    {
                                        "name": "plugin-scripts",
                                        "title": "Scripts",
                                        "group": "io.kestra.plugin",
                                        "version": "0.5.0"
                                    }
                                ]
                            """)
                )
        );

        try (
            ApplicationContext ctx = ApplicationContext.builder(Environment.CLI, Environment.TEST)
                .properties(Map.of("micronaut.http.services.api.url", wmInfo.getHttpBaseUrl()))
                .start()
        ) {
            String[] args = { "notifications" };
            String output = ConsoleCapture.run(() -> PicocliRunner.call(PluginSearchCommand.class, ctx, args)).out().trim();

            assertThat(output).contains("Found 1 plugins matching 'notifications'");
            assertThat(output).contains("plugin-notifications");
            assertThat(output).doesNotContain("plugin-scripts");
        }
    }

    @Test
    void searchWithEmptyQuery(WireMockRuntimeInfo wmInfo) {
        stubFor(
            get(urlEqualTo("/v1/plugins"))
                .willReturn(
                    aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                [
                                    {
                                        "name": "plugin-notifications",
                                        "title": "Notifications",
                                        "group": "io.kestra.plugin",
                                        "version": "0.6.0"
                                    },
                                    {
                                        "name": "plugin-scripts",
                                        "title": "Scripts",
                                        "group": "io.kestra.plugin",
                                        "version": "0.5.0"
                                    }
                                ]
                            """)
                )
        );

        try (
            ApplicationContext ctx = ApplicationContext.builder(Environment.CLI, Environment.TEST)
                .properties(Map.of("micronaut.http.services.api.url", wmInfo.getHttpBaseUrl()))
                .start()
        ) {

            String[] args = { "" };
            String output = ConsoleCapture.run(() -> PicocliRunner.call(PluginSearchCommand.class, ctx, args)).out().trim();

            assertThat(output).contains("Found 2 plugins");
            assertThat(output).contains("plugin-notifications");
            assertThat(output).contains("plugin-scripts");
        }
    }
}