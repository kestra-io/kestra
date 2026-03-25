package io.kestra.scheduler.internals;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

import io.kestra.core.exceptions.InvalidTriggerConfigurationException;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.flows.FlowId;
import io.kestra.core.models.flows.FlowInterface;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.triggers.Schedulable;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.scheduler.model.TriggerState;
import io.kestra.core.scheduler.store.TriggerStateStore;
import io.kestra.core.services.PluginDefaultService;
import io.kestra.core.utils.Logs;
import io.kestra.scheduler.SchedulableTriggerFetcher;
import io.kestra.scheduler.models.TriggerEvaluationContext;
import io.kestra.scheduler.stores.FlowMetaStore;

import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Singleton
public class DefaultSchedulableTriggerFetcher implements SchedulableTriggerFetcher {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultSchedulableTriggerFetcher.class);

    // Services
    private final RunContextFactory runContextFactory;

    // Stores
    private final TriggerStateStore triggerStateStore;
    private final FlowMetaStore flowMetaStore;
    private final PluginDefaultService pluginDefaultService;

    public DefaultSchedulableTriggerFetcher(RunContextFactory runContextFactory,
        @Named("cached") TriggerStateStore triggerStateStore,
        FlowMetaStore flowMetaStore,
        PluginDefaultService pluginDefaultService) {
        this.runContextFactory = runContextFactory;
        this.triggerStateStore = triggerStateStore;
        this.flowMetaStore = flowMetaStore;
        this.pluginDefaultService = pluginDefaultService;
    }

    /**
     * {@inheritDoc}
     **/
    @Override
    public List<TriggerEvaluationContext> getSchedulableTriggers(final Clock clock, final ZonedDateTime now, final Set<Integer> assignments) {
        List<TriggerState> triggers = this.triggerStateStore.findTriggersEligibleForScheduling(now, assignments, false);

        return triggers.stream()
            .filter(triggerState -> !triggerState.isDisabled())
            .map(triggerState ->
            {
                Optional<FlowWithSource> maybeFlowTrigger = flowMetaStore.find(
                    FlowId.of(
                        triggerState.getTenantId(),
                        triggerState.getNamespace(),
                        triggerState.getFlowId(),
                        null
                    )
                );

                // Check whether the Flow still exists
                if (maybeFlowTrigger.isEmpty()) {
                    triggerStateStore.delete(triggerState); // Delete triggerState state
                    return null;
                }

                final FlowWithSource flow = pluginDefaultService.injectAllDefaults(maybeFlowTrigger.get(), LOG);

                // Validate that the trigger still exists and is enabled before processing. This check covers several cases:
                // 1. The overall Flow might be disabled 
                // 2. The specific trigger may have been removed.
                // 3. The trigger itself may have been disabled .
                // 
                // 2. and 3. can occur if the Flow has been updated but the associated TriggerEvent
                // has not yet been processed. In these cases, 
                final String triggerId = triggerState.getTriggerId();
                Optional<AbstractTrigger> maybeTrigger = flow.getTriggers().stream().filter(it -> it.getId().equals(triggerId)).findFirst();
                if (flow.isDisabled() || maybeTrigger.isEmpty() || maybeTrigger.get().isDisabled()) {
                    // Skip processing this trigger to avoid acting on stale or invalid trigger.
                    return null;
                }

                final AbstractTrigger trigger = maybeTrigger.get();

                RunContext runContext = runContextFactory.of(flow, trigger);
                ConditionContext conditionContext = ConditionContext.builder().flow(flow).runContext(runContext).build();

                try {
                    if (triggerState.getNextEvaluationDate() == null) {
                        ZonedDateTime nextEvaluationDate = NextEvaluationDate.get(clock, trigger, triggerState.context(), conditionContext);
                        triggerState = triggerState.updateForNextEvaluationDate(clock, nextEvaluationDate);
                    }

                    return new TriggerEvaluationContext(
                        flow,
                        trigger,
                        triggerState,
                        conditionContext.withVariables(Map.of("trigger", Map.of("date", getTriggerDateForConditionContext(clock, now, triggerState, trigger))))
                    );
                } catch (Exception e) {
                    logError(now, conditionContext, flow, trigger, e);
                    if (e instanceof InvalidTriggerConfigurationException) {
                        triggerStateStore.save(triggerState.disabled(clock, true));
                    }
                    return null;
                }
            })
            .filter(Objects::nonNull)
            .toList();
    }

    /**
     * Returns the {@code trigger.date} to be injected into the {@link ConditionContext}.
     */
    private static ZonedDateTime getTriggerDateForConditionContext(Clock clock, ZonedDateTime now, TriggerState state, AbstractTrigger trigger) {
        try {
            ZoneId tz = trigger instanceof Schedulable schedulable ? ZoneId.of(schedulable.getTimezone()) : clock.getZone();
            return Optional.ofNullable(state.getNextEvaluationDate())
                .map(instant -> instant.atZone(tz))
                .orElseGet(() -> now.truncatedTo(ChronoUnit.SECONDS));
        } catch (DateTimeException e) {
            throw new InvalidTriggerConfigurationException();
        }
    }

    private void logError(final ZonedDateTime now, ConditionContext conditionContext, FlowInterface flow, AbstractTrigger trigger, Throwable e) {
        Logger logger = conditionContext.getRunContext().logger();

        Logs.logExecution(
            flow,
            logger,
            Level.ERROR,
            "[trigger: {}] [date: {}] Evaluate Failed with error '{}'",
            trigger.getId(),
            now.truncatedTo(ChronoUnit.SECONDS),
            e.getMessage(),
            e
        );
    }
}
