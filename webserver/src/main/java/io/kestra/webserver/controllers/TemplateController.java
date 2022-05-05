package io.kestra.webserver.controllers;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.*;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.validation.Validated;
import io.kestra.core.models.templates.Template;
import io.kestra.core.models.validations.ManualConstraintViolation;
import io.kestra.core.repositories.TemplateRepositoryInterface;
import io.kestra.webserver.responses.PagedResults;
import io.kestra.webserver.utils.PageableUtils;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import io.micronaut.core.annotation.Nullable;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.inject.Inject;
import javax.validation.ConstraintViolationException;
import javax.validation.Valid;

@Validated
@Controller("/api/v1/templates")
public class TemplateController {
    @Inject
    private TemplateRepositoryInterface templateRepository;

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "{namespace}/{id}", produces = MediaType.TEXT_JSON)
    @Operation(tags = {"Template"}, summary = "Get a template")
    public Template index(
        @Parameter(description = "The template namespace") String namespace,
        @Parameter(description = "The template id") String id
    ) {
        return templateRepository
            .findById(namespace, id)
            .orElse(null);
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "/search", produces = MediaType.TEXT_JSON)
    @Operation(tags = {"Template"}, summary = "Search for templates")
    public PagedResults<Template> find(
        @Parameter(description = "Lucene string filter") @QueryValue(value = "q") String query,
        @Parameter(description = "The current page") @QueryValue(value = "page", defaultValue = "1") int page,
        @Parameter(description = "The current page size") @QueryValue(value = "size", defaultValue = "10") int size,
        @Parameter(description = "The sort of current page") @Nullable @QueryValue(value = "sort") List<String> sort
    ) throws HttpStatusException {
        return PagedResults.of(templateRepository.find(query, PageableUtils.from(page, size, sort)));
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(produces = MediaType.TEXT_JSON)
    @Operation(tags = {"Template"}, summary = "Create a template")
    public HttpResponse<Template> create(
        @Parameter(description = "The template") @Valid @Body Template template
    ) throws ConstraintViolationException {
        if (templateRepository.findById(template.getNamespace(), template.getId()).isPresent()) {
            throw new ConstraintViolationException(Collections.singleton(ManualConstraintViolation.of(
                "Template id already exists",
                template,
                Template.class,
                "template.id",
                template.getId()
            )));
        }

        return HttpResponse.ok(templateRepository.create(template));
    }

    @ExecuteOn(TaskExecutors.IO)
    @Put(uri = "{namespace}/{id}", produces = MediaType.TEXT_JSON)
    @Operation(tags = {"Template"}, summary = "Update a template")
    public HttpResponse<Template> update(
        @Parameter(description = "The template namespace") String namespace,
        @Parameter(description = "The template id") String id,
        @Parameter(description = "The template") @Valid @Body Template template
    ) throws ConstraintViolationException {
        Optional<Template> existingTemplate = templateRepository.findById(namespace, id);

        if (existingTemplate.isEmpty()) {
            return HttpResponse.status(HttpStatus.NOT_FOUND);
        }

        return HttpResponse.ok(templateRepository.update(template, existingTemplate.get()));
    }

    @ExecuteOn(TaskExecutors.IO)
    @Delete(uri = "{namespace}/{id}", produces = MediaType.TEXT_JSON)
    @Operation(tags = {"Template"}, summary = "Delete a template")
    @ApiResponses(
        @ApiResponse(responseCode = "204", description = "On success")
    )
    public HttpResponse<Void> delete(
        @Parameter(description = "The template namespace") String namespace,
        @Parameter(description = "The template id") String id
    ) {
        Optional<Template> template = templateRepository.findById(namespace, id);
        if (template.isPresent()) {
            templateRepository.delete(template.get());
            return HttpResponse.status(HttpStatus.NO_CONTENT);
        } else {
            return HttpResponse.status(HttpStatus.NOT_FOUND);
        }
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "distinct-namespaces", produces = MediaType.TEXT_JSON)
    @Operation(tags = {"Template"}, summary = "List all distinct namespaces")
    public List<String> listDistinctNamespace() {
        return templateRepository.findDistinctNamespace();
    }
}
