package io.kestra.core.models.triggers;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.kestra.core.exceptions.FlowProcessingException;
import io.kestra.core.models.flows.Flow;
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
 * the scheduler with the lenient YAML one. The pre-2.0 filtering properties — `conditions`, `preconditions`,
 * `scheduleConditions` — must not be dropped silently on either path: the trigger would then run without the
 * filtering the user configured and match far more than it used to. See
 * {@link AbstractTrigger#rejectRemovedConditions}.
 */
class AbstractTriggerRemovedPropertyTest {

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

    @Test
    void shouldRejectLegacyTriggerConditions() {
        assertThatThrownBy(() -> JacksonMapper.ofJson().convertValue(legacyConditionsFlow(), Flow.class))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Trigger \"on_foreach\" (io.kestra.plugin.core.trigger.Flow) uses \"conditions\"")
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
            .hasMessageContaining("uses \"preconditions\"")
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
            .hasMessageContaining("Trigger \"every_minute\" (io.kestra.plugin.core.trigger.Schedule) uses \"conditions\"");
    }

    /**
     * `scheduleConditions` was the 1.x predecessor of `conditions` on a Schedule. It filtered dates, so
     * dropping it makes the schedule fire on every tick it used to skip — the same failure as `conditions`.
     */
    @Test
    void shouldRejectLegacyScheduleConditionsUnderItsOlderName() {
        Map<String, Object> flow = flowWithTrigger(
            trigger(
                "id", "every_minute",
                "type", "io.kestra.plugin.core.trigger.Schedule",
                "cron", "* * * * *",
                "scheduleConditions", List.of(Map.of("type", "io.kestra.plugin.core.condition.DayWeek", "dayOfWeek", "MONDAY"))
            )
        );

        assertThatThrownBy(() -> JacksonMapper.ofJson().convertValue(flow, Flow.class))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("uses \"scheduleConditions\"");
    }

    /**
     * Only the removed <em>filtering</em> properties are rejected. A property that moved or was renamed —
     * `workerGroup` became `workerSelector`, `pluginDefaultsRef` and `Schedule.backfill` are gone — keeps
     * being dropped by the lenient read path: it does not change what the trigger matches, so bricking every
     * 1.x flow that carries one would cost more than the drop.
     */
    @Test
    void shouldStillDropAPropertyThatDoesNotChangeWhatTheTriggerMatches() {
        Map<String, Object> flow = flowWithTrigger(
            trigger(
                "id", "every_minute",
                "type", "io.kestra.plugin.core.trigger.Schedule",
                "cron", "* * * * *",
                "workerGroup", Map.of("key", "gpu"),
                "pluginDefaultsRef", "some-ref",
                "backfill", Map.of("start", "2024-01-01T00:00:00Z")
            )
        );

        assertThatCode(() -> {
            Flow parsed = JacksonMapper.ofJson().convertValue(flow, Flow.class);
            assertThat(parsed.getTriggers()).hasSize(1);
            assertThat(parsed.getTriggers().getFirst().getId()).isEqualTo("every_minute");
        }).doesNotThrowAnyException();
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
            .hasMessageContaining("Trigger \"<unknown>\"")
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

        assertThatCode(() -> {
            Flow parsed = JacksonMapper.ofJson().convertValue(flow, Flow.class);
            assertThat(parsed.getTriggers()).hasSize(1);
            assertThat(parsed.getTriggers().getFirst().getId()).isEqualTo("on_foreach");
        }).doesNotThrowAnyException();
    }

    /**
     * The YAML path — {@code YamlParser}, used by validation and by the runtime re-parse — turns it into the
     * constraint violation a save reports as a 422.
     */
    @Test
    void shouldReportAConstraintViolationOnTheYamlPath() {
        assertThatThrownBy(() -> YamlParser.parse(LEGACY_CONDITIONS_SOURCE, FlowWithSource.class, false))
            .isInstanceOf(ConstraintViolationException.class)
            .hasMessageContaining("uses \"conditions\"")
            .hasMessageContaining("replaced by \"when\"");
    }

    /**
     * The runtime path the executor and the scheduler go through: a stored flow whose trigger carries a removed
     * property cannot be parsed for runtime, so callers keep the {@code FlowWithException} the repository gave
     * them rather than a trigger that would fire on everything.
     */
    @Test
    void shouldFailToParseForRuntime() {
        FlowWithSource stored = FlowWithSource.builder()
            .tenantId("main")
            .namespace("qa.deprecated")
            .id("legacy")
            .revision(1)
            .source(LEGACY_CONDITIONS_SOURCE)
            .build();

        assertThatThrownBy(() -> new FlowParsingService().parseForRuntime(stored))
            .isInstanceOf(FlowProcessingException.class)
            .hasMessageContaining("uses \"conditions\"");
    }
}
