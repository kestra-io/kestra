package io.kestra.webserver.controllers.tables;

import io.kestra.core.contexts.configuration.SystemFlowsConfiguration;
import io.kestra.core.tenant.TenantService;
import io.kestra.fethr.auth.Permission;
import io.kestra.fethr.table.IndexService;
import io.kestra.fethr.table.TableIndexDefinition;
import io.kestra.webserver.models.tables.IndexForm;
import io.kestra.webserver.models.tables.TableDetailVo;

import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.security.annotation.Secured;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.inject.Inject;
import jakarta.validation.Valid;

/** The indexes of a user-defined table. */
@Controller("/api/v1/{tenant}/tables/{name}/indexes")
@Requires(beans = IndexService.class)
@ExecuteOn(TaskExecutors.IO)
@Tag(name = "Tables")
public class IndexController {

    private static final String NAMESPACE = SystemFlowsConfiguration.DEFAULT_NAMESPACE;

    private final IndexService indexService;
    private final TenantService tenantService;

    @Inject
    public IndexController(IndexService indexService, TenantService tenantService) {
        this.indexService = indexService;
        this.tenantService = tenantService;
    }

    @Secured(Permission.Names.TABLE_UPDATE)
    @Post
    @Operation(summary = "Create an index, which runs CREATE INDEX.")
    public HttpResponse<TableDetailVo> create(@PathVariable String name, @Valid @Body IndexForm body) {
        TableIndexDefinition index = new TableIndexDefinition(body.name(), body.columns(), body.unique());

        return HttpResponse.created(
            TableDetailVo.of(
                indexService.createIndex(tenantService.resolveTenant(), NAMESPACE, name, index)
            )
        );
    }

    @Secured(Permission.Names.TABLE_DELETE)
    @Delete("/{indexName}")
    @Operation(summary = "Drop an index, which runs DROP INDEX.")
    public TableDetailVo drop(@PathVariable String name, @PathVariable String indexName) {
        return TableDetailVo.of(
            indexService.dropIndex(tenantService.resolveTenant(), NAMESPACE, name, indexName)
        );
    }
}
