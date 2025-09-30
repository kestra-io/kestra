package io.kestra.core.models.triggers;

import io.kestra.core.models.HasUID;
import io.kestra.core.models.flows.FlowId;
import io.kestra.core.utils.IdUtils;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * Represents a unique and global identifier for a trigger.
 */
public interface TriggerId extends HasUID {
    
    String getTenantId();
    
    String getNamespace();
    
    String getFlowId();
    
    String getTriggerId();
    
    /**
     * {@inheritDoc}
     */
    @Override
    default String uid() {
        return IdUtils.fromParts(
            getTenantId(),
            getNamespace(),
            getFlowId(),
            getTriggerId()
        );
    }
    
    /**
     * Static helper method for constructing a new {@link TriggerId}.
     *
     * @return a new {@link TriggerId}.
     */
    static TriggerId of(String tenantId, String namespace, String flowId, String triggerId) {
        return new TriggerId.Default(tenantId, namespace, flowId, triggerId);
    }
    
    /**
     * Static helper method for constructing a new {@link TriggerId}.
     *
     * @param flowId  a {@link FlowId}
     * @param trigger an {@link AbstractTrigger}.
     * @return a new {@link TriggerId}.
     */
    static TriggerId of(FlowId flowId, AbstractTrigger trigger) {
        return new Default(flowId.getTenantId(), flowId.getNamespace(), flowId.getId(), trigger.getId());
    }
    
    /**
     * Static helper method for constructing a new {@link TriggerId}.
     *
     * @param triggerId  a {@link TriggerId}
     * @return a new {@link TriggerId}.
     */
    static TriggerId of(TriggerId triggerId) {
        return new Default(triggerId.getTenantId(), triggerId.getNamespace(), triggerId.getFlowId(), triggerId.getTriggerId());
    }
    
    @Getter
    @AllArgsConstructor
    @EqualsAndHashCode
    class Default implements TriggerId {
        private final String tenantId;
        private final String namespace;
        private final String flowId;
        private final String triggerId;
        
        @Override
        public String toString() {
            return "[tenant=" + tenantId +", namespace= " + namespace + ", flow=" + flowId + ", trigger=" + triggerId + "]";
        }
    }
}
