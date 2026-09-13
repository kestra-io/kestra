package io.kestra.webserver.controllers.api;

import java.io.IOException;
import java.util.List;

import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.QueryFilter.Resource;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.fethr.auth.Permission;
import io.kestra.fethr.secret.Secret;
import io.kestra.fethr.secret.SecretRepositoryInterface;
import io.kestra.webserver.converters.QueryFilterFormat;
import io.kestra.webserver.models.api.secret.ApiSecretListResponse;
import io.kestra.webserver.models.api.secret.FethrSecretMeta;
import io.kestra.webserver.utils.PageableUtils;

import io.micronaut.context.annotation.Replaces;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.model.Pageable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.security.annotation.Secured;
import io.micronaut.validation.Validated;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.inject.Inject;
import jakarta.validation.constraints.Max;

/**
 * Lists database-backed secrets across namespaces.
 *
 * <p>
 * Kestra 2.0's OSS {@link SecretController} lists the {@code SECRET_*} environment variables and
 * reports itself read-only, because storing secrets is what its secret-manager implementations
 * supply. This replaces the listing with one over Kestra's own {@code secrets} table, returning
 * {@link FethrSecretMeta} so the secrets table can render namespace, description and tags rather
 * than bare key names.
 *
 * <p>
 * Writing lives on {@link FethrNamespaceSecretController}, under the namespace that owns the
 * secret, which is where the secrets table already sends its create, update and delete calls.
 */
@Validated
@Controller("/api/v1/{tenant}/secrets")
@Replaces(SecretController.class)
public class FethrSecretController extends SecretController<FethrSecretMeta> {

    @Inject
    private SecretRepositoryInterface secretRepository;

    @Secured(Permission.Names.SECRET_READ)
    @Get
    @ExecuteOn(TaskExecutors.IO)
    @Operation(tags = { "Secrets" }, summary = "Search secrets of all namespaces")
    @Override
    public HttpResponse<ApiSecretListResponse<FethrSecretMeta>> listSecrets(
        @Parameter(description = "The current page") @QueryValue(value = "page", defaultValue = "1") int page,
        @Parameter(description = "The current page size") @QueryValue(value = "size", defaultValue = "10") @Max(PageableUtils.MAX_PAGE_SIZE) int size,
        @Parameter(description = "The sort of current page") @Nullable @QueryValue(value = "sort") List<String> sort,
        @Parameter(description = "Filters") @QueryFilterFormat(Resource.SECRET_METADATA) List<QueryFilter> filters) throws IllegalArgumentException, IOException {
        Pageable pageable = PageableUtils.from(page, size, sort, this::sortMapper);
        ArrayListTotal<Secret> secrets = secretRepository.find(pageable, tenantService.resolveTenant(), filters);

        return HttpResponse.ok(
            new ApiSecretListResponse<>(
                false,
                secrets.stream().map(FethrSecretMeta::of).toList(),
                secrets.getTotal()
            )
        );
    }
}
