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
import jakarta.inject.Inject;
import javax.validation.ConstraintViolationException;
import javax.validation.Valid;

@Validated
@Controller("/api/v1/templates")
public class TemplateController {
    @Inject
    private TemplateRepositoryInterface templateRepository;

    /**
     * @param id The template id
     * @return template found
     */
    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "{namespace}/{id}", produces = MediaType.TEXT_JSON)
    public Template index(String namespace, String id) {
        return templateRepository
            .findById(namespace, id)
            .orElse(null);
    }

    /**
     * @param query The template query that is a lucen string
     * @param page Page in template pagination
     * @param size Element count in pagination selection
     * @return template list
     */
    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "/search", produces = MediaType.TEXT_JSON)
    public PagedResults<Template> find(
        @QueryValue(value = "q") String query, //Search by namespace using lucene
        @QueryValue(value = "page", defaultValue = "1") int page,
        @QueryValue(value = "size", defaultValue = "10") int size,
        @Nullable @QueryValue(value = "sort") List<String> sort
    ) throws HttpStatusException {
        return PagedResults.of(templateRepository.find(query, PageableUtils.from(page, size, sort)));
    }

    /**
     * @param template The template content
     * @return template created
     */
    @ExecuteOn(TaskExecutors.IO)
    @Post(produces = MediaType.TEXT_JSON)
    public HttpResponse<Template> create(@Valid @Body Template template) throws ConstraintViolationException {
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

    /**
     * @param id template id to update
     * @return template updated
     */
    @ExecuteOn(TaskExecutors.IO)
    @Put(uri = "{namespace}/{id}", produces = MediaType.TEXT_JSON)
    public HttpResponse<Template> update(String namespace, String id, @Valid @Body Template template) throws ConstraintViolationException {
        Optional<Template> existingTemplate = templateRepository.findById(namespace, id);

        if (existingTemplate.isEmpty()) {
            return HttpResponse.status(HttpStatus.NOT_FOUND);
        }

        return HttpResponse.ok(templateRepository.update(template, existingTemplate.get()));
    }

    /**
     * @param id template id to delete
     * @return Http 204 on delete or Http 404 when not found
     */
    @ExecuteOn(TaskExecutors.IO)
    @Delete(uri = "{namespace}/{id}", produces = MediaType.TEXT_JSON)
    public HttpResponse<Void> delete(String namespace, String id) {
        Optional<Template> template = templateRepository.findById(namespace, id);
        if (template.isPresent()) {
            templateRepository.delete(template.get());
            return HttpResponse.status(HttpStatus.NO_CONTENT);
        } else {
            return HttpResponse.status(HttpStatus.NOT_FOUND);
        }
    }

    /**
     * @return The template's namespaces set
     */
    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "distinct-namespaces", produces = MediaType.TEXT_JSON)
    public List<String> listDistinctNamespace() {
        return templateRepository.findDistinctNamespace();
    }
}
