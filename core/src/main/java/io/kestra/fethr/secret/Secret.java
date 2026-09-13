package io.kestra.fethr.secret;

import io.kestra.fethr.vault.CryptographicValue;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import io.kestra.core.models.HasUID;
import io.kestra.core.models.SoftDeletable;
import io.kestra.core.models.TenantInterface;
import io.kestra.core.models.validations.ManualConstraintViolation;

import io.micronaut.core.annotation.Introspected;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

/**
 * A tenant- and namespace-scoped secret held in Kestra's own database.
 *
 * <p>
 * Namespace is significant: resolution is an exact match, so a secret stored in one namespace is
 * not visible to a flow in another. See {@link FethrSecretService}.
 *
 * <p>
 * {@code toString} is Lombok-generated over the annotated fields only, and {@link #value} is
 * excluded from it so a secret's plaintext never reaches a log line through an accidental
 * interpolation.
 */
@SuperBuilder(toBuilder = true)
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Introspected
@ToString(exclude = "value")
public class Secret implements SoftDeletable<Secret>, TenantInterface, HasUID {

    @Setter
    @Hidden
    @Pattern(regexp = "^[a-z0-9][a-z0-9_-]*")
    private String tenantId;

    @Setter
    @Hidden
    @Pattern(regexp = "^[a-z0-9][a-z0-9._-]*")
    private String namespace;

    @NotBlank
    private String key;

    private String description;

    @NotNull
    private CryptographicValue value;

    /**
     * Free-form labels, mirroring the tags 2.0's secrets table renders and filters on.
     */
    @Builder.Default
    private List<Tag> tags = List.of();

    @Hidden
    @NotNull
    @Builder.Default
    private boolean deleted = false;

    @Hidden
    private Instant created;

    @Hidden
    private Instant updated;

    /**
     * A secret is addressed by tenant, namespace and key, so those three make its identity. The
     * value is deliberately not part of it: rotating a secret updates the same row.
     */
    @Override
    public String uid() {
        return String.join("_", Optional.ofNullable(tenantId).orElse(""), namespace, key);
    }

    @Override
    public Secret toDeleted() {
        return this.toBuilder()
            .deleted(true)
            .updated(Instant.now())
            .build();
    }

    /**
     * Rejects an update that would move a secret to another key or namespace. Either is really a
     * different secret, and allowing it silently would orphan whatever referenced the old address.
     */
    public Optional<ConstraintViolationException> validateUpdate(Secret updated) {
        Set<ConstraintViolation<?>> violations = new HashSet<>();

        if (!Objects.equals(updated.getKey(), this.getKey())) {
            violations.add(
                ManualConstraintViolation.of(
                    "Illegal secret key update",
                    updated,
                    Secret.class,
                    "secret.key",
                    updated.getKey()
                )
            );
        }

        if (!Objects.equals(updated.getNamespace(), this.getNamespace())) {
            violations.add(
                ManualConstraintViolation.of(
                    "Illegal namespace update",
                    updated,
                    Secret.class,
                    "secret.namespace",
                    updated.getNamespace()
                )
            );
        }

        return violations.isEmpty() ? Optional.empty() : Optional.of(new ConstraintViolationException(violations));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Secret secret = (Secret) o;
        return deleted == secret.deleted
            && Objects.equals(tenantId, secret.tenantId)
            && Objects.equals(namespace, secret.namespace)
            && Objects.equals(key, secret.key)
            && Objects.equals(value, secret.value)
            && Objects.equals(tags, secret.tags);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tenantId, namespace, key, description, value, tags, deleted);
    }

    /**
     * A single key/value label on a secret.
     */
    @Introspected
    public record Tag(String key, String value) {
    }
}
