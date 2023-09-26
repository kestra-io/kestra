package io.kestra.webserver.controllers;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.exceptions.InternalException;
import io.kestra.core.models.SearchResult;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.FlowWithException;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.hierarchies.FlowGraph;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.topologies.FlowTopology;
import io.kestra.core.models.topologies.FlowTopologyGraph;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.validations.ManualConstraintViolation;
import io.kestra.core.models.validations.ModelValidator;
import io.kestra.core.models.validations.ValidateConstraintViolation;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.repositories.FlowTopologyRepositoryInterface;
import io.kestra.core.serializers.YamlFlowParser;
import io.kestra.core.services.GraphService;
import io.kestra.core.services.FlowService;
import io.kestra.core.services.TaskDefaultService;
import io.kestra.core.topologies.FlowTopologyService;
import io.kestra.webserver.controllers.domain.IdWithNamespace;
import io.kestra.webserver.responses.BulkResponse;
import io.kestra.webserver.responses.PagedResults;
import io.kestra.webserver.utils.PageableUtils;
import io.kestra.webserver.utils.RequestUtils;
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
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.inject.Inject;

import javax.validation.ConstraintViolationException;
import javax.validation.Valid;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static io.kestra.core.utils.Rethrow.throwFunction;

@Validated
@Controller("/api/v1/flows")
public class FlowController {
    @Inject
    private FlowRepositoryInterface flowRepository;

    @Inject
    private TaskDefaultService taskDefaultService;

    @Inject
    private ModelValidator modelValidator;

    @Inject
    private FlowTopologyService flowTopologyService;

    @Inject
    private FlowTopologyRepositoryInterface flowTopologyRepository;

    @Inject
    private FlowService flowService;

    @Inject
    private YamlFlowParser yamlFlowParser;

    @Inject
    private GraphService graphService;

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "{namespace}/{id}/graph", produces = MediaType.TEXT_JSON)
    @Operation(tags = {"Flows"}, summary = "Generate a graph for a flow")
    public FlowGraph flowGraph(
        @Parameter(description = "The flow namespace") @PathVariable String namespace,
        @Parameter(description = "The flow id") @PathVariable String id,
        @Parameter(description = "The flow revision") @QueryValue Optional<Integer> revision,
        @Parameter(description = "The subflow tasks to display") @Nullable @QueryValue List<String> subflows
    ) throws IllegalVariableEvaluationException {
        Flow flow = flowRepository
            .findById(namespace, id, revision)
            .orElse(null);

        String flowUid = revision.isEmpty() ? Flow.uidWithoutRevision(namespace, id) : Flow.uid(namespace, id, revision);
        if(flow == null) {
            throw new NoSuchElementException(
                "Unable to find flow " + flowUid
            );
        }

        if(flow instanceof FlowWithException fwe) {
            throw new IllegalStateException(
                "Unable to generate graph for flow " + flowUid +
                    " because of exception " + fwe.getException()
            );
        }

        return graphService.flowGraph(flow, subflows);
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "graph", produces = MediaType.TEXT_JSON, consumes = MediaType.APPLICATION_YAML)
    @Operation(tags = {"Flows"}, summary = "Generate a graph for a flow source")
    public FlowGraph flowGraphSource(
        @Parameter(description = "The flow") @Body String flow,
        @Parameter(description = "The subflow tasks to display") @Nullable @QueryValue List<String> subflows
    ) throws ConstraintViolationException, IllegalVariableEvaluationException {
        Flow flowParsed = yamlFlowParser.parse(flow, Flow.class);

        return graphService.flowGraph(flowParsed, subflows);
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "{namespace}/{id}", produces = MediaType.TEXT_JSON)
    @Operation(tags = {"Flows"}, summary = "Get a flow")
    @Schema(
        anyOf = {FlowWithSource.class, Flow.class}
    )
    public Flow index(
        @Parameter(description = "The flow namespace") @PathVariable String namespace,
        @Parameter(description = "The flow id") @PathVariable String id,
        @Parameter(description = "Include the source code") @QueryValue(defaultValue = "false") boolean source,
        @Parameter(description = "Get latest revision by default") @Nullable @QueryValue Integer revision,
        @Parameter(description = "Get flow even if deleted") @QueryValue(defaultValue = "false") boolean allowDeleted
    ) {
        return source ?
            flowRepository
                .findByIdWithSource(namespace, id, Optional.ofNullable(revision), allowDeleted)
                .orElse(null) :
            flowRepository
                .findById(namespace, id, Optional.ofNullable(revision), allowDeleted)
                .orElse(null);
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "{namespace}/{id}/revisions", produces = MediaType.TEXT_JSON)
    @Operation(tags = {"Flows"}, summary = "Get revisions for a flow")
    public List<FlowWithSource> revisions(
        @Parameter(description = "The flow namespace") @PathVariable String namespace,
        @Parameter(description = "The flow id") @PathVariable String id
    ) {
        return flowRepository.findRevisions(namespace, id);
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "{namespace}/{id}/tasks/{taskId}", produces = MediaType.TEXT_JSON)
    @Operation(tags = {"Flows"}, summary = "Get a flow task")
    public Task flowTask(
        @Parameter(description = "The flow namespace") @PathVariable String namespace,
        @Parameter(description = "The flow id") @PathVariable String id,
        @Parameter(description = "The task id") @PathVariable String taskId,
        @Parameter(description = "The flow revision") @Nullable @QueryValue Integer revision
    ) {
        return flowRepository
            .findById(namespace, id, Optional.ofNullable(revision))
            .flatMap(flow -> {
                try {
                    return Optional.of(flow.findTaskByTaskId(taskId));
                } catch (InternalException e) {
                    return Optional.empty();
                }
            })
            .orElse(null);
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "/search", produces = MediaType.TEXT_JSON)
    @Operation(tags = {"Flows"}, summary = "Search for flows")
    public PagedResults<Flow> find(
        @Parameter(description = "The current page") @QueryValue(defaultValue = "1") int page,
        @Parameter(description = "The current page size") @QueryValue(defaultValue = "10") int size,
        @Parameter(description = "The sort of current page") @Nullable @QueryValue List<String> sort,
        @Parameter(description = "A string filter") @Nullable @QueryValue(value = "q") String query,
        @Parameter(description = "A namespace filter prefix") @Nullable @QueryValue String namespace,
        @Parameter(description = "A labels filter") @Nullable @QueryValue List<String> labels
    ) throws HttpStatusException {

        return PagedResults.of(flowRepository.find(
            PageableUtils.from(page, size, sort),
            query,
            namespace,
            RequestUtils.toMap(labels)
        ));
    }


    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "/source", produces = MediaType.TEXT_JSON)
    @Operation(tags = {"Flows"}, summary = "Search for flows source code")
    public PagedResults<SearchResult<Flow>> source(
        @Parameter(description = "The current page") @QueryValue(defaultValue = "1") int page,
        @Parameter(description = "The current page size") @QueryValue(defaultValue = "10") int size,
        @Parameter(description = "The sort of current page") @Nullable @QueryValue List<String> sort,
        @Parameter(description = "A string filter") @Nullable @QueryValue(value = "q") String query,
        @Parameter(description = "A namespace filter prefix") @Nullable @QueryValue String namespace
    ) throws HttpStatusException {
        return PagedResults.of(flowRepository.findSourceCode(PageableUtils.from(page, size, sort), query, namespace));
    }


    @ExecuteOn(TaskExecutors.IO)
    @Post(produces = MediaType.TEXT_JSON, consumes = MediaType.APPLICATION_YAML)
    @Operation(tags = {"Flows"}, summary = "Create a flow from yaml source")
    public HttpResponse<FlowWithSource> create(
        @Parameter(description = "The flow") @Body String flow
    ) throws ConstraintViolationException {
        Flow flowParsed = yamlFlowParser.parse(flow, Flow.class);

        return HttpResponse.ok(flowRepository.create(flowParsed, flow, taskDefaultService.injectDefaults(flowParsed)));
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(consumes = MediaType.ALL, produces = MediaType.TEXT_JSON)
    @Operation(tags = {"Flows"}, summary = "Create a flow from json object")
    public HttpResponse<Flow> create(
        @Parameter(description = "The flow") @Body @Valid Flow flow
    ) throws ConstraintViolationException {
        return HttpResponse.ok(flowRepository.create(flow, flow.generateSource(), taskDefaultService.injectDefaults(flow)).toFlow());
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "{namespace}", produces = MediaType.TEXT_JSON, consumes = MediaType.APPLICATION_YAML)
    @Operation(
        tags = {"Flows"},
        summary = "Update a complete namespace from yaml source",
        description = "All flow will be created / updated for this namespace.\n" +
            "Flow that already created but not in `flows` will be deleted if the query delete is `true`"
    )
    public List<FlowWithSource> updateNamespace(
        @Parameter(description = "The flow namespace") @PathVariable String namespace,
        @Parameter(description = "A list of flows") @Body @Nullable String flows,
        @Parameter(description = "If missing flow should be deleted") @QueryValue(defaultValue = "true") Boolean delete
    ) throws ConstraintViolationException {
        List<String> sources = flows != null ? List.of(flows.split("---")) : new ArrayList<>();

        return this.updateCompleteNamespace(
            namespace,
            sources
                .stream()
                .map(flow -> FlowWithSource.of(yamlFlowParser.parse(flow, Flow.class), flow))
                .toList(),
            delete
        );
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "{namespace}", produces = MediaType.TEXT_JSON)
    @Operation(
        tags = {"Flows"},
        summary = "Update a complete namespace from json object",
        description = "All flow will be created / updated for this namespace.\n" +
            "Flow that already created but not in `flows` will be deleted if the query delete is `true`"
    )
    public List<Flow> updateNamespace(
        @Parameter(description = "The flow namespace") @PathVariable String namespace,
        @Parameter(description = "A list of flows") @Body @Valid List<Flow> flows,
        @Parameter(description = "If missing flow should be deleted") @QueryValue(defaultValue = "true") Boolean delete
    ) throws ConstraintViolationException {
        return this
            .updateCompleteNamespace(
                namespace,
                flows
                    .stream()
                    .map(throwFunction(flow -> FlowWithSource.of(flow, flow.generateSource())))
                    .collect(Collectors.toList()),
                delete
            )
            .stream()
            .map(FlowWithSource::toFlow)
            .toList();
    }

    protected List<FlowWithSource> updateCompleteNamespace(String namespace, List<FlowWithSource> flows, Boolean delete) {
        // control namespace to update
        Set<ManualConstraintViolation<Flow>> invalids = flows
            .stream()
            .filter(flow -> !flow.getNamespace().equals(namespace))
            .map(flow -> ManualConstraintViolation.of(
                "Flow namespace is invalid",
                flow,
                Flow.class,
                "flow.namespace",
                flow.getNamespace()
            ))
            .collect(Collectors.toSet());

        if (invalids.size() > 0) {
            throw new ConstraintViolationException(invalids);
        }

        // multiple same flows
        List<String> duplicate = flows
            .stream()
            .map(Flow::getId)
            .distinct()
            .toList();

        if (duplicate.size() < flows.size()) {
            throw new ConstraintViolationException(Collections.singleton(ManualConstraintViolation.of(
                "Duplicate flow id",
                flows,
                List.class,
                "flow.id",
                duplicate
            )));
        }

        // list all ids of updated flows
        List<String> ids = flows
            .stream()
            .map(Flow::getId)
            .toList();

        // delete all not in updated ids
        List<FlowWithSource> deleted = new ArrayList<>();
        if (delete) {
            deleted = flowRepository
                .findByNamespace(namespace)
                .stream()
                .filter(flow -> !ids.contains(flow.getId()))
                .map(flow -> {
                    flowRepository.delete(flow);
                    return FlowWithSource.of(flow, flow.generateSource());
                })
                .toList();
        }

        // update or create flows
        List<FlowWithSource> updatedOrCreated = flows.stream()
            .map(flowWithSource -> {
                Flow flow = flowWithSource.toFlow();
                Optional<Flow> existingFlow = flowRepository.findById(namespace, flow.getId());
                if (existingFlow.isPresent()) {
                    return flowRepository.update(flow, existingFlow.get(), flowWithSource.getSource(), taskDefaultService.injectDefaults(flow));
                } else {
                    return flowRepository.create(flow, flowWithSource.getSource(), taskDefaultService.injectDefaults(flow));
                }
            })
            .toList();

        return Stream.concat(deleted.stream(), updatedOrCreated.stream()).toList();
    }

    @Put(uri = "{namespace}/{id}", produces = MediaType.TEXT_JSON, consumes = MediaType.APPLICATION_YAML)
    @ExecuteOn(TaskExecutors.IO)
    @Operation(tags = {"Flows"}, summary = "Update a flow")
    public HttpResponse<FlowWithSource> update(
        @Parameter(description = "The flow namespace") @PathVariable String namespace,
        @Parameter(description = "The flow id") @PathVariable String id,
        @Parameter(description = "The flow") @Body String flow
    ) throws ConstraintViolationException {
        Optional<Flow> existingFlow = flowRepository.findById(namespace, id);
        if (existingFlow.isEmpty()) {

            return HttpResponse.status(HttpStatus.NOT_FOUND);
        }
        Flow flowParsed = yamlFlowParser.parse(flow, Flow.class);

        return HttpResponse.ok(flowRepository.update(flowParsed, existingFlow.get(), flow, taskDefaultService.injectDefaults(flowParsed)));
    }

    @Put(uri = "{namespace}/{id}", produces = MediaType.TEXT_JSON, consumes = MediaType.ALL)
    @ExecuteOn(TaskExecutors.IO)
    @Operation(tags = {"Flows"}, summary = "Update a flow")
    public HttpResponse<Flow> update(
        @Parameter(description = "The flow namespace") @PathVariable String namespace,
        @Parameter(description = "The flow id") @PathVariable String id,
        @Parameter(description = "The flow") @Body @Valid Flow flow
    ) throws ConstraintViolationException {
        Optional<Flow> existingFlow = flowRepository.findById(namespace, id);
        if (existingFlow.isEmpty()) {
            return HttpResponse.status(HttpStatus.NOT_FOUND);
        }

        return HttpResponse.ok(flowRepository.update(flow, existingFlow.get(), flow.generateSource(), taskDefaultService.injectDefaults(flow)).toFlow());
    }

    @Patch(uri = "{namespace}/{id}/{taskId}", produces = MediaType.TEXT_JSON)
    @ExecuteOn(TaskExecutors.IO)
    @Operation(tags = {"Flows"}, summary = "Update a single task on a flow")
    public HttpResponse<Flow> updateTask(
        @Parameter(description = "The flow namespace") @PathVariable String namespace,
        @Parameter(description = "The flow id") @PathVariable String id,
        @Parameter(description = "The task id") @PathVariable String taskId,
        @Parameter(description = "The task") @Valid @Body Task task
    ) throws ConstraintViolationException {
        Optional<Flow> existingFlow = flowRepository.findById(namespace, id);

        if (existingFlow.isEmpty()) {
            return HttpResponse.status(HttpStatus.NOT_FOUND);
        }

        if (!taskId.equals(task.getId())) {
            throw new IllegalArgumentException("Invalid taskId, previous '" + taskId + "' & current '" + task.getId() + "'");
        }

        Flow flow = existingFlow.get();
        try {
            Flow newValue = flow.updateTask(taskId, task);
            return HttpResponse.ok(flowRepository.update(newValue, flow, flow.generateSource(), taskDefaultService.injectDefaults(newValue)).toFlow());
        } catch (InternalException e) {
            return HttpResponse.status(HttpStatus.NOT_FOUND);
        }
    }

    @Delete(uri = "{namespace}/{id}", produces = MediaType.TEXT_JSON)
    @ExecuteOn(TaskExecutors.IO)
    @Operation(tags = {"Flows"}, summary = "Delete a flow")
    @ApiResponse(responseCode = "204", description = "On success")
    public HttpResponse<Void> delete(
        @Parameter(description = "The flow namespace") @PathVariable String namespace,
        @Parameter(description = "The flow id") @PathVariable String id
    ) {
        Optional<Flow> flow = flowRepository.findById(namespace, id);
        if (flow.isPresent()) {
            flowRepository.delete(flow.get());
            return HttpResponse.status(HttpStatus.NO_CONTENT);
        } else {
            return HttpResponse.status(HttpStatus.NOT_FOUND);
        }
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "distinct-namespaces", produces = MediaType.TEXT_JSON)
    @Operation(tags = {"Flows"}, summary = "List all distinct namespaces")
    public List<String> listDistinctNamespace() {
        return flowRepository.findDistinctNamespace();
    }


    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "{namespace}/{id}/dependencies", produces = MediaType.TEXT_JSON)
    @Operation(tags = {"Flows"}, summary = "Get flow dependencies")
    public FlowTopologyGraph dependencies(
        @Parameter(description = "The flow namespace") @PathVariable String namespace,
        @Parameter(description = "The flow id") @PathVariable String id,
        @Parameter(description = "if true, list only destination dependencies, otherwise list also source dependencies") @QueryValue(defaultValue = "false") boolean destinationOnly
    ) {
        List<FlowTopology> flowTopologies = flowTopologyRepository.findByFlow(namespace, id, destinationOnly);

        return flowTopologyService.graph(
            flowTopologies.stream(),
            (flowNode -> flowNode)
        );
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "validate", produces = MediaType.TEXT_JSON, consumes = MediaType.APPLICATION_YAML)
    @Operation(tags = {"Flows"}, summary = "Validate a list of flows")
    public List<ValidateConstraintViolation> validateFlows(
        @Parameter(description = "A list of flows") @Body String flows
    ) {
        AtomicInteger index = new AtomicInteger(0);
        return Stream
            .of(flows.split("\\n+---\\n*?"))
            .map(flow -> {
                ValidateConstraintViolation.ValidateConstraintViolationBuilder<?, ?> validateConstraintViolationBuilder = ValidateConstraintViolation.builder();
                validateConstraintViolationBuilder.index(index.getAndIncrement());

                try {
                    Flow flowParse = yamlFlowParser.parse(flow, Flow.class);

                    validateConstraintViolationBuilder.deprecationPaths(flowService.deprecationPaths(flowParse));

                    validateConstraintViolationBuilder.flow(flowParse.getId());
                    validateConstraintViolationBuilder.namespace(flowParse.getNamespace());

                    modelValidator.validate(taskDefaultService.injectDefaults(flowParse));
                } catch (ConstraintViolationException e) {
                    validateConstraintViolationBuilder.constraints(e.getMessage());
                }

                return validateConstraintViolationBuilder.build();
            })
            .collect(Collectors.toList());
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/validate/task", produces = MediaType.TEXT_JSON, consumes = MediaType.APPLICATION_YAML)
    @Operation(tags = {"Flows"}, summary = "Validate a list of flows")
    public ValidateConstraintViolation validateTask(
        @Parameter(description = "A list of flows") @Body String task,
        @Parameter(description = "Type of task") @QueryValue TaskValidationType section
    ) {
        ValidateConstraintViolation.ValidateConstraintViolationBuilder<?, ?> validateConstraintViolationBuilder = ValidateConstraintViolation.builder();

        try {
            if (section == TaskValidationType.TASKS) {
                Task taskParse = yamlFlowParser.parse(task, Task.class);
                modelValidator.validate(taskParse);
            } else if (section == TaskValidationType.TRIGGERS) {
                AbstractTrigger triggerParse = yamlFlowParser.parse(task, AbstractTrigger.class);
                modelValidator.validate(triggerParse);
            }
        } catch (ConstraintViolationException e) {
            validateConstraintViolationBuilder.constraints(e.getMessage());
        }
        return validateConstraintViolationBuilder.build();
    }

    public enum TaskValidationType {
        TASKS,
        TRIGGERS
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "/export/by-query", produces = MediaType.APPLICATION_OCTET_STREAM)
    @Operation(
        tags = {"Flows"},
        summary = "Export flows as a ZIP archive of yaml sources."
    )
    public HttpResponse<byte[]> exportByQuery(
        @Parameter(description = "A string filter") @Nullable @QueryValue(value = "q") String query,
        @Parameter(description = "A namespace filter prefix") @Nullable @QueryValue String namespace,
        @Parameter(description = "A labels filter") @Nullable @QueryValue List<String> labels
    ) throws IOException {
        var flows = flowRepository.findWithSource(query, namespace, RequestUtils.toMap(labels));
        var bytes = zipFlows(flows);

        return HttpResponse.ok(bytes).header("Content-Disposition", "attachment; filename=\"flows.zip\"");
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/export/by-ids", produces = MediaType.APPLICATION_OCTET_STREAM, consumes = MediaType.APPLICATION_JSON)
    @Operation(
        tags = {"Flows"},
        summary = "Export flows as a ZIP archive of yaml sources."
    )
    public HttpResponse<byte[]> exportByIds(
        @Parameter(description = "A list of tuple flow ID and namespace as flow identifiers") @Body List<IdWithNamespace> ids
    ) throws IOException {
        var flows = ids.stream()
            .map(id -> flowRepository.findByIdWithSource(id.getNamespace(), id.getId()).orElseThrow())
            .collect(Collectors.toList());
        var bytes = zipFlows(flows);
        return HttpResponse.ok(bytes).header("Content-Disposition", "attachment; filename=\"flows.zip\"");
    }

    private static byte[] zipFlows(List<FlowWithSource> flows) throws IOException {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ZipOutputStream archive = new ZipOutputStream(bos)) {

            for (var flow : flows) {
                var zipEntry = new ZipEntry(flow.getNamespace() + "." + flow.getId() + ".yml");
                archive.putNextEntry(zipEntry);
                archive.write(flow.getSource().getBytes());
                archive.closeEntry();
            }

            archive.finish();
            return bos.toByteArray();
        }
    }

    @ExecuteOn(TaskExecutors.IO)
    @Delete(uri = "/delete/by-query")
    @Operation(
        tags = {"Flows"},
        summary = "Delete flows returned by the query parameters."
    )
    public HttpResponse<BulkResponse> deleteByQuery(
        @Parameter(description = "A string filter") @Nullable @QueryValue(value = "q") String query,
        @Parameter(description = "A namespace filter prefix") @Nullable @QueryValue String namespace,
        @Parameter(description = "A labels filter") @Nullable @QueryValue List<String> labels
    ) {
        List<Flow> list = flowRepository
            .findWithSource(query, namespace, RequestUtils.toMap(labels))
            .stream()
            .peek(flowRepository::delete)
            .collect(Collectors.toList());

        return HttpResponse.ok(BulkResponse.builder().count(list.size()).build());
    }

    @ExecuteOn(TaskExecutors.IO)
    @Delete(uri = "/delete/by-ids")
    @Operation(
        tags = {"Flows"},
        summary = "Delete flows by their IDs."
    )
    public HttpResponse<BulkResponse> deleteByIds(
        @Parameter(description = "A list of tuple flow ID and namespace as flow identifiers") @Body List<IdWithNamespace> ids
    ) {
        List<Flow> list = ids
            .stream()
            .map(id -> flowRepository.findByIdWithSource(id.getNamespace(), id.getId()).orElseThrow())
            .peek(flowRepository::delete)
            .collect(Collectors.toList());

        return HttpResponse.ok(BulkResponse.builder().count(list.size()).build());
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/disable/by-query")
    @Operation(
        tags = {"Flows"},
        summary = "Disable flows returned by the query parameters."
    )
    public HttpResponse<BulkResponse> disableByQuery(
        @Parameter(description = "A string filter") @Nullable @QueryValue(value = "q") String query,
        @Parameter(description = "A namespace filter prefix") @Nullable @QueryValue String namespace,
        @Parameter(description = "A labels filter") @Nullable @QueryValue List<String> labels
    ) {

        return HttpResponse.ok(BulkResponse.builder().count(setFlowsDisableByQuery(query, namespace, labels, true).size()).build());
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/disable/by-ids")
    @Operation(
        tags = {"Flows"},
        summary = "Disable flows by their IDs."
    )
    public HttpResponse<BulkResponse> disableByIds(
        @Parameter(description = "A list of tuple flow ID and namespace as flow identifiers") @Body List<IdWithNamespace> ids
    ) {

        return HttpResponse.ok(BulkResponse.builder().count(setFlowsDisableByIds(ids, true).size()).build());
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/enable/by-query")
    @Operation(
        tags = {"Flows"},
        summary = "Enable flows returned by the query parameters."
    )
    public HttpResponse<BulkResponse> enableByQuery(
        @Parameter(description = "A string filter") @Nullable @QueryValue(value = "q") String query,
        @Parameter(description = "A namespace filter prefix") @Nullable @QueryValue String namespace,
        @Parameter(description = "A labels filter") @Nullable @QueryValue List<String> labels
    ) {

        return HttpResponse.ok(BulkResponse.builder().count(setFlowsDisableByQuery(query, namespace, labels, false).size()).build());
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/enable/by-ids")
    @Operation(
        tags = {"Flows"},
        summary = "Enable flows by their IDs."
    )
    public HttpResponse<BulkResponse> enableByIds(
        @Parameter(description = "A list of tuple flow ID and namespace as flow identifiers") @Body List<IdWithNamespace> ids
    ) {

        return HttpResponse.ok(BulkResponse.builder().count(setFlowsDisableByIds(ids, false).size()).build());
    }


    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/import", consumes = MediaType.MULTIPART_FORM_DATA)
    @Operation(
        tags = {"Flows"},
        summary = "Import flows as a ZIP archive of yaml sources or a multi-objects YAML file."
    )
    @ApiResponse(responseCode = "204", description = "On success")
    public HttpResponse<Void> importFlows(
        @Parameter(description = "The file to import, can be a ZIP archive or a multi-objects YAML file")
        @Part CompletedFileUpload fileUpload
    ) throws IOException {
        String fileName = fileUpload.getFilename().toLowerCase();
        if (fileName.endsWith(".yaml") || fileName.endsWith(".yml")) {
            List<String> sources = List.of(new String(fileUpload.getBytes()).split("---"));
            for (String source : sources) {
                Flow parsed = yamlFlowParser.parse(source, Flow.class);
                importFlow(source, parsed);
            }
        } else if (fileName.endsWith(".zip")) {
            try (ZipInputStream archive = new ZipInputStream(fileUpload.getInputStream())) {
                ZipEntry entry;
                while ((entry = archive.getNextEntry()) != null) {
                    if (entry.isDirectory() || !entry.getName().endsWith(".yml") && !entry.getName().endsWith(".yaml")) {
                        continue;
                    }

                    String source = new String(archive.readAllBytes());
                    Flow parsed = yamlFlowParser.parse(source, Flow.class);
                    importFlow(source, parsed);
                }
            }
        } else {
            throw new IllegalArgumentException("Cannot import file of type " + fileName.substring(fileName.lastIndexOf('.')));
        }

        return HttpResponse.status(HttpStatus.NO_CONTENT);
    }

    protected void importFlow(String source, Flow parsed) {
        flowRepository
            .findById(parsed.getNamespace(), parsed.getId())
            .ifPresentOrElse(
                previous -> flowRepository.update(parsed, previous, source, taskDefaultService.injectDefaults(parsed)),
                () -> flowRepository.create(parsed, source, taskDefaultService.injectDefaults(parsed))
            );
    }

    protected List<FlowWithSource> setFlowsDisableByIds(List<IdWithNamespace> ids, boolean disable) {
        return ids
            .stream()
            .map(id -> flowRepository.findByIdWithSource(id.getNamespace(), id.getId()).orElseThrow())
            .filter(flowWithSource -> disable != flowWithSource.isDisabled())
            .peek(flow -> {
                FlowWithSource flowUpdated = flow.toBuilder()
                    .disabled(disable)
                    .source(FlowService.injectDisabled(flow.getSource(), disable))
                    .build();

                flowRepository.update(
                    flowUpdated,
                    flow,
                    flowUpdated.getSource(),
                    taskDefaultService.injectDefaults(flowUpdated)
                );
            })
            .toList();
    }

    protected List<FlowWithSource> setFlowsDisableByQuery(String query, String namespace, List<String> labels, boolean disable) {
        return flowRepository
            .findWithSource(query, namespace, RequestUtils.toMap(labels))
            .stream()
            .filter(flowWithSource -> disable != flowWithSource.isDisabled())
            .peek(flow -> {
                FlowWithSource flowUpdated = flow.toBuilder()
                    .disabled(disable)
                    .source(FlowService.injectDisabled(flow.getSource(), disable))
                    .build();

                flowRepository.update(
                    flowUpdated,
                    flow,
                    flowUpdated.getSource(),
                    taskDefaultService.injectDefaults(flowUpdated)
                );
            })
            .toList();
    }
}
