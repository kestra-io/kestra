package io.kestra.webserver.controllers.api;

import java.io.IOException;
import java.util.List;

import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.QueryFilter.Resource;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.secret.SecretService;
import io.kestra.core.tenant.TenantService;
import io.kestra.webserver.converters.QueryFilterFormat;
import io.kestra.webserver.models.api.Pagination;
import io.kestra.webserver.models.api.secret.ApiSecretListResponse;
import io.kestra.webserver.models.api.secret.ApiSecretMeta;

import io.micronaut.data.model.Pageable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.RequestBean;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.inject.Inject;
import jakarta.validation.Valid;

@Controller("/api/v1/{tenant}/secrets")
public class SecretController<META extends ApiSecretMeta> {
    @Inject
    protected TenantService tenantService;

    @Inject
    protected SecretService<String> secretService;

    protected String sortMapper(String key) {
        if (key != null && key.equals("key")) {
            return "name";
        }
        return key;
    }

    @Get
    @ExecuteOn(TaskExecutors.IO)
    @Operation(tags = { "Secrets" }, summary = "Search secrets of all namespaces")
    public HttpResponse<ApiSecretListResponse<META>> listSecrets(
        @Valid @RequestBean Pagination paging,
        @Parameter(description = "Filters") @QueryFilterFormat(Resource.SECRET_METADATA) List<QueryFilter> filters) throws IllegalArgumentException, IOException {
        final String tenantId = this.tenantService.resolveTenant();

        Pageable pageable = paging.toPageable(this::sortMapper);

        ArrayListTotal<String> items = secretService.list(pageable, tenantId, filters);
        //noinspection unchecked
        return HttpResponse.ok(
            (ApiSecretListResponse<META>) new ApiSecretListResponse<>(
                true,
                items.map(ApiSecretMeta::new),
                items.getTotal()
            )
        );
    }
}
