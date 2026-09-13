package io.kestra.webserver.controllers.tables;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import io.kestra.core.contexts.configuration.SystemFlowsConfiguration;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.tenant.TenantService;
import io.kestra.fethr.auth.Permission;
import io.kestra.fethr.table.RowService;
import io.kestra.webserver.models.tables.CsvImportVo;
import io.kestra.webserver.responses.PagedResults;
import io.kestra.webserver.utils.CSVUtils;
import io.kestra.webserver.utils.PageableUtils;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Part;
import io.micronaut.http.annotation.Patch;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.micronaut.http.uri.UriBuilder;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.security.annotation.Secured;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.inject.Inject;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * The rows of a user-defined table.
 *
 * <p>
 * Rows are dynamic maps keyed by column name, so there is no typed body here: what a row may contain
 * is decided by the table's own schema, and the service coerces and refuses against it.
 */
@Controller("/api/v1/{tenant}/tables/{name}/rows")
@Requires(beans = RowService.class)
@ExecuteOn(TaskExecutors.IO)
@Tag(name = "Tables")
public class RowController {

    private static final String NAMESPACE = SystemFlowsConfiguration.DEFAULT_NAMESPACE;
    private static final String PK = "_id";

    private final RowService rowService;
    private final TenantService tenantService;

    @Inject
    public RowController(RowService rowService, TenantService tenantService) {
        this.rowService = rowService;
        this.tenantService = tenantService;
    }

    @Secured(Permission.Names.TABLE_WRITE)
    @Post
    @Operation(summary = "Insert a row, values keyed by column name.")
    public HttpResponse<Map<String, Object>> insert(
        HttpRequest<?> request,
        @PathVariable String name,
        @Body Map<String, Object> values) {
        Map<String, Object> row = rowService.insertRow(tenantService.resolveTenant(), NAMESPACE, name, values);

        return HttpResponse.created(
            row,
            UriBuilder.of(request.getPath()).path(String.valueOf(row.get(PK))).build()
        );
    }

    @Secured(Permission.Names.TABLE_WRITE)
    @Post(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA)
    @Operation(
        summary = "Import rows from a CSV upload.",
        description = "The header names must match the table's columns. All-or-nothing: one bad row "
            + "fails the request and inserts none of them."
    )
    public CsvImportVo importCsv(@PathVariable String name, @Part CompletedFileUpload fileUpload) throws IOException {
        List<Map<String, Object>> rows = CSVUtils.parseCSV(new String(fileUpload.getBytes(), StandardCharsets.UTF_8));
        int inserted = rowService.importRows(tenantService.resolveTenant(), NAMESPACE, name, rows);

        return new CsvImportVo(inserted);
    }

    @Secured(Permission.Names.TABLE_READ)
    @Get
    @Operation(summary = "List a page of rows, with free-text search and sort.")
    public PagedResults<Map<String, Object>> list(
        @PathVariable String name,
        @QueryValue(defaultValue = "") String q,
        @QueryValue(defaultValue = "") String sort,
        @QueryValue(defaultValue = "asc") String order,
        @QueryValue(defaultValue = "1") @Min(1) int page,
        @QueryValue(defaultValue = "25") @Min(1) @Max(PageableUtils.MAX_PAGE_SIZE) int size) {
        ArrayListTotal<Map<String, Object>> rows = rowService.listRows(
            tenantService.resolveTenant(),
            NAMESPACE,
            name,
            q,
            sort,
            "desc".equalsIgnoreCase(order),
            page,
            size
        );

        return PagedResults.of(rows);
    }

    @Secured(Permission.Names.TABLE_WRITE)
    @Patch(value = "/{rowId}", consumes = "application/json-patch+json")
    @Operation(
        summary = "Update the given columns of a row.",
        description = "A merge: a column absent from the body keeps its value."
    )
    public Map<String, Object> update(
        @PathVariable String name,
        @PathVariable String rowId,
        @Body Map<String, Object> values) {
        return rowService.updateRow(tenantService.resolveTenant(), NAMESPACE, name, rowId, values);
    }

    @Secured(Permission.Names.TABLE_WRITE)
    @Delete
    @Operation(summary = "Delete one or more rows by id.")
    public HttpResponse<Void> delete(@PathVariable String name, @Body List<String> rowIds) {
        rowService.deleteRows(tenantService.resolveTenant(), NAMESPACE, name, rowIds);
        return HttpResponse.noContent();
    }
}
