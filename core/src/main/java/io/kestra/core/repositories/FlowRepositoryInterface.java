package io.kestra.core.repositories;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.kestra.core.models.SearchResult;
import io.micronaut.data.model.Pageable;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.annotation.Nullable;
import javax.validation.ConstraintViolationException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface FlowRepositoryInterface {
    Optional<Flow> findById(String namespace, String id, Optional<Integer> revision);

    @Getter
    @NoArgsConstructor
    class FlowWithSource {
        private Flow flow;
        private String sourceCode;

        public FlowWithSource(Flow flow, String sourceCode) {
            this.flow = flow;
            this.sourceCode = sourceCode;
        }
    }

    default Flow findByExecution(Execution execution) {
        Optional<Flow> find = this.findById(
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

    default Optional<Flow> findById(String namespace, String id) {
        return this.findById(namespace, id, Optional.empty());
    }

    Optional<String> findSourceById(String namespace, String id, Optional<Integer> revision);

    default Optional<String> findSourceById(String namespace, String id) {
        return this.findSourceById(namespace, id, Optional.empty());
    }

    Optional<FlowWithSource> findByIdWithSource(String namespace, String id, Optional<Integer> revision) throws JsonProcessingException;

    default Optional<FlowWithSource> findByIdWithSource(String namespace, String id) throws JsonProcessingException {
        return this.findByIdWithSource(namespace, id, Optional.empty());
    }

    List<Flow> findRevisions(String namespace, String id);

    List<Flow> findAll();

    List<Flow> findAllWithRevisions();

    List<Flow> findByNamespace(String namespace);

    ArrayListTotal<Flow> find(
        Pageable pageable,
        @Nullable String query,
        @Nullable String namespace,
        @Nullable Map<String, String> labels
    );

    ArrayListTotal<SearchResult<Flow>> findSourceCode(Pageable pageable, @Nullable String query, @Nullable String namespace);

    List<String> findDistinctNamespace();

    FlowWithSource create(Flow flow, String flowSource, Flow flowWithDefaults);

    FlowWithSource update(Flow flow, Flow previous, String flowSource, Flow flowWithDefaults) throws ConstraintViolationException;

    Flow delete(Flow flow);
}
