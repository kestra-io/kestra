package org.kestra.webserver.controllers;

import com.devskiller.friendly_id.FriendlyId;
import com.google.common.collect.ImmutableList;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.RxHttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.hateoas.JsonError;
import org.junit.jupiter.api.Test;
import org.kestra.core.Helpers;
import org.kestra.core.models.tasks.Task;
import org.kestra.core.models.templates.Template;
import org.kestra.core.runners.AbstractMemoryRunnerTest;
import org.kestra.core.tasks.debugs.Return;
import org.kestra.webserver.responses.PagedResults;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static io.micronaut.http.HttpRequest.*;
import static io.micronaut.http.HttpStatus.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TemplateControllerTest extends AbstractMemoryRunnerTest {
    @Inject
    @Client("/")
    RxHttpClient client;

    public static final String TESTS_FLOW_NS = "org.kestra.tests";

    private Template createTemplate() {
        Task t1 = Return.builder().id("task-1").type(Return.class.getName()).format("test").build();
        Task t2 = Return.builder().id("task-2").type(Return.class.getName()).format("test").build();
        return Template.builder()
            .id(FriendlyId.createFriendlyId())
            .tasks(Arrays.asList(t1, t2)).build();
    }

    @Test
    void create() {
        Template template = createTemplate();
        HttpClientResponseException e = assertThrows(HttpClientResponseException.class, () -> {
            client.toBlocking().retrieve(HttpRequest.GET("/api/v1/templates/" + template.getId()));
        });
        assertThat(e.getStatus(), is(HttpStatus.NOT_FOUND));

        Template result = client.toBlocking().retrieve(POST("/api/v1/templates", template), Template.class);
        Template createdTemplate = client.toBlocking().retrieve(HttpRequest.GET("/api/v1/templates/" + template.getId()), Template.class);
        assertThat(createdTemplate.getId(), is(template.getId()));
    }

    @Test
    void idNotFound() {
        HttpClientResponseException e = assertThrows(HttpClientResponseException.class, () -> {
            client.toBlocking().retrieve(HttpRequest.GET("/api/v1/templates/notFound"));
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
        Template createdTemplate = client.toBlocking().retrieve(HttpRequest.GET("/api/v1/templates/" + template.getId()), Template.class);
        assertThat(createdTemplate.getId(), is(template.getId()));
        HttpResponse<Void> deleteResult = client.toBlocking().exchange(
            DELETE("/api/v1/templates/" + template.getId())
        );
        assertThat(deleteResult.getStatus(), is(NO_CONTENT));
        HttpClientResponseException e = assertThrows(HttpClientResponseException.class, () -> {
            client.toBlocking().retrieve(HttpRequest.GET("/api/v1/templates/" + template.getId()));
        });
        assertThat(e.getStatus(), is(HttpStatus.NOT_FOUND));
    }

    @Test
    void updateTemplate() {
        Template template = createTemplate();
        client.toBlocking().retrieve(POST("/api/v1/templates", template), Template.class);
        Template createdTemplate = client.toBlocking().retrieve(HttpRequest.GET("/api/v1/templates/" + template.getId()), Template.class);
        assertThat(template.getTasks().size(), is(2));
        Task t3 = Return.builder().id("task-3").type(Return.class.getName()).format("test").build();
        Template updateTemplate = Template.builder().id(template.getId()).tasks(Arrays.asList(t3)).build();
        client.toBlocking().retrieve(PUT("/api/v1/templates/" + template.getId(), updateTemplate), Template.class);
        Template updatedTemplate = client.toBlocking().retrieve(HttpRequest.GET("/api/v1/templates/" + template.getId()), Template.class);
        assertThat(updatedTemplate.getTasks().size(), is(1));
        assertThat(updatedTemplate.getTasks().get(0).getId(), is("task-3"));
    }

}