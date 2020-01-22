package org.kestra.webserver.controllers;

import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.*;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.validation.Validated;
import io.reactivex.Maybe;
import org.kestra.core.exceptions.InvalidFlowException;
import org.kestra.core.models.flows.Flow;
import org.kestra.core.repositories.FlowRepositoryInterface;
import org.kestra.core.serializers.Validator;
import org.kestra.webserver.responses.FlowResponse;
import org.kestra.webserver.responses.PagedResults;
import org.kestra.webserver.utils.PageableUtils;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Validated
@Controller("/api/v1/flows")
public class FlowController {
    @Inject
    private FlowRepositoryInterface flowRepository;

    /**
     * @param namespace The flow namespace
     * @param id        The flow id
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


    /**
     * @param namespace flow namespace
     * @param id        flow id to update
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
