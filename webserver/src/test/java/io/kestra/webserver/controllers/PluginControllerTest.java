package io.kestra.webserver.controllers;

import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.RxHttpClient;
import org.junit.jupiter.api.Test;
import io.kestra.core.Helpers;
import io.kestra.core.tasks.scripts.Bash;

import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

class PluginControllerTest {
    @Test
    void plugins() throws URISyntaxException {
        Helpers.runApplicationContext((applicationContext, embeddedServer) -> {
            RxHttpClient client = RxHttpClient.create(embeddedServer.getURL());

            List<io.kestra.webserver.controllers.PluginController.Plugin> list = client.toBlocking().retrieve(
                HttpRequest.GET("/api/v1/plugins"),
                Argument.listOf(io.kestra.webserver.controllers.PluginController.Plugin.class)
            );

            assertThat(list.size(), is(2));

            io.kestra.webserver.controllers.PluginController.Plugin template = list.stream()
                .filter(plugin -> plugin.getManifest().get("X-Kestra-Title").equals("plugin-template-test"))
                .findFirst()
                .orElseThrow();

            assertThat(template.getTasks().size(), is(1));
            assertThat(template.getTasks().get(0), is("io.kestra.plugin.templates.ExampleTask"));

            // classLoader can lead to duplicate plugins for the core, just verify that the response is still the same
            list = client.toBlocking().retrieve(
                HttpRequest.GET("/api/v1/plugins"),
                Argument.listOf(io.kestra.webserver.controllers.PluginController.Plugin.class)
            );

            assertThat(list.size(), is(2));

        });
    }

    @Test
    void icons() throws URISyntaxException {
        Helpers.runApplicationContext((applicationContext, embeddedServer) -> {
            RxHttpClient client = RxHttpClient.create(embeddedServer.getURL());

            Map<String, io.kestra.webserver.controllers.PluginController.PluginIcon> list = client.toBlocking().retrieve(
                HttpRequest.GET("/api/v1/plugins/icons"),
                Argument.mapOf(String.class, io.kestra.webserver.controllers.PluginController.PluginIcon.class)
            );

            assertThat(list.entrySet().stream().filter(e -> e.getKey().equals(Bash.class.getName())).findFirst().orElseThrow().getValue().getIcon(), is(notNullValue()));
        });
    }


    @SuppressWarnings("unchecked")
    @Test
    void bash() throws URISyntaxException {
        Helpers.runApplicationContext((applicationContext, embeddedServer) -> {
            RxHttpClient client = RxHttpClient.create(embeddedServer.getURL());

            io.kestra.webserver.controllers.PluginController.Doc doc = client.toBlocking().retrieve(
                HttpRequest.GET("/api/v1/plugins/io.kestra.core.tasks.scripts.Bash"),
                io.kestra.webserver.controllers.PluginController.Doc.class
            );

            assertThat(doc.getMarkdown(), containsString("io.kestra.core.tasks.scripts.Bash"));
            assertThat(doc.getMarkdown(), containsString("Exit if any non true return value"));
            assertThat(doc.getMarkdown(), containsString("The standard output line count"));
            assertThat(((Map<String, Object>) doc.getSchema().getProperties().get("properties")).size(), is(9));
            assertThat(((Map<String, Object>) doc.getSchema().getOutputs().get("properties")).size(), is(6));
        });
    }

    @SuppressWarnings("unchecked")
    @Test
    void docs() throws URISyntaxException {
        Helpers.runApplicationContext((applicationContext, embeddedServer) -> {
            RxHttpClient client = RxHttpClient.create(embeddedServer.getURL());

            io.kestra.webserver.controllers.PluginController.Doc doc = client.toBlocking().retrieve(
                HttpRequest.GET("/api/v1/plugins/io.kestra.plugin.templates.ExampleTask"),
                io.kestra.webserver.controllers.PluginController.Doc.class
            );

            assertThat(doc.getMarkdown(), containsString("io.kestra.plugin.templates.ExampleTask"));
            assertThat(((Map<String, Object>) doc.getSchema().getProperties().get("properties")).size(), is(2));
            assertThat(((Map<String, Object>) doc.getSchema().getOutputs().get("properties")).size(), is(1));
        });
    }
}
