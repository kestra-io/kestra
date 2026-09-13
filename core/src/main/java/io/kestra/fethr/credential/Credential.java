package io.kestra.fethr.credential;

import java.time.Instant;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

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
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

/**
 * A stored credential: the material a task needs to authenticate against something outside Kestra.
 *
 * <p>
 * The subtype carries the material and the base carries the identity, so every credential is
 * addressed the same way -- tenant, namespace, name -- whatever it holds. The {@code type}
 * discriminator is persisted with the row, which is why {@link CredentialType} values are fixed.
 *
 * <p>
 * A credential on a parent namespace is inherited by flows beneath it; see
 * {@link CredentialFunction} for why that differs from how secrets resolve.
 */
@Getter
@Setter
@SuperBuilder(toBuilder = true)
@Introspected
@ToString(onlyExplicitlyIncluded = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = "type",
    visible = true
)
@JsonSubTypes(
    {
        @JsonSubTypes.Type(value = ApiKeyCredential.class, name = "API_KEY"),
        @JsonSubTypes.Type(value = OAuth2Credential.class, name = "OAUTH2"),
        @JsonSubTypes.Type(value = BearerTokenCredential.class, name = "BEARER_TOKEN"),
        @JsonSubTypes.Type(value = BasicAuthCredential.class, name = "BASIC_AUTH"),
        @JsonSubTypes.Type(value = CernerFhirCredential.class, name = "CERNER_FHIR")
    }
)
public abstract class Credential implements SoftDeletable<Credential>, TenantInterface, HasUID {

    @Hidden
    @Pattern(regexp = "^[a-z0-9][a-z0-9_-]*")
    private String tenantId;

    @Hidden
    @ToString.Include
    @Pattern(regexp = "^[a-z0-9][a-z0-9._-]*")
    private String namespace;

    @NotBlank
    @ToString.Include
    private String name;

    private String description;

    @NotNull
    @ToString.Include
    private CredentialType type;

    @Hidden
    @NotNull
    @Builder.Default
    private boolean deleted = false;

    @Hidden
    private Instant created;

    @Hidden
    private Instant updated;

    /**
     * A credential is addressed by tenant, namespace and name, so those three make its identity.
     */
    @Override
    public String uid() {
        return String.join("_", Optional.ofNullable(tenantId).orElse(""), namespace, name);
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * Returns a deleted copy rather than mutating in place. The 1.x fork set the flag on
     * {@code this} and returned it, which left the caller's own reference deleted too -- fine where
     * it was only ever called on a freshly loaded row, but not a contract anything should rely on.
     *
     * <p>
     * Abstract because Lombok puts {@code toBuilder()} on the concrete subtypes, not here: only a
     * subtype knows the builder that reproduces all of its fields. Each one hands that builder to
     * {@link #markDeleted}, so what "deleted" means is still decided in one place.
     */
    @Override
    public abstract Credential toDeleted();

    /**
     * Stamps a builder as deleted. The single definition of what deletion does to a credential,
     * shared by every subtype's {@link #toDeleted()}.
     */
    protected static Credential markDeleted(CredentialBuilder<?, ?> builder) {
        builder.deleted(true);
        builder.updated(Instant.now());
        return builder.build();
    }

    /**
     * Rejects an update that would change what a credential <em>is</em> rather than what it holds.
     * Name and namespace are its address, and type decides which material it carries; changing any
     * of them silently would orphan whatever referenced it.
     */
    public Optional<ConstraintViolationException> validateUpdate(Credential updated) {
        Set<ConstraintViolation<?>> violations = new HashSet<>();

        if (!Objects.equals(updated.getName(), this.getName())) {
            violations.add(
                ManualConstraintViolation.of(
                    "Illegal credential name update",
                    updated,
                    Credential.class,
                    "credential.name",
                    updated.getName()
                )
            );
        }

        if (!Objects.equals(updated.getNamespace(), this.getNamespace())) {
            violations.add(
                ManualConstraintViolation.of(
                    "Illegal namespace update",
                    updated,
                    Credential.class,
                    "credential.namespace",
                    updated.getNamespace()
                )
            );
        }

        if (updated.getType() != this.getType()) {
            violations.add(
                ManualConstraintViolation.of(
                    "Illegal type update",
                    updated,
                    Credential.class,
                    "credential.type",
                    updated.getType()
                )
            );
        }

        return violations.isEmpty() ? Optional.empty() : Optional.of(new ConstraintViolationException(violations));
    }
}
