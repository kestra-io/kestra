package io.kestra.webserver.controllers.api;

import io.kestra.core.models.namespaces.NamespaceInterface;
import io.kestra.core.models.topologies.FlowTopologyGraph;
import io.kestra.webserver.models.namespaces.DisabledInterface;
import io.kestra.webserver.responses.PagedResults;
import io.micronaut.http.exceptions.HttpStatusException;

import java.util.List;

public interface NamespaceControllerInterface<N extends NamespaceInterface, D extends NamespaceInterface & DisabledInterface> {
    N index(String id);

    PagedResults<D> find(String query, int page, int size, List<String> sort, Boolean existingOnly) throws HttpStatusException;

    FlowTopologyGraph dependencies(String namespace, boolean destinationOnly);
}
