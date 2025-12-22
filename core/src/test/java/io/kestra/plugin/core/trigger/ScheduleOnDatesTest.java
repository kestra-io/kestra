package io.kestra.plugin.core.trigger;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.Label;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.Type;
import io.kestra.core.models.flows.input.StringInput;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.triggers.Trigger;
import io.kestra.core.models.triggers.TriggerContext;
import io.kestra.core.runners.DefaultRunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.runners.RunContextInitializer;
import io.kestra.core.utils.IdUtils;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
class ScheduleOnDatesTest {
    @Inject
    RunContextFactory runContextFactory;

    @Inject
    RunContextInitializer runContextInitializer;

    @Test
    public void shouldReturnNextDateWhenNextEvaluationDateAndAnExistingTriggerDate() throws Exception {
        // given
        var now = ZonedDateTime.now();
        var before = now.minusMinutes(1).truncatedTo(ChronoUnit.SECONDS);
        var after = now.plusMinutes(1).truncatedTo(ChronoUnit.SECONDS);
        var later = now.plusMinutes(2).truncatedTo(ChronoUnit.SECONDS);
        var scheduleOnDates = ScheduleOnDates.builder()
            .id(IdUtils.create())
            .type(ScheduleOnDates.class.getName())
            .interval(null)
            .dates(Property.ofValue(List.of(before, after, later)))
            .build();
        var triggerContext = TriggerContext.builder().date(now).build();
        var trigger = Trigger.of(triggerContext, now);
        var conditionContext =conditionContext(scheduleOnDates);

        // when
        ZonedDateTime nextDate = scheduleOnDates.nextEvaluationDate(conditionContext, Optional.of(trigger));

        // then
        assertThat(nextDate).isEqualTo(after);
    }

    @Test
    public void shouldReturnFirstDateWhenNextEvaluationDateAndNoExistingTriggerDate() {
        // given
        var now = ZonedDateTime.now();
        var before = now.minusMinutes(1).truncatedTo(ChronoUnit.SECONDS);
        var after = now.plusMinutes(1).truncatedTo(ChronoUnit.SECONDS);
        var later = now.plusMinutes(2).truncatedTo(ChronoUnit.SECONDS);
        var scheduleOnDates = ScheduleOnDates.builder()
            .id(IdUtils.create())
            .type(ScheduleOnDates.class.getName())
            .interval(null)
            .dates(Property.ofValue(List.of(before, after, later)))
            .build();
        var conditionContext = conditionContext(scheduleOnDates);

        // when
        ZonedDateTime nextDate = scheduleOnDates.nextEvaluationDate(conditionContext, Optional.empty());

        // then
        assertThat(nextDate).isEqualTo(after);
    }

    @Test
    public void shouldReturnPreviousDateWhenPreviousEvaluationDate() throws Exception {
        // given
        var now = ZonedDateTime.now();
        var first = now.minusMinutes(2).truncatedTo(ChronoUnit.SECONDS);
        var before = now.minusMinutes(1).truncatedTo(ChronoUnit.SECONDS);
        var next = now.plusMinutes(1).truncatedTo(ChronoUnit.SECONDS);
        var scheduleOnDates = ScheduleOnDates.builder()
            .id(IdUtils.create())
            .type(ScheduleOnDates.class.getName())
            .interval(null)
            .dates(Property.ofValue(List.of(first, before, next)))
            .build();
        var conditionContext = conditionContext(scheduleOnDates);

        // when
        ZonedDateTime previousDate = scheduleOnDates.previousEvaluationDate(conditionContext);

        // then
        assertThat(previousDate).isEqualTo(before);
    }

    private ConditionContext conditionContext(AbstractTrigger trigger) {
        io.kestra.core.models.flows.Flow flow = Flow.builder()
            .id(IdUtils.create())
            .namespace("io.kestra.tests")
            .labels(
                List.of(
                    new Label("flow-label-1", "flow-label-1"),
                    new Label("flow-label-2", "flow-label-2")
                )
            )
            .inputs(List.of(
                StringInput.builder().id("input1").type(Type.STRING).required(false).build(),
                StringInput.builder().id("input2").type(Type.STRING).defaults(Property.ofValue("default")).build()
            ))
            .build();

        TriggerContext triggerContext = TriggerContext.builder()
            .namespace(flow.getNamespace())
            .flowId(flow.getId())
            .triggerId(trigger.getId())
            .build();

        return ConditionContext.builder()
            .runContext(runContextInitializer.forScheduler((DefaultRunContext) runContextFactory.of(), triggerContext, trigger))
            .flow(flow)
            .build();
    }
}