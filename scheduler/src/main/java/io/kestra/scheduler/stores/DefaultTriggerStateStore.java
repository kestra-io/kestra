package io.kestra.scheduler.stores;

import com.google.common.annotations.VisibleForTesting;
import io.kestra.core.models.triggers.Trigger;
import io.kestra.core.models.triggers.TriggerId;
import io.kestra.core.repositories.TriggerRepositoryInterface;
import io.kestra.scheduler.SchedulerConfiguration;
import io.kestra.scheduler.vnodes.VNodes;
import io.kestra.scheduler.model.TriggerState;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The  {@link TriggerStateStore} implementation.
 * <p>
 * Implementation based on the {@link TriggerRepositoryInterface}.
 */
public class DefaultTriggerStateStore implements TriggerStateStore {
    
    private final TriggerRepositoryInterface triggerRepository;
    private final SchedulerConfiguration schedulerConfiguration;
    
    public DefaultTriggerStateStore(SchedulerConfiguration schedulerConfiguration,
                                    TriggerRepositoryInterface triggerRepository) {
        this.triggerRepository = triggerRepository;
        this.schedulerConfiguration = schedulerConfiguration;
    }
    
    /**
     * {@inheritDoc}
     **/
    @Override
    public List<TriggerState> findTriggersEligibleForScheduling(ZonedDateTime now, Set<Integer> vNodes, boolean locked) {
        return triggerRepository.findTriggersEligibleForScheduling(now, vNodes, locked)
            .stream()
            .map(TriggerStateAdapter::fromTrigger)
            .toList();
    }
    
    /**
     * {@inheritDoc}
     **/
    @Override
    public List<TriggerState> findAllForVNodes(final Set<Integer> vNodes) {
        return this.triggerRepository.findAllForAllTenants()
            .stream()
            .filter(f -> vNodes.contains(VNodes.computeVNodeFromTrigger(TriggerId.of(f), schedulerConfiguration.vnodes())))
            .map(TriggerStateAdapter::fromTrigger)
            .toList();
    }
    
    /**
     * {@inheritDoc}
     **/
    @Override
    public Optional<TriggerState> find(TriggerId triggerId) {
        return triggerRepository.findLast(triggerId).map(TriggerStateAdapter::fromTrigger);
    }
    
    /**
     * {@inheritDoc}
     **/
    @Override
    public void save(TriggerState triggerState) {
        Trigger entity = TriggerStateAdapter.toTrigger(triggerState);
        triggerRepository.save(entity);
    }
    
    /**
     * {@inheritDoc}
     **/
    @Override
    public void delete(TriggerId triggerId) {
        triggerRepository.findLast(triggerId).ifPresent(triggerRepository::delete);
    }
    
    /**
     * TODO Temporary adapter.
     */
    @VisibleForTesting
    static class TriggerStateAdapter {
        
        public static TriggerState fromTrigger(Trigger trigger) {
            return new TriggerState(
                trigger.getTenantId(),
                trigger.getNamespace(),
                trigger.getFlowId(),
                trigger.getTriggerId(),
                trigger.getUpdatedDate(),
                trigger.getDate(),
                trigger.getNextExecutionDate(),
                trigger.getBackfill(),
                trigger.getStopAfter(),
                trigger.getDisabled(),
                trigger.getVnode(),
                trigger.getLocked() != null ? trigger.getLocked() : trigger.getExecutionId() != null
            );
        }
        
        public static Trigger toTrigger(TriggerState triggerState) {
            return Trigger.builder()
                .tenantId(triggerState.getTenantId())
                .namespace(triggerState.getNamespace())
                .flowId(triggerState.getFlowId())
                .triggerId(triggerState.getTriggerId())
                .date(triggerState.getEvaluatedAt())
                .backfill(triggerState.getBackfill())
                .stopAfter(triggerState.getStopAfter())
                .disabled(triggerState.getDisabled())
                .nextExecutionDate(triggerState.getNextEvaluationDate())
                .vnode(triggerState.getVnode())
                .locked(triggerState.getLocked())
                .build();
        }
    }
}
