package io.kestra.webserver.controllers.api;

import io.kestra.core.models.QueryFilter;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.secret.SecretService;
import io.kestra.core.tenant.TenantService;
import io.kestra.webserver.converters.QueryFilterFormat;
import io.kestra.webserver.models.api.secret.ApiSecretListResponse;
import io.kestra.webserver.models.api.secret.ApiSecretMeta;
import io.kestra.webserver.utils.Searcheable;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.validation.Validated;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import jakarta.inject.Inject;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

@Validated
@Controller("/api/v1/{tenant}/namespaces")
public class NamespaceSecretController<META extends ApiSecretMeta> {
    @Inject
    protected TenantService tenantService;

    @Inject
    protected SecretService<String> secretService;

    @Get(uri = "{namespace}/secrets")
    @ExecuteOn(TaskExecutors.IO)
    @Operation(tags = {"Namespaces"}, summary = "Get secrets for a namespace")
    @Deprecated
    public HttpResponse<ApiSecretListResponse<META>> listNamespaceSecrets(
        @Parameter(description = "The namespace id") @PathVariable String namespace,
        @Parameter(description = "The current page") @QueryValue(value = "page", defaultValue = "1") int page,
        @Parameter(description = "The current page size") @QueryValue(value = "size", defaultValue = "10") int size,
        @Parameter(description = "The sort of current page") @Nullable @QueryValue(value = "sort") List<String> sort,
        @Parameter(description = "Filters", in = ParameterIn.QUERY) @QueryFilterFormat List<QueryFilter> filters
    ) throws IllegalArgumentException, IOException {
        final String tenantId = this.tenantService.resolveTenant();
        List<String> items = secretService.inheritedSecrets(tenantId, namespace).get(namespace).stream().toList();

        final String query = filters.stream()
            .filter(filter -> filter.field().equals(QueryFilter.Field.QUERY))
            .map(QueryFilter::value)
            .map(Object::toString)
            .findFirst()
            .orElse(null);

        final ArrayListTotal<String> results = Searcheable.of(items)
            .search(Searcheable.Searched.<String>builder()
                .query(query)
                .size(size)
                .sort(sort)
                .page(page)
                .sortableExtractor("key", Function.identity())
                .searchableExtractor("key", Function.identity())
                .build()
            );

        //noinspection unchecked
        return HttpResponse.ok((ApiSecretListResponse<META>) new ApiSecretListResponse<>(
                true,
                results.map(ApiSecretMeta::new),
                results.getTotal()
            )
        );
    }

    @Get(uri = "{namespace}/inherited-secrets")
    @ExecuteOn(TaskExecutors.IO)
    @Operation(tags = {"Namespaces"}, summary = "List inherited secrets")
    public HttpResponse<Map<String, Set<String>>> getInheritedSecrets(
        @Parameter(description = "The namespace id") @PathVariable String namespace
    ) throws IllegalArgumentException, IOException {
        return HttpResponse.ok(secretService.inheritedSecrets(tenantService.resolveTenant(), namespace));
    }
}
