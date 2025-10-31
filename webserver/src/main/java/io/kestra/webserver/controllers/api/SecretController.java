package io.kestra.webserver.controllers.api;

import io.kestra.core.models.QueryFilter;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.secret.SecretService;
import io.kestra.core.tenant.TenantService;
import io.kestra.webserver.converters.QueryFilterFormat;
import io.kestra.webserver.models.api.secret.ApiSecretListResponse;
import io.kestra.webserver.models.api.secret.ApiSecretMeta;
import io.kestra.webserver.utils.PageableUtils;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.model.Pageable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.validation.Validated;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.inject.Inject;

import java.io.IOException;
import java.util.List;

@Validated
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
    @Operation(tags = {"Secrets"}, summary = "Search secrets of all namespaces")
    public HttpResponse<ApiSecretListResponse<META>> listSecrets(
        @Parameter(description = "The current page") @QueryValue(value = "page", defaultValue = "1") int page,
        @Parameter(description = "The current page size") @QueryValue(value = "size", defaultValue = "10") int size,
        @Parameter(description = "The sort of current page") @Nullable @QueryValue(value = "sort") List<String> sort,
        @Parameter(description = "Filters") @QueryFilterFormat List<QueryFilter> filters
    ) throws IllegalArgumentException, IOException {
        final String tenantId = this.tenantService.resolveTenant();

        Pageable pageable = PageableUtils.from(page, size, sort, this::sortMapper);

        ArrayListTotal<String> items = secretService.list(pageable, tenantId, filters);
        //noinspection unchecked
        return HttpResponse.ok((ApiSecretListResponse<META>) new ApiSecretListResponse<>(
                true,
                items.map(ApiSecretMeta::new),
                items.getTotal()
            )
        );
    }
}
