package io.kestra.webserver.controllers.tables;

import io.kestra.fethr.auth.Permission;
import io.kestra.webserver.models.tables.ColumnTypesVo;

import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.security.annotation.Secured;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * The column-type catalogue the table designer offers.
 *
 * <p>
 * Served from the enum rather than duplicated in the UI, so the options a person can pick cannot
 * drift from the ones the DDL layer will accept.
 */
@Controller("/api/v1/{tenant}/column/types")
@ExecuteOn(TaskExecutors.IO)
@Tag(name = "Tables")
public class ColumnMetadataController {

    @Secured(Permission.Names.TABLE_READ)
    @Get
    @Operation(summary = "List the column data types, and which of them can back a primary key.")
    public ColumnTypesVo listColumnTypes() {
        return ColumnTypesVo.fromEnum();
    }
}
