package io.kestra.core.models.triggers;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.kestra.core.exceptions.FlowProcessingException;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.FlowWithException;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.serializers.YamlParser;
import io.kestra.core.services.FlowParsingService;

import jakarta.validation.ConstraintViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A flow is read back from the repositories with the lenient JSON mapper, and re-parsed for the executor and
 * the scheduler with the lenient YAML one. Properties removed from the trigger model — pre-2.0 `conditions` /
 * `preconditions` — must not be dropped silently on either path: the trigger would then run without the
 * filtering the user configured. See {@link AbstractTrigger#failOnUnknownProperty}.
 */
class AbstractTriggerUnknownPropertyTest {

    private static final String LEGACY_CONDITIONS_SOURCE = """
        id: legacy
        namespace: qa.deprecated
        tasks:
          - id: hello
            type: io.kestra.plugin.core.log.Log
            message: hello
        triggers:
          - id: on_foreach
            type: io.kestra.plugin.core.trigger.Flow
            states: [SUCCESS, FAILED]
            conditions:
              - type: io.kestra.plugin.core.condition.ExecutionFlow
                namespace: qa.deprecated
                flowId: dep-foreach
        """;

    private static final String FRAMED_MESSAGE =
        "Unrecognized property \"conditions\" on trigger \"on_foreach\" (io.kestra.plugin.core.trigger.Flow): "
            + "trigger conditions were replaced by \"when\" in 2.0 (and by \"dependsOn\" on io.kestra.plugin.core.trigger.Flow) "
            + "- see the migration guide https://kestra.io/docs/migration-guide/v2.0.0";

    /**
     * Builds the trigger as an ordered map: the message names the trigger only if its `id` was read before the
     * offending property, which is the order both the YAML users write and the stored flow JSON have.
     */
    private static Map<String, Object> trigger(Object... keyValues) {
        Map<String, Object> trigger = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            trigger.put((String) keyValues[i], keyValues[i + 1]);
        }
        return trigger;
    }

    private static Map<String, Object> flowWithTrigger(Map<String, Object> trigger) {
        return Map.of(
            "id", "legacy",
            "namespace", "qa.deprecated",
            "revision", 1,
            "deleted", false,
            "tasks", List.of(Map.of("id", "hello", "type", "io.kestra.plugin.core.log.Log", "message", "hello")),
            "triggers", List.of(trigger)
        );
    }

    private static Map<String, Object> legacyConditionsFlow() {
        return flowWithTrigger(
            trigger(
                "id", "on_foreach",
                "type", "io.kestra.plugin.core.trigger.Flow",
                "states", List.of("SUCCESS", "FAILED"),
                "conditions", List.of(
                    Map.of(
                        "type", "io.kestra.plugin.core.condition.ExecutionFlow",
                        "namespace", "qa.deprecated",
                        "flowId", "dep-foreach"
                    )
                )
            )
        );
    }

    private static FlowWithSource storedFlow() {
        return FlowWithSource.builder()
            .tenantId("main")
            .namespace("qa.deprecated")
            .id("legacy")
            .revision(1)
            .source(LEGACY_CONDITIONS_SOURCE)
            .build();
    }

    @Test
    void shouldRejectLegacyTriggerConditions() {
        assertThatThrownBy(() -> JacksonMapper.ofJson().convertValue(legacyConditionsFlow(), Flow.class))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unrecognized property \"conditions\" on trigger \"on_foreach\"")
            .hasMessageContaining("io.kestra.plugin.core.trigger.Flow")
            .hasMessageContaining("replaced by \"when\"")
            .hasMessageContaining("https://kestra.io/docs/migration-guide/v2.0.0");
    }

    @Test
    void shouldRejectLegacyFlowTriggerPreconditions() {
        Map<String, Object> flow = flowWithTrigger(
            trigger(
                "id", "on_foreach",
                "type", "io.kestra.plugin.core.trigger.Flow",
                "states", List.of("SUCCESS"),
                "preconditions", Map.of("id", "dep", "flows", List.of(Map.of("namespace", "qa.deprecated", "flowId", "dep-foreach")))
            )
        );

        assertThatThrownBy(() -> JacksonMapper.ofJson().convertValue(flow, Flow.class))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unrecognized property \"preconditions\" on trigger \"on_foreach\"")
            .hasMessageContaining("replaced by \"dependsOn\"");
    }

    @Test
    void shouldRejectLegacyScheduleConditions() {
        Map<String, Object> flow = flowWithTrigger(
            trigger(
                "id", "every_minute",
                "type", "io.kestra.plugin.core.trigger.Schedule",
                "cron", "* * * * *",
                "conditions", List.of(Map.of("type", "io.kestra.plugin.core.condition.DayWeek", "dayOfWeek", "MONDAY"))
            )
        );

        assertThatThrownBy(() -> JacksonMapper.ofJson().convertValue(flow, Flow.class))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unrecognized property \"conditions\" on trigger \"every_minute\"");
    }

    /**
     * Strictness is not limited to the properties 2.0 removed: any property the trigger — core or plugin
     * defined — does not declare fails the same way, so a renamed or dropped plugin property cannot silently
     * change what a stored trigger does either.
     */
    @Test
    void shouldRejectAnyUnknownTriggerProperty() {
        Map<String, Object> flow = flowWithTrigger(
            trigger(
                "id", "every_minute",
                "type", "io.kestra.plugin.core.trigger.Schedule",
                "cron", "* * * * *",
                "notAPropertyOfSchedule", "boom"
            )
        );

        assertThatThrownBy(() -> JacksonMapper.ofJson().convertValue(flow, Flow.class))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unrecognized property \"notAPropertyOfSchedule\" on trigger \"every_minute\"")
            .hasMessageContaining("this property does not exist on this trigger");
    }

    /**
     * The trigger id is only known if it was read before the offending property. When it was not, the message
     * still names the property and the trigger type, and Jackson's reference chain gives the position of the
     * trigger in the list.
     */
    @Test
    void shouldFallBackToUnknownWhenTheRemovedPropertyPrecedesTheId() {
        Map<String, Object> flow = flowWithTrigger(
            trigger(
                "conditions", List.of(Map.of("type", "io.kestra.plugin.core.condition.DayWeek", "dayOfWeek", "MONDAY")),
                "id", "every_minute",
                "type", "io.kestra.plugin.core.trigger.Schedule",
                "cron", "* * * * *"
            )
        );

        assertThatThrownBy(() -> JacksonMapper.ofJson().convertValue(flow, Flow.class))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unrecognized property \"conditions\" on trigger \"<unknown>\"")
            .hasMessageContaining("io.kestra.plugin.core.trigger.Schedule[\"conditions\"]");
    }

    @Test
    void shouldStillDeserializeAValidTrigger() {
        Map<String, Object> flow = flowWithTrigger(
            trigger(
                "id", "on_foreach",
                "type", "io.kestra.plugin.core.trigger.Flow",
                "states", List.of("SUCCESS"),
                "when", "{{ true }}"
            )
        );

        assertThatCode(() ->
        {
            Flow parsed = JacksonMapper.ofJson().convertValue(flow, Flow.class);
            assertThat(parsed.getTriggers()).hasSize(1);
            assertThat(parsed.getTriggers().getFirst().getId()).isEqualTo("on_foreach");
        }).doesNotThrowAnyException();
    }

    /**
     * The YAML path — {@code YamlParser}, used by validation and by the runtime re-parse — reports the framed
     * message as the violation instead of wrapping it in a generic parsing error.
     */
    @Test
    void shouldReportTheFramedMessageAsAConstraintViolationOnTheYamlPath() {
        assertThatThrownBy(() -> YamlParser.parse(LEGACY_CONDITIONS_SOURCE, FlowWithSource.class, false))
            .isInstanceOf(ConstraintViolationException.class)
            .hasMessage(FRAMED_MESSAGE);
    }

    /**
     * The runtime path the executor and the scheduler go through: a stored flow whose trigger carries a removed
     * property cannot be parsed for runtime, so callers keep the {@code FlowWithException} the repository gave
     * them rather than a trigger that would fire on everything.
     */
    @Test
    void shouldFailToParseForRuntime() {
        assertThatThrownBy(() -> new FlowParsingService().parseForRuntime(storedFlow()))
            .isInstanceOf(FlowProcessingException.class)
            .hasMessageContaining(FRAMED_MESSAGE);
    }

    /**
     * What the API and the UI show: the framed message prefixed with the flow, without the
     * {@code (through reference chain: …)} tail Jackson appends — the trigger id, its type and the property
     * already say where to look.
     */
    @Test
    void shouldSurfaceTheFramedMessageOnTheFlowWithException() {
        Exception jacksonWrapped = jacksonWrappedFailure();
        // the wrapping this must not surface
        assertThat(jacksonWrapped.getMessage()).contains("(through reference chain:");

        FlowWithException flowWithException = FlowWithException.from(storedFlow(), jacksonWrapped);

        assertThat(flowWithException.getException()).isEqualTo("Flow 'qa.deprecated/legacy': " + FRAMED_MESSAGE);
    }

    private static Exception jacksonWrappedFailure() {
        try {
            JacksonMapper.ofJson().convertValue(legacyConditionsFlow(), Flow.class);
            throw new AssertionError("Expected the legacy trigger property to be rejected");
        } catch (IllegalArgumentException e) {
            return e;
        }
    }
}
