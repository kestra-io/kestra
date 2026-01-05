package io.kestra.core.runners;

import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.triggers.Trigger;
import io.kestra.core.models.triggers.TriggerContext;
import io.kestra.core.queues.QueueException;
import jakarta.validation.ConstraintViolationException;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

public interface SchedulerTriggerStateInterface {
    Optional<Trigger> findLast(TriggerContext trigger);

    List<Trigger> findAllForAllTenants();

    Trigger save(Trigger trigger, ScheduleContextInterface scheduleContext) throws ConstraintViolationException;

    Trigger create(Trigger trigger) throws ConstraintViolationException;

    Trigger save(Trigger trigger, ScheduleContextInterface scheduleContext, String headerContent) throws ConstraintViolationException;

    Trigger create(Trigger trigger, String headerContent) throws ConstraintViolationException;

    Trigger update(Trigger trigger);

    Trigger update(FlowWithSource flow, AbstractTrigger abstractTrigger, ConditionContext conditionContext) throws Exception;

    /**
     * QueueException required for Kafka implementation
     */
    void delete(Trigger trigger) throws QueueException;
    /**
     * Used by the JDBC implementation: find triggers in all tenants.
     */
    List<Trigger> findByNextExecutionDateReadyForAllTenants(ZonedDateTime now, ScheduleContextInterface scheduleContext);

    /**
     * Used by the JDBC implementation: find ready but locked triggers
     */
    List<Trigger> findByNextExecutionDateReadyButLockedTriggers(ZonedDateTime now);

    /**
     * Used by the Kafka implementation: find triggers in the scheduler assigned flow (as in Kafka partition assignment).
     */
    List<Trigger> findByNextExecutionDateReadyForGivenFlows(List<FlowWithSource> flows, ZonedDateTime now, ScheduleContextInterface scheduleContext);
}
