package io.kestra.webserver.controllers;

import org.junit.jupiter.api.Test;

import io.kestra.core.exceptions.NotFoundException;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.hateoas.JsonError;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorControllerTest {
    private enum TestEntity {
        WIDGET
    }

    private final ErrorController controller = new ErrorController();

    @Test
    void shouldSetEntityHeaderWhenExceptionCarriesEntity() {
        // Given
        HttpRequest<?> request = HttpRequest.GET("/api/v1/main/widgets/123");
        NotFoundException exception = new NotFoundException(TestEntity.WIDGET, "Widget not found for id '123'.");

        // When
        HttpResponse<JsonError> response = controller.error(request, exception);

        // Then
        assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.NOT_FOUND.getCode());
        assertThat(response.getHeaders().get(ErrorController.ENTITY_HEADER)).isEqualTo("WIDGET");
    }

    @Test
    void shouldNotSetEntityHeaderWhenExceptionHasNoEntity() {
        // Given
        HttpRequest<?> request = HttpRequest.GET("/api/v1/main/executions/123");
        NotFoundException exception = new NotFoundException("Execution not found for id '123'.");

        // When
        HttpResponse<JsonError> response = controller.error(request, exception);

        // Then
        assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.NOT_FOUND.getCode());
        assertThat(response.getHeaders().contains(ErrorController.ENTITY_HEADER)).isFalse();
    }

    @Test
    void shouldNotSetEntityHeaderOnUnmatchedRouteFallback() {
        // Given
        HttpRequest<?> request = HttpRequest.GET("/api/v1/main/this-route-does-not-exist");

        // When
        HttpResponse<JsonError> response = controller.notFound(request);

        // Then
        assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.NOT_FOUND.getCode());
        assertThat(response.getHeaders().contains(ErrorController.ENTITY_HEADER)).isFalse();
    }
}
