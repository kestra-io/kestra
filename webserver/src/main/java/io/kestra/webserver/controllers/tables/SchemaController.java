package io.kestra.webserver.controllers.tables;

import java.util.Collections;
import java.util.List;

import io.kestra.core.contexts.configuration.SystemFlowsConfiguration;
import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.QueryFilter.Resource;
import io.kestra.core.models.validations.ManualConstraintViolation;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.tenant.TenantService;
import io.kestra.fethr.auth.Permission;
import io.kestra.fethr.table.ColumnDefinition;
import io.kestra.fethr.table.TableDefinition;
import io.kestra.fethr.table.TableRepositoryInterface;
import io.kestra.fethr.table.TableService;
import io.kestra.webserver.converters.QueryFilterFormat;
import io.kestra.webserver.models.tables.TableDetailVo;
import io.kestra.webserver.models.tables.TableForm;
import io.kestra.webserver.models.tables.TableRenameForm;
import io.kestra.webserver.models.tables.TableVo;
import io.kestra.webserver.responses.PagedResults;
import io.kestra.webserver.utils.PageableUtils;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.model.Pageable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Patch;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.security.annotation.Secured;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;

/**
 * A user-defined table's schema: create, read, list, rename, delete.
 *
 * <p>
 * Creating one runs real DDL, so everything a caller supplies is validated on the form before any of
 * it reaches the database -- see the annotations on {@link TableForm}.
 *
 * <p>
 * Tables live in the system namespace, as they did in the 1.x fork. The service layer takes a
 * namespace throughout and would support per-namespace tables; it is this controller that pins it,
 * so opening that up later is a change here rather than a change everywhere.
 */
@Controller("/api/v1/{tenant}/tables")
@Requires(beans = TableService.class)
@ExecuteOn(TaskExecutors.IO)
@Tag(name = "Tables")
public class SchemaController {

    private static final String NAMESPACE = SystemFlowsConfiguration.DEFAULT_NAMESPACE;

    private final TableService tableService;
    private final TableRepositoryInterface tableRepository;
    private final TenantService tenantService;

    @Inject
    public SchemaController(TableService tableService, TableRepositoryInterface tableRepository, TenantService tenantService) {
        this.tableService = tableService;
        this.tableRepository = tableRepository;
        this.tenantService = tenantService;
    }

    @Secured(Permission.Names.TABLE_CREATE)
    @Post
    @Operation(summary = "Create a table, which creates a real backing table at runtime.")
    public HttpResponse<TableVo> create(@Valid @Body TableForm body) {
        String tenantId = tenantService.resolveTenant();

        if (tableRepository.findByName(tenantId, NAMESPACE, body.name()).isPresent()) {
            throw nameExists(body.name());
        }

        TableDefinition definition = TableDefinition.builder()
            .tenantId(tenantId)
            .namespace(NAMESPACE)
            .name(body.name())
            .description(body.description())
            .primaryKeyType(body.primaryKeyType())
            .columns(toColumns(body))
            .build();

        return HttpResponse.created(TableVo.of(tableService.createTable(definition)));
    }

    @Secured(Permission.Names.TABLE_READ)
    @Get("/{name}")
    @Operation(summary = "Get a table's schema.")
    public TableDetailVo get(@PathVariable String name) {
        return TableDetailVo.of(tableService.getTable(tenantService.resolveTenant(), NAMESPACE, name));
    }

    @Secured(Permission.Names.TABLE_READ)
    @Get
    @Operation(summary = "List and search the tables.")
    public PagedResults<TableVo> list(
        @QueryValue(value = "page", defaultValue = "1") int page,
        @QueryValue(value = "size", defaultValue = "25") @Max(PageableUtils.MAX_PAGE_SIZE) int size,
        @Nullable @QueryValue(value = "sort") List<String> sort,
        @QueryFilterFormat(Resource.NAMESPACE) List<QueryFilter> filters) {
        Pageable pageable = PageableUtils.from(page, size, sort, key -> key);
        ArrayListTotal<TableDefinition> tables = tableService.listTables(pageable, tenantService.resolveTenant(), filters);

        return PagedResults.of(
            new ArrayListTotal<>(
                tables.stream().map(TableVo::of).toList(),
                tables.getTotal()
            )
        );
    }

    @Secured(Permission.Names.TABLE_UPDATE)
    @Patch(value = "/{name}", consumes = "application/json-patch+json")
    @Operation(summary = "Rename a table and change its description.")
    public HttpResponse<Void> rename(@PathVariable String name, @Valid @Body TableRenameForm body) {
        String tenantId = tenantService.resolveTenant();
        TableDefinition current = tableService.getTable(tenantId, NAMESPACE, name);

        // A merge patch over two optional fields: an absent one keeps what the table already has.
        // The 1.x fork ran this through a JSON merge-patch library, round-tripping the whole
        // definition through a tree to decide two values. Naming them is clearer and drops a
        // dependency the webserver does not otherwise carry.
        String newName = body.name() != null ? body.name() : current.getName();
        String newDescription = body.description() != null ? body.description() : current.getDescription();

        if (!newName.equals(name) && tableRepository.findByName(tenantId, NAMESPACE, newName).isPresent()) {
            throw nameExists(newName);
        }

        tableService.renameTable(current, newName, newDescription);
        return HttpResponse.noContent();
    }

    @Secured(Permission.Names.TABLE_DELETE)
    @Delete("/{name}")
    @Operation(summary = "Delete a table, which drops the real backing table.")
    public HttpResponse<Void> delete(@PathVariable String name) {
        tableService.deleteTable(tenantService.resolveTenant(), NAMESPACE, name);
        return HttpResponse.noContent();
    }

    private static List<ColumnDefinition> toColumns(TableForm body) {
        // Positions are assigned by the service when it normalizes, so the list order is what counts here.
        return body.columns().stream()
            .map(
                column -> new ColumnDefinition(
                    column.name(),
                    column.type(),
                    column.nullable(),
                    column.sensitive(),
                    0,
                    column.hint(),
                    column.defaultValue()
                )
            )
            .toList();
    }

    private static ConstraintViolationException nameExists(String name) {
        return new ConstraintViolationException(
            Collections.singleton(
                ManualConstraintViolation.of(
                    "A table named '" + name + "' already exists",
                    name,
                    String.class,
                    "table.name",
                    name
                )
            )
        );
    }
}
