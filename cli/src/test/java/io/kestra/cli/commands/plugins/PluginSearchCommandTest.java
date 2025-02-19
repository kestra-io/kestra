package io.kestra.cli.commands.plugins;

import io.micronaut.configuration.picocli.PicocliRunner;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.AfterEach;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

class PluginSearchCommandTest {
    private WireMockServer wireMockServer;
    private final ByteArrayOutputStream outputStreamCaptor = new ByteArrayOutputStream();
    private PrintStream standardOut;

    @BeforeEach
    void setUp() {
        // Setup WireMock server
        wireMockServer = new WireMockServer(8080);
        wireMockServer.start();
        WireMock.configureFor("localhost", 8080);

        // Setup output capture
        standardOut = System.out;
        System.setOut(new PrintStream(outputStreamCaptor));
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
        System.setOut(standardOut);
    }

    @Test
    void searchWithExactMatch() {
        // Setup mock response
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

        try (ApplicationContext ctx = ApplicationContext.run(Environment.CLI, Environment.TEST)) {
            String[] args = {"notifications"};
            PicocliRunner.call(PluginSearchCommand.class, ctx, args);

            String output = outputStreamCaptor.toString();

            assertThat(output, containsString("Found 1 plugins matching 'notifications'"));
            assertThat(output, containsString("plugin-notifications"));
            assertThat(output, containsString("0.6.0"));
            assertThat(output, not(containsString("plugin-scripts")));
        }
    }

    @Test
    void searchWithNoResults() {
        stubFor(get(urlEqualTo("/v1/plugins"))
            .willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("[]")));

        try (ApplicationContext ctx = ApplicationContext.run(Environment.CLI, Environment.TEST)) {
            String[] args = {"nonexistent-plugin"};
            PicocliRunner.call(PluginSearchCommand.class, ctx, args);

            String output = outputStreamCaptor.toString();
            assertThat(output, containsString("No plugins found matching 'nonexistent-plugin'"));
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

        try (ApplicationContext ctx = ApplicationContext.run(Environment.CLI, Environment.TEST)) {
            String[] args = {""};
            PicocliRunner.call(PluginSearchCommand.class, ctx, args);

            String output = outputStreamCaptor.toString();
            assertThat(output, containsString("Found 2 plugins"));
            assertThat(output, containsString("plugin-notifications"));
            assertThat(output, containsString("plugin-scripts"));
        }
    }

    @Test
    void handleApiError() {
        stubFor(get(urlEqualTo("/v1/plugins"))
            .willReturn(aResponse()
                .withStatus(500)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"error\": \"Internal Server Error\"}")));

        try (ApplicationContext ctx = ApplicationContext.run(Environment.CLI, Environment.TEST)) {
            String[] args = {""};
            Integer result = PicocliRunner.call(PluginSearchCommand.class, ctx, args);

            assertThat(result, is(1));
            String output = outputStreamCaptor.toString();
            assertThat(output, containsString("API request failed with status: 500"));
        }
    }

    @Test
    void searchWithPartialMatch() {
        stubFor(get(urlEqualTo("/v1/plugins"))
            .willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    [
                        {
                            "name": "storage-s3",
                            "title": "S3 Storage",
                            "group": "io.kestra.storage",
                            "version": "0.12.1"
                        },
                        {
                            "name": "storage-local",
                            "title": "Local Storage",
                            "group": "io.kestra.storage",
                            "version": "0.12.0"
                        }
                    ]
                """)));

        try (ApplicationContext ctx = ApplicationContext.run(Environment.CLI, Environment.TEST)) {
            String[] args = {"storage"};
            PicocliRunner.call(PluginSearchCommand.class, ctx, args);

            String output = outputStreamCaptor.toString();
            assertThat(output, containsString("Found 2 plugins matching 'storage'"));
            assertThat(output, containsString("storage-s3"));
            assertThat(output, containsString("storage-local"));
            assertThat(output, containsString("0.12.1"));
            assertThat(output, containsString("0.12.0"));
        }
    }
}