package io.kestra.webserver.controllers;

import io.kestra.core.repositories.ServiceInstanceRepositoryInterface;
import io.kestra.core.server.ServerInstance;
import io.kestra.core.server.Service;
import io.kestra.core.server.ServiceInstance;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.List;

import static io.kestra.core.server.Service.ServiceState.EMPTY;
import static io.kestra.core.server.Service.ServiceState.NOT_RUNNING;

@Controller("/api/v1/cluster")
@Requires(bean = ServiceInstanceRepositoryInterface.class)
public class ClusterController {

    private final ServiceInstanceRepositoryInterface repository;

    @Inject
    public ClusterController(final ServiceInstanceRepositoryInterface repository) {
        this.repository = repository;
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get("services")
    @Operation(tags = {"Services"}, summary = "Get all the services for teh Kestra cluster")
    public HttpResponse<List<ApiServiceInstance>> findAll() throws HttpStatusException {
        List<ServiceInstance> instances = repository.findAll();
        List<ApiServiceInstance> entities = instances.stream()
            .filter(it -> {
                Service.ServiceState state = it.state();
                return !(state.equals(EMPTY) || state.equals(NOT_RUNNING));
            })
            .map(it -> new ApiServiceInstance(
                    it.id(),
                    it.type(),
                    it.state(),
                    new ApiServerInstance(
                        it.server().id(),
                        it.server().type(),
                        it.server().version(),
                        it.server().hostname()
                    ),
                    it.createdAt(),
                    it.updatedAt()
                )
            ).toList();
        return HttpResponse.ok(entities);
    }

    public record ApiServerInstance(
        String id,
        ServerInstance.Type type,
        String version,
        String hostname
    ){}

    public record ApiServiceInstance(
        String id,
        Service.ServiceType type,
        Service.ServiceState state,
        ApiServerInstance server,
        Instant createdAt,
        Instant updatedAt
    ){}
}