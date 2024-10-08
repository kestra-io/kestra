package io.kestra.webserver.controllers;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.InvalidTypeIdException;
import io.kestra.core.exceptions.DeserializationException;
import io.kestra.core.exceptions.InvalidException;
import io.kestra.core.exceptions.ResourceExpiredException;
import io.micronaut.core.convert.exceptions.ConversionErrorException;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Error;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.http.hateoas.JsonError;
import io.micronaut.http.hateoas.Link;
import io.micronaut.web.router.exceptions.UnsatisfiedBodyRouteException;
import io.micronaut.web.router.exceptions.UnsatisfiedQueryValueRouteException;
import lombok.extern.slf4j.Slf4j;

import java.io.FileNotFoundException;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.stream.Collectors;
import jakarta.validation.ConstraintViolationException;

@Slf4j
@Controller
public class ErrorController {
    @Error(global = true)
    public HttpResponse<JsonError> error(HttpRequest<?> request, JsonParseException e) {
        return jsonError(request, e, HttpStatus.UNPROCESSABLE_ENTITY, "Invalid json");
    }

    @Error(global = true)
    public HttpResponse<JsonError> error(HttpRequest<?> request, ConversionErrorException e) {
        if (e.getConversionError().getCause() instanceof InvalidTypeIdException) {
            try {
                InvalidTypeIdException invalidTypeIdException = ((InvalidTypeIdException) e.getConversionError().getCause());

                String path = path(invalidTypeIdException);

                Field typeField = InvalidTypeIdException.class.getDeclaredField("_typeId");
                typeField.setAccessible(true);
                Object typeClass = typeField.get(invalidTypeIdException);

                JsonError error = new JsonError("Invalid type: " + typeClass)
                    .link(Link.SELF, Link.of(request.getUri()))
                    .embedded(
                        "errors",
                        Arrays.asList(
                            new JsonError("Invalid type: " + typeClass)
                                .path(path),
                            new JsonError(e.getMessage())
                                .path(path)
                        )
                    );

                return jsonError(error, HttpStatus.UNPROCESSABLE_ENTITY, "Invalid entity");
            } catch (Exception ignored) {
            }
        } else if (e.getConversionError().getCause() instanceof JsonMappingException) {
            try {
                JsonMappingException jsonMappingException = ((JsonMappingException) e.getConversionError().getCause());

                String path = path(jsonMappingException);

                JsonError error = new JsonError("Invalid json mapping")
                    .link(Link.SELF, Link.of(request.getUri()))
                    .embedded(
                        "errors",
                        Collections.singletonList(
                            new JsonError(e.getMessage())
                                .path(path)
                        )
                    );

                return jsonError(error, HttpStatus.UNPROCESSABLE_ENTITY, "Invalid json mapping");
            } catch (Exception ignored) {
            }
        }

        return jsonError(request, e, HttpStatus.UNPROCESSABLE_ENTITY, "Internal server error");
    }

    @SuppressWarnings("unchecked")
    private static String path(JsonMappingException jsonMappingException) throws NoSuchFieldException, IllegalAccessException {
        Field pathField = JsonMappingException.class.getDeclaredField("_path");
        pathField.setAccessible(true);
        LinkedList<JsonMappingException.Reference> path = (LinkedList<JsonMappingException.Reference>) pathField.get(jsonMappingException);

        return path
            .stream()
            .map(JsonMappingException.Reference::getDescription)
            .collect(Collectors.joining(" > "));
    }

    @Error(global = true)
    public HttpResponse<JsonError> error(HttpRequest<?> request, ConstraintViolationException e) {
        JsonError error = new JsonError("Invalid entity: " + e.getMessage())
            .link(Link.SELF, Link.of(request.getUri()))
            .embedded(
                "errors",
                e.getConstraintViolations()
                    .stream()
                    .map(ex -> new JsonError(ex.getMessage())
                        .path(ex.getPropertyPath().toString())
                    )
                    .collect(Collectors.toList())
            );


        return jsonError(error, HttpStatus.UNPROCESSABLE_ENTITY, "Invalid entity");
    }

    @Error(global = true)
    public HttpResponse<JsonError> error(HttpRequest<?> request, IllegalArgumentException e) {
        return jsonError(request, e, HttpStatus.UNPROCESSABLE_ENTITY, "Illegal argument");
    }

    @Error(global = true)
    public HttpResponse<JsonError> error(HttpRequest<?> request, IllegalStateException e) {
        return jsonError(request, e, HttpStatus.CONFLICT, "Illegal state");
    }

    @Error(global = true)
    public HttpResponse<JsonError> error(HttpRequest<?> request, InvalidFormatException e) {
        return jsonError(request, e, HttpStatus.UNPROCESSABLE_ENTITY, "Invalid format");
    }

    @Error(global = true)
    public HttpResponse<JsonError> error(HttpRequest<?> request, UnsatisfiedBodyRouteException e) {
        return jsonError(request, e, HttpStatus.UNPROCESSABLE_ENTITY, "Invalid route params");
    }

    @Error(global = true)
    public HttpResponse<JsonError> error(HttpRequest<?> request, InvalidException e) {
        String entity = Optional.ofNullable(e.invalidObject()).map(Object::getClass).map(Class::getSimpleName).orElse("entity");
        return jsonError(request, e, HttpStatus.UNPROCESSABLE_ENTITY, "Invalid " + entity);
    }

    @Error(global = true)
    public HttpResponse<JsonError> error(HttpRequest<?> request, HttpStatusException e) {
        return jsonError(request, e, e.getStatus(), e.getStatus().getReason());
    }

    @Error(global = true)
    public HttpResponse<JsonError> error(HttpRequest<?> request, Throwable e) {
        return jsonError(request, e, HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error");
    }

    @Error(global = true, status = HttpStatus.NOT_FOUND)
    public HttpResponse<JsonError> notFound(HttpRequest<?> request) {
        return jsonError(request, HttpStatus.NOT_FOUND, "Not Found");
    }

    @Error(global = true)
    public HttpResponse<JsonError> notFound(HttpRequest<?> request, NoSuchElementException e) {
        return jsonError(request, e, HttpStatus.NOT_FOUND, "Not Found");
    }

    @Error(global = true)
    public HttpResponse<JsonError> notFound(HttpRequest<?> request, FileNotFoundException e) {
        return jsonError(request, e, HttpStatus.NOT_FOUND, "Not Found");
    }

    @Error(global = true)
    public HttpResponse<JsonError> notFound(HttpRequest<?> request, UnsatisfiedQueryValueRouteException e) {
        return jsonError(request, e, HttpStatus.BAD_REQUEST, "Bad Request");
    }

    @Error(global = true)
    public HttpResponse<JsonError> serialization(HttpRequest<?> request, DeserializationException e) {
        return jsonError(request, e, HttpStatus.LOCKED, "Locked");
    }

    @Error(global = true)
    public HttpResponse<JsonError> serialization(HttpRequest<?> request, ResourceExpiredException e) {
        return jsonError(request, e, HttpStatus.GONE, "Resource has expired");
    }

    @Error(global = true)
    public HttpResponse<JsonError> httpClient(HttpRequest<?> request, HttpClientResponseException e) {
        return jsonError(request, e, e.getStatus(), e.getStatus().getReason());
    }

    private static HttpResponse<JsonError> jsonError(JsonError jsonError, HttpStatus status, String reason) {
        return HttpResponse
            .<JsonError>status(status, reason)
            .body(jsonError);
    }

    public static HttpResponse<JsonError> jsonError(HttpRequest<?> request, HttpStatus status, String reason) {
        JsonError error = new JsonError(reason)
            .link(Link.SELF, Link.of(request.getUri()));

        return jsonError(error, status, reason);
    }

    public static HttpResponse<JsonError> jsonError(HttpRequest<?> request, Throwable e, HttpStatus status, String reason) {
        if (status == HttpStatus.INTERNAL_SERVER_ERROR) {
            log.error(e.getMessage(), e);
        } else {
            log.trace(e.getMessage(), e);
        }

        JsonError error = new JsonError(reason + (e.getMessage() != null ? ": " + e.getMessage() : ""))
            .link(Link.SELF, Link.of(request.getUri()));

        return jsonError(error, status, reason);
    }
}
