package io.kestra.fethr.table;

import java.util.Optional;

import io.micronaut.context.annotation.Context;
import io.micronaut.core.annotation.Nullable;
import jakarta.inject.Inject;

/**
 * Makes {@link RowService} reachable from a plugin task.
 *
 * <p>
 * A task is a POJO deserialized from YAML, so nothing is injected into it. The 1.x fork solved that
 * by casting the run context and asking its application context for the bean; Kestra 2.0 closed that
 * off deliberately -- {@code DefaultRunContext} resolves its services once at init and exposes them
 * through named accessors, and the context itself is private.
 *
 * <p>
 * Adding a Fethr service to that list would put a Fethr type inside an upstream class. This holder
 * keeps the coupling on this side instead, and is the same shape as
 * {@link io.kestra.fethr.vault.VaultEncryption}, which exists for the same reason: Jackson builds
 * the serializers, so they cannot be injected either.
 *
 * <p>
 * The service is optional because tables are PostgreSQL-only: on H2 there is no repository, so no
 * {@link RowService}, and a caller is expected to say so rather than fail on a null.
 */
@Context
public class TableServices {

    private static volatile RowService rowService;

    @Inject
    public TableServices(@Nullable RowService rowService) {
        TableServices.rowService = rowService;
    }

    /** The row service, or empty where tables are not backed at all. */
    public static Optional<RowService> rowService() {
        return Optional.ofNullable(rowService);
    }
}
