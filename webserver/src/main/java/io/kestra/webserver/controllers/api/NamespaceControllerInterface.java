package io.kestra.webserver.controllers.api;

import io.kestra.core.models.namespaces.NamespaceInterface;
import io.kestra.core.models.topologies.FlowTopologyGraph;
import io.kestra.webserver.models.namespaces.DisabledInterface;
import io.kestra.webserver.responses.PagedResults;
import io.micronaut.http.exceptions.HttpStatusException;
import jakarta.validation.constraints.Min;

import java.util.List;

public interface NamespaceControllerInterface<N extends NamespaceInterface, D extends NamespaceInterface & DisabledInterface> {
    N index(String id);

    PagedResults<D> find(String query, @Min(1) int page, @Min(1) int size, List<String> sort, Boolean existingOnly) throws HttpStatusException;

    FlowTopologyGraph dependencies(String namespace, boolean destinationOnly);
}
