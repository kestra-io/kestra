package io.kestra.scheduler.stores;

import io.kestra.core.scheduler.model.TriggerState;
import io.kestra.core.models.triggers.TriggerId;
import io.kestra.core.repositories.TriggerRepositoryInterface;
import io.kestra.core.scheduler.SchedulerConfiguration;
import io.kestra.core.scheduler.vnodes.VNodes;

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
            .toList();
    }
    
    /**
     * {@inheritDoc}
     **/
    @Override
    public Optional<TriggerState> find(TriggerId triggerId) {
        return triggerRepository.findById(triggerId);
    }
    
    /**
     * {@inheritDoc}
     **/
    @Override
    public void save(TriggerState triggerState) {
        triggerRepository.save(triggerState);
    }
    
    /**
     * {@inheritDoc}
     **/
    @Override
    public void delete(TriggerId triggerId) {
        triggerRepository.findById(triggerId).ifPresent(triggerRepository::delete);
    }
}
