package io.kestra.core.services;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.executions.ExecutionIndexedField;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.IndexedField;
import io.kestra.core.repositories.IndexedFieldRepositoryInterface;
import io.kestra.core.runners.RunContext;

import jakarta.inject.Singleton;

/**
 * Service to compute and store execution indexed fields. Indexed fields are evaluated from Pebble expressions when an
 * execution ends and persisted in a dedicated, searchable table.
 */
@Singleton
public class IndexedFieldService {
    private static final Logger LOG = LoggerFactory.getLogger(IndexedFieldService.class);

    /**
     * Maximum length of an indexed field value. Values are stored in a {@code VARCHAR(1024)} column so the index stays
     * portable and bounded across H2, PostgreSQL and MySQL; longer rendered values are truncated.
     */
    public static final int MAX_VALUE_LENGTH = 1024;

    private final IndexedFieldRepositoryInterface indexedFieldRepository;

    public IndexedFieldService(IndexedFieldRepositoryInterface indexedFieldRepository) {
        this.indexedFieldRepository = indexedFieldRepository;
    }

    /**
     * Compute and persist the indexed fields declared on a flow for a finished execution.
     *
     * @param runContext the run context used to render the Pebble expressions
     * @param flow the flow declaring the indexed fields
     * @param execution the finished execution
     */
    public void saveIndexedFields(RunContext runContext, Flow flow, Execution execution) {
        if (flow.getIndexedFields() == null || flow.getIndexedFields().isEmpty()) {
            return;
        }

        for (IndexedField indexedField : flow.getIndexedFields()) {
            try {
                String rendered = runContext.render(indexedField.getValue());
                if (rendered == null || rendered.isBlank()) {
                    continue;
                }

                if (rendered.length() > MAX_VALUE_LENGTH) {
                    LOG.debug(
                        "Indexed field '{}' for execution '{}' was truncated from {} to {} characters",
                        indexedField.getId(),
                        execution.getId(),
                        rendered.length(),
                        MAX_VALUE_LENGTH
                    );
                    rendered = rendered.substring(0, MAX_VALUE_LENGTH);
                }

                ExecutionIndexedField field = new ExecutionIndexedField(
                    execution.getTenantId(),
                    execution.getId(),
                    indexedField.getId(),
                    rendered,
                    execution.getNamespace(),
                    execution.getFlowId()
                );
                indexedFieldRepository.save(field);
            } catch (IllegalVariableEvaluationException e) {
                LOG.warn(
                    "Failed to render indexed field '{}' for execution '{}': {}",
                    indexedField.getId(),
                    execution.getId(),
                    e.getMessage()
                );
            }
        }
    }

    /**
     * Purge (hard delete) indexed fields for a given list of executions.
     *
     * @return the number of deleted indexed fields
     */
    public int purge(List<Execution> executions) {
        return this.indexedFieldRepository.purgeByExecutionIds(executions.stream().map(Execution::getId).toList());
    }
}
