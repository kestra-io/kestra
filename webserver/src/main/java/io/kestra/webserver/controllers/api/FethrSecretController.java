package io.kestra.webserver.controllers.api;

import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.QueryFilter.Resource;
import io.kestra.core.models.validations.ManualConstraintViolation;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.fethr.secret.CryptographicValue;
import io.kestra.fethr.secret.Secret;
import io.kestra.fethr.secret.SecretRepositoryInterface;
import io.kestra.webserver.converters.QueryFilterFormat;
import io.kestra.webserver.models.api.secret.ApiSecretListResponse;
import io.kestra.webserver.models.api.secret.FethrSecretMeta;
import io.kestra.webserver.utils.PageableUtils;

import io.micronaut.context.annotation.Replaces;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.model.Pageable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.validation.Validated;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;

/**
 * Administration of database-backed secrets.
 *
 * <p>
 * Kestra 2.0's OSS {@link SecretController} lists only, because writing is what its secret-manager
 * implementations supply. This replaces it with the full set over Kestra's own {@code secrets}
 * table, and returns {@link FethrSecretMeta} so the secrets table can render namespace, description
 * and tags rather than bare key names.
 *
 * <p>
 * Addressing is by namespace and key together, because that is a secret's identity: two namespaces
 * may each hold a {@code DB_PASSWORD} and they are different secrets.
 *
 * <p>
 * Reading a value is a separate endpoint from reading metadata, so plaintext leaves the server only
 * when a caller asks for it explicitly and never as a side effect of listing.
 */
@Validated
@Controller("/api/v1/{tenant}/secrets")
@Replaces(SecretController.class)
public class FethrSecretController extends SecretController<FethrSecretMeta> {

    @Inject
    private SecretRepositoryInterface secretRepository;

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

    @Get(uri = "{namespace}/{key}")
    @ExecuteOn(TaskExecutors.IO)
    @Operation(tags = { "Secrets" }, summary = "Get a secret's metadata")
    public HttpResponse<FethrSecretMeta> getSecret(
        @Parameter(description = "The namespace") @PathVariable String namespace,
        @Parameter(description = "The secret key") @PathVariable String key) {
        return secretRepository.findByKey(tenantService.resolveTenant(), namespace, key)
            .map(FethrSecretMeta::of)
            .map(HttpResponse::ok)
            .orElseGet(HttpResponse::notFound);
    }

    @Get(uri = "{namespace}/{key}/value")
    @ExecuteOn(TaskExecutors.IO)
    @Operation(
        tags = { "Secrets" },
        summary = "Reveal a secret's value",
        description = "Returns the plaintext. Separate from the metadata endpoints so a value is "
            + "only ever served to a caller that asked for it."
    )
    public HttpResponse<String> getSecretValue(
        @Parameter(description = "The namespace") @PathVariable String namespace,
        @Parameter(description = "The secret key") @PathVariable String key) {
        return secretRepository.findByKey(tenantService.resolveTenant(), namespace, key)
            .map(Secret::getValue)
            .map(CryptographicValue::content)
            .map(HttpResponse::ok)
            .orElseGet(HttpResponse::notFound);
    }

    @Post(consumes = MediaType.APPLICATION_JSON)
    @ExecuteOn(TaskExecutors.IO)
    @Operation(tags = { "Secrets" }, summary = "Create a secret")
    public HttpResponse<FethrSecretMeta> createSecret(
        @Parameter(description = "The secret") @Valid @Body SecretForm form) throws ConstraintViolationException {
        String tenantId = tenantService.resolveTenant();

        if (secretRepository.findByKey(tenantId, form.namespace(), form.key()).isPresent()) {
            throw new ConstraintViolationException(
                Collections.singleton(
                    ManualConstraintViolation.of(
                        "A secret with this key already exists in this namespace",
                        form,
                        SecretForm.class,
                        "secret.key",
                        form.key()
                    )
                )
            );
        }

        Instant now = Instant.now();
        Secret secret = Secret.builder()
            .tenantId(tenantId)
            .namespace(form.namespace())
            .key(form.key())
            .description(form.description())
            .value(CryptographicValue.builder().content(form.value()).build())
            .tags(Optional.ofNullable(form.tags()).orElseGet(List::of))
            .created(now)
            .updated(now)
            .build();

        return HttpResponse.created(FethrSecretMeta.of(secretRepository.save(secret)));
    }

    @Put(uri = "{namespace}/{key}", consumes = MediaType.APPLICATION_JSON)
    @ExecuteOn(TaskExecutors.IO)
    @Operation(
        tags = { "Secrets" },
        summary = "Update a secret",
        description = "A null value leaves the stored value untouched, so description and tags can "
            + "be edited without the caller having to know the secret."
    )
    public HttpResponse<FethrSecretMeta> updateSecret(
        @Parameter(description = "The namespace") @PathVariable String namespace,
        @Parameter(description = "The secret key") @PathVariable String key,
        @Parameter(description = "The new value, description and tags") @Valid @Body SecretUpdate update) throws ConstraintViolationException {
        Optional<Secret> existing = secretRepository.findByKey(tenantService.resolveTenant(), namespace, key);
        if (existing.isEmpty()) {
            return HttpResponse.notFound();
        }

        Secret previous = existing.get();
        Secret updated = previous.toBuilder()
            .value(
                update.value() == null
                    ? previous.getValue()
                    : CryptographicValue.builder().content(update.value()).build()
            )
            .description(update.description())
            .tags(Optional.ofNullable(update.tags()).orElseGet(List::of))
            .updated(Instant.now())
            .build();

        return HttpResponse.ok(FethrSecretMeta.of(secretRepository.update(updated, previous)));
    }

    @Delete(uri = "{namespace}/{key}")
    @ExecuteOn(TaskExecutors.IO)
    @Operation(tags = { "Secrets" }, summary = "Delete a secret")
    public HttpResponse<Void> deleteSecret(
        @Parameter(description = "The namespace") @PathVariable String namespace,
        @Parameter(description = "The secret key") @PathVariable String key) {
        return secretRepository.delete(tenantService.resolveTenant(), namespace, key)
            .map(deleted -> HttpResponse.<Void> noContent())
            .orElseGet(HttpResponse::notFound);
    }

    @Delete
    @ExecuteOn(TaskExecutors.IO)
    @Operation(
        tags = { "Secrets" },
        summary = "Delete several secrets",
        description = "Deletes every secret that exists and reports which ones were deleted, rather "
            + "than failing the whole request because one of them was already gone."
    )
    public HttpResponse<ApiDeleteBulkResponse> deleteSecrets(
        @Parameter(description = "The secrets to delete") @Body ApiDeleteBulkRequest request) {
        String tenantId = tenantService.resolveTenant();

        List<SecretId> deleted = request.secrets().stream()
            .filter(id -> secretRepository.delete(tenantId, id.namespace(), id.key()).isPresent())
            .toList();

        return HttpResponse.ok(new ApiDeleteBulkResponse(deleted));
    }

    /**
     * A secret's address: namespace and key together.
     */
    @Introspected
    public record SecretId(String namespace, String key) {
    }

    @Introspected
    public record SecretForm(String namespace, String key, String value, String description, List<Secret.Tag> tags) {
    }

    @Introspected
    public record SecretUpdate(String value, String description, List<Secret.Tag> tags) {
    }

    @Introspected
    public record ApiDeleteBulkRequest(List<SecretId> secrets) {
        public List<SecretId> secrets() {
            return Optional.ofNullable(secrets).orElseGet(List::of);
        }
    }

    @Introspected
    public record ApiDeleteBulkResponse(List<SecretId> secrets) {
    }
}
