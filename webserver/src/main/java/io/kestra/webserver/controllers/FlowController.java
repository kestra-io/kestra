package io.kestra.webserver.controllers;

import io.kestra.core.models.SearchResult;
import io.kestra.webserver.utils.RequestUtils;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.*;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.validation.Validated;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.exceptions.InternalException;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.hierarchies.FlowGraph;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.validations.ManualConstraintViolation;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.webserver.responses.PagedResults;
import io.kestra.webserver.utils.PageableUtils;

import java.util.*;
import java.util.stream.Collectors;
import io.micronaut.core.annotation.Nullable;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.inject.Inject;
import javax.validation.ConstraintViolationException;
import javax.validation.Valid;

import static io.kestra.core.utils.Rethrow.throwFunction;

@Validated
@Controller("/api/v1/flows")
public class FlowController {
    @Inject
    private FlowRepositoryInterface flowRepository;

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "{namespace}/{id}/graph", produces = MediaType.TEXT_JSON)
    @Operation(tags = {"Flows"}, summary = "Generate a graph for a flow")
    public FlowGraph flowGraph(
        @Parameter(description = "The flow namespace") String namespace,
        @Parameter(description = "The flow id") String id,
        @Parameter(description = "The flow revision") Optional<Integer> revision
    ) throws IllegalVariableEvaluationException {
        return flowRepository
            .findById(namespace, id, revision)
            .map(throwFunction(FlowGraph::of))
            .orElse(null);
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "{namespace}/{id}", produces = MediaType.TEXT_JSON)
    @Operation(tags = {"Flows"}, summary = "Get a flow")
    public Flow index(
        @Parameter(description = "The flow namespace") String namespace,
        @Parameter(description = "The flow id") String id
    ) {
        return flowRepository
            .findById(namespace, id)
            .orElse(null);
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "{namespace}/{id}/revisions", produces = MediaType.TEXT_JSON)
    @Operation(tags = {"Flows"}, summary = "Get revisions for a flow")
    public List<Flow> revisions(
        @Parameter(description = "The flow namespace") String namespace,
        @Parameter(description = "The flow id") String id
    ) {
        return flowRepository.findRevisions(namespace, id);
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "/search", produces = MediaType.TEXT_JSON)
    @Operation(tags = {"Flows"}, summary = "Search for flows")
    public PagedResults<Flow> find(
        @Parameter(description = "The current page") @QueryValue(value = "page", defaultValue = "1") int page,
        @Parameter(description = "The current page size") @QueryValue(value = "size", defaultValue = "10") int size,
        @Parameter(description = "The sort of current page") @Nullable @QueryValue(value = "sort") List<String> sort,
        @Parameter(description = "A string filter") @Nullable @QueryValue(value = "q") String query,
        @Parameter(description = "A namespace filter prefix") @Nullable @QueryValue(value = "namespace") String namespace,
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
        @Parameter(description = "The current page") @QueryValue(value = "page", defaultValue = "1") int page,
        @Parameter(description = "The current page size") @QueryValue(value = "size", defaultValue = "10") int size,
        @Parameter(description = "The sort of current page") @Nullable @QueryValue(value = "sort") List<String> sort,
        @Parameter(description = "A string filter") @Nullable @QueryValue(value = "q") String query,
        @Parameter(description = "A namespace filter prefix") @Nullable @QueryValue(value = "namespace") String namespace
    ) throws HttpStatusException {
        return PagedResults.of(flowRepository.findSourceCode(PageableUtils.from(page, size, sort), query, namespace));
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(produces = MediaType.TEXT_JSON)
    @Operation(tags = {"Flows"}, summary = "Create a flow")
    public HttpResponse<Flow> create(
        @Parameter(description = "The flow") @Body @Valid Flow flow
    ) throws ConstraintViolationException {
        if (flowRepository.findById(flow.getNamespace(), flow.getId()).isPresent()) {
            throw new ConstraintViolationException(Collections.singleton(ManualConstraintViolation.of(
                "Flow id already exists",
                flow,
                Flow.class,
                "flow.id",
                flow.getId()
            )));
        }

        return HttpResponse.ok(flowRepository.create(flow));
    }

    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "{namespace}", produces = MediaType.TEXT_JSON)
    @Operation(
        tags = {"Flows"},
        summary = "Update a complete namespace",
        description = "All flow will be created / updated for this namespace.\n" +
            "Flow that already created but not in `flows` will be deleted"
    )
    public List<Flow> updateNamespace(
        @Parameter(description = "The flow namespace") String namespace,
        @Parameter(description = "A list of flows") @Body @Valid List<Flow> flows
    ) throws ConstraintViolationException {
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
            .collect(Collectors.toList());

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
            .collect(Collectors.toList());

        // delete all not in updated ids
        flowRepository
            .findByNamespace(namespace)
            .stream()
            .filter(flow -> !ids.contains(flow.getId()))
            .forEach(flow -> flowRepository.delete(flow));

        // update or create flows
        return flows
            .stream()
            .map(flow -> {
                Optional<Flow> existingFlow = flowRepository.findById(namespace, flow.getId());
                if (existingFlow.isPresent()) {
                    return flowRepository.update(flow, existingFlow.get());
                } else {
                    return flowRepository.create(flow);
                }
            })
            .collect(Collectors.toList());
    }

    @Put(uri = "{namespace}/{id}", produces = MediaType.TEXT_JSON)
    @ExecuteOn(TaskExecutors.IO)
    @Operation(tags = {"Flows"}, summary = "Update a flow")
    public HttpResponse<Flow> update(
        @Parameter(description = "The flow namespace") String namespace,
        @Parameter(description = "The flow id") String id,
        @Parameter(description = "The flow") @Body @Valid Flow flow
    ) throws ConstraintViolationException {
        Optional<Flow> existingFlow = flowRepository.findById(namespace, id);

        if (existingFlow.isEmpty()) {
            return HttpResponse.status(HttpStatus.NOT_FOUND);
        }

        return HttpResponse.ok(flowRepository.update(flow, existingFlow.get()));
    }

    @Patch(uri = "{namespace}/{id}/{taskId}", produces = MediaType.TEXT_JSON)
    @ExecuteOn(TaskExecutors.IO)
    @Operation(tags = {"Flows"}, summary = "Update a single task on a flow")
    public HttpResponse<Flow> updateTask(
        @Parameter(description = "The flow namespace") String namespace,
        @Parameter(description = "The flow id") String id,
        @Parameter(description = "The task id") String taskId,
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
            return HttpResponse.ok(flowRepository.update(newValue, flow));
        } catch (InternalException e) {
            return HttpResponse.status(HttpStatus.NOT_FOUND);
        }
    }

    @Delete(uri = "{namespace}/{id}", produces = MediaType.TEXT_JSON)
    @ExecuteOn(TaskExecutors.IO)
    @Operation(tags = {"Flows"}, summary = "Delete a flow")
    @ApiResponses(
        @ApiResponse(responseCode = "204", description = "On success")
    )
    public HttpResponse<Void> delete(
        @Parameter(description = "The flow namespace") String namespace,
        @Parameter(description = "The flow id") String id
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
}
