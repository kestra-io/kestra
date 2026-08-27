package io.kestra.core.services;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.kestra.core.context.TestRunContextFactory;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.Label;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.core.execution.Labels;
import io.kestra.plugin.core.trigger.Schedule;

import jakarta.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@KestraTest
class LabelServiceTest {

    @Inject
    private TestRunContextFactory runContextFactory;

    @Test
    void shouldFilterSystemLabels() {
        Flow flow = Flow.builder()
            .labels(List.of(new Label("key", "value"), new Label(Label.SYSTEM_PREFIX + "label", "systemValue")))
            .build();

        List<Label> labels = LabelService.labelsExcludingSystem(flow.getLabels());

        assertThat(labels).hasSize(1);
        assertThat(labels.getFirst()).isEqualTo(new Label("key", "value"));
    }

    @Test
    void shouldReturnLabelsFromTrigger() {
        // Given
        RunContext runContext = runContextFactory.of(Map.of("variable", "variableValue"));
        AbstractTrigger trigger = Schedule.builder()
            .labels(List.of(new Label("scheduleLabel", "scheduleValue"), new Label("variable", "{{variable}}")))
            .build();

        // When
        List<Label> labels = LabelService.fromTrigger(runContext, trigger, Collections.emptyMap());

        // Then only the trigger's labels are returned, so the flow's cannot override the resolved ones
        assertThat(labels).containsExactly(new Label("scheduleLabel", "scheduleValue"), new Label("variable", "variableValue"));
    }

    @Test
    void shouldFilterNonRenderableLabels() {
        RunContext runContext = runContextFactory.of();
        AbstractTrigger trigger = Schedule.builder()
            .labels(List.of(new Label("scheduleLabel", "scheduleValue"), new Label("variable", "{{variable}}")))
            .build();

        List<Label> labels = LabelService.fromTrigger(runContext, trigger, Collections.emptyMap());

        assertThat(labels).containsExactly(new Label("scheduleLabel", "scheduleValue"));
    }

    @Test
    void shouldRenderLabelValueUsingProvidedVariables() {
        RunContext runContext = runContextFactory.of();
        AbstractTrigger trigger = Schedule.builder()
            .labels(List.of(new Label("dynamicLabel", "{{ trigger.executionId }}")))
            .build();

        List<Label> labels = LabelService.fromTrigger(runContext, trigger, Map.of("trigger", Map.of("executionId", "exec-123")));

        assertThat(labels).containsExactly(new Label("dynamicLabel", "exec-123"));
    }

    @Test
    void shouldLetContributedLabelsOverrideTheFlowWhenMergingForAnExecution() {
        // Given a flow and a caller both labelling the same key
        Flow flow = Flow.builder()
            .labels(List.of(new Label("env", "dev"), new Label("owner", "platform")))
            .build();

        // When
        List<Label> labels = LabelService.forExecution(flow, List.of(new Label("env", "prod")), "execId");

        // Then the contributed value wins and the flow's other label is kept
        assertThat(labels).contains(new Label("env", "prod"), new Label("owner", "platform"));
        assertThat(labels).doesNotContain(new Label("env", "dev"));
    }

    @Test
    void shouldStripSystemLabelsFromTheFlowWhenMergingForAnExecution() {
        // Given a flow authoring a system label, which it must not be able to do
        Flow flow = Flow.builder()
            .labels(List.of(new Label(Label.CORRELATION_ID, "forged"), new Label("env", "dev")))
            .build();

        // When
        List<Label> labels = LabelService.forExecution(flow, List.of(), "execId");

        // Then the forged correlation id is dropped and the execution's own is used
        assertThat(labels).contains(new Label(Label.CORRELATION_ID, "execId"), new Label("env", "dev"));
    }

    @Test
    void shouldKeepTheContributedCorrelationIdWhenMergingForAnExecution() {
        // Given a contributed correlation id, as a child execution inherits its parent's
        Flow flow = Flow.builder().build();

        // When
        List<Label> labels = LabelService.forExecution(flow, List.of(new Label(Label.CORRELATION_ID, "parentId")), "execId");

        // Then it is not replaced
        assertThat(labels).containsExactly(new Label(Label.CORRELATION_ID, "parentId"));
    }

    @Test
    void shouldAddACorrelationIdWhenNoneIsPresent() {
        List<Label> labels = LabelService.withCorrelationId(List.of(new Label("env", "prod")), "execId");

        assertThat(labels).containsExactly(new Label("env", "prod"), new Label(Label.CORRELATION_ID, "execId"));
    }

    @Test
    void shouldNotAddACorrelationIdWhenOneIsAlreadyPresent() {
        List<Label> labels = LabelService.withCorrelationId(List.of(new Label(Label.CORRELATION_ID, "parentId")), "execId");

        assertThat(labels).containsExactly(new Label(Label.CORRELATION_ID, "parentId"));
    }

    @Test
    void containsAll() {
        assertFalse(LabelService.containsAll(null, List.of(new Label("key", "value"))));
        assertFalse(LabelService.containsAll(Collections.emptyList(), List.of(new Label("key", "value"))));
        assertFalse(LabelService.containsAll(List.of(new Label("key1", "value1")), List.of(new Label("key2", "value2"))));
        assertTrue(LabelService.containsAll(List.of(new Label("key", "value")), null));
        assertTrue(LabelService.containsAll(List.of(new Label("key", "value")), Collections.emptyList()));
        assertTrue(LabelService.containsAll(List.of(new Label("key1", "value1")), List.of(new Label("key1", "value1"))));
        assertTrue(LabelService.containsAll(List.of(new Label("key1", "value1"), new Label("key2", "value2")), List.of(new Label("key1", "value1"))));
    }

    @Test
    void shouldDropContributedLabelsWhoseKeyGovernancePinned() {
        // Given a caller labelling a key an overriding policy rule force-set, and one of its own
        List<Label> supplied = List.of(new Label("env", "dev"), new Label("owner", "platform"));

        // When
        List<Label> contributed = LabelService.withoutPinned(supplied, Set.of("env"));

        // Then the governed key is the policy's to set and the caller's other label survives
        assertThat(contributed).containsExactly(new Label("owner", "platform"));
    }

    @Test
    void shouldKeepEveryContributedLabelWhenGovernancePinnedNothing() {
        List<Label> contributed = LabelService.withoutPinned(List.of(new Label("env", "dev")), Set.of());

        assertThat(contributed).containsExactly(new Label("env", "dev"));
    }

    @Test
    void shouldReturnAnEmptyListWhenNothingIsContributed() {
        assertThat(LabelService.withoutPinned(null, Set.of("env"))).isEmpty();
    }

    @Test
    void shouldReportAPinnedKeySuppliedWithAnotherValueAsOverridden() {
        // Given a governed flow and a caller supplying its own value for the pinned key
        Flow flow = Flow.builder().labels(List.of(new Label("env", "prod"))).build();

        // When
        Set<String> overridden = LabelService.overriddenPinnedKeys(flow, List.of(new Label("env", "dev")), Set.of("env"));

        // Then
        assertThat(overridden).containsExactly("env");
    }

    @Test
    void shouldNotReportAPinnedKeyEchoingTheFlowValueAsOverridden() {
        // Given a route contributing the flow's own labels along with its own, as the webhook one does
        Flow flow = Flow.builder().labels(List.of(new Label("env", "prod"))).build();
        List<Label> supplied = List.of(new Label("env", "prod"), new Label(Label.FROM, Label.FromLabel.TRIGGER.value));

        // When
        Set<String> overridden = LabelService.overriddenPinnedKeys(flow, supplied, Set.of("env"));

        // Then nothing was overruled, so the caller must not be reported for it
        assertThat(overridden).isEmpty();
    }

    @Test
    void shouldReportNoOverriddenKeyWhenGovernancePinnedNothing() {
        Flow flow = Flow.builder().labels(List.of(new Label("env", "prod"))).build();

        assertThat(LabelService.overriddenPinnedKeys(flow, List.of(new Label("env", "dev")), Set.of())).isEmpty();
    }

    @Test
    void shouldReportAPinnedKeyAbsentFromTheFlowAsOverridden() {
        // Given a pinned key the flow does not carry, which a policy mutation failing open leaves behind
        Flow flow = Flow.builder().build();

        Set<String> overridden = LabelService.overriddenPinnedKeys(flow, List.of(new Label("env", "dev")), Set.of("env"));

        assertThat(overridden).containsExactly("env");
    }

    @Test
    void shouldThrowExceptionOnEmptyLabelValueInLabelsTask() throws Exception {
        Labels task = Labels.builder()
            .id("test")
            .type(Labels.class.getName())
            .labels(Map.of("invalidLabel", "")) //  empty value
            .build();

        RunContext runContext = runContextFactory.of();

        Execution execution = Execution.builder()
            .id("execId")
            .namespace("test.ns")
            .build();

        assertThatThrownBy(() -> task.update(execution, runContext))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Label values cannot be empty");
    }

}