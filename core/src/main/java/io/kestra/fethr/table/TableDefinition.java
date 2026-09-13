package io.kestra.fethr.table;

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
 * The schema of a user-defined table.
 *
 * <p>
 * Persisted as the JSON value of one row in the {@code tables} registry, the same way secrets and
 * credentials are. The rows themselves live somewhere else entirely: in a physical table named by
 * {@link #physicalTableName}, derived from the tenant, namespace and name and never user-supplied.
 * That separation is what keeps a user able to name a table anything without naming a real one.
 */
@SuperBuilder(toBuilder = true)
@Getter
@AllArgsConstructor
@NoArgsConstructor
@Introspected
@ToString
public class TableDefinition implements SoftDeletable<TableDefinition>, TenantInterface, HasUID {

    @Setter
    @Hidden
    @Pattern(regexp = "^[a-z0-9][a-z0-9_-]*")
    private String tenantId;

    @Setter
    @Hidden
    @Pattern(regexp = "^[a-z0-9][a-z0-9._-]*")
    private String namespace;

    @NotBlank
    private String name;

    private String description;

    @NotNull
    private ColumnDataType primaryKeyType;

    @Setter
    @Hidden
    private String physicalTableName;

    @Builder.Default
    private List<ColumnDefinition> columns = List.of();

    @Builder.Default
    private List<TableIndexDefinition> indexes = List.of();

    @Hidden
    @NotNull
    @Builder.Default
    private boolean deleted = false;

    @Hidden
    private Instant created;

    @Hidden
    private Instant updated;

    /**
     * A table is addressed by tenant, namespace and name, so those three make its identity.
     */
    @Override
    public String uid() {
        return String.join("_", Optional.ofNullable(tenantId).orElse(""), namespace, name);
    }

    @Override
    public TableDefinition toDeleted() {
        return this.toBuilder()
            .deleted(true)
            .updated(Instant.now())
            .build();
    }

    /**
     * Rejects an update that would change a table's identity or move its data.
     *
     * <p>
     * The physical name matters most here: it is what points the registry row at real rows, so
     * letting it change would silently orphan every row the table holds.
     */
    public Optional<ConstraintViolationException> validateUpdate(TableDefinition updated) {
        Set<ConstraintViolation<?>> violations = new HashSet<>();

        if (!Objects.equals(updated.getName(), this.getName())) {
            violations.add(
                ManualConstraintViolation.of(
                    "Illegal table name update",
                    updated,
                    TableDefinition.class,
                    "table.name",
                    updated.getName()
                )
            );
        }

        if (!Objects.equals(updated.getNamespace(), this.getNamespace())) {
            violations.add(
                ManualConstraintViolation.of(
                    "Illegal namespace update",
                    updated,
                    TableDefinition.class,
                    "table.namespace",
                    updated.getNamespace()
                )
            );
        }

        if (!Objects.equals(updated.getPhysicalTableName(), this.getPhysicalTableName())) {
            violations.add(
                ManualConstraintViolation.of(
                    "Illegal physical table name update",
                    updated,
                    TableDefinition.class,
                    "table.physicalTableName",
                    updated.getPhysicalTableName()
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
        TableDefinition that = (TableDefinition) o;
        return deleted == that.deleted
            && Objects.equals(tenantId, that.tenantId)
            && Objects.equals(namespace, that.namespace)
            && Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tenantId, namespace, name, deleted);
    }
}
