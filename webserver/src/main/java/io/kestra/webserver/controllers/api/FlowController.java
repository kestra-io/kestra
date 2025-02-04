package io.kestra.webserver.controllers.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.exc.InvalidTypeIdException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.exceptions.InternalException;
import io.kestra.core.models.HasSource;
import io.kestra.core.models.SearchResult;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.FlowScope;
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
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.serializers.YamlParser;
import io.kestra.core.services.FlowService;
import io.kestra.core.services.GraphService;
import io.kestra.core.services.PluginDefaultService;
import io.kestra.core.tenant.TenantService;
import io.kestra.core.topologies.FlowTopologyService;
import io.kestra.webserver.controllers.domain.IdWithNamespace;
import io.kestra.webserver.responses.BulkResponse;
import io.kestra.webserver.responses.PagedResults;
import io.kestra.webserver.utils.PageableUtils;
import io.kestra.webserver.utils.RequestUtils;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.convert.format.Format;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.*;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.validation.Validated;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.extensions.Extension;
import io.swagger.v3.oas.annotations.extensions.ExtensionProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.extern.slf4j.Slf4j;

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
@Slf4j
public class FlowController {
    private static final String WARNING_JSON_FLOW_ENDPOINT = "This endpoint is deprecated. Handling flows as 'application/json' is no longer supported and will be removed in a future release. Please use the same endpoint with an 'application/x-yaml' content type.";

    @Inject
    private FlowRepositoryInterface flowRepository;

    @Inject
    private PluginDefaultService pluginDefaultService;

    @Inject
    private ModelValidator modelValidator;

    @Inject
    private FlowTopologyService flowTopologyService;

    @Inject
    private FlowTopologyRepositoryInterface flowTopologyRepository;

    @Inject
    private FlowService flowService;

    @Inject
    private YamlParser yamlParser;

    @Inject
    private GraphService graphService;

    @Inject
    private TenantService tenantService;

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "{namespace}/{id}/graph")
    @Operation(tags = {"Flows"}, summary = "Generate a graph for a flow")
    public FlowGraph flowGraph(
        @Parameter(description = "The flow namespace") @PathVariable String namespace,
        @Parameter(description = "The flow id") @PathVariable String id,
        @Parameter(description = "The flow revision") @QueryValue Optional<Integer> revision,
        @Parameter(description = "The subflow tasks to display") @Nullable @QueryValue List<String> subflows
    ) throws IllegalVariableEvaluationException {
        FlowWithSource flow = flowRepository
            .findByIdWithSource(tenantService.resolveTenant(), namespace, id, revision)
            .orElse(null);

        String flowUid = revision.isEmpty() ? Flow.uidWithoutRevision(tenantService.resolveTenant(), namespace, id) : Flow.uid(tenantService.resolveTenant(), namespace, id, revision);
        if (flow == null) {
            throw new NoSuchElementException(
                "Unable to find flow " + flowUid
            );
        }

        if (flow instanceof FlowWithException fwe) {
            throw new IllegalStateException(
                "Unable to generate graph for flow " + flowUid +
                    " because of exception " + fwe.getException()
            );
        }

        return graphService.flowGraph(flow, subflows);
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "graph", consumes = MediaType.APPLICATION_YAML)
    @Operation(tags = {"Flows"}, summary = "Generate a graph for a flow source")
    public FlowGraph flowGraphSource(
        @Parameter(description = "The flow") @Body String flow,
        @Parameter(description = "The subflow tasks to display") @Nullable @QueryValue List<String> subflows
    ) throws ConstraintViolationException, IllegalVariableEvaluationException {
        FlowWithSource flowParsed = yamlParser.parse(flow, Flow.class).withSource(flow);

        return graphService.flowGraph(flowParsed, subflows);
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "{namespace}/{id}")
    @Operation(tags = {"Flows"}, summary = "Get a flow")
    @Schema(
        oneOf = {FlowWithSource.class, Flow.class}
    )
    //FIXME we return Object instead of Flow as Micronaut, since 4, has an issue with subtypes serialization, see https://github.com/micronaut-projects/micronaut-core/issues/10294.
    public Object index(
        @Parameter(description = "The flow namespace") @PathVariable String namespace,
        @Parameter(description = "The flow id") @PathVariable String id,
        @Parameter(description = "Include the source code") @QueryValue(defaultValue = "false") boolean source,
        @Parameter(description = "Get latest revision by default") @Nullable @QueryValue Integer revision,
        @Parameter(description = "Get flow even if deleted") @QueryValue(defaultValue = "false") boolean allowDeleted
    ) {
        return source ?
            flowRepository
                .findByIdWithSource(tenantService.resolveTenant(), namespace, id, Optional.ofNullable(revision), allowDeleted)
                .orElse(null) :
            flowRepository
                .findById(tenantService.resolveTenant(), namespace, id, Optional.ofNullable(revision), allowDeleted)
                .orElse(null);
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "{namespace}/{id}/revisions")
    @Operation(tags = {"Flows"}, summary = "Get revisions for a flow")
    public List<FlowWithSource> revisions(
        @Parameter(description = "The flow namespace") @PathVariable String namespace,
        @Parameter(description = "The flow id") @PathVariable String id
    ) {
        return flowRepository.findRevisions(tenantService.resolveTenant(), namespace, id);
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "{namespace}/{id}/tasks/{taskId}")
    @Operation(tags = {"Flows"}, summary = "Get a flow task")
    //FIXME we return Object instead of Task as Micronaut, since 4, has an issue with subtypes serialization, see https://github.com/micronaut-projects/micronaut-core/issues/10294.
    @Schema(implementation = Task.class)
    public Object flowTask(
        @Parameter(description = "The flow namespace") @PathVariable String namespace,
        @Parameter(description = "The flow id") @PathVariable String id,
        @Parameter(description = "The task id") @PathVariable String taskId,
        @Parameter(description = "The flow revision") @Nullable @QueryValue Integer revision
    ) {
        return flowRepository
            .findById(tenantService.resolveTenant(), namespace, id, Optional.ofNullable(revision))
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
    @Get(uri = "/search")
    @Operation(tags = {"Flows"}, summary = "Search for flows")
    public PagedResults<Flow> find(
        @Parameter(description = "The current page") @QueryValue(defaultValue = "1") @Min(1) int page,
        @Parameter(description = "The current page size") @QueryValue(defaultValue = "10") @Min(1) int size,
        @Parameter(description = "The sort of current page") @Nullable @QueryValue List<String> sort,
        @Parameter(description = "A string filter") @Nullable @QueryValue(value = "q") String query,
        @Parameter(description = "The scope of the flows to include") @Nullable @QueryValue List<FlowScope> scope,
        @Parameter(description = "A namespace filter prefix") @Nullable @QueryValue String namespace,
        @Parameter(description = "A labels filter as a list of 'key:value'") @Nullable @QueryValue @Format("MULTI") List<String> labels
    ) throws HttpStatusException {

        return PagedResults.of(flowRepository.find(
            PageableUtils.from(page, size, sort),
            query,
            tenantService.resolveTenant(),
            scope,
            namespace,
            RequestUtils.toMap(labels)
        ));
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "/{namespace}")
    @Operation(tags = {"Flows"}, summary = "Retrieve all flows from a given namespace")
    public List<Flow> getFlowsByNamespace(
        @Parameter(description = "Namespace to filter flows") @PathVariable String namespace
    ) throws HttpStatusException {
        return flowRepository.findByNamespace(tenantService.resolveTenant(), namespace);
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "/source")
    @Operation(tags = {"Flows"}, summary = "Search for flows source code")
    public PagedResults<SearchResult<Flow>> source(
        @Parameter(description = "The current page") @QueryValue(defaultValue = "1") @Min(1) int page,
        @Parameter(description = "The current page size") @QueryValue(defaultValue = "10") @Min(1) int size,
        @Parameter(description = "The sort of current page") @Nullable @QueryValue List<String> sort,
        @Parameter(description = "A string filter") @Nullable @QueryValue(value = "q") String query,
        @Parameter(description = "A namespace filter prefix") @Nullable @QueryValue String namespace
    ) throws HttpStatusException {
        return PagedResults.of(flowRepository.findSourceCode(PageableUtils.from(page, size, sort), query, tenantService.resolveTenant(), namespace));
    }


    @ExecuteOn(TaskExecutors.IO)
    @Post(consumes = MediaType.APPLICATION_YAML)
    @Operation(tags = {"Flows"}, summary = "Create a flow from yaml source")
    public HttpResponse<FlowWithSource> create(
        @Parameter(description = "The flow") @Body String flow
    ) throws ConstraintViolationException {
        Flow flowParsed = yamlParser.parse(flow, Flow.class);

        return HttpResponse.ok(doCreate(flowParsed, flow));
    }

    /**
     * @deprecated use {@link #create(String)} instead
     */
    @ExecuteOn(TaskExecutors.IO)
    @Post(consumes = MediaType.ALL)
    @Operation(tags = {"Flows"}, summary = "Create a flow from json object", deprecated = true)
    @Deprecated(forRemoval = true, since = "0.18")
    @Hidden // we hide it otherwise this is the one that will be included in the OpenAPI spec instead of the YAML one.
    public HttpResponse<Flow> create(
        @Parameter(description = "The flow") @Body Flow flow
    ) throws ConstraintViolationException {
        log.warn(WARNING_JSON_FLOW_ENDPOINT);

        return HttpResponse.ok(doCreate(flow, flow.generateSource()).toFlow());
    }

    protected FlowWithSource doCreate(Flow flow, String source) {
        return flowRepository.create(flow, source, pluginDefaultService.injectDefaults(flow.withSource(source)));
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "{namespace}", consumes = MediaType.APPLICATION_YAML)
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

        return this.bulkUpdateOrCreate(
            namespace,
            sources
                .stream()
                .map(flow -> FlowWithSource.of(yamlParser.parse(flow, Flow.class), flow.trim()))
                .toList(),
            delete
        );
    }

    /**
     * @deprecated use {@link #updateNamespace(String, String, Boolean)} instead
     */
    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "{namespace}")
    @Operation(
        tags = {"Flows"},
        summary = "Update a complete namespace from json object",
        description = "All flow will be created / updated for this namespace.\n" +
            "Flow that already created but not in `flows` will be deleted if the query delete is `true`",
        deprecated = true
    )
    @Deprecated(forRemoval = true, since = "0.18")
    @Hidden // we hide it otherwise this is the one that will be included in the OpenAPI spec instead of the YAML one.
    public List<Flow> updateNamespace(
        @Parameter(description = "The flow namespace") @PathVariable String namespace,
        @Parameter(description = "A list of flows") @Body @Valid List<Flow> flows,
        @Parameter(description = "If missing flow should be deleted") @QueryValue(defaultValue = "true") Boolean delete
    ) throws ConstraintViolationException {
        log.warn(WARNING_JSON_FLOW_ENDPOINT);

        return this
            .bulkUpdateOrCreate(
                namespace,
                flows
                    .stream()
                    .map(throwFunction(flow -> FlowWithSource.of(flow, flow.generateSource())))
                    .toList(),
                delete
            )
            .stream()
            .map(FlowWithSource::toFlow)
            .toList();
    }

    protected List<FlowWithSource> bulkUpdateOrCreate(@Nullable String namespace, List<FlowWithSource> flows, Boolean delete) {

        if (namespace != null) {
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

            if (!invalids.isEmpty()) {
                throw new ConstraintViolationException(invalids);
            }
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
            if (namespace != null) {
                deleted = flowRepository
                    .findByNamespaceWithSource(tenantService.resolveTenant(), namespace);
            } else {
                deleted = flowRepository
                    .findAllWithSource(tenantService.resolveTenant());
            }
            deleted = deleted.stream()
                .filter(flow -> !ids.contains(flow.getId()))
                .peek(flow -> flowRepository.delete(flow))
                .toList();
        }

        // update or create flows
        List<FlowWithSource> updatedOrCreated = flows.stream()
            .map(flowWithSource -> {
                Optional<Flow> existingFlow = flowRepository.findById(tenantService.resolveTenant(), flowWithSource.getNamespace(), flowWithSource.getId());
                if (existingFlow.isPresent()) {
                    return flowRepository.update(flowWithSource, existingFlow.get(), flowWithSource.getSource(), pluginDefaultService.injectDefaults(flowWithSource));
                } else {
                    return this.doCreate(flowWithSource, flowWithSource.getSource());
                }
            })
            .toList();

        return Stream.concat(deleted.stream(), updatedOrCreated.stream()).toList();
    }

    @Put(uri = "{namespace}/{id}", consumes = MediaType.APPLICATION_YAML)
    @ExecuteOn(TaskExecutors.IO)
    @Operation(tags = {"Flows"}, summary = "Update a flow")
    public HttpResponse<FlowWithSource> update(
        @Parameter(description = "The flow namespace") @PathVariable String namespace,
        @Parameter(description = "The flow id") @PathVariable String id,
        @Parameter(description = "The flow") @Body String flow
    ) throws ConstraintViolationException {
        Optional<Flow> existingFlow = flowRepository.findById(tenantService.resolveTenant(), namespace, id);
        if (existingFlow.isEmpty()) {

            return HttpResponse.status(HttpStatus.NOT_FOUND);
        }
        Flow flowParsed = yamlParser.parse(flow, Flow.class);
        flowService.checkValidSubflows(flowParsed);
        return HttpResponse.ok(update(flowParsed, existingFlow.get(), flow));
    }

    /**
     * @deprecated use {@link #update(String, String, String)} instead
     */
    @Put(uri = "{namespace}/{id}", consumes = MediaType.ALL)
    @ExecuteOn(TaskExecutors.IO)
    @Operation(tags = {"Flows"}, summary = "Update a flow", deprecated = true)
    @Deprecated(forRemoval = true, since = "0.18")
    @Hidden // we hide it otherwise this is the one that will be included in the OpenAPI spec instead of the JSON one.
    public HttpResponse<Flow> update(
        @Parameter(description = "The flow namespace") @PathVariable String namespace,
        @Parameter(description = "The flow id") @PathVariable String id,
        @Parameter(description = "The flow") @Body Flow flow
    ) throws ConstraintViolationException {
        log.warn(WARNING_JSON_FLOW_ENDPOINT);

        Optional<Flow> existingFlow = flowRepository.findById(tenantService.resolveTenant(), namespace, id);
        if (existingFlow.isEmpty()) {
            return HttpResponse.status(HttpStatus.NOT_FOUND);
        }

        return HttpResponse.ok(update(flow, existingFlow.get(), flow.generateSource()).toFlow());
    }

    protected FlowWithSource update(Flow current, Flow previous, String source) {
        return flowRepository.update(current, previous, source, pluginDefaultService.injectDefaults(current.withSource(source)));
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "bulk", consumes = MediaType.APPLICATION_YAML)
    @Operation(
        tags = {"Flows"},
        summary = "Update from multiples yaml sources",
        description = "All flow will be created / updated for this namespace.\n" +
            "Flow that already created but not in `flows` will be deleted if the query delete is `true`"
    )
    public List<FlowWithSource> bulkUpdate(
        @Parameter(description = "A list of flows") @Body @Nullable String flows,
        @Parameter(description = "If missing flow should be deleted") @QueryValue(defaultValue = "true") Boolean delete
    ) throws ConstraintViolationException {
        List<String> sources = flows != null ? List.of(flows.split("---")) : new ArrayList<>();

        return this.bulkUpdateOrCreate(
            null,
            sources
                .stream()
                .map(flow -> FlowWithSource.of(yamlParser.parse(flow, Flow.class), flow.trim()))
                .toList(),
            delete
        );
    }

    /**
     * @deprecated should not be used anymore
     */
    @Patch(uri = "{namespace}/{id}/{taskId}")
    @ExecuteOn(TaskExecutors.IO)
    @Operation(tags = {"Flows"}, summary = "Update a single task on a flow", deprecated = true)
    @Deprecated(forRemoval = true, since = "0.18")
    @SuppressWarnings("deprecated")
    public HttpResponse<Flow> updateTask(
        @Parameter(description = "The flow namespace") @PathVariable String namespace,
        @Parameter(description = "The flow id") @PathVariable String id,
        @Parameter(description = "The task id") @PathVariable String taskId,
        @Parameter(description = "The task") @Valid @Body Task task
    ) throws ConstraintViolationException {
        log.warn("This endpoint is deprecated: updating a single task is not longer supported and will be removed in a future release.");

        Optional<Flow> existingFlow = flowRepository.findById(tenantService.resolveTenant(), namespace, id);

        if (existingFlow.isEmpty()) {
            return HttpResponse.status(HttpStatus.NOT_FOUND);
        }

        if (!taskId.equals(task.getId())) {
            throw new IllegalArgumentException("Invalid taskId, previous '" + taskId + "' & current '" + task.getId() + "'");
        }

        Flow flow = existingFlow.get();
        try {
            Flow newValue = flow.updateTask(taskId, task);
            String newSource = newValue.generateSource();
            return HttpResponse.ok(flowRepository.update(newValue, flow, newSource, pluginDefaultService.injectDefaults(newValue.withSource(newSource))).toFlow());
        } catch (InternalException e) {
            return HttpResponse.status(HttpStatus.NOT_FOUND);
        }
    }

    @Delete(uri = "{namespace}/{id}")
    @ExecuteOn(TaskExecutors.IO)
    @Operation(tags = {"Flows"}, summary = "Delete a flow")
    @ApiResponse(responseCode = "204", description = "On success")
    public HttpResponse<Void> delete(
        @Parameter(description = "The flow namespace") @PathVariable String namespace,
        @Parameter(description = "The flow id") @PathVariable String id
    ) {
        Optional<FlowWithSource> flow = flowRepository.findByIdWithSource(tenantService.resolveTenant(), namespace, id);
        if (flow.isPresent()) {
            flowRepository.delete(flow.get());
            return HttpResponse.status(HttpStatus.NO_CONTENT);
        } else {
            return HttpResponse.status(HttpStatus.NOT_FOUND);
        }
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "distinct-namespaces")
    @Operation(tags = {"Flows"}, summary = "List all distinct namespaces")
    public List<String> listDistinctNamespace(
        @Parameter(description = "A string filter") @Nullable @QueryValue(value = "q") String query
    ) {
        return flowRepository.findDistinctNamespace(tenantService.resolveTenant(), query);
    }


    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "{namespace}/{id}/dependencies")
    @Operation(tags = {"Flows"}, summary = "Get flow dependencies")
    public FlowTopologyGraph dependencies(
        @Parameter(description = "The flow namespace") @PathVariable String namespace,
        @Parameter(description = "The flow id") @PathVariable String id,
        @Parameter(description = "if true, list only destination dependencies, otherwise list also source dependencies") @QueryValue(defaultValue = "false") boolean destinationOnly
    ) {
        List<FlowTopology> flowTopologies = flowTopologyRepository.findByFlow(tenantService.resolveTenant(), namespace, id, destinationOnly);

        return flowTopologyService.graph(
            flowTopologies.stream(),
            (flowNode -> flowNode)
        );
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "validate", consumes = MediaType.APPLICATION_YAML)
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
                    Flow flowParse = yamlParser.parse(flow, Flow.class);
                    Integer sentRevision = flowParse.getRevision();
                    if (sentRevision != null) {
                        Integer lastRevision = Optional.ofNullable(flowRepository.lastRevision(tenantService.resolveTenant(), flowParse.getNamespace(), flowParse.getId()))
                            .orElse(0);
                        validateConstraintViolationBuilder.outdated(!sentRevision.equals(lastRevision + 1));
                    }

                    validateConstraintViolationBuilder.deprecationPaths(flowService.deprecationPaths(flowParse));
                    validateConstraintViolationBuilder.warnings(flowService.warnings(flowParse));
                    validateConstraintViolationBuilder.infos(flowService.relocations(flow).stream().map(relocation -> relocation.from() + " is replaced by " + relocation.to()).toList());
                    validateConstraintViolationBuilder.flow(flowParse.getId());
                    validateConstraintViolationBuilder.namespace(flowParse.getNamespace());

                    modelValidator.validate(pluginDefaultService.injectDefaults(flowParse.withSource(flow)));
                    flowService.checkValidSubflows(flowParse);
                } catch (ConstraintViolationException e) {
                    validateConstraintViolationBuilder.constraints(e.getMessage());
                } catch (RuntimeException re) {
                    // In case of any error, we add a validation violation so the error is displayed in the UI.
                    // We may change that by throwing an internal error and handle it in the UI, but this should not occur except for rare cases
                    // in dev like incompatible plugin versions.
                    log.error("Unable to validate the flow", re);
                    validateConstraintViolationBuilder.constraints("Unable to validate the flow: " + re.getMessage());
                }

                return validateConstraintViolationBuilder.build();
            })
            .collect(Collectors.toList());
    }

    // This endpoint is not used by the Kestra UI nor our CLI but is provided for the API users for convenience
    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/validate/task", consumes = MediaType.APPLICATION_JSON)
    @Operation(tags = {"Flows"}, summary = "Validate task")
    public ValidateConstraintViolation validateTask(
        @Parameter(description = "Task") @Body String task
    ) {
        ValidateConstraintViolation.ValidateConstraintViolationBuilder<?, ?> validateConstraintViolationBuilder = ValidateConstraintViolation.builder();

        try {
            var taskParse = parseTaskTrigger(task, Task.class);
            modelValidator.validate(taskParse);
        } catch (ConstraintViolationException e) {
            validateConstraintViolationBuilder.constraints(e.getMessage());
        } catch (RuntimeException re) {
            // In case of any error, we add a validation violation so the error is displayed in the UI.
            // We may change that by throwing an internal error and handle it in the UI, but this should not occur except for rare cases
            // in dev like incompatible plugin versions.
            log.error("Unable to validate the task", re);
            validateConstraintViolationBuilder.constraints("Unable to validate the task: " + re.getMessage());
        }

        return validateConstraintViolationBuilder.build();
    }

    // This endpoint is not used by the Kestra UI nor our CLI but is provided for the API users for convenience
    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/validate/trigger", consumes = MediaType.APPLICATION_JSON)
    @Operation(tags = {"Flows"}, summary = "Validate trigger")
    public ValidateConstraintViolation validateTrigger(
        @Parameter(description = "Trigger") @Body String trigger
    ) {
        ValidateConstraintViolation.ValidateConstraintViolationBuilder<?, ?> validateConstraintViolationBuilder = ValidateConstraintViolation.builder();

        try {
            var triggerParse = parseTaskTrigger(trigger, AbstractTrigger.class);
            modelValidator.validate(triggerParse);
        } catch (ConstraintViolationException e) {
            validateConstraintViolationBuilder.constraints(e.getMessage());
        } catch (RuntimeException re) {
            // In case of any error, we add a validation violation so the error is displayed in the UI.
            // We may change that by throwing an internal error and handle it in the UI, but this should not occur except for rare cases
            // in dev like incompatible plugin versions.
            log.error("Unable to validate the trigger", re);
            validateConstraintViolationBuilder.constraints("Unable to validate the trigger: " + re.getMessage());
        }
        return validateConstraintViolationBuilder.build();
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/validate/task", consumes = MediaType.APPLICATION_YAML)
    @Operation(tags = {"Flows"}, summary = "Validate a list of flows")
    public ValidateConstraintViolation validateTask(
        @Parameter(description = "A list of flows") @Body String task,
        @Parameter(description = "Type of task") @QueryValue TaskValidationType section
    ) {
        ValidateConstraintViolation.ValidateConstraintViolationBuilder<?, ?> validateConstraintViolationBuilder = ValidateConstraintViolation.builder();

        try {
            if (section == TaskValidationType.TASKS) {
                Task taskParse = yamlParser.parse(task, Task.class);
                modelValidator.validate(taskParse);
            } else if (section == TaskValidationType.TRIGGERS) {
                AbstractTrigger triggerParse = yamlParser.parse(task, AbstractTrigger.class);
                modelValidator.validate(triggerParse);
            }
        } catch (ConstraintViolationException e) {
            validateConstraintViolationBuilder.constraints(e.getMessage());
        } catch (RuntimeException re) {
            // In case of any error, we add a validation violation so the error is displayed in the UI.
            // We may change that by throwing an internal error and handle it in the UI, but this should not occur except for rare cases
            // in dev like incompatible plugin versions.
            log.error("Unable to validate the flow", re);
            validateConstraintViolationBuilder.constraints("Unable to validate the flow: " + re.getMessage());
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
        @Parameter(description = "The scope of the flows to include") @Nullable @QueryValue List<FlowScope> scope,
        @Parameter(description = "A namespace filter prefix") @Nullable @QueryValue String namespace,
        @Parameter(description = "A labels filter as a list of 'key:value'") @Nullable @QueryValue @Format("MULTI") List<String> labels
    ) throws IOException {
        var flows = flowRepository.findWithSource(query, tenantService.resolveTenant(), scope, namespace, RequestUtils.toMap(labels));
        var bytes = HasSource.asZipFile(flows, flow -> flow.getNamespace() + "-" + flow.getId() + ".yml");

        return HttpResponse.ok(bytes).header("Content-Disposition", "attachment; filename=\"flows.zip\"");
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "/export/by-ids", produces = MediaType.APPLICATION_OCTET_STREAM)
    @Operation(
        tags = {"Flows"},
        summary = "Export flows as a ZIP archive of yaml sources."
    )
    public HttpResponse<byte[]> exportByIds(
        @Parameter(description = "A list of tuple flow ID and namespace as flow identifiers") @Body List<IdWithNamespace> ids
    ) throws IOException {
        var flows = ids.stream()
            .map(id -> flowRepository.findByIdWithSource(tenantService.resolveTenant(), id.getNamespace(), id.getId()).orElseThrow())
            .toList();
        var bytes = HasSource.asZipFile(flows, flow -> flow.getNamespace() + "." + flow.getId() + ".yml");
        return HttpResponse.ok(bytes).header("Content-Disposition", "attachment; filename=\"flows.zip\"");
    }

    @ExecuteOn(TaskExecutors.IO)
    @Delete(uri = "/delete/by-query")
    @Operation(
        tags = {"Flows"},
        summary = "Delete flows returned by the query parameters."
    )
    public HttpResponse<BulkResponse> deleteByQuery(
        @Parameter(description = "A string filter") @Nullable @QueryValue(value = "q") String query,
        @Parameter(description = "The scope of the flows to include") @Nullable @QueryValue List<FlowScope> scope,
        @Parameter(description = "A namespace filter prefix") @Nullable @QueryValue String namespace,
        @Parameter(description = "A labels filter as a list of 'key:value'") @Nullable @QueryValue @Format("MULTI") List<String> labels
    ) {
        List<Flow> list = flowRepository
            .findWithSource(query, tenantService.resolveTenant(), scope, namespace, RequestUtils.toMap(labels))
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
            .map(id -> flowRepository.findByIdWithSource(tenantService.resolveTenant(), id.getNamespace(), id.getId()).orElseThrow())
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
        @Parameter(description = "The scope of the flows to include") @Nullable @QueryValue List<FlowScope> scope,
        @Parameter(description = "A namespace filter prefix") @Nullable @QueryValue String namespace,
        @Parameter(description = "A labels filter as a list of 'key:value'") @Nullable @QueryValue @Format("MULTI") List<String> labels
    ) {

        return HttpResponse.ok(BulkResponse.builder().count(setFlowsDisableByQuery(query, scope, namespace, labels, true).size()).build());
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
        @Parameter(description = "The scope of the flows to include") @Nullable @QueryValue List<FlowScope> scope,
        @Parameter(description = "A namespace filter prefix") @Nullable @QueryValue String namespace,
        @Parameter(description = "A labels filter as a list of 'key:value'") @Nullable @QueryValue @Format("MULTI") List<String> labels
    ) {

        return HttpResponse.ok(BulkResponse.builder().count(setFlowsDisableByQuery(query, scope, namespace, labels, false).size()).build());
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
        summary = """
            Import flows as a ZIP archive of yaml sources or a multi-objects YAML file.
            When sending a Yaml that contains one or more flows, a list of index is returned.
            When sending a ZIP archive, a list of files that couldn't be imported is returned.
        """
    )
    @ApiResponse(responseCode = "200", description = "On success")
    public HttpResponse<List<String>> importFlows(
        @Parameter(description = "The file to import, can be a ZIP archive or a multi-objects YAML file")
        @Part CompletedFileUpload fileUpload
    ) throws IOException {
        String tenantId = tenantService.resolveTenant();
        final List<String> wrongFiles = new ArrayList<>();
        try {
            HasSource.readSourceFile(fileUpload, (source, name) -> {
                try {
                    this.importFlow(tenantId, source);
                } catch (Exception e) {
                    wrongFiles.add(name);
                }
            });
        } catch (IOException e){
            log.error("Unexpected error while importing flows", e);
            fileUpload.discard();
            return HttpResponse.badRequest();
        }
        return HttpResponse.ok(wrongFiles);
    }

    protected void importFlow(String tenantId, String source) {
        flowService.importFlow(tenantId, source);
    }

    protected List<FlowWithSource> setFlowsDisableByIds(List<IdWithNamespace> ids, boolean disable) {
        return ids
            .stream()
            .map(id -> flowRepository.findByIdWithSource(tenantService.resolveTenant(), id.getNamespace(), id.getId()).orElseThrow())
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
                    pluginDefaultService.injectDefaults(flowUpdated)
                );
            })
            .toList();
    }

    protected List<FlowWithSource> setFlowsDisableByQuery(String query, List<FlowScope> scope, String namespace, List<String> labels, boolean disable) {
        return flowRepository
            .findWithSource(query, tenantService.resolveTenant(), scope, namespace, RequestUtils.toMap(labels))
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
                    pluginDefaultService.injectDefaults(flowUpdated)
                );
            })
            .toList();
    }

    protected <T> T parseTaskTrigger(String input, Class<T> cls) throws ConstraintViolationException {
        try {
            return JacksonMapper.ofJson().readValue(input, cls);
        } catch (JsonProcessingException e) {
            if (e.getCause() instanceof ConstraintViolationException constraintViolationException) {
                throw constraintViolationException;
            } else if (e instanceof InvalidTypeIdException invalidTypeIdException) {
                // This error is thrown when a non-existing task is used
                throw new ConstraintViolationException(
                    "Invalid type: " + invalidTypeIdException.getTypeId(),
                    Set.of(
                        ManualConstraintViolation.of(
                            "Invalid type: " + invalidTypeIdException.getTypeId(),
                            input,
                            String.class,
                            invalidTypeIdException.getPathReference(),
                            null
                        ),
                        ManualConstraintViolation.of(
                            e.getMessage(),
                            input,
                            String.class,
                            invalidTypeIdException.getPathReference(),
                            null
                        )
                    )
                );
            }
            else if (e instanceof UnrecognizedPropertyException unrecognizedPropertyException) {
                var message = unrecognizedPropertyException.getOriginalMessage() + unrecognizedPropertyException.getMessageSuffix();
                throw new ConstraintViolationException(
                    message,
                    Collections.singleton(
                        ManualConstraintViolation.of(
                            e.getCause() == null ? message : message + "\nCaused by: " + e.getCause().getMessage(),
                            input,
                            String.class,
                            unrecognizedPropertyException.getPathReference(),
                            null
                        )
                    ));
            }
            else {
                throw new ConstraintViolationException(
                    "Illegal source: " + e.getMessage(),
                    Collections.singleton(
                        ManualConstraintViolation.of(
                            e.getCause() == null ? e.getMessage() : e.getMessage() + "\nCaused by: " + e.getCause().getMessage(),
                            input,
                            String.class,
                            "flow",
                            null
                        )
                    )
                );
            }
        }
    }
}
