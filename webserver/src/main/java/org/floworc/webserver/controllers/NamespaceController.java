package org.floworc.webserver.controllers;

import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.validation.Validated;
import org.floworc.core.repositories.ArrayListTotal;
import org.floworc.core.repositories.FlowRepositoryInterface;
import org.floworc.webserver.responses.PagedResults;

import javax.inject.Inject;
import java.util.List;
import java.util.Optional;

@Validated
@Controller("/api/v1/namespaces")
public class NamespaceController {
    @Inject
    private FlowRepositoryInterface flowRepository;

    /**
     * @param prefix The searched namespace prefix
     * @return The flow's namespaces set
     */
    @Get(produces = MediaType.TEXT_JSON)
    public List<String> listDistinctNamespace(Optional<String> prefix) {
        return flowRepository.findDistinctNamespace(prefix);
    }
}
