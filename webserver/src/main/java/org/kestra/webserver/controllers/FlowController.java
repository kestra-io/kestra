package org.kestra.webserver.controllers;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.*;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.validation.Validated;
import io.reactivex.Maybe;
import org.kestra.core.exceptions.IllegalVariableEvaluationException;
import org.kestra.core.models.flows.Flow;
import org.kestra.core.models.hierarchies.FlowTree;
import org.kestra.core.repositories.FlowRepositoryInterface;
import org.kestra.webserver.responses.PagedResults;
import org.kestra.webserver.utils.PageableUtils;

import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.validation.ConstraintViolationException;

import static org.kestra.core.utils.Rethrow.throwFunction;

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
    @Get(uri = "{namespace}/{id}/tree", produces = MediaType.TEXT_JSON)
    public FlowTree flowTree(String namespace, String id) throws IllegalVariableEvaluationException {
        return flowRepository
            .findById(namespace, id)
            .map(throwFunction(FlowTree::of))
            .orElse(null);
    }

    /**
     * @param namespace The flow namespace
     * @param id        The flow id
     * @return flow found
     */
    @Get(uri = "{namespace}/{id}", produces = MediaType.TEXT_JSON)
    public Flow index(String namespace, String id) {
        return flowRepository
            .findById(namespace, id)
            .orElse(null);
    }

    /**
     * @param query The flow query that is a lucen string
     * @param page  Page in flow pagination
     * @param size  Element count in pagination selection
     * @return flow list
     */
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
     * @param flow The flow content
     * @return flow created
     */
    @Post(produces = MediaType.TEXT_JSON)
    public HttpResponse<Flow> create(@Body Flow flow) throws ConstraintViolationException {
        if (flowRepository.exists(flow).isPresent()) {
            return HttpResponse.status(HttpStatus.CONFLICT, "Flow already exists");
        }

        return HttpResponse.ok(flowRepository.create(flow));
    }

    /**
     * @param namespace flow namespace
     * @param id        flow id to update
     * @return flow updated
     */
    @Put(uri = "{namespace}/{id}", produces = MediaType.TEXT_JSON)
    public HttpResponse<Flow> update(String namespace, String id, @Body Flow flow) throws ConstraintViolationException {
        Optional<Flow> existingFlow = flowRepository.findById(namespace, id);

        if (existingFlow.isEmpty()) {
            return HttpResponse.status(HttpStatus.NOT_FOUND);
        }

        return HttpResponse.ok(flowRepository.update(flow, existingFlow.get()));
    }

    /**
     * @param namespace flow namespace
     * @param id        flow id to delete
     * @return Http 204 on delete or Http 404 when not found
     */
    @Delete(uri = "{namespace}/{id}", produces = MediaType.TEXT_JSON)
    public HttpResponse<Void> delete(String namespace, String id) {
        Optional<Flow> flow = flowRepository.findById(namespace, id);
        if (flow.isPresent()) {
            flowRepository.delete(flow.get());
            return HttpResponse.status(HttpStatus.NO_CONTENT);
        } else {
            return HttpResponse.status(HttpStatus.NOT_FOUND);
        }
    }
}
