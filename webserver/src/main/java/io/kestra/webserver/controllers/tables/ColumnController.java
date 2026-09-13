package io.kestra.webserver.controllers.tables;

import io.kestra.core.contexts.configuration.SystemFlowsConfiguration;
import io.kestra.core.tenant.TenantService;
import io.kestra.fethr.table.ColumnDefinition;
import io.kestra.fethr.table.ColumnService;
import io.kestra.webserver.models.tables.ColumnForm;
import io.kestra.webserver.models.tables.ColumnRenameForm;
import io.kestra.webserver.models.tables.ColumnTypeForm;
import io.kestra.webserver.models.tables.TableDetailVo;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Patch;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.inject.Inject;
import jakarta.validation.Valid;

/**
 * The columns of a user-defined table.
 *
 * <p>
 * Every operation here runs {@code ALTER TABLE} against real data, which is why each returns the
 * whole schema back: after a change of this kind the caller's copy of it is stale.
 */
@Controller("/api/v1/{tenant}/tables/{name}/columns")
@Requires(beans = ColumnService.class)
@ExecuteOn(TaskExecutors.IO)
@Tag(name = "Tables")
public class ColumnController {

    private static final String NAMESPACE = SystemFlowsConfiguration.DEFAULT_NAMESPACE;

    private final ColumnService columnService;
    private final TenantService tenantService;

    @Inject
    public ColumnController(ColumnService columnService, TenantService tenantService) {
        this.columnService = columnService;
        this.tenantService = tenantService;
    }

    @Post
    @Operation(summary = "Add a column, which runs ALTER TABLE ADD COLUMN.")
    public HttpResponse<TableDetailVo> add(@PathVariable String name, @Valid @Body ColumnForm body) {
        ColumnDefinition column = new ColumnDefinition(
            body.name(),
            body.type(),
            body.nullable(),
            body.sensitive(),
            // The service places it at the end and renumbers, so the position sent here is ignored.
            0,
            body.hint(),
            body.defaultValue()
        );

        return HttpResponse.created(
            TableDetailVo.of(
                columnService.addColumn(tenantService.resolveTenant(), NAMESPACE, name, column)
            )
        );
    }

    @Patch(value = "/{columnName}", consumes = "application/json-patch+json")
    @Operation(summary = "Rename a column, which runs ALTER TABLE RENAME COLUMN.")
    public TableDetailVo rename(
        @PathVariable String name,
        @PathVariable String columnName,
        @Valid @Body ColumnRenameForm body) {
        return TableDetailVo.of(
            columnService.renameColumn(tenantService.resolveTenant(), NAMESPACE, name, columnName, body.name())
        );
    }

    @Patch(value = "/{columnName}/type")
    @Operation(
        summary = "Change a column's data type, which runs ALTER TABLE ALTER COLUMN TYPE.",
        description = "Refused when a stored value cannot be converted to the new type."
    )
    public TableDetailVo changeType(
        @PathVariable String name,
        @PathVariable String columnName,
        @Valid @Body ColumnTypeForm body) {
        return TableDetailVo.of(
            columnService.updateColumn(tenantService.resolveTenant(), NAMESPACE, name, columnName, body.type())
        );
    }

    @Delete("/{columnName}")
    @Operation(
        summary = "Drop a column, which runs ALTER TABLE DROP COLUMN.",
        description = "Any index over the column is dropped with it."
    )
    public TableDetailVo drop(@PathVariable String name, @PathVariable String columnName) {
        return TableDetailVo.of(
            columnService.dropColumn(tenantService.resolveTenant(), NAMESPACE, name, columnName)
        );
    }
}
