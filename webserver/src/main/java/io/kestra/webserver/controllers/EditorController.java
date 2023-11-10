package io.kestra.webserver.controllers;

import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.services.StorageInterfaceService;
import io.kestra.core.tenant.TenantService;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.validation.Validated;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;
import java.util.stream.Stream;

@Slf4j
@Validated
@Controller("/api/v1/editors")
public class EditorController {
    @Inject
    StorageInterfaceService storageInterfaceService;
    @Inject
    FlowRepositoryInterface flowRepository;
    @Inject
    TenantService tenantService;

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "/distinct-namespaces", produces = MediaType.TEXT_JSON)
    @Operation(tags = {"Editor"}, summary = "Get namespaces that contains files or flows")
    public List<String> distinctNamespaces() throws IOException, URISyntaxException {
        return Stream.concat(
            storageInterfaceService.distinctNamespacesFolders(tenantService.resolveTenant()).stream(),
            flowRepository.findDistinctNamespace(tenantService.resolveTenant()).stream()
        ).distinct().toList();
    }
}
