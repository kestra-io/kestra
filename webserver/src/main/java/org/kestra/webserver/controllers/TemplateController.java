package org.kestra.webserver.controllers;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.*;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.validation.Validated;
import org.kestra.core.models.templates.Template;
import org.kestra.core.models.validations.ManualConstraintViolation;
import org.kestra.core.repositories.TemplateRepositoryInterface;
import org.kestra.webserver.responses.PagedResults;
import org.kestra.webserver.utils.PageableUtils;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.validation.ConstraintViolationException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Validated
@Controller("/api/v1/templates")
public class TemplateController {
    @Inject
    private TemplateRepositoryInterface templateRepository;

    /**
     * @param id The template id
     * @return template found
     */
    @Get(uri = "{id}", produces = MediaType.TEXT_JSON)
    public Template index(String id) {
        return templateRepository
            .findById(id)
            .orElse(null);
    }

    /**
     * @param query The template query that is a lucen string
     * @param page  Page in template pagination
     * @param size  Element count in pagination selection
     * @return template list
     */
    @Get(uri = "/search", produces = MediaType.TEXT_JSON)
    public PagedResults<Template> find(
        @QueryValue(value = "q") String query, //Search by namespace using lucene
        @QueryValue(value = "page", defaultValue = "1") int page,
        @QueryValue(value = "size", defaultValue = "10") int size,
        @Nullable @QueryValue(value = "sort") List<String> sort
    ) throws HttpStatusException {
        return PagedResults.of(templateRepository.find(Optional.of(query), PageableUtils.from(page, size, sort)));
    }

    /**
     * @param template The template content
     * @return template created
     */
    @Post(produces = MediaType.TEXT_JSON)
    public HttpResponse<Template> create(@Body Template template) throws ConstraintViolationException {
        if (templateRepository.findById(template.getId()).isPresent()) {
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
    @Put(uri = "{id}", produces = MediaType.TEXT_JSON)
    public HttpResponse<Template> update(String id, @Body Template template) throws ConstraintViolationException {
        Optional<Template> existingTemplate = templateRepository.findById(id);

        if (existingTemplate.isEmpty()) {
            return HttpResponse.status(HttpStatus.NOT_FOUND);
        }

        return HttpResponse.ok(templateRepository.update(template, existingTemplate.get()));
    }

    /**
     * @param id        template id to delete
     * @return Http 204 on delete or Http 404 when not found
     */
    @Delete(uri = "{id}", produces = MediaType.TEXT_JSON)
    public HttpResponse<Void> delete(String id) {
        Optional<Template> template = templateRepository.findById(id);
        if (template.isPresent()) {
            templateRepository.delete(template.get());
            return HttpResponse.status(HttpStatus.NO_CONTENT);
        } else {
            return HttpResponse.status(HttpStatus.NOT_FOUND);
        }
    }

}
