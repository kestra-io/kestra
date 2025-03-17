package io.kestra.webserver.controllers.api;

import static io.micronaut.http.HttpRequest.DELETE;
import static io.micronaut.http.HttpRequest.POST;
import static io.micronaut.http.HttpRequest.PUT;
import static io.micronaut.http.HttpStatus.NO_CONTENT;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.templates.Template;
import io.kestra.core.utils.IdUtils;
import io.kestra.jdbc.repository.AbstractJdbcTemplateRepository;
import io.kestra.plugin.core.debug.Return;
import io.kestra.webserver.controllers.domain.IdWithNamespace;
import io.kestra.webserver.responses.BulkResponse;
import io.kestra.webserver.responses.PagedResults;
import io.micronaut.context.annotation.Property;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.client.multipart.MultipartBody;
import io.micronaut.reactor.http.client.ReactorHttpClient;
import jakarta.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@KestraTest
@io.micronaut.context.annotation.Property(name = "kestra.templates.enabled", value = StringUtils.TRUE)
class TemplateControllerTest {
    @Inject
    @Client("/")
    ReactorHttpClient client;

    @Inject
    AbstractJdbcTemplateRepository templateRepository;

    @BeforeEach
    protected void init() {
        templateRepository.findAll(null)
            .forEach(templateRepository::delete);
    }

    private Template createTemplate() {
        Task t1 = Return.builder().id("task-1").type(Return.class.getName()).format(io.kestra.core.models.property.Property.of("test")).build();
        Task t2 = Return.builder().id("task-2").type(Return.class.getName()).format(io.kestra.core.models.property.Property.of("test")).build();
        return Template.builder()
            .id(IdUtils.create())
            .namespace("kestra.test")
            .description("My template description")
            .tasks(Arrays.asList(t1, t2)).build();
    }

    private Template createTemplate(String friendlyId, String namespace) {
        Task t1 = Return.builder().id("task-1").type(Return.class.getName()).format(io.kestra.core.models.property.Property.of("test")).build();
        Task t2 = Return.builder().id("task-2").type(Return.class.getName()).format(io.kestra.core.models.property.Property.of("test")).build();
        return Template.builder()
            .id(friendlyId)
            .namespace(namespace)
            .description("My template description")
            .tasks(Arrays.asList(t1, t2)).build();
    }

    private Template postTemplate(String friendlyId, String namespace) {
        return client.toBlocking().retrieve(POST("/api/v1/templates", createTemplate(friendlyId, namespace)), Template.class);
    }

    @Test
    void create() {
        Template template = createTemplate();
        HttpClientResponseException e = assertThrows(HttpClientResponseException.class, () -> {
            client.toBlocking().retrieve(HttpRequest.GET("/api/v1/templates/io.kestra.tests/" + template.getId()));
        });
        assertThat(e.getStatus(), is(HttpStatus.NOT_FOUND));

        Template result = client.toBlocking().retrieve(POST("/api/v1/templates", template), Template.class);
        Template createdTemplate = client.toBlocking().retrieve(HttpRequest.GET("/api/v1/templates/" + template.getNamespace() + "/" + template.getId()), Template.class);
        assertThat(createdTemplate.getId(), is(template.getId()));
        assertThat(createdTemplate.getDescription(), is("My template description"));
    }

    @Test
    void idNotFound() {
        HttpClientResponseException e = assertThrows(HttpClientResponseException.class, () -> {
            client.toBlocking().retrieve(HttpRequest.GET("/api/v1/templates/io.kestra.tests/notFound"));
        });
        assertThat(e.getStatus(), is(HttpStatus.NOT_FOUND));
    }

    @SuppressWarnings("unchecked")
    @Test
    void findAll() {
        client.toBlocking().retrieve(POST("/api/v1/templates", createTemplate()), Template.class);
        int size1 = client.toBlocking().retrieve(HttpRequest.GET("/api/v1/templates/search?q=*"), Argument.of(PagedResults.class, Template.class)).getResults().size();
        client.toBlocking().retrieve(POST("/api/v1/templates", createTemplate()), Template.class);
        int size2 = client.toBlocking().retrieve(HttpRequest.GET("/api/v1/templates/search?q=*"), Argument.of(PagedResults.class, Template.class)).getResults().size();
        assertThat(size1, is(size2 - 1));
    }

    @Test
    void deleteTemplate() {
        Template template = createTemplate();
        client.toBlocking().retrieve(POST("/api/v1/templates", template), Template.class);
        Template createdTemplate = client.toBlocking().retrieve(HttpRequest.GET("/api/v1/templates/" + template.getNamespace() + "/" + template.getId()), Template.class);
        assertThat(createdTemplate.getId(), is(template.getId()));
        HttpResponse<Void> deleteResult = client.toBlocking().exchange(
            DELETE("/api/v1/templates/" + template.getNamespace() + "/" + template.getId())
        );
        assertThat(deleteResult.getStatus(), is(NO_CONTENT));
        HttpClientResponseException e = assertThrows(HttpClientResponseException.class, () -> {
            client.toBlocking().retrieve(HttpRequest.GET("/api/v1/templates/" + template.getNamespace() + "/" + template.getId()));
        });
        assertThat(e.getStatus(), is(HttpStatus.NOT_FOUND));
    }

    @Test
    void updateTemplate() {
        Template template = createTemplate();
        client.toBlocking().retrieve(POST("/api/v1/templates", template), Template.class);
        Template createdTemplate = client.toBlocking().retrieve(HttpRequest.GET("/api/v1/templates/" + template.getNamespace() + "/" + template.getId()), Template.class);
        assertThat(template.getTasks().size(), is(2));
        Task t3 = Return.builder().id("task-3").type(Return.class.getName()).format(io.kestra.core.models.property.Property.of("test")).build();
        Template updateTemplate = Template.builder().id(template.getId()).namespace(template.getNamespace()).description("My new template description").tasks(Arrays.asList(t3)).build();
        client.toBlocking().retrieve(PUT("/api/v1/templates/" + template.getNamespace() + "/" + template.getId(), updateTemplate), Template.class);
        Template updatedTemplate = client.toBlocking().retrieve(HttpRequest.GET("/api/v1/templates/" + template.getNamespace() + "/" + template.getId()), Template.class);
        assertThat(updatedTemplate.getTasks().size(), is(1));
        assertThat(updatedTemplate.getTasks().getFirst().getId(), is("task-3"));
        assertThat(updatedTemplate.getDescription(),is("My new template description"));
    }

    @Test
    void listDistinctNamespace() {
        List<String> namespaces = client.toBlocking().retrieve(
            HttpRequest.GET("/api/v1/templates/distinct-namespaces"), Argument.listOf(String.class));
        assertThat(namespaces.size(), is(0));
            Template t1 = Template.builder()
            .id(IdUtils.create())
            .namespace("kestra.template.custom")
            .tasks(Arrays.asList(Return.builder().id("task").type(Return.class.getName()).format(io.kestra.core.models.property.Property.of("test")).build()))
            .build();
        client.toBlocking().retrieve(POST("/api/v1/templates", t1), Template.class);
        client.toBlocking().retrieve(POST("/api/v1/templates", createTemplate()), Template.class);
        client.toBlocking().retrieve(POST("/api/v1/templates", createTemplate()), Template.class);
        namespaces = client.toBlocking().retrieve(
            HttpRequest.GET("/api/v1/templates/distinct-namespaces"), Argument.listOf(String.class));

        assertThat(namespaces.size(), is(2));
    }

    @Test
    void exportByQuery() throws IOException {
        // create 3 templates, so we have at least 3 of them
        client.toBlocking().retrieve(POST("/api/v1/templates", createTemplate()), Template.class);
        client.toBlocking().retrieve(POST("/api/v1/templates", createTemplate()), Template.class);
        client.toBlocking().retrieve(POST("/api/v1/templates", createTemplate()), Template.class);
        int size = client.toBlocking().retrieve(HttpRequest.GET("/api/v1/templates/search?namespace=kestra.test"), Argument.of(PagedResults.class, Template.class)).getResults().size();

        byte[] zip = client.toBlocking().retrieve(HttpRequest.GET("/api/v1/templates/export/by-query?namespace=kestra.test"),
            Argument.of(byte[].class));
        File file = File.createTempFile("templates", ".zip");
        Files.write(file.toPath(), zip);

        try (ZipFile zipFile = new ZipFile(file)) {
            assertThat(zipFile.stream().count(), is((long) size));
        }

        file.delete();
    }

    @Test
    void exportByIds() throws IOException {
        // create 3 templates, so we can retrieve them by id
        var template1 = client.toBlocking().retrieve(POST("/api/v1/templates", createTemplate()), Template.class);
        var template2 = client.toBlocking().retrieve(POST("/api/v1/templates", createTemplate()), Template.class);
        var template3 = client.toBlocking().retrieve(POST("/api/v1/templates", createTemplate()), Template.class);

        List<IdWithNamespace> ids = List.of(
            new IdWithNamespace("kestra.test", template1.getId()),
            new IdWithNamespace("kestra.test", template2.getId()),
            new IdWithNamespace("kestra.test", template3.getId()));
        byte[] zip = client.toBlocking().retrieve(HttpRequest.POST("/api/v1/templates/export/by-ids?namespace=kestra.test", ids),
            Argument.of(byte[].class));
        File file = File.createTempFile("templates", ".zip");
        Files.write(file.toPath(), zip);

        try(ZipFile zipFile = new ZipFile(file)) {
            assertThat(zipFile.stream().count(), is(3L));
        }

        file.delete();
    }

    @Test
    void importTemplatesWithYaml() throws IOException {
        var yaml = createTemplate().generateSource() + "---" +
            createTemplate().generateSource() + "---" +
            createTemplate().generateSource();

        var temp = File.createTempFile("templates", ".yaml");
        Files.writeString(temp.toPath(), yaml);
        var body = MultipartBody.builder()
            .addPart("fileUpload", "templates.yaml", temp)
            .build();
        var response = client.toBlocking().exchange(POST("/api/v1/templates/import", body).contentType(MediaType.MULTIPART_FORM_DATA));

        assertThat(response.getStatus(), is(NO_CONTENT));
        temp.delete();
    }

    @Test
    void importTemplatesWithZip() throws IOException {
        // create 3 templates, so we have at least 3 of them
        client.toBlocking().retrieve(POST("/api/v1/templates", createTemplate()), Template.class);
        client.toBlocking().retrieve(POST("/api/v1/templates", createTemplate()), Template.class);
        client.toBlocking().retrieve(POST("/api/v1/templates", createTemplate()), Template.class);
        int size = client.toBlocking().retrieve(HttpRequest.GET("/api/v1/templates/search?namespace=kestra.test"), Argument.of(PagedResults.class, Template.class)).getResults().size();

        // extract the created templates
        byte[] zip = client.toBlocking().retrieve(HttpRequest.GET("/api/v1/templates/export/by-query?namespace=kestra.test"),
            Argument.of(byte[].class));
        File temp = File.createTempFile("templates", ".zip");
        Files.write(temp.toPath(), zip);

        // import the templates
        var body = MultipartBody.builder()
            .addPart("fileUpload", "templates.zip", temp)
            .build();
        var response = client.toBlocking().exchange(POST("/api/v1/templates/import", body).contentType(MediaType.MULTIPART_FORM_DATA));

        assertThat(response.getStatus(), is(NO_CONTENT));
        temp.delete();
    }

    @Test
    void deleteTemplatesByIds() {
        postTemplate("template-a", "kestra.test.delete");
        postTemplate("template-b", "kestra.test.delete");
        postTemplate("template-c", "kestra.test.delete");

        List<IdWithNamespace> ids = List.of(
            new IdWithNamespace("kestra.test.delete", "template-a"),
            new IdWithNamespace("kestra.test.delete", "template-b"),
            new IdWithNamespace("kestra.test.delete", "template-c")
        );

        HttpResponse<BulkResponse> response = client
            .toBlocking()
            .exchange(DELETE("/api/v1/templates/delete/by-ids", ids), BulkResponse.class);

        assertThat(response.getBody().get().getCount(), is(3));

        HttpClientResponseException templateA = assertThrows(HttpClientResponseException.class, () -> {
            client.toBlocking().retrieve(HttpRequest.GET("/api/v1/templates/kestra.test.delete/template-a"));
        });
        HttpClientResponseException templateB = assertThrows(HttpClientResponseException.class, () -> {
            client.toBlocking().retrieve(HttpRequest.GET("/api/v1/templates/kestra.test.delete/template-b"));
        });
        HttpClientResponseException templateC = assertThrows(HttpClientResponseException.class, () -> {
            client.toBlocking().retrieve(HttpRequest.GET("/api/v1/templates/kestra.test.delete/template-c"));
        });

        assertThat(templateA.getStatus(), is(HttpStatus.NOT_FOUND));
        assertThat(templateB.getStatus(), is(HttpStatus.NOT_FOUND));
        assertThat(templateC.getStatus(), is(HttpStatus.NOT_FOUND));
    }

    @Test
    void deleteTemplatesByQuery() {
        Template template = createTemplate("toDelete", "kestra.test.delete");
        client.toBlocking().retrieve(POST("/api/v1/templates", template), String.class);

        HttpResponse<BulkResponse> response = client
            .toBlocking()
            .exchange(DELETE("/api/v1/templates/delete/by-query?namespace=kestra.test.delete"), BulkResponse.class);

        assertThat(response.getBody().get().getCount(), is(1));

        HttpClientResponseException e = assertThrows(HttpClientResponseException.class, () -> {
            client.toBlocking().retrieve(HttpRequest.GET("/api/v1/templates/kestra.test.delete/toDelete"));
        });

        assertThat(e.getStatus(), is(HttpStatus.NOT_FOUND));
    }
}
