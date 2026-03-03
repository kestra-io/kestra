package io.kestra.webserver.controllers.api;

import io.kestra.core.repositories.ServiceInstanceRepositoryInterface;
import io.kestra.core.server.*;
import io.micronaut.context.annotation.Requires;
import io.micronaut.data.model.Pageable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.*;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.inject.Inject;

import java.util.Set;
import java.util.stream.Collectors;

@Controller("/api/v1/{tenant}/cluster")
@Requires(bean = ServiceInstanceRepositoryInterface.class)
public class ClusterController {

    private final ServiceInstanceRepositoryInterface repository;

    @Inject
    public ClusterController(final ServiceInstanceRepositoryInterface repository) {
        this.repository = repository;
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get("services/{id}")
    @Operation(tags = {"Services"}, summary = "Get details about a service")
    public HttpResponse<ServiceInstance> getService(@PathVariable("id") String id) throws HttpStatusException {
        return repository.findById(id)
            .map(HttpResponse::ok)
            .orElseGet(HttpResponse::notFound);
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get("/metrics/{serviceType}")
    @Operation(tags = {"Services"}, summary = "Get metrics for running services")
    public Set<Metric> metrics(@QueryValue ServiceType serviceType) {
        return repository.find(
                Pageable.unpaged(),
                Service.ServiceState.allRunningStates(),
                Set.of(serviceType)
            ).stream()
            .map(ServiceInstance::metrics)
            .flatMap(Set::stream)
            .collect(Collectors.toSet());
    }
}
