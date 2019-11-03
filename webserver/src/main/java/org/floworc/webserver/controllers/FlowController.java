package org.floworc.webserver.controllers;

import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.validation.Validated;
import io.reactivex.Maybe;
import org.floworc.core.models.flows.Flow;
import org.floworc.core.repositories.FlowRepositoryInterface;

import javax.inject.Inject;

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
}
