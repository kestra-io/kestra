package io.kestra.webserver.controllers;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.InvalidTypeIdException;
import io.micronaut.core.convert.exceptions.ConversionErrorException;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Error;
import io.micronaut.http.hateoas.JsonError;
import io.micronaut.http.hateoas.Link;
import lombok.extern.slf4j.Slf4j;

import java.io.FileNotFoundException;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;
import javax.validation.ConstraintViolationException;

@Slf4j
@Controller
public class ErrorController {
    @Error(global = true)
    public HttpResponse<JsonError> jsonError(HttpRequest<?> request, JsonParseException e) {
        return jsonError(request, e, HttpStatus.UNPROCESSABLE_ENTITY, "Invalid json");
    }

    @SuppressWarnings("unchecked")
    @Error(global = true)
    public HttpResponse<JsonError> invalidTypeIdException(HttpRequest<?> request, ConversionErrorException e) {
        if (e.getConversionError().getCause() instanceof InvalidTypeIdException) {
            try {
                InvalidTypeIdException invalidTypeIdException = ((InvalidTypeIdException) e.getConversionError()
                    .getCause());

                Field pathField = JsonMappingException.class.getDeclaredField("_path");
                pathField.setAccessible(true);
                LinkedList<JsonMappingException.Reference> path = (LinkedList< JsonMappingException.Reference>) pathField.get(invalidTypeIdException);

                Field typeField = InvalidTypeIdException.class.getDeclaredField("_typeId");
                typeField.setAccessible(true);
                Object typeClass = typeField.get(invalidTypeIdException);

                JsonError error = new JsonError("Invalid type: " + typeClass)
                    .link(Link.SELF, Link.of(request.getUri()))
                    .embedded(
                        "errors",
                        Arrays.asList(
                            new JsonError("Invalid type: " + typeClass)
                                .path(path
                                    .stream()
                                    .map(JsonMappingException.Reference::getDescription)
                                    .collect(Collectors.joining(" > "))
                                ),
                            new JsonError(e.getMessage())
                                .path(path
                                    .stream()
                                    .map(JsonMappingException.Reference::getDescription)
                                    .collect(Collectors.joining(" > "))
                                )
                        )
                    );

                return jsonError(error, HttpStatus.UNPROCESSABLE_ENTITY, "Invalid entity");
            } catch (Exception ignored) {
            }
        }

        return jsonError(request, e, HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error");
    }

    @Error(global = true)
    public HttpResponse<JsonError> constraintError(HttpRequest<?> request, ConstraintViolationException e) {
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
    public HttpResponse<JsonError> illegalArgumentException(HttpRequest<?> request, IllegalArgumentException e) {
        return jsonError(request, e, HttpStatus.UNPROCESSABLE_ENTITY, "Illegal argument");
    }

    @Error(global = true)
    public HttpResponse<JsonError> invalidFormatException(HttpRequest<?> request, InvalidFormatException e) {
        return jsonError(request, e, HttpStatus.UNPROCESSABLE_ENTITY, "Invalid Format");
    }

    @Error(global = true)
    public HttpResponse<JsonError> internalServerError(HttpRequest<?> request, Throwable e) {
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
