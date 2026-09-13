package io.kestra.webserver.controllers.api;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import io.kestra.fethr.secret.CryptographicValue;
import io.kestra.fethr.secret.Secret;
import io.kestra.fethr.secret.SecretRepositoryInterface;
import io.kestra.webserver.models.api.secret.FethrSecretMeta;

import io.micronaut.context.annotation.Replaces;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Patch;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Put;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.validation.Validated;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.inject.Inject;
import jakarta.validation.Valid;

/**
 * Writing secrets, under the namespace that owns them.
 *
 * <p>
 * These are the paths 2.0's secrets table already calls, through
 * {@code namespacesStore.createSecrets}, {@code patchSecret} and {@code deleteSecrets}. OSS ships
 * no implementation of them -- writing is what its secret-manager implementations supply -- so the
 * screen is fully built but inert. Implementing the endpoints it already targets, rather than
 * inventing new ones, is what lets that screen work unchanged.
 *
 * <p>
 * Two endpoints go beyond what the OSS screen calls, both from the 1.x fork: revealing a value, and
 * deleting several secrets at once.
 */
@Validated
@Controller("/api/v1/{tenant}/namespaces")
@Replaces(NamespaceSecretController.class)
public class FethrNamespaceSecretController<META extends io.kestra.webserver.models.api.secret.ApiSecretMeta>
    extends NamespaceSecretController<META> {

    @Inject
    private SecretRepositoryInterface secretRepository;

    /**
     * Creates a secret, or replaces the value of one that exists.
     *
     * <p>
     * Upsert rather than create-only because that is what the secrets table expects: it sends the
     * same call whether the drawer was opened through "add" or through "update with a new value".
     */
    @Put(uri = "{namespace}/secrets", consumes = MediaType.APPLICATION_JSON)
    @ExecuteOn(TaskExecutors.IO)
    @Operation(tags = { "Secrets" }, summary = "Create or replace a secret")
    public HttpResponse<FethrSecretMeta> createSecret(
        @Parameter(description = "The namespace") @PathVariable String namespace,
        @Parameter(description = "The secret") @Valid @Body SecretForm form) {
        String tenantId = tenantService.resolveTenant();
        Instant now = Instant.now();

        Optional<Secret> existing = secretRepository.findByKey(tenantId, namespace, form.key());

        if (existing.isPresent()) {
            Secret previous = existing.get();
            Secret updated = previous.toBuilder()
                .value(CryptographicValue.builder().content(form.value()).build())
                .description(form.description())
                .tags(tagsOrEmpty(form.tags()))
                .updated(now)
                .build();

            return HttpResponse.ok(FethrSecretMeta.of(secretRepository.update(updated, previous)));
        }

        Secret created = Secret.builder()
            .tenantId(tenantId)
            .namespace(namespace)
            .key(form.key())
            .description(form.description())
            .value(CryptographicValue.builder().content(form.value()).build())
            .tags(tagsOrEmpty(form.tags()))
            .created(now)
            .updated(now)
            .build();

        return HttpResponse.created(FethrSecretMeta.of(secretRepository.save(created)));
    }

    /**
     * Edits a secret's description and tags, leaving its value untouched.
     *
     * <p>
     * Separate from the upsert above so someone can relabel a secret without knowing it, which is
     * exactly the distinction the secrets table draws between editing a secret and rotating it.
     */
    @Patch(uri = "{namespace}/secrets", consumes = MediaType.APPLICATION_JSON)
    @ExecuteOn(TaskExecutors.IO)
    @Operation(tags = { "Secrets" }, summary = "Update a secret's description and tags")
    public HttpResponse<FethrSecretMeta> patchSecret(
        @Parameter(description = "The namespace") @PathVariable String namespace,
        @Parameter(description = "The metadata to write") @Valid @Body SecretPatch patch) {
        Optional<Secret> existing = secretRepository.findByKey(tenantService.resolveTenant(), namespace, patch.key());
        if (existing.isEmpty()) {
            return HttpResponse.notFound();
        }

        Secret previous = existing.get();
        Secret updated = previous.toBuilder()
            .description(patch.description())
            .tags(tagsOrEmpty(patch.tags()))
            .updated(Instant.now())
            .build();

        return HttpResponse.ok(FethrSecretMeta.of(secretRepository.update(updated, previous)));
    }

    @Delete(uri = "{namespace}/secrets/{key}")
    @ExecuteOn(TaskExecutors.IO)
    @Operation(tags = { "Secrets" }, summary = "Delete a secret")
    public HttpResponse<Void> deleteSecret(
        @Parameter(description = "The namespace") @PathVariable String namespace,
        @Parameter(description = "The secret key") @PathVariable String key) {
        return secretRepository.delete(tenantService.resolveTenant(), namespace, key)
            .map(deleted -> HttpResponse.<Void> noContent())
            .orElseGet(HttpResponse::notFound);
    }

    @Delete(uri = "{namespace}/secrets", consumes = MediaType.APPLICATION_JSON)
    @ExecuteOn(TaskExecutors.IO)
    @Operation(
        tags = { "Secrets" },
        summary = "Delete several secrets of a namespace",
        description = "Deletes every key that exists and reports which ones went, rather than "
            + "failing the whole request because one of them was already gone."
    )
    public HttpResponse<ApiDeleteBulkResponse> deleteSecrets(
        @Parameter(description = "The namespace") @PathVariable String namespace,
        @Parameter(description = "The keys to delete") @Body ApiDeleteBulkRequest request) {
        String tenantId = tenantService.resolveTenant();

        List<String> deleted = request.keys().stream()
            .filter(key -> secretRepository.delete(tenantId, namespace, key).isPresent())
            .toList();

        return HttpResponse.ok(new ApiDeleteBulkResponse(deleted));
    }

    /**
     * Returns a secret's plaintext.
     *
     * <p>
     * Its own endpoint, so a value is served only to a caller that asked for it and never as a side
     * effect of listing or of reading metadata.
     */
    @Get(uri = "{namespace}/secrets/{key}/value")
    @ExecuteOn(TaskExecutors.IO)
    @Operation(tags = { "Secrets" }, summary = "Reveal a secret's value")
    public HttpResponse<String> getSecretValue(
        @Parameter(description = "The namespace") @PathVariable String namespace,
        @Parameter(description = "The secret key") @PathVariable String key) {
        return secretRepository.findByKey(tenantService.resolveTenant(), namespace, key)
            .map(Secret::getValue)
            .map(CryptographicValue::content)
            .map(HttpResponse::ok)
            .orElseGet(HttpResponse::notFound);
    }

    private static List<Secret.Tag> tagsOrEmpty(List<Secret.Tag> tags) {
        return tags == null ? List.of() : tags;
    }

    @Introspected
    public record SecretForm(String key, String value, String description, List<Secret.Tag> tags) {
    }

    @Introspected
    public record SecretPatch(String key, String description, List<Secret.Tag> tags) {
    }

    @Introspected
    public record ApiDeleteBulkRequest(List<String> keys) {
        public List<String> keys() {
            return Optional.ofNullable(keys).orElseGet(List::of);
        }
    }

    @Introspected
    public record ApiDeleteBulkResponse(List<String> keys) {
    }
}
