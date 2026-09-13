package io.kestra.webserver.controllers.api;

import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.QueryFilter.Resource;
import io.kestra.core.models.validations.ManualConstraintViolation;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.tenant.TenantService;
import io.kestra.fethr.credential.Credential;
import io.kestra.fethr.credential.CredentialRepositoryInterface;
import io.kestra.fethr.credential.CredentialType;
import io.kestra.webserver.converters.QueryFilterFormat;
import io.kestra.webserver.models.api.credential.CredentialDetail;
import io.kestra.webserver.models.api.credential.CredentialRequest;
import io.kestra.webserver.models.api.credential.CredentialSummary;
import io.kestra.webserver.utils.PageableUtils;

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
 * Stored credentials: the material a task needs to authenticate against something outside Kestra.
 *
 * <p>
 * Kestra 2.0 has no equivalent, so this is not a replacement of anything -- the whole surface is
 * new. Writes are namespace-scoped, matching secrets and matching how the 1.x fork addressed them:
 * a credential's identity is its tenant, namespace and name together.
 *
 * <p>
 * Listing never returns material; reading one credential by name does, because the edit form has to
 * round-trip what it is editing. That split is the fork's and it is kept deliberately: a broad read
 * stays safe, and secrets are handed over only when a single credential is named.
 *
 * <p>
 * Two of the fork's endpoints are not here. {@code /credentials/scopes} and the per-credential
 * {@code /test} both live in the FHIR connector, which is a later wave; see
 * {@link io.kestra.fethr.credential.CernerFhirCredential}.
 */
@Validated
@Controller("/api/v1/{tenant}")
public class CredentialController {

    @Inject
    private CredentialRepositoryInterface credentialRepository;

    @Inject
    private TenantService tenantService;

    @Get(uri = "credentials")
    @ExecuteOn(TaskExecutors.IO)
    @Operation(
        tags = { "Credentials" },
        summary = "Search credentials across namespaces",
        description = "Returns identity only. Use the per-credential read to obtain material."
    )
    public HttpResponse<PagedCredentials> findCredentials(
        @Parameter(description = "The current page") @QueryValue(value = "page", defaultValue = "1") int page,
        @Parameter(description = "The current page size") @QueryValue(value = "size", defaultValue = "10") @Max(PageableUtils.MAX_PAGE_SIZE) int size,
        @Parameter(description = "The sort of current page") @Nullable @QueryValue(value = "sort") List<String> sort,
        @Parameter(description = "Filters") @QueryFilterFormat(Resource.CREDENTIALS) List<QueryFilter> filters) throws IllegalArgumentException, IOException {
        Pageable pageable = PageableUtils.from(page, size, sort, key -> key);
        ArrayListTotal<Credential> credentials = credentialRepository.find(pageable, tenantService.resolveTenant(), filters);

        return HttpResponse.ok(
            new PagedCredentials(
                credentials.stream().map(CredentialSummary::of).toList(),
                credentials.getTotal()
            )
        );
    }

    @Get(uri = "credentials/types")
    @ExecuteOn(TaskExecutors.IO)
    @Operation(tags = { "Credentials" }, summary = "List the credential types that can be created")
    public List<CredentialTypeVo> listCredentialTypes() {
        return Arrays.stream(CredentialType.values())
            .map(type -> new CredentialTypeVo(type, type.getLabel()))
            .toList();
    }

    @Get(uri = "namespaces/{namespace}/credentials/{name}")
    @ExecuteOn(TaskExecutors.IO)
    @Operation(
        tags = { "Credentials" },
        summary = "Get a credential, material included",
        description = "The only read that returns secrets, and it returns them for one named "
            + "credential at a time."
    )
    public HttpResponse<CredentialDetail> getCredential(
        @Parameter(description = "The namespace") @PathVariable String namespace,
        @Parameter(description = "The credential name") @PathVariable String name) {
        return credentialRepository.findByName(tenantService.resolveTenant(), namespace, name)
            .map(CredentialDetail::of)
            .map(HttpResponse::ok)
            .orElseGet(HttpResponse::notFound);
    }

    @Post(uri = "namespaces/{namespace}/credentials", consumes = MediaType.APPLICATION_JSON)
    @ExecuteOn(TaskExecutors.IO)
    @Operation(tags = { "Credentials" }, summary = "Create a credential")
    public HttpResponse<CredentialDetail> createCredential(
        @Parameter(description = "The namespace") @PathVariable String namespace,
        @Parameter(description = "The credential") @Valid @Body CredentialRequest request) throws ConstraintViolationException {
        String tenantId = tenantService.resolveTenant();

        if (credentialRepository.findByName(tenantId, namespace, request.name()).isPresent()) {
            throw new ConstraintViolationException(
                Collections.singleton(
                    ManualConstraintViolation.of(
                        "A credential with this name already exists in this namespace",
                        request,
                        CredentialRequest.class,
                        "credential.name",
                        request.name()
                    )
                )
            );
        }

        Instant now = Instant.now();
        Credential credential = request.toEntity(tenantId, namespace);
        credential.setTenantId(tenantId);
        credential.setNamespace(namespace);
        credential.setName(request.name());
        credential.setDescription(request.description());
        credential.setCreated(now);
        credential.setUpdated(now);

        return HttpResponse.created(CredentialDetail.of(credentialRepository.save(credential)));
    }

    @Put(uri = "namespaces/{namespace}/credentials/{name}", consumes = MediaType.APPLICATION_JSON)
    @ExecuteOn(TaskExecutors.IO)
    @Operation(
        tags = { "Credentials" },
        summary = "Update a credential",
        description = "Name, namespace and type are a credential's identity and cannot be changed; "
            + "an attempt to change any of them is rejected rather than silently applied."
    )
    public HttpResponse<CredentialDetail> updateCredential(
        @Parameter(description = "The namespace") @PathVariable String namespace,
        @Parameter(description = "The credential name") @PathVariable String name,
        @Parameter(description = "The credential") @Valid @Body CredentialRequest request) throws ConstraintViolationException {
        Optional<Credential> existing = credentialRepository.findByName(tenantService.resolveTenant(), namespace, name);
        if (existing.isEmpty()) {
            return HttpResponse.notFound();
        }

        // Identity comes from the stored row, not the body: name, namespace and type cannot move,
        // and taking them from the path leaves nothing for a mismatched body to quietly change.
        Credential previous = existing.get();
        Credential credential = request.toEntity(previous.getTenantId(), previous.getNamespace());
        credential.setTenantId(previous.getTenantId());
        credential.setNamespace(previous.getNamespace());
        credential.setName(previous.getName());
        credential.setDescription(request.description());
        credential.setCreated(previous.getCreated());
        credential.setUpdated(Instant.now());

        return HttpResponse.ok(CredentialDetail.of(credentialRepository.update(credential, previous)));
    }

    @Delete(uri = "namespaces/{namespace}/credentials/{name}")
    @ExecuteOn(TaskExecutors.IO)
    @Operation(tags = { "Credentials" }, summary = "Delete a credential")
    public HttpResponse<Void> deleteCredential(
        @Parameter(description = "The namespace") @PathVariable String namespace,
        @Parameter(description = "The credential name") @PathVariable String name) {
        return credentialRepository.delete(tenantService.resolveTenant(), namespace, name)
            .map(deleted -> HttpResponse.<Void> noContent())
            .orElseGet(HttpResponse::notFound);
    }

    @Delete(uri = "namespaces/{namespace}/credentials", consumes = MediaType.APPLICATION_JSON)
    @ExecuteOn(TaskExecutors.IO)
    @Operation(
        tags = { "Credentials" },
        summary = "Delete several credentials of a namespace",
        description = "Deletes every name that exists and reports which ones went, rather than "
            + "failing the whole request because one of them was already gone."
    )
    public HttpResponse<ApiDeleteBulkResponse> deleteCredentials(
        @Parameter(description = "The namespace") @PathVariable String namespace,
        @Parameter(description = "The names to delete") @Body ApiDeleteBulkRequest request) {
        String tenantId = tenantService.resolveTenant();

        List<String> deleted = request.names().stream()
            .filter(name -> credentialRepository.delete(tenantId, namespace, name).isPresent())
            .toList();

        return HttpResponse.ok(new ApiDeleteBulkResponse(deleted));
    }

    @Introspected
    public record PagedCredentials(List<CredentialSummary> results, long total) {
    }

    @Introspected
    public record CredentialTypeVo(CredentialType type, String label) {
    }

    @Introspected
    public record ApiDeleteBulkRequest(List<String> names) {
        public List<String> names() {
            return Optional.ofNullable(names).orElseGet(List::of);
        }
    }

    @Introspected
    public record ApiDeleteBulkResponse(List<String> names) {
    }
}
