package io.kestra.webserver.controllers.api;

import io.kestra.core.models.templates.Template;
import io.kestra.core.models.templates.TemplateEnabled;
import io.kestra.core.models.validations.ManualConstraintViolation;
import io.kestra.core.models.validations.ModelValidator;
import io.kestra.core.models.validations.ValidateConstraintViolation;
import io.kestra.core.repositories.TemplateRepositoryInterface;
import io.kestra.core.serializers.YamlFlowParser;
import io.kestra.core.tenant.TenantService;
import io.kestra.webserver.controllers.domain.IdWithNamespace;
import io.kestra.webserver.responses.BulkResponse;
import io.kestra.webserver.responses.PagedResults;
import io.kestra.webserver.utils.PageableUtils;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.*;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.validation.Validated;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.inject.Inject;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

@Validated
@Controller("/api/v1/templates")
@TemplateEnabled
public class TemplateController {
    @Inject
    private TemplateRepositoryInterface templateRepository;

    @Inject
    private ModelValidator modelValidator;

    @Inject
    private TenantService tenantService;

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "{namespace}/{id}")
    @Operation(tags = {"Templates"}, summary = "Get a template")
    public Template index(
        @Parameter(description = "The template namespace") @PathVariable String namespace,
        @Parameter(description = "The template id") @PathVariable String id
    ) {
        return templateRepository
            .findById(tenantService.resolveTenant(), namespace, id)
            .orElse(null);
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "/search")
    @Operation(tags = {"Templates"}, summary = "Search for templates")
    public PagedResults<Template> find(
        @Parameter(description = "The current page") @QueryValue(defaultValue = "1") @Min(1) int page,
        @Parameter(description = "The current page size") @QueryValue(defaultValue = "10") @Min(1) int size,
        @Parameter(description = "The sort of current page") @Nullable @QueryValue List<String> sort,
        @Parameter(description = "A string filter") @Nullable @QueryValue(value = "q") String query,
        @Parameter(description = "A namespace filter prefix") @Nullable @QueryValue String namespace
    ) throws HttpStatusException {
        return PagedResults.of(templateRepository.find(PageableUtils.from(page, size, sort), query, tenantService.resolveTenant(), namespace));
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post
    @Operation(tags = {"Templates"}, summary = "Create a template")
    public HttpResponse<Template> create(
        @Parameter(description = "The template") @Valid @Body Template template
    ) throws ConstraintViolationException {
        if (templateRepository.findById(tenantService.resolveTenant(), template.getNamespace(), template.getId()).isPresent()) {
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
    @Put(uri = "{namespace}/{id}")
    @Operation(tags = {"Templates"}, summary = "Update a template")
    public HttpResponse<Template> update(
        @Parameter(description = "The template namespace") @PathVariable String namespace,
        @Parameter(description = "The template id") @PathVariable String id,
        @Parameter(description = "The template") @Valid @Body Template template
    ) throws ConstraintViolationException {
        Optional<Template> existingTemplate = templateRepository.findById(tenantService.resolveTenant(), namespace, id);

        if (existingTemplate.isEmpty()) {
            return HttpResponse.status(HttpStatus.NOT_FOUND);
        }

        return HttpResponse.ok(templateRepository.update(template, existingTemplate.get()));
    }

    @ExecuteOn(TaskExecutors.IO)
    @Delete(uri = "{namespace}/{id}")
    @Operation(tags = {"Templates"}, summary = "Delete a template")
    @ApiResponse(responseCode = "204", description = "On success")
    public HttpResponse<Void> delete(
        @Parameter(description = "The template namespace") @PathVariable String namespace,
        @Parameter(description = "The template id") @PathVariable String id
    ) {
        Optional<Template> template = templateRepository.findById(tenantService.resolveTenant(), namespace, id);
        if (template.isPresent()) {
            templateRepository.delete(template.get());
            return HttpResponse.status(HttpStatus.NO_CONTENT);
        } else {
            return HttpResponse.status(HttpStatus.NOT_FOUND);
        }
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "distinct-namespaces")
    @Operation(tags = {"Templates"}, summary = "List all distinct namespaces")
    public List<String> listDistinctNamespace() {
        return templateRepository.findDistinctNamespace(tenantService.resolveTenant());
    }


    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "{namespace}")
    @Operation(
        tags = {"Templates"},
        summary = "Update a complete namespace from json object",
        description = "All Template will be created / updated for this namespace.\n" +
            "Template already created but not in `templates` will be deleted if the query delete is `true`"
    )
    public List<Template> updateNamespace(
        @Parameter(description = "The template namespace") @PathVariable String namespace,
        @Parameter(description = "A list of templates") @Body @Valid List<Template> templates,
        @Parameter(description = "If missing template should be deleted") @QueryValue(defaultValue = "true") Boolean delete
    ) throws ConstraintViolationException {
        return new ArrayList<>(this
            .updateCompleteNamespace(
                namespace,
                templates,
                delete
            )
        );
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

        if (!invalids.isEmpty()) {
            throw new ConstraintViolationException(invalids);
        }

        // multiple same templates
        List<String> duplicate = templates
            .stream()
            .map(Template::getId)
            .distinct()
            .toList();

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
            .toList();

        // delete all not in updated ids
        List<Template> deleted = new ArrayList<>();
        if (delete) {
            deleted = templateRepository
                .findByNamespace(tenantService.resolveTenant(), namespace)
                .stream()
                .filter(template -> !ids.contains(template.getId()))
                .peek(template -> templateRepository.delete(template))
                .toList();
        }

        // update or create templates
        List<Template> updatedOrCreated = templates
            .stream()
            .map(item -> {
                Optional<Template> existingTemplate = templateRepository.findById(tenantService.resolveTenant(), namespace, item.getId());
                if (existingTemplate.isPresent()) {
                    return templateRepository.update(item, existingTemplate.get());
                } else {
                    return templateRepository.create(item);
                }
            })
            .toList();

        return Stream.concat(deleted.stream(), updatedOrCreated.stream()).toList();
    }


    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "validate", consumes = MediaType.APPLICATION_YAML)
    @Operation(tags = {"Templates"}, summary = "Validate a list of templates")
    public List<ValidateConstraintViolation> validateTemplates(
        @Parameter(description= "A list of templates") @Body String templates
    ) {
        AtomicInteger index = new AtomicInteger(0);
        return Stream
            .of(templates.split("---"))
            .map(template -> {
                ValidateConstraintViolation.ValidateConstraintViolationBuilder<?, ?> validateConstraintViolationBuilder = ValidateConstraintViolation.builder();
                validateConstraintViolationBuilder.index(index.getAndIncrement());
                try {
                    Template templateParse = new YamlFlowParser().<Template>parse(template, Template.class);

                    validateConstraintViolationBuilder.flow(templateParse.getId());
                    validateConstraintViolationBuilder.namespace(templateParse.getNamespace());

                    modelValidator.validate(templateParse);
                } catch (ConstraintViolationException e){
                    validateConstraintViolationBuilder.constraints(e.getMessage());
                }
                return validateConstraintViolationBuilder.build();
            })
            .collect(Collectors.toList());
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "/export/by-query", produces = MediaType.APPLICATION_OCTET_STREAM)
    @Operation(
        tags = {"Templates"},
        summary = "Export templates as a ZIP archive of yaml sources."
    )
    public HttpResponse<byte[]> exportByQuery(
        @Parameter(description = "A string filter") @Nullable @QueryValue(value = "q") String query,
        @Parameter(description = "A namespace filter prefix") @Nullable @QueryValue String namespace
    ) throws IOException {
        var templates = templateRepository.find(tenantService.resolveTenant(), query, namespace);
        var bytes = zipTemplates(templates);
        return HttpResponse.ok(bytes).header("Content-Disposition", "attachment; filename=\"templates.zip\"");
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/export/by-ids", produces = MediaType.APPLICATION_OCTET_STREAM)
    @Operation(
        tags = {"Templates"},
        summary = "Export templates as a ZIP archive of yaml sources."
    )
    public HttpResponse<byte[]> exportByIds(
        @Parameter(description = "A list of tuple flow ID and namespace as template identifiers") @Body List<IdWithNamespace> ids
    ) throws IOException {
        var templates = ids.stream()
            .map(id -> templateRepository.findById(tenantService.resolveTenant(), id.getNamespace(), id.getId()).orElseThrow())
            .toList();
        var bytes = zipTemplates(templates);
        return HttpResponse.ok(bytes).header("Content-Disposition", "attachment; filename=\"templates.zip\"");
    }

    @ExecuteOn(TaskExecutors.IO)
    @Delete(uri = "/delete/by-query")
    @Operation(
        tags = {"Templates"},
        summary = "Delete templates returned by the query parameters."
    )
    public HttpResponse<BulkResponse> deleteByQuery(
        @Parameter(description = "A string filter") @Nullable @QueryValue(value = "q") String query,
        @Parameter(description = "A namespace filter prefix") @Nullable @QueryValue String namespace
    ){
        List<Template> list = templateRepository
            .find(tenantService.resolveTenant(), query, namespace)
            .stream()
            .peek(templateRepository::delete)
            .toList();

        return HttpResponse.ok(BulkResponse.builder().count(list.size()).build());
    }

    @ExecuteOn(TaskExecutors.IO)
    @Delete(uri = "/delete/by-ids")
    @Operation(
        tags = {"Templates"},
        summary = "Delete templates by their IDs."
    )
    public HttpResponse<BulkResponse> deleteByIds(
        @Parameter(description = "A list of tuple flow ID and namespace as flow identifiers") @Body List<IdWithNamespace> ids
    ) {
        List<Template> list = ids
            .stream()
            .map(id -> templateRepository.findById(tenantService.resolveTenant(), id.getNamespace(), id.getId()).orElseThrow())
            .peek(templateRepository::delete)
            .toList();

        return HttpResponse.ok(BulkResponse.builder().count(list.size()).build());
    }

    private static byte[] zipTemplates(List<Template> templates) throws IOException {
        try(ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ZipOutputStream archive = new ZipOutputStream(bos)) {

            for(var template : templates) {
                var zipEntry = new ZipEntry(template.getNamespace() + "." + template.getId() + ".yml");
                archive.putNextEntry(zipEntry);
                archive.write(template.generateSource().getBytes());
                archive.closeEntry();
            }

            archive.finish();
            return bos.toByteArray();
        }
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/import", consumes = MediaType.MULTIPART_FORM_DATA)
    @Operation(
        tags = {"Templates"},
        summary = "Import templates as a ZIP archive of yaml sources or a multi-objects YAML file."
    )
    @ApiResponse(responseCode = "204", description = "On success")
    public HttpResponse<Void> importTemplates(@Parameter(description = "The file to import, can be a ZIP archive or a multi-objects YAML file") @Part CompletedFileUpload fileUpload) throws IOException {
        String fileName = fileUpload.getFilename().toLowerCase();
        if (fileName.endsWith(".yaml") || fileName.endsWith(".yml")) {
            List<String> sources = List.of(new String(fileUpload.getBytes()).split("---"));
            for (String source : sources) {
                Template parsed = new YamlFlowParser().parse(source, Template.class);
                importTemplate(parsed);
            }
        } else if (fileName.endsWith(".zip")) {
            try (ZipInputStream archive = new ZipInputStream(fileUpload.getInputStream())) {
                ZipEntry entry;
                while ((entry = archive.getNextEntry()) != null) {
                    if (entry.isDirectory() || !entry.getName().endsWith(".yml") && !entry.getName().endsWith(".yaml")) {
                        continue;
                    }

                    String source = new String(archive.readAllBytes());
                    Template parsed = new YamlFlowParser().parse(source, Template.class);
                    importTemplate(parsed);
                }
            }
        } else {
            throw new IllegalArgumentException("Cannot import file of type " + fileName.substring(fileName.lastIndexOf('.')));
        }

        return HttpResponse.status(HttpStatus.NO_CONTENT);
    }

    protected void importTemplate(Template parsed) {
        templateRepository.findById(tenantService.resolveTenant(), parsed.getNamespace(), parsed.getId()).ifPresentOrElse(
            previous -> templateRepository.update(parsed, previous),
            () -> templateRepository.create(parsed)
        );
    }
}
