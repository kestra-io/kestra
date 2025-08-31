package io.kestra.core.models.flows;

import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.triggers.Trigger;
import io.kestra.core.utils.IdUtils;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.Optional;

/**
 * Represents a unique and global identifier for a flow.
 */
public interface FlowId {

    String getId();

    String getNamespace();

    Integer getRevision();

    String getTenantId();


    static String uid(FlowId flow) {
        return uid(flow.getTenantId(), flow.getNamespace(), flow.getId(), Optional.ofNullable(flow.getRevision()));
    }

    static String uid(String tenantId, String namespace, String id, Optional<Integer> revision) {
        return of(tenantId, namespace, id, revision.orElse(-1)).toString();
    }

    static String uidWithoutRevision(FlowId flow) {
        return of(flow.getTenantId(), flow.getNamespace(), flow.getId(), null).toString();
    }

    static String uidWithoutRevision(String tenantId, String namespace, String id) {
        return of(tenantId, namespace, id,null).toString();
    }

    static String uid(Trigger trigger) {
        return of(trigger.getTenantId(), trigger.getNamespace(), trigger.getFlowId(), null).toString();
    }

    static String uidWithoutRevision(Execution execution) {
        return of(execution.getTenantId(), execution.getNamespace(), execution.getFlowId(), null).toString();
    }

    /**
     * Static helper method for constructing a new {@link FlowId}.
     *
     * @return   a new {@link FlowId}.
     */
    static FlowId of(String tenantId, String namespace, String id, Integer revision) {
        return new Default(tenantId, namespace, id, revision);
    }

    @Getter
    @AllArgsConstructor
    @EqualsAndHashCode
    class Default implements FlowId {
        private final String tenantId;
        private final String namespace;
        private final String id;
        private final Integer revision;

        @Override
        public String toString() {
            return IdUtils.fromParts(tenantId, namespace, id, Optional.ofNullable(revision).map(String::valueOf).orElse(null));
        }
    }
}
