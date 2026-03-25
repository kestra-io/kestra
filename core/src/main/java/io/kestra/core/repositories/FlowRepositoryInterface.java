package io.kestra.core.repositories;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import io.kestra.core.models.QueryFilter;
import io.kestra.core.models.SearchResult;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.*;
import io.kestra.core.models.namespaces.NamespaceInterface;
import io.kestra.plugin.core.dashboard.data.Flows;

import io.micronaut.data.model.Pageable;
import jakarta.annotation.Nullable;
import jakarta.validation.ConstraintViolationException;
import reactor.core.publisher.Flux;

public interface FlowRepositoryInterface extends QueryBuilderInterface<Flows.Fields> {

    Optional<Flow> findById(String tenantId, String namespace, String id, Optional<Integer> revision, Boolean allowDeleted);

    default Optional<Flow> findById(String tenantId, String namespace, String id, Optional<Integer> revision) {
        return this.findById(tenantId, namespace, id, revision, false);
    }

    Optional<Flow> findByIdWithoutAcl(String tenantId, String namespace, String id, Optional<Integer> revision);

    /**
     * Checks whether a given namespace exists.
     * <p>
     * A namespace is considered existing if at least one Flow is within the namespace or a parent namespace.
     *
     * @param tenant The tenant ID
     * @param namespace The namespace - cannot be null.
     * @return {@code true} if the namespace exist. Otherwise {@link false}.
     */
    default boolean isNamespaceExists(String tenant, String namespace) {
        Objects.requireNonNull(namespace, "namespace cannot be null");
        List<String> namespaces = findDistinctNamespace(tenant).stream()
            .map(NamespaceInterface::asTree)
            .flatMap(Collection::stream)
            .toList();
        return namespaces.stream().anyMatch(ns -> ns.equals(namespace) || ns.startsWith(namespace));
    }

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
            throw new IllegalStateException(
                "Unable to find flow '" + execution.getNamespace() + "." +
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
            throw new IllegalStateException(
                "Unable to find flow '" + execution.getNamespace() + "." +
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
            throw new IllegalStateException(
                "Unable to find flow '" + execution.getNamespace() + "." +
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

    List<FlowWithSource> findRevisions(String tenantId, String namespace, String id, Boolean allowDeleted);

    List<FlowWithSource> findRevisions(String tenantId, String namespace, String id, Boolean allowDeleted, List<Integer> revisions);

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
    int count(@Nullable String tenantId);

    List<Flow> findByNamespace(String tenantId, String namespace);

    List<Flow> findByNamespacePrefix(String tenantId, String namespacePrefix);

    List<FlowForExecution> findByNamespaceExecutable(String tenantId, String namespace);

    List<FlowWithSource> findByNamespaceWithSource(String tenantId, String namespace);

    List<FlowWithSource> findByNamespacePrefixWithSource(String tenantId, String namespace);

    ArrayListTotal<Flow> find(
        Pageable pageable,
        @Nullable String tenantId,
        @Nullable List<QueryFilter> filters);

    ArrayListTotal<FlowWithSource> findWithSource(
        Pageable pageable,
        @Nullable String tenantId,
        @Nullable List<QueryFilter> filters);

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

    /**
     * Create a flow.
     * It should not be called directly but instead <code>FlowService.create(GenericFlow flow)</code> should be used as it re-computes topology and triggers.
     */
    FlowWithSource create(GenericFlow flow);

    /**
     * Update a flow.
     * It should not be called directly but instead <code>FlowService.update(GenericFlow flow)</code> should be used as it re-computes topology and triggers.
     */
    FlowWithSource update(GenericFlow flow, FlowInterface previous) throws ConstraintViolationException;

    /**
     * Delete a flow.
     * It should not be called directly but instead <code>FlowService.delete(GenericFlow flow)</code> should be used as it re-computes topology and triggers.
     */
    FlowWithSource delete(FlowInterface flow);

    /**
     * Delete a flow bypassing ACL checks.
     * Used only for internal/test cleanup operations where no user context is available.
     */
    FlowWithSource deleteWithoutAcl(FlowInterface flow);

    void deleteRevisions(String tenantId, String namespace, String id, List<Integer> revisions);

    Boolean existAnyNoAcl(String tenantId);
}
