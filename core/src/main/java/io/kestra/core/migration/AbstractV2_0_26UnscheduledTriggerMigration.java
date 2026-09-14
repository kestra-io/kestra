package io.kestra.core.migration;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;

import io.kestra.core.models.flows.State;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.triggers.TriggerId;
import io.kestra.core.models.triggers.WorkerTriggerInterface;
import io.kestra.core.scheduler.SchedulerConfiguration;
import io.kestra.core.scheduler.model.TriggerState;
import io.kestra.core.scheduler.model.TriggerType;
import io.kestra.core.scheduler.vnodes.VNodes;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.utils.ListUtils;

import lombok.extern.slf4j.Slf4j;

/**
 * Creates the missing {@link TriggerState} of the triggers the scheduler does not evaluate — webhook,
 * MCP-tool, flow and asset event triggers — of the flows that already exist.
 * <p>
 * Until 2.0.26 only the triggers the scheduler evaluates held a state, so the others could not be listed on
 * the triggers page. The scheduler now creates their state on every flow mutation; this back-fills the
 * flows that already exist, so an upgrade does not require re-saving them.
 *
 * <p>
 * One migration over two backends: a JDBC subclass and an Elasticsearch one, mutually exclusive through their
 * {@code @Requires}, sharing the script id that makes them one migration rather than two. Everything but the
 * storage sits here so the two cannot drift — most of all the rule below deciding which of a flow's triggers
 * needs a state, which has to give the same answer on both.
 */
@Slf4j
public abstract class AbstractV2_0_26UnscheduledTriggerMigration implements MigrationScript {

    private static final TypeReference<RawFlow> RAW_FLOW_TYPE = new TypeReference<>() {
    };

    private final int vnodes;

    protected AbstractV2_0_26UnscheduledTriggerMigration(final SchedulerConfiguration schedulerConfiguration) {
        this.vnodes = schedulerConfiguration.vnodes();
    }

    @Override
    public String scriptId() {
        return "2.0.26-unscheduled-triggers";
    }

    @Override
    public String description() {
        return "Create the missing trigger states of the webhook, MCP and flow triggers of existing flows";
    }

    @Override
    public String checksum() {
        // Java-only migration, no SQL resource file to checksum.
        return null;
    }

    /**
     * Returns the states to create for the unscheduled triggers the given flow declares, or an empty list when
     * it declares none or its source cannot be parsed.
     *
     * @param source the flow source, as YAML.
     */
    protected List<TriggerState> unscheduledTriggerStates(String tenantId, String namespace, String flowId, String source) {
        RawFlow flow;
        try {
            flow = JacksonMapper.ofYaml().readValue(source, RAW_FLOW_TYPE);
        } catch (Exception e) {
            log.warn("Skipping flow '{}' of namespace '{}': its source cannot be parsed.", flowId, namespace, e);
            return List.of();
        }

        return ListUtils.emptyOnNull(flow.triggers()).stream()
            .filter(trigger -> trigger.id() != null && isUnscheduled(trigger.type()))
            .map(trigger ->
            {
                TriggerId id = TriggerId.of(tenantId, namespace, flowId, trigger.id());
                return TriggerState.of(
                    id,
                    TriggerType.UNSCHEDULED,
                    trigger.stopAfter(),
                    VNodes.computeVNodeFromTrigger(id, vnodes)
                ).sourceDisabled(null, Boolean.TRUE.equals(trigger.disabled()));
            })
            .toList();
    }

    /**
     * Whether the scheduler holds a state for a trigger of this type but never evaluates it, resolved from the
     * class rather than matched against a list of names.
     * <p>
     * This is the same rule {@link TriggerType#from(AbstractTrigger)} applies at runtime — every kind the
     * scheduler evaluates is a {@link WorkerTriggerInterface} — so the states this creates cannot disagree with
     * the ones the scheduler creates from then on. It also keeps the edition's own kinds working without
     * core having to name them: EE's asset event trigger classifies itself, and is simply absent in OSS.
     * <p>
     * Resolved without initializing the class, and a type that cannot be loaded is left alone: a migration runs
     * before the plugin registry, so an external plugin's trigger is unresolvable here either way, and one that
     * needs a state gets it on the next save of its flow.
     */
    private static boolean isUnscheduled(String type) {
        if (type == null) {
            return false;
        }

        try {
            Class<?> clazz = Class.forName(type, false, AbstractV2_0_26UnscheduledTriggerMigration.class.getClassLoader());
            return AbstractTrigger.class.isAssignableFrom(clazz) && !WorkerTriggerInterface.class.isAssignableFrom(clazz);
        } catch (ClassNotFoundException | LinkageError e) {
            log.debug("Skipping trigger of type '{}': it cannot be resolved at migration time.", type);
            return false;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RawFlow(List<RawTrigger> triggers) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record RawTrigger(String id, String type, Boolean disabled, List<State.Type> stopAfter) {
    }
}
