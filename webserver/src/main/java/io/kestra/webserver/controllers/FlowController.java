package io.kestra.webserver.controllers;

import io.kestra.core.models.SearchResult;
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

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.validation.ConstraintViolationException;
import javax.validation.Valid;

import static io.kestra.core.utils.Rethrow.throwFunction;

@Validated
@Controller("/api/v1/flows")
public class FlowController {
    @Inject
    private FlowRepositoryInterface flowRepository;

    /**
     * @param namespace The flow namespace
     * @param id        The flow id
     * @return flow tree found
     */
    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "{namespace}/{id}/graph", produces = MediaType.TEXT_JSON)
    public FlowGraph flowGraph(String namespace, String id, Optional<Integer> revision) throws IllegalVariableEvaluationException {
        return flowRepository
            .findById(namespace, id, revision)
            .map(throwFunction(FlowGraph::of))
            .orElse(null);
    }

    /**
     * @param namespace The flow namespace
     * @param id        The flow id
     * @return flow found
     */
    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "{namespace}/{id}", produces = MediaType.TEXT_JSON)
    public Flow index(String namespace, String id) {
        return flowRepository
            .findById(namespace, id)
            .orElse(null);
    }

    /**
     * @param namespace The flow namespace
     * @param id The flow id
     * @return flow revisions found
     */
    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "{namespace}/{id}/revisions", produces = MediaType.TEXT_JSON)
    public List<Flow> revisions(String namespace, String id) {
        return flowRepository.findRevisions(namespace, id);
    }

    /**
     * @param query The flow query that is a lucene string
     * @param page  Page in flow pagination
     * @param size  Element count in pagination selection
     * @return flow list
     */
    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "/search", produces = MediaType.TEXT_JSON)
    public PagedResults<Flow> find(
        @QueryValue(value = "q") String query, //Search by namespace using lucene
        @QueryValue(value = "page", defaultValue = "1") int page,
        @QueryValue(value = "size", defaultValue = "10") int size,
        @Nullable @QueryValue(value = "sort") List<String> sort
    ) throws HttpStatusException {
        return PagedResults.of(flowRepository.find(query, PageableUtils.from(page, size, sort)));
    }

    /**
     * @param query The flow query that is a lucene string
     * @param page  Page in flow pagination
     * @param size  Element count in pagination selection
     * @return flow search list
     */
    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "/source", produces = MediaType.TEXT_JSON)
    public PagedResults<SearchResult<Flow>> source(
        @QueryValue(value = "q") String query, //Search by namespace using lucene
        @QueryValue(value = "page", defaultValue = "1") int page,
        @QueryValue(value = "size", defaultValue = "10") int size,
        @Nullable @QueryValue(value = "sort") List<String> sort
    ) throws HttpStatusException {
        return PagedResults.of(flowRepository.findSourceCode(query, PageableUtils.from(page, size, sort)));
    }

    /**
     * @param flow The flow content
     * @return flow created
     */
    @ExecuteOn(TaskExecutors.IO)
    @Post(produces = MediaType.TEXT_JSON)
    public HttpResponse<Flow> create(@Body @Valid Flow flow) throws ConstraintViolationException {
        if (flowRepository.findById(flow.getNamespace(), flow.getId()).isPresent()) {
            throw new ConstraintViolationException(Collections.singleton(ManualConstraintViolation.of(
                "Flow id already exists",
                flow,
                Flow.class,
                "namespace.id",
                flow.getId()
            )));
        }

        return HttpResponse.ok(flowRepository.create(flow));
    }

    /**
     * @param namespace The namespace to update
     * @param flows The flows content, all flow will be created / updated for this namespace.
     *                  Flow in repository but not in {@code flows} will also be deleted
     * @return flows created or updated
     */
    @ExecuteOn(TaskExecutors.IO)
    @Post(uri = "{namespace}", produces = MediaType.TEXT_JSON)
    public List<Flow> updateNamespace(String namespace, @Body @Valid  List<Flow> flows) throws ConstraintViolationException {
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

    /**
     * @param namespace flow namespace
     * @param id        flow id to update
     * @return flow updated
     */
    @Put(uri = "{namespace}/{id}", produces = MediaType.TEXT_JSON)
    @ExecuteOn(TaskExecutors.IO)
    public HttpResponse<Flow> update(String namespace, String id, @Body @Valid Flow flow) throws ConstraintViolationException {
        Optional<Flow> existingFlow = flowRepository.findById(namespace, id);

        if (existingFlow.isEmpty()) {
            return HttpResponse.status(HttpStatus.NOT_FOUND);
        }

        return HttpResponse.ok(flowRepository.update(flow, existingFlow.get()));
    }

    /**
     * @param namespace flow namespace
     * @param id        flow id to update
     * @param taskId    taskId id to update
     * @return flow updated
     */
    @Patch(uri = "{namespace}/{id}/{taskId}", produces = MediaType.TEXT_JSON)
    @ExecuteOn(TaskExecutors.IO)
    public HttpResponse<Flow> updateTask(String namespace, String id, String taskId, @Body Task task) throws ConstraintViolationException {
        Optional<Flow> existingFlow = flowRepository.findById(namespace, id);

        if (existingFlow.isEmpty()) {
            return HttpResponse.status(HttpStatus.NOT_FOUND);
        }

        Flow flow = existingFlow.get();
        Task previousTask;
        try {
            previousTask = flow.findTaskByTaskId(taskId);
        } catch (InternalException e) {
            return HttpResponse.status(HttpStatus.NOT_FOUND);
        }

        int index = flow.getTasks().indexOf(previousTask);
        flow.getTasks().set(index, task);

        return HttpResponse.ok(flowRepository.update(flow, flowRepository.findById(namespace, id).get()));
    }


    /**
     * @param namespace flow namespace
     * @param id        flow id to delete
     * @return Http 204 on delete or Http 404 when not found
     */
    @Delete(uri = "{namespace}/{id}", produces = MediaType.TEXT_JSON)
    @ExecuteOn(TaskExecutors.IO)
    public HttpResponse<Void> delete(String namespace, String id) {
        Optional<Flow> flow = flowRepository.findById(namespace, id);
        if (flow.isPresent()) {
            flowRepository.delete(flow.get());
            return HttpResponse.status(HttpStatus.NO_CONTENT);
        } else {
            return HttpResponse.status(HttpStatus.NOT_FOUND);
        }
    }

    /**
     * @return The flow's namespaces set
     */
    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "distinct-namespaces", produces = MediaType.TEXT_JSON)
    public List<String> listDistinctNamespace() {
        return flowRepository.findDistinctNamespace();
    }
}
