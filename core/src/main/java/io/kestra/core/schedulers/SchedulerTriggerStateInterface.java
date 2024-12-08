package io.kestra.core.schedulers;

import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.triggers.Trigger;
import io.kestra.core.models.triggers.TriggerContext;
import jakarta.validation.ConstraintViolationException;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

public interface SchedulerTriggerStateInterface {
    Optional<Trigger> findLast(TriggerContext trigger);

    List<Trigger> findAllForAllTenants();

    Trigger save(Trigger trigger, ScheduleContextInterface scheduleContext) throws ConstraintViolationException;

    Trigger create(Trigger trigger) throws ConstraintViolationException;

    Trigger update(Trigger trigger);

    Trigger update(Flow flow, AbstractTrigger abstractTrigger, ConditionContext conditionContext) throws Exception;


    /**
     * Used by the JDBC implementation: find triggers in all tenants.
     */
    List<Trigger> findByNextExecutionDateReadyForAllTenants(ZonedDateTime now, ScheduleContextInterface scheduleContext);

    /**
     * Used by the Kafka implementation: find triggers in the scheduler assigned flow (as in Kafka partition assignment).
     */
    List<Trigger> findByNextExecutionDateReadyForGivenFlows(List<FlowWithSource> flows, ZonedDateTime now, ScheduleContextInterface scheduleContext);
}
