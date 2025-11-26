package io.kestra.core.repositories;

import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.SearchResult;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.*;
import io.kestra.plugin.core.dashboard.data.Flows;
import io.micronaut.data.model.Pageable;
import jakarta.annotation.Nullable;
import jakarta.validation.ConstraintViolationException;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Optional;

public interface FlowRepositoryInterface extends QueryBuilderInterface<Flows.Fields> {

    Optional<Flow> findById(String tenantId, String namespace, String id, Optional<Integer> revision, Boolean allowDeleted);

    default Optional<Flow> findById(String tenantId, String namespace, String id, Optional<Integer> revision) {
        return this.findById(tenantId, namespace, id, revision, false);
    }

    Optional<Flow> findByIdWithoutAcl(String tenantId, String namespace, String id, Optional<Integer> revision);

    /**
     * Used only if result is used internally and not exposed to the user.
     * It is useful when we want to restart/resume a flow.
     */
    default FlowWithSource findByExecutionWithoutAcl(Execution execution) {
        Optional<FlowWithSource> find = this.findByIdWithSourceWithoutAcl(
            execution.getTenantId(),
            execution.getNamespace(),
            execution.getFlowId(),
            Optional.ofNullable(execution.getFlowRevision())
        );

        if (find.isEmpty()) {
            throw new IllegalStateException("Unable to find flow '" + execution.getNamespace() + "." +
                execution.getFlowId() + "' with revision " + execution.getFlowRevision() + " on execution " +
                execution.getId()
            );
        } else {
            return find.get();
        }
    }

    default Flow findByExecution(Execution execution) {
        Optional<Flow> find = this.findById(
            execution.getTenantId(),
            execution.getNamespace(),
            execution.getFlowId(),
            Optional.ofNullable(execution.getFlowRevision())
        );

        if (find.isEmpty()) {
            throw new IllegalStateException("Unable to find flow '" + execution.getNamespace() + "." +
                execution.getFlowId() + "' with revision " + execution.getFlowRevision() + " on execution " +
                execution.getId()
            );
        } else {
            return find.get();
        }
    }

    default FlowWithSource findByExecutionWithSource(Execution execution) {
        Optional<FlowWithSource> find = this.findByIdWithSource(
            execution.getTenantId(),
            execution.getNamespace(),
            execution.getFlowId(),
            Optional.of(execution.getFlowRevision())
        );

        if (find.isEmpty()) {
            throw new IllegalStateException("Unable to find flow '" + execution.getNamespace() + "." +
                execution.getFlowId() + "' with revision " + execution.getFlowRevision() + " on execution " +
                execution.getId()
            );
        } else {
            return find.get();
        }
    }

    default Optional<Flow> findById(String tenantId, String namespace, String id) {
        return this.findById(tenantId, namespace, id, Optional.empty(), false);
    }

    Optional<FlowWithSource> findByIdWithSource(String tenantId, String namespace, String id, Optional<Integer> revision, Boolean allowDelete);

    default Optional<FlowWithSource> findByIdWithSource(String tenantId, String namespace, String id, Optional<Integer> revision) {
        return this.findByIdWithSource(tenantId, namespace, id, revision, false);
    }

    default Optional<FlowWithSource> findByIdWithSource(String tenantId, String namespace, String id) {
        return this.findByIdWithSource(tenantId, namespace, id, Optional.empty(), false);
    }

    Optional<FlowWithSource> findByIdWithSourceWithoutAcl(String tenantId, String namespace, String id, Optional<Integer> revision);

    List<FlowWithSource> findRevisions(String tenantId, String namespace, String id);

    Integer lastRevision(String tenantId, String namespace, String id);

    List<Flow> findAll(String tenantId);

    List<FlowWithSource> findAllWithSource(String tenantId);

    List<FlowWithSource> findAllWithSourceWithNoAcl(String tenantId);

    List<Flow> findAllForAllTenants();

    List<FlowWithSource> findAllWithSourceForAllTenants();

    /**
     * Counts the total number of flows.
     *
     * @param tenantId the tenant ID.
     * @return The count.
     */
    int count(@Nullable  String tenantId);

    List<Flow> findByNamespace(String tenantId, String namespace);

    List<Flow> findByNamespacePrefix(String tenantId, String namespacePrefix);

    List<FlowForExecution> findByNamespaceExecutable(String tenantId, String namespace);

    List<FlowWithSource> findByNamespaceWithSource(String tenantId, String namespace);

    List<FlowWithSource> findByNamespacePrefixWithSource(String tenantId, String namespace);

    ArrayListTotal<Flow> find(
        Pageable pageable,
        @Nullable String tenantId,
        @Nullable List<QueryFilter> filters
    );

    ArrayListTotal<FlowWithSource> findWithSource(
        Pageable pageable,
        @Nullable String tenantId,
        @Nullable List<QueryFilter> filters
    );

    ArrayListTotal<SearchResult<Flow>> findSourceCode(Pageable pageable, @Nullable String query, @Nullable String tenantId, @Nullable String namespace);

    List<String> findDistinctNamespace(String tenantId);

    List<String> findDistinctNamespaceExecutable(String tenantId);

    default List<String> findDistinctNamespace(String tenantId, String prefix) {
        List<String> distinctNamespaces = this.findDistinctNamespace(tenantId);

        if (prefix == null) {
            return distinctNamespaces;
        }

        return distinctNamespaces.stream()
            .filter(n -> n.startsWith(prefix))
            .toList();
    }

    Flux<Flow> findAsync(String tenantId, List<QueryFilter> filters);

    FlowWithSource create(GenericFlow flow);

    FlowWithSource update(GenericFlow flow, FlowInterface previous) throws ConstraintViolationException;

    FlowWithSource delete(FlowInterface flow);

    Boolean existAnyNoAcl(String tenantId);
}
