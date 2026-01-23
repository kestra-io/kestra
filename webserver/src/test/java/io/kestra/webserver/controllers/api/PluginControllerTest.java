package io.kestra.webserver.controllers.api;

import io.kestra.core.Helpers;
import io.kestra.core.docs.DocumentationWithSchema;
import io.kestra.core.docs.InputType;
import io.kestra.core.docs.Plugin;
import io.kestra.core.docs.PluginIcon;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.annotations.PluginSubGroup;
import io.kestra.plugin.core.debug.Return;
import io.kestra.plugin.core.log.Log;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
class PluginControllerTest {

    @Inject
    @Client("/")
    ReactorHttpClient client;

    public static final String PATH = "/api/v1/plugins";

    @BeforeAll
    public static void beforeAll() {
        Helpers.loadExternalPluginsFromClasspath();
    }

    @Test
    void plugins() {
        List<Plugin> list = client.toBlocking().retrieve(
            HttpRequest.GET(PATH),
            Argument.listOf(Plugin.class)
        );

        assertThat(list.size()).isEqualTo(2);

        Plugin template = list.stream()
            .filter(plugin -> plugin.getTitle().equals("plugin-template-test"))
            .findFirst()
            .orElseThrow();

        assertThat(template.getTitle()).isEqualTo("plugin-template-test");
        assertThat(template.getGroup()).isEqualTo("io.kestra.plugin.templates");
        assertThat(template.getDescription()).isEqualTo("Plugin template for Kestra");

        assertThat(template.getTasks().size()).isEqualTo(1);
        assertThat(template.getTasks().getFirst().cls()).isEqualTo("io.kestra.plugin.templates.ExampleTask");

        assertThat(template.getGuides().size()).isEqualTo(2);
        assertThat(template.getGuides().getFirst()).isEqualTo("authentication");

        Plugin core = list.stream()
            .filter(plugin -> plugin.getTitle().equals("core"))
            .findFirst()
            .orElseThrow();

        assertThat(core.getCategories()).containsExactlyInAnyOrder(PluginSubGroup.PluginCategory.STORAGE, PluginSubGroup.PluginCategory.CORE);

        // classLoader can lead to duplicate plugins for the core, just verify that the response is still the same
        list = client.toBlocking().retrieve(
            HttpRequest.GET(PATH),
            Argument.listOf(Plugin.class)
        );

        assertThat(list.size()).isEqualTo(2);
    }

    @Test
    void icons() {
        Map<String, PluginIcon> list = client.toBlocking().retrieve(
            HttpRequest.GET(PATH + "/icons"),
            Argument.mapOf(String.class, PluginIcon.class)
        );

        assertThat(list.entrySet().stream()
            .filter(e -> e.getKey().equals(Log.class.getName()))
            .findFirst().orElseThrow().getValue().getIcon()).isNotNull();
        // test an alias
        assertThat(list.entrySet().stream()
            .filter(e -> e.getKey().equals("io.kestra.core.tasks.log.Log"))
            .findFirst().orElseThrow().getValue().getIcon()).isNotNull();
    }


    @SuppressWarnings("unchecked")
    @Test
    void returnTask() {
        DocumentationWithSchema doc = client.toBlocking().retrieve(
            HttpRequest.GET(PATH + "/" + Return.class.getName()),
            DocumentationWithSchema.class
        );

        assertThat(doc.getMarkdown()).contains("io.kestra.plugin.core.debug.Return");
        assertThat(doc.getMarkdown()).contains("Return a value for debugging purposes.");
        assertThat(doc.getMarkdown()).contains("The templated string to render");
        assertThat(doc.getMarkdown()).contains("The generated string");
        assertThat(((Map<String, Object>) doc.getSchema().getProperties().get("properties")).size()).isEqualTo(1);
        assertThat(((Map<String, Object>) doc.getSchema().getOutputs().get("properties")).size()).isEqualTo(1);
    }

    @SuppressWarnings("unchecked")
    @Test
    void docs() {
        DocumentationWithSchema doc = client.toBlocking().retrieve(
            HttpRequest.GET(PATH + "/io.kestra.plugin.templates.ExampleTask"),
            DocumentationWithSchema.class
        );

        assertThat(doc.getMarkdown()).contains("io.kestra.plugin.templates.ExampleTask");
        assertThat(((Map<String, Object>) doc.getSchema().getProperties().get("properties")).size()).isEqualTo(5);
        assertThat(((Map<String, Object>) doc.getSchema().getOutputs().get("properties")).size()).isEqualTo(1);
    }

    @Test
    void docWithAlert() {
        DocumentationWithSchema doc = client.toBlocking().retrieve(
            HttpRequest.GET(PATH + "/io.kestra.plugin.core.state.Set"),
            DocumentationWithSchema.class
        );

        assertThat(doc.getMarkdown()).contains("io.kestra.plugin.core.state.Set");
        assertThat(doc.getMarkdown()).contains("::: warning\n");
    }


    @SuppressWarnings("unchecked")
    @Test
    void taskWithBase() {
        DocumentationWithSchema doc = client.toBlocking().retrieve(
            HttpRequest.GET(PATH + "/io.kestra.plugin.templates.ExampleTask?all=true"),
            DocumentationWithSchema.class
        );

        Map<String, Map<String, Object>> properties = (Map<String, Map<String, Object>>) doc.getSchema().getProperties().get("properties");

        assertThat(doc.getMarkdown()).contains("io.kestra.plugin.templates.ExampleTask");
        assertThat(properties.size()).isEqualTo(19);
        assertThat(properties.get("id").size()).isEqualTo(5);
        assertThat(((Map<String, Object>) doc.getSchema().getOutputs().get("properties")).size()).isEqualTo(1);
    }

    @Test
    void flowSchema() {
        Map<String, Object> doc = client.toBlocking().retrieve(
            HttpRequest.GET(PATH + "/schemas/flow"),
            Argument.mapOf(String.class, Object.class)
        );

        assertThat(doc.get("$ref")).isEqualTo("#/definitions/io.kestra.core.models.flows.Flow");
    }

    @Test
    void flowProperties() {
        Map<String, Object> doc = client.toBlocking().retrieve(
            HttpRequest.GET(PATH + "/properties/flow"),
            Argument.mapOf(String.class, Object.class)
        );

        assertThat((Map<String, Object>) doc.get("properties")).hasSize(23);
        assertThat((List<String>) doc.get("required")).hasSize(3);
    }

    @Test
    void template() {
        Map<String, Object> doc = client.toBlocking().retrieve(
            HttpRequest.GET(PATH + "/schemas/template"),
            Argument.mapOf(String.class, Object.class)
        );

        assertThat(doc.get("$ref")).isEqualTo("#/definitions/io.kestra.core.models.templates.Template");
    }

    @Test
    void task() {
        Map<String, Object> doc = client.toBlocking().retrieve(
            HttpRequest.GET(PATH + "/schemas/task"),
            Argument.mapOf(String.class, Object.class)
        );

        assertThat(doc.get("$ref")).isEqualTo("#/definitions/io.kestra.core.models.tasks.Task");
    }

    @Test
    void inputs() {
        List<InputType> doc = client.toBlocking().retrieve(
            HttpRequest.GET(PATH + "/inputs"),
            Argument.listOf(InputType.class)
        );

        assertThat(doc.size()).isEqualTo(19);
    }

    @SuppressWarnings("unchecked")
    @Test
    void input() {
        DocumentationWithSchema doc = client.toBlocking().retrieve(
            HttpRequest.GET(PATH + "/inputs/STRING"),
            DocumentationWithSchema.class
        );

        assertThat(doc.getSchema().getProperties().size()).isEqualTo(3);
        Map<String, Object> properties = (Map<String, Object>) doc.getSchema().getProperties().get("properties");
        assertThat(properties.size()).isEqualTo(9);
        assertThat(((Map<String, Object>) properties.get("name")).get("$deprecated")).isEqualTo(true);
    }
}
