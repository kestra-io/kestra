package org.kestra.webserver.controllers;

import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.RxHttpClient;
import org.junit.jupiter.api.Test;
import org.kestra.core.Helpers;

import java.net.URISyntaxException;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

class PluginControllerTest {
    @Test
    void plugins() throws URISyntaxException {
        Helpers.runApplicationContext((applicationContext, embeddedServer) -> {
            RxHttpClient client = RxHttpClient.create(embeddedServer.getURL());

            List<PluginController.Plugin> list = client.toBlocking().retrieve(
                HttpRequest.GET("/api/v1/plugins"),
                Argument.listOf(PluginController.Plugin.class)
            );

            assertThat(list.size(), is(2));

            PluginController.Plugin template = list.stream()
                .filter(plugin -> plugin.getManifest().get("X-Kestra-Title").equals("plugin-template"))
                .findFirst()
                .orElseThrow();

            assertThat(template.getTasks().size(), is(1));
            assertThat(template.getTasks().get(0), is("io.kestra.task.templates.Example"));

            // classLoader can lead to duplicate plugins for the core, just verify that the response is still the same
            list = client.toBlocking().retrieve(
                HttpRequest.GET("/api/v1/plugins"),
                Argument.listOf(PluginController.Plugin.class)
            );

            assertThat(list.size(), is(2));

        });
    }

    @Test
    void docs() throws URISyntaxException {
        Helpers.runApplicationContext((applicationContext, embeddedServer) -> {
            RxHttpClient client = RxHttpClient.create(embeddedServer.getURL());

            PluginController.Doc doc = client.toBlocking().retrieve(
                HttpRequest.GET("/api/v1/plugins/io.kestra.task.templates.Example"),
                PluginController.Doc.class
            );

            assertThat(doc.getMarkdown(), containsString("io.kestra.task.templates.Example"));
            assertThat(doc.getDetails().getInputs().size(), is(3));
            assertThat(doc.getDetails().getOutputs().size(), is(6));
        });
    }
}
