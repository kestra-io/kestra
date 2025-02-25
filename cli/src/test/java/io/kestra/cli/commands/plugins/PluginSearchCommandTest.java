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
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

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
            .properties(Map.of("kestra.plugins.api-url", "http://localhost:28181/v1/plugins"))
            .start()) {
            String[] args = {"notifications"};
            PicocliRunner.call(PluginSearchCommand.class, ctx, args);

            String output = outputStreamCaptor.toString().trim();
            System.out.println("Captured output: [" + output + "]"); // Debug
            assertThat(output, containsString("Found 1 plugins matching 'notifications'"));
            assertThat(output, containsString("plugin-notifications"));
            assertThat(output, not(containsString("plugin-scripts")));
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
            .properties(Map.of("kestra.plugins.api-url", "http://localhost:28181/v1/plugins"))
            .start()) {
            String[] args = {""};
            PicocliRunner.call(PluginSearchCommand.class, ctx, args);

            String output = outputStreamCaptor.toString().trim();
            System.out.println("Captured output: [" + output + "]"); // Debug
            assertThat(output, containsString("Found 2 plugins"));
            assertThat(output, containsString("plugin-notifications"));
            assertThat(output, containsString("plugin-scripts"));
        }
    }
}