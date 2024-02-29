package io.kestra.webserver.controllers;

import io.kestra.core.repositories.WorkerInstanceRepositoryInterface;
import io.kestra.core.runners.WorkerInstance;
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
@Requires(bean = WorkerInstanceRepositoryInterface.class)
public class WorkerInstanceController {
    @Inject
    private WorkerInstanceRepositoryInterface workerInstanceRepositoryInterface;

    @ExecuteOn(TaskExecutors.IO)
    @Get
    @Operation(tags = {"Workers"}, summary = "Get all workers")
    public List<WorkerInstance> findAll() throws HttpStatusException {
        return workerInstanceRepositoryInterface.findAll();
    }
}