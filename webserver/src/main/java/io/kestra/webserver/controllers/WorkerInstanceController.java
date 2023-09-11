package io.kestra.webserver.controllers;

import io.kestra.core.repositories.WorkerHeartbeatRepositoryInterface;
import io.kestra.core.runners.WorkerHeartbeat;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.inject.Inject;

import java.util.List;

@Controller("/api/v1/workers")
@Requires(bean = WorkerHeartbeatRepositoryInterface.class)
public class WorkerInstanceController {
    @Inject
    private WorkerHeartbeatRepositoryInterface workerHeartbeatRepositoryInterface;

    @ExecuteOn(TaskExecutors.IO)
    @Get(produces = MediaType.TEXT_JSON)
    @Operation(tags = {"Workers"}, summary = "Get all workers")
    public List<WorkerHeartbeat> findAll() throws HttpStatusException {
        return workerHeartbeatRepositoryInterface.findAll();
    }
}