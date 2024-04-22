package io.kestra.core.schedulers;

import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.flows.Flow;
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

    List<Trigger> findByNextExecutionDateReadyForAllTenants(ZonedDateTime now, ScheduleContextInterface scheduleContext);

    /**
     * Required for Kafka
     */
    List<Trigger> findByNextExecutionDateReadyForGivenFlows(List<Flow> flows, ZonedDateTime now, ScheduleContextInterface scheduleContext);

    /**
     * Required for Kafka
     */
    void unlock(Trigger trigger);
}
