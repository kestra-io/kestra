package io.kestra.webserver.controllers.api;

import io.kestra.core.exceptions.NotFoundException;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.repositories.ExecutionRepositoryInterface;
import io.kestra.core.searchable.SearchableDataRepositoryInterface;
import io.kestra.core.tenant.TenantService;
import io.kestra.webserver.responses.PagedResults;
import io.kestra.webserver.utils.PageableUtils;

import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.inject.Inject;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Controller("/api/v1/{tenant}/searchable_data")
public class SearchableDataController {
    @Inject
    private ExecutionRepositoryInterface executionRepository;

    @Inject
    private SearchableDataRepositoryInterface searchableDataRepository;

    @Inject
    private TenantService tenantService;

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "{executionId}")
    @Operation(tags = { "Searchable data" }, summary = "List the searchable data of an execution")
    public PagedResults<SearchableDataInformation> listSearchableData(
        @Parameter(description = "The execution id") @PathVariable String executionId,
        @Parameter(description = "The current page") @QueryValue(defaultValue = "1") @Min(1) int page,
        @Parameter(description = "The current page size") @QueryValue(defaultValue = "10") @Min(1) @Max(PageableUtils.MAX_PAGE_SIZE) int size) {
        var tenantId = tenantService.resolveTenant();
        executionRepository.findById(tenantId, executionId).orElseThrow(NotFoundException::new);

        var searchableData = searchableDataRepository.findByExecutionId(tenantId, executionId, PageableUtils.from(page, size));
        return PagedResults.of(new ArrayListTotal<>(
            searchableData.stream().map(it -> new SearchableDataInformation(it.dataId(), it.value())).toList(),
            searchableData.getTotal()
        ));
    }

    public record SearchableDataInformation(String dataId, String value) {
    }
}
