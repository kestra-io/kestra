package io.kestra.cli.commands.plugins;

import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.micronaut.configuration.picocli.PicocliRunner;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

@WireMockTest(httpPort = 28181)
class PluginSearchCommandTest {
    private ByteArrayOutputStream outputStreamCaptor;
    private final PrintStream originalOut = System.out;

    @BeforeEach
    void setUp() {
        outputStreamCaptor = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outputStreamCaptor));
    }

    @AfterEach
    void tearDown() {
        System.setOut(originalOut);
    }

    @Test
    void searchWithExactMatch() {
        stubFor(get(urlEqualTo("/v1/plugins"))
            .willReturn(aResponse()
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
                """)));

        try (ApplicationContext ctx = ApplicationContext.builder(Environment.CLI, Environment.TEST)
            .properties(Map.of("micronaut.http.services.api.url", "http://localhost:28181"))
            .start()) {
            String[] args = {"notifications"};
            PicocliRunner.call(PluginSearchCommand.class, ctx, args);

            String output = outputStreamCaptor.toString().trim();
            assertThat(output).contains("Found 1 plugins matching 'notifications'");
            assertThat(output).contains("plugin-notifications");
            assertThat(output).doesNotContain("plugin-scripts");
        }
    }

    @Test
    void searchWithEmptyQuery() {
        stubFor(get(urlEqualTo("/v1/plugins"))
            .willReturn(aResponse()
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
                """)));

        try (ApplicationContext ctx = ApplicationContext.builder(Environment.CLI, Environment.TEST)
            .properties(Map.of("micronaut.http.services.api.url", "http://localhost:28181"))
            .start()) {

            String[] args = {""};
            PicocliRunner.call(PluginSearchCommand.class, ctx, args);

            String output = outputStreamCaptor.toString().trim();
            assertThat(output).contains("Found 2 plugins");
            assertThat(output).contains("plugin-notifications");
            assertThat(output).contains("plugin-scripts");
        }
    }
}