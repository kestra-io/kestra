package io.kestra.core.models.triggers.multipleflows;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnore;

import io.kestra.core.models.HasUID;
import io.kestra.core.models.flows.FlowId;
import io.kestra.core.utils.IdUtils;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class MultipleConditionWindow implements HasUID {
    String tenantId;

    String namespace;

    String flowId;

    String conditionId;

    ZonedDateTime start;

    ZonedDateTime end;

    Map<String, Boolean> results;

    Map<String, Object> outputs;

    /** {@inheritDoc **/
    @Override
    @JsonIgnore
    public String uid() {
        return IdUtils.fromParts(
            this.tenantId,
            this.namespace,
            this.flowId,
            this.conditionId
        );
    }

    public static String uid(FlowId flow, String conditionId) {
        return IdUtils.fromParts(
            flow.getTenantId(),
            flow.getNamespace(),
            flow.getId(),
            conditionId
        );
    }

    public boolean isValid(ZonedDateTime now) {
        return now.isAfter(this.getStart()) && now.isBefore(this.getEnd());
    }

    public MultipleConditionWindow with(Map<String, Boolean> newResult) {
        Map<String, Boolean> finalResults = new HashMap<>();

        if (results != null) {
            finalResults.putAll(results);
        }

        newResult
            .entrySet()
            .stream()
            .filter(Map.Entry::getValue)
            .forEach(e -> finalResults.put(e.getKey(), true));

        return new MultipleConditionWindow(
            this.tenantId,
            this.namespace,
            this.flowId,
            this.conditionId,
            this.start,
            this.end,
            finalResults,
            this.outputs
        );
    }

    /**
     * Stores the outputs of an execution that satisfied a condition of this window, under its namespace and flow ID,
     * replacing the ones of a previous execution of the same flow.
     */
    @SuppressWarnings("unchecked")
    public MultipleConditionWindow withOutputs(String executionNamespace, String executionFlowId, Map<String, Object> executionOutputs) {
        if (executionOutputs == null || executionOutputs.isEmpty()) {
            return this;
        }

        Map<String, Object> finalOutputs = new HashMap<>();
        if (outputs != null) {
            finalOutputs.putAll(outputs);
        }

        Map<String, Object> namespaceOutputs = new HashMap<>();
        if (finalOutputs.get(executionNamespace) instanceof Map<?, ?> existing) {
            namespaceOutputs.putAll((Map<String, Object>) existing);
        }
        namespaceOutputs.put(executionFlowId, executionOutputs);
        finalOutputs.put(executionNamespace, namespaceOutputs);

        return new MultipleConditionWindow(
            this.tenantId,
            this.namespace,
            this.flowId,
            this.conditionId,
            this.start,
            this.end,
            this.results,
            finalOutputs
        );
    }
}
