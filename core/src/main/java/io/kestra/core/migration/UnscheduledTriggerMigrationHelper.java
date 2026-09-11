package io.kestra.core.migration;

import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;

import io.kestra.core.models.flows.State;
import io.kestra.core.models.triggers.TriggerId;
import io.kestra.core.scheduler.model.TriggerState;
import io.kestra.core.scheduler.model.TriggerType;
import io.kestra.core.scheduler.vnodes.VNodes;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.utils.ListUtils;

import lombok.extern.slf4j.Slf4j;

/**
 * Builds the {@link TriggerState} of the triggers the scheduler never evaluates, from a flow source.
 * <p>
 * Shared by the JDBC and the Elasticsearch implementations of the {@code 2.0.26-unscheduled-triggers}
 * back-fill, which are one migration over two backends.
 */
@Slf4j
public final class UnscheduledTriggerMigrationHelper {

    /**
     * The trigger types the scheduler does not evaluate, as of 2.0.26.
     * <p>
     * Matched by name rather than resolved through {@code TriggerType.from(AbstractTrigger)}, which would need
     * the plugin registry a migration runs too early to rely on. The list needs no maintenance: this migration
     * is frozen at the version it targets, and a kind added later gets its state from {@code FlowService} on
     * the next flow save.
     */
    private static final Set<String> UNSCHEDULED_TRIGGER_TYPES = Set.of(
        "io.kestra.plugin.core.trigger.Webhook",
        "io.kestra.plugin.core.trigger.Flow",
        "io.kestra.plugin.core.trigger.McpToolTrigger",
        "io.kestra.plugin.ee.assets.EventTrigger"
    );

    private static final TypeReference<RawFlow> RAW_FLOW_TYPE = new TypeReference<>() {
    };

    private UnscheduledTriggerMigrationHelper() {
    }

    /**
     * Returns the states to create for the unscheduled triggers the given flow declares, or an empty list when
     * it declares none or its source cannot be parsed.
     *
     * @param source the flow source, as YAML.
     * @param vNodeCount the configured number of virtual nodes.
     */
    public static List<TriggerState> unscheduledTriggerStates(
        String tenantId,
        String namespace,
        String flowId,
        String source,
        int vNodeCount) {
        RawFlow flow;
        try {
            flow = JacksonMapper.ofYaml().readValue(source, RAW_FLOW_TYPE);
        } catch (Exception e) {
            log.warn("Skipping flow '{}' of namespace '{}': its source cannot be parsed.", flowId, namespace, e);
            return List.of();
        }

        return ListUtils.emptyOnNull(flow.triggers()).stream()
            .filter(trigger -> trigger.id() != null && UNSCHEDULED_TRIGGER_TYPES.contains(trigger.type()))
            .map(trigger ->
            {
                TriggerId id = TriggerId.of(tenantId, namespace, flowId, trigger.id());
                return TriggerState.of(
                    id,
                    TriggerType.UNSCHEDULED,
                    trigger.stopAfter(),
                    Boolean.TRUE.equals(trigger.disabled()),
                    VNodes.computeVNodeFromTrigger(id, vNodeCount)
                );
            })
            .toList();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RawFlow(List<RawTrigger> triggers) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RawTrigger(String id, String type, Boolean disabled, List<State.Type> stopAfter) {
    }
}
