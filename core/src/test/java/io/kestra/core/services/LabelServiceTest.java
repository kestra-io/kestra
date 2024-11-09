package io.kestra.core.services;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.Label;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.plugin.core.trigger.Schedule;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@KestraTest
class LabelServiceTest {

    @Inject
    private RunContextFactory runContextFactory;

    @Test
    void shouldFilterSystemLabels() {
        Flow flow = Flow.builder()
            .labels(List.of(new Label("key", "value"), new Label(Label.SYSTEM_PREFIX + "label", "systemValue")))
            .build();

        List<Label> labels = LabelService.labelsExcludingSystem(flow);

        assertThat(labels, hasSize(1));
        assertThat(labels.getFirst(), is(new Label("key", "value")));
    }

    @Test
    void shouldReturnLabelsFromFlowAndTrigger() {
        RunContext runContext = runContextFactory.of(Map.of("variable", "variableValue"));
        Flow flow = Flow.builder()
            .labels(List.of(new Label("key", "value"), new Label(Label.SYSTEM_PREFIX + "label", "systemValue")))
            .build();
        AbstractTrigger trigger = Schedule.builder()
            .labels(List.of(new Label("scheduleLabel", "scheduleValue"), new Label("variable", "{{variable}}")))
            .build();

        List<Label> labels = LabelService.fromTrigger(runContext, flow, trigger);

        assertThat(labels, hasSize(3));
        assertThat(labels, hasItems(new Label("key", "value"), new Label("scheduleLabel", "scheduleValue"), new Label("variable", "variableValue")));
    }

    @Test
    void shouldFilterNonRenderableLabels() {
        RunContext runContext = runContextFactory.of();
        Flow flow = Flow.builder()
            .labels(List.of(new Label("key", "value"), new Label(Label.SYSTEM_PREFIX + "label", "systemValue")))
            .build();
        AbstractTrigger trigger = Schedule.builder()
            .labels(List.of(new Label("scheduleLabel", "scheduleValue"), new Label("variable", "{{variable}}")))
            .build();

        List<Label> labels = LabelService.fromTrigger(runContext, flow, trigger);

        assertThat(labels, hasSize(2));
        assertThat(labels, hasItems(new Label("key", "value"), new Label("scheduleLabel", "scheduleValue")));
    }
}