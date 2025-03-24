package io.kestra.webserver.controllers.api;

import io.kestra.core.secret.SecretService;
import io.kestra.core.tenant.TenantService;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.validation.Validated;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.inject.Inject;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

@Validated
@Controller("/api/v1{/tenant}/namespaces")
public class NamespaceSecretController {
    @Inject
    protected TenantService tenantService;

    @Inject
    protected SecretService secretService;

    @Get(uri = "{namespace}/inherited-secrets")
    @ExecuteOn(TaskExecutors.IO)
    @Operation(tags = {"Namespaces"}, summary = "List inherited secrets")
    public HttpResponse<Map<String, Set<String>>> inheritedSecrets(
        @Parameter(description = "The namespace id") @PathVariable String namespace
    ) throws IllegalArgumentException, IOException {
        return HttpResponse.ok(secretService.inheritedSecrets(tenantService.resolveTenant(), namespace));
    }
}
