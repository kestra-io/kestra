package io.kestra.webserver.controllers;

import com.fasterxml.jackson.core.JsonProcessingException;
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

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

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
        @Parameter(description = "The current page") @QueryValue(value = "page", defaultValue = "1") int page,
        @Parameter(description = "The current page size") @QueryValue(value = "size", defaultValue = "10") int size,
        @Parameter(description = "The sort of current page") @Nullable @QueryValue(value = "sort") List<String> sort,
        @Parameter(description = "A string filter") @Nullable @QueryValue(value = "q") String query,
        @Parameter(description = "A namespace filter prefix") @Nullable @QueryValue(value = "namespace") String namespace
    ) throws HttpStatusException {
        return PagedResults.of(templateRepository.find(PageableUtils.from(page, size, sort), query, namespace));
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


    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "{namespace}", produces = MediaType.TEXT_JSON)
    @Operation(
        tags = {"Templates"},
        summary = "Update a complete namespace from json object",
        description = "All Template will be created / updated for this namespace.\n" +
            "Template already created but not in `templates` will be deleted if the query delete is `true`"
    )
    public List<Template> updateNamespace(
        @Parameter(description = "The template namespace") String namespace,
        @Parameter(description = "A list of templates") @Body @Valid List<Template> templates,
        @Parameter(description = "If missing template should be deleted") @QueryValue(defaultValue = "true") Boolean delete
    ) throws ConstraintViolationException, JsonProcessingException {
        return this
            .updateCompleteNamespace(
                namespace,
                templates,
                delete
            )
            .stream()
            .collect(Collectors.toList());
    }

    private List<Template> updateCompleteNamespace(String namespace, List<Template> templates, Boolean delete) {
        // control namespace to update
        Set<ManualConstraintViolation<Template>> invalids = templates
            .stream()
            .filter(template -> !template.getNamespace().equals(namespace))
            .map(template -> ManualConstraintViolation.of(
                "Template namespace is invalid",
                template,
                Template.class,
                "template.namespace",
                template.getNamespace()
            ))
            .collect(Collectors.toSet());

        if (invalids.size() > 0) {
            throw new ConstraintViolationException(invalids);
        }

        // multiple same templates
        List<String> duplicate = templates
            .stream()
            .map(Template::getId)
            .distinct()
            .collect(Collectors.toList());

        if (duplicate.size() < templates.size()) {
            throw new ConstraintViolationException(Collections.singleton(ManualConstraintViolation.of(
                "Duplicate template id",
                templates,
                List.class,
                "template.id",
                duplicate
            )));
        }

        // list all ids of updated templates
        List<String> ids = templates
            .stream()
            .map(Template::getId)
            .collect(Collectors.toList());

        // delete all not in updated ids
        List<Template> deleted = new ArrayList<>();
        if (delete) {
            deleted = templateRepository
                .findByNamespace(namespace)
                .stream()
                .filter(template -> !ids.contains(template.getId()))
                .map(template -> {
                    templateRepository.delete(template);
                    return template;
                })
                .collect(Collectors.toList());;
        }

        // update or create templates
        List<Template> updatedOrCreated =  IntStream.range(0, templates.size())
            .mapToObj(index -> {
                Template template = templates.get(index);

                Optional<Template> existingTemplate = templateRepository.findById(namespace, template.getId());
                if (existingTemplate.isPresent()) {
                    return templateRepository.update(template, existingTemplate.get());
                } else {
                    return templateRepository.create(template);
                }
            })
            .collect(Collectors.toList());

        return Stream.concat(deleted.stream(), updatedOrCreated.stream()).collect(Collectors.toList());
    }

}
