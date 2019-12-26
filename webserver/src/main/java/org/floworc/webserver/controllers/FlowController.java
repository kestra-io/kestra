package org.floworc.webserver.controllers;

import io.micronaut.data.model.Pageable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.*;
import io.micronaut.validation.Validated;
import io.reactivex.Maybe;
import org.floworc.core.exceptions.InvalidFlowException;
import org.floworc.core.models.flows.Flow;
import org.floworc.core.repositories.FlowRepositoryInterface;
import org.floworc.core.serializers.Validator;
import org.floworc.webserver.responses.FlowResponse;
import org.floworc.webserver.responses.PagedResults;

import javax.inject.Inject;
import java.util.Optional;

@Validated
@Controller("/api/v1/flows")
public class FlowController {
    @Inject
    private FlowRepositoryInterface flowRepository;

    /**
     * @param namespace The flow namespace
     * @param id The flow id
     * @return flow found
     */
    @Get(uri = "{namespace}/{id}", produces = MediaType.TEXT_JSON)
    public Maybe<Flow> index(String namespace, String id) {
        return flowRepository
            .findById(namespace, id)
            .map(Maybe::just)
            .orElse(Maybe.empty());
    }

    /**
     * @param namespace The flow namespace
     * @return flow list
     */
    @Get(uri = "{namespace}", produces = MediaType.TEXT_JSON)
    public PagedResults<Flow> findByNamespace(String namespace, @QueryValue(value = "page", defaultValue = "1") int page, @QueryValue(value = "size", defaultValue = "10") int size) {
        return PagedResults.of(flowRepository.findByNamespace(namespace, Pageable.from(page, size)));
    }

    /**
     * @param query The flow query that is a lucen string
     * @param page Page in flow pagination
     * @param size Element count in pagination selection
     * @return flow list
     */
    @Get(uri = "/search",produces = MediaType.TEXT_JSON)
    public PagedResults<Flow> find(@QueryValue(value = "q") String query, @QueryValue(value = "page", defaultValue = "1") int page, @QueryValue(value = "size", defaultValue = "10") int size) {
        return PagedResults.of(flowRepository.find(query, Pageable.from(page, size)));
    }


    /**
     * @param flow The flow content
     * @return flow created
     */

    @Post(produces = MediaType.TEXT_JSON)
    public HttpResponse<FlowResponse> create(@Body Flow flow) {
        if (flowRepository.exists(flow).isPresent()) {
            return HttpResponse.status(HttpStatus.CONFLICT, "Flow already exists");
        }
        try {
            Validator.isValid(flow);
            return HttpResponse.ok(FlowResponse.of(flowRepository.save(flow)));
        } catch (InvalidFlowException error) {
            return HttpResponse.badRequest(FlowResponse.of(flowRepository.save(flow), error.getViolations()));
        }
    }

    /**
     * @param namespace flow namespace
     * @param id flow id to delete
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


    /**
     * @param namespace flow namespace
     * @param id flow id to update
     * @return flow updated
     */
    @Put(uri = "{namespace}/{id}", produces = MediaType.TEXT_JSON)
    public HttpResponse<Flow> update(String namespace, String id, @Body Flow flow) {
        Optional<Flow> existingFlow = flowRepository.findById(namespace, id);
        if (existingFlow.isPresent()) {
            return HttpResponse.ok(flowRepository.save(flow));
        } else {
            return HttpResponse.status(HttpStatus.NOT_FOUND);
        }
    }
}
