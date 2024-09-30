package io.kestra.webserver.controllers.api;

import io.kestra.core.models.namespaces.Namespace;
import io.kestra.core.models.topologies.FlowTopologyGraph;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.utils.NamespaceUtils;
import io.kestra.core.tenant.TenantService;
import io.kestra.core.topologies.FlowTopologyService;
import io.kestra.webserver.models.namespaces.NamespaceWithDisabled;
import io.kestra.webserver.responses.PagedResults;
import io.kestra.webserver.utils.PageableUtils;
import io.micronaut.context.annotation.Value;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import io.micronaut.http.annotation.*;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import io.micronaut.validation.Validated;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.inject.Inject;
import jakarta.validation.constraints.Min;

import java.util.*;
import java.util.stream.Collectors;

@Validated
@Controller("/api/v1/namespaces")
public class NamespaceController implements NamespaceControllerInterface<Namespace, NamespaceWithDisabled> {
    @Inject
    private TenantService tenantService;

    @Inject
    private FlowRepositoryInterface flowRepository;

    @Inject
    private FlowTopologyService flowTopologyService;

    @Inject
    private NamespaceUtils namespaceUtils;

    @Get(uri = "{id}")
    @ExecuteOn(TaskExecutors.IO)
    @Operation(tags = {"Namespaces"}, summary = "Get a namespace")
    public Namespace index(
        @Parameter(description = "The namespace id") @PathVariable String id
    ) {
        return Namespace.builder().id(id).build();
    }

    @Get(uri = "/search")
    @ExecuteOn(TaskExecutors.IO)
    @Operation(tags = {"Namespaces"}, summary = "Search for namespaces")
    public PagedResults<NamespaceWithDisabled> find(
        @Parameter(description = "A string filter") @Nullable @QueryValue(value = "q") String query,
        @Parameter(description = "The current page") @QueryValue(defaultValue = "1") @Min(1) int page,
        @Parameter(description = "The current page size") @QueryValue(defaultValue = "10") @Min(1) int size,
        @Parameter(description = "The sort of current page") @Nullable @QueryValue List<String> sort,
        @Parameter(description = "Return only existing namespace") @Nullable @QueryValue(value = "existing", defaultValue = "false") Boolean existingOnly
    ) throws HttpStatusException {
        List<String> distinctNamespaces = flowRepository.findDistinctNamespace(tenantService.resolveTenant()).stream()
            .flatMap(n -> NamespaceUtils.asTree(n).stream())
            .collect(Collectors.toList());

        // we manually add it here so it is always listed in the Namespaces page.
        if (distinctNamespaces.stream().noneMatch(ns -> namespaceUtils.getSystemFlowNamespace().equals(ns))) {
            distinctNamespaces.add(namespaceUtils.getSystemFlowNamespace());
        }

        distinctNamespaces = distinctNamespaces.stream().sorted()
            .distinct()
            .collect(Collectors.toList());

        if (query != null) {
            distinctNamespaces = distinctNamespaces
                .stream()
                .filter(s -> s.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT)))
                .collect(Collectors.toList());
        }

        var total = distinctNamespaces.size();

        Pageable pageable = PageableUtils.from(page, size, sort);

        if (sort != null) {
            Sort.Order.Direction direction = pageable.getSort().getOrderBy().getFirst().getDirection();

            if (direction.equals(Sort.Order.Direction.ASC)) {
                Collections.sort(distinctNamespaces);
            } else {
                Collections.reverse(distinctNamespaces);
            }
        }

        if (distinctNamespaces.size() > pageable.getSize()) {
            distinctNamespaces = distinctNamespaces.subList(
                (int) pageable.getOffset() - pageable.getSize(),
                Math.min((int) pageable.getOffset(), distinctNamespaces.size())
            );
        }

        return PagedResults.of(new ArrayListTotal<>(
            distinctNamespaces
                .stream()
                .<NamespaceWithDisabled>map(s -> NamespaceWithDisabled.builder()
                    .id(s)
                    .disabled(true)
                    .build()
                ).toList(),
            total
        ));
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "{namespace}/dependencies")
    @Operation(tags = {"Flows"}, summary = "Get flow dependencies")
    public FlowTopologyGraph dependencies(
        @Parameter(description = "The flow namespace") @PathVariable String namespace,
        @Parameter(description = "if true, list only destination dependencies, otherwise list also source dependencies") @QueryValue(defaultValue = "false") boolean destinationOnly
    ) {
        return flowTopologyService.namespaceGraph(tenantService.resolveTenant(), namespace);
    }
}
