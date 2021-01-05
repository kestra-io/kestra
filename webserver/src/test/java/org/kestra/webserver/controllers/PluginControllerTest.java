package org.kestra.webserver.controllers;

import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.RxHttpClient;
import org.junit.jupiter.api.Test;
import org.kestra.core.Helpers;
import org.kestra.core.tasks.scripts.Bash;

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

            List<PluginController.Plugin> list = client.toBlocking().retrieve(
                HttpRequest.GET("/api/v1/plugins"),
                Argument.listOf(PluginController.Plugin.class)
            );

            assertThat(list.size(), is(2));

            PluginController.Plugin template = list.stream()
                .filter(plugin -> plugin.getManifest().get("X-Kestra-Title").equals("plugin-template-test"))
                .findFirst()
                .orElseThrow();

            assertThat(template.getTasks().size(), is(1));
            assertThat(template.getTasks().get(0), is("io.kestra.task.templates.ExampleTask"));

            // classLoader can lead to duplicate plugins for the core, just verify that the response is still the same
            list = client.toBlocking().retrieve(
                HttpRequest.GET("/api/v1/plugins"),
                Argument.listOf(PluginController.Plugin.class)
            );

            assertThat(list.size(), is(2));

        });
    }

    @Test
    void icons() throws URISyntaxException {
        Helpers.runApplicationContext((applicationContext, embeddedServer) -> {
            RxHttpClient client = RxHttpClient.create(embeddedServer.getURL());

            Map<String, PluginController.PluginIcon> list = client.toBlocking().retrieve(
                HttpRequest.GET("/api/v1/plugins/icons"),
                Argument.mapOf(String.class, PluginController.PluginIcon.class)
            );

            assertThat(list.entrySet().stream().filter(e -> e.getKey().equals(Bash.class.getName())).findFirst().orElseThrow().getValue().getIcon(), is(notNullValue()));
        });
    }


    @Test
    void bash() throws URISyntaxException {
        Helpers.runApplicationContext((applicationContext, embeddedServer) -> {
            RxHttpClient client = RxHttpClient.create(embeddedServer.getURL());

            PluginController.Doc doc = client.toBlocking().retrieve(
                HttpRequest.GET("/api/v1/plugins/org.kestra.core.tasks.scripts.Bash"),
                PluginController.Doc.class
            );

            assertThat(doc.getMarkdown(), containsString("org.kestra.core.tasks.scripts.Bash"));
            assertThat(doc.getMarkdown(), containsString("Exit if any non true return value"));
            assertThat(doc.getMarkdown(), containsString("The standard output line count"));
            assertThat(((Map<String, Object>) doc.getSchema().getProperties().get("properties")).size(), is(8));
            assertThat(((Map<String, Object>) doc.getSchema().getOutputs().get("properties")).size(), is(6));
        });
    }

    @Test
    void docs() throws URISyntaxException {
        Helpers.runApplicationContext((applicationContext, embeddedServer) -> {
            RxHttpClient client = RxHttpClient.create(embeddedServer.getURL());

            PluginController.Doc doc = client.toBlocking().retrieve(
                HttpRequest.GET("/api/v1/plugins/io.kestra.task.templates.ExampleTask"),
                PluginController.Doc.class
            );

            assertThat(doc.getMarkdown(), containsString("io.kestra.task.templates.ExampleTask"));
            assertThat(((Map<String, Object>) doc.getSchema().getProperties().get("properties")).size(), is(2));
            assertThat(((Map<String, Object>) doc.getSchema().getOutputs().get("properties")).size(), is(1));
        });
    }
}
