package io.kestra.webserver.controllers.api;

import io.kestra.core.models.namespaces.Namespace;
import io.kestra.core.models.topologies.FlowTopologyGraph;
import io.kestra.core.repositories.ArrayListTotal;
import io.kestra.core.repositories.FlowRepositoryInterface;
import io.kestra.core.tenant.TenantService;
import io.kestra.core.topologies.FlowTopologyService;
import io.kestra.core.utils.NamespaceUtils;
import io.kestra.webserver.models.api.ApiAutocomplete;
import io.kestra.webserver.responses.PagedResults;
import io.kestra.webserver.utils.AutocompleteUtils;
import io.kestra.webserver.utils.PageableUtils;
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
import jakarta.validation.constraints.NotNull;
import org.apache.commons.lang3.Strings;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Validated
@Controller("/api/v1/{tenant}/namespaces")
public class NamespaceController<N extends Namespace> {
    protected static final Pageable AUTOCOMPLETE_PAGEABLE = PageableUtils.from(1, 50, null);

    @Inject
    private TenantService tenantService;

    @Inject
    private FlowRepositoryInterface flowRepository;

    @Inject
    private FlowTopologyService flowTopologyService;

    @Inject
    private NamespaceUtils namespaceUtils;

    protected Comparator<String> sorter(Pageable pageable) {
        return Optional.of(pageable.getSort().getOrderBy())
            .map(o -> o.isEmpty() ? null : o.getFirst().getDirection())
            .orElse(Sort.Order.Direction.ASC) == Sort.Order.Direction.ASC ? Comparator.naturalOrder() : Comparator.reverseOrder();
    }

    protected ArrayListTotal<N> getNamespaces(Pageable pageable, @Nullable String q, List<String> forceIncludeIds, boolean existingOnly) {
        // We separate the namespaces into two groups: those that are force included and those that are not.
        // Force included namespaces are always returned, while the others are filtered + trimmed based on the query + pageable.
        Map<Boolean, List<String>> fetchedNamespacesByForceInclude = flowRepository.findDistinctNamespace(tenantService.resolveTenant()).stream()
            .flatMap(n -> NamespaceUtils.asTree(n).stream())
            .collect(Collectors.groupingBy(forceIncludeIds::contains));
        List<String> filteredFetchedNamespaces = Optional.ofNullable(fetchedNamespacesByForceInclude.get(false)).orElse(Collections.emptyList()).stream()
            .filter(n -> q == null || Strings.CI.contains(n, q))
            .toList();
        List<String> systemFlowNamespace = q == null || Strings.CI.contains(namespaceUtils.getSystemFlowNamespace(), q)
            ? List.of(namespaceUtils.getSystemFlowNamespace())
            : Collections.emptyList();

        List<String> forceIncludeExistingNamespaceIds = Optional.ofNullable(fetchedNamespacesByForceInclude.get(true)).orElse(Collections.emptyList());

        List<N> finalNamespaces = AutocompleteUtils.from(
                Stream.concat(
                        filteredFetchedNamespaces.stream(),
                        systemFlowNamespace.stream()
                    )
                    .distinct()
                    .sorted(sorter(pageable))
                    .skip(pageable.getOffset() - pageable.getSize())
                    .limit(pageable.getSize())
                    .toList(),
                forceIncludeExistingNamespaceIds
            ).stream()
            .sorted(sorter(pageable))
            .map(id -> (N) Namespace.builder()
                .id(id)
                .build()
            ).toList();

        // If no namespaces are returned, we return total 0 because criteria are wrong
        if (finalNamespaces.isEmpty()) {
            return new ArrayListTotal<>(0);
        }

        return new ArrayListTotal<>(
            finalNamespaces,
            AutocompleteUtils.from(filteredFetchedNamespaces, forceIncludeExistingNamespaceIds, systemFlowNamespace).size()
        );
    }

    @Post(uri = "/autocomplete")
    @ExecuteOn(TaskExecutors.IO)
    @Operation(tags = {"Namespaces"}, summary = "List namespaces for autocomplete", description = "Returns a list of namespaces for use in autocomplete fields, optionally allowing to filter by query and ids. Used especially for binding creation.")
    public List<String> autocompleteNamespaces(@NotNull @Body ApiAutocomplete autocomplete) throws HttpStatusException {
        return this.getNamespaces(
                AUTOCOMPLETE_PAGEABLE,
                autocomplete.getQ(),
                Optional.ofNullable(autocomplete.getIds()).orElse(Collections.emptyList()),
                autocomplete.isExistingOnly()
            ).stream()
            .map(Namespace::getId)
            .toList();
    }

    @Get(uri = "{id}")
    @ExecuteOn(TaskExecutors.IO)
    @Operation(tags = {"Namespaces"}, summary = "Get a namespace")
    public N getNamespace(
        @Parameter(description = "The namespace id") @PathVariable String id
    ) {
        return (N) Namespace.builder().id(id).build();
    }

    @Get(uri = "/search")
    @ExecuteOn(TaskExecutors.IO)
    @Operation(tags = {"Namespaces"}, summary = "Search for namespaces")
    public PagedResults<N> searchNamespaces(
        @Parameter(description = "A string filter") @Nullable @QueryValue(value = "q") String query,
        @Parameter(description = "The current page") @QueryValue(defaultValue = "1") @Min(1) int page,
        @Parameter(description = "The current page size") @QueryValue(defaultValue = "10") @Min(1) int size,
        @Parameter(description = "The sort of current page") @Nullable @QueryValue List<String> sort,
        @Parameter(description = "Return only existing namespace") @Nullable @QueryValue(value = "existing", defaultValue = "false") boolean existingOnly
    ) throws HttpStatusException {
        return PagedResults.of(getNamespaces(
            PageableUtils.from(page, size, sort),
            query,
            Collections.emptyList(),
            existingOnly
        ));
    }

    @ExecuteOn(TaskExecutors.IO)
    @Get(uri = "{namespace}/dependencies")
    @Operation(tags = {"Flows"}, summary = "Get flow dependencies")
    public FlowTopologyGraph getFlowDependenciesFromNamespace(
        @Parameter(description = "The flow namespace") @PathVariable String namespace,
        @Parameter(description = "if true, list only destination dependencies, otherwise list also source dependencies") @QueryValue(defaultValue = "false") boolean destinationOnly
    ) {
        return flowTopologyService.namespaceGraph(tenantService.resolveTenant(), namespace);
    }
}
