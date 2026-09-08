package io.kestra.core.models.triggers;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.serializers.JacksonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A flow is read back from the repositories with the lenient JSON mapper. Properties removed from the trigger
 * model — pre-2.0 `conditions` / `preconditions` — must not be dropped silently there: the trigger would then
 * run without the filtering the user configured. See {@link AbstractTrigger#failOnUnknownProperty}.
 */
@KestraTest
class AbstractTriggerUnknownPropertyTest {

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

    @Test
    void shouldRejectLegacyTriggerConditions() {
        Map<String, Object> flow = flowWithTrigger(
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

        assertThatThrownBy(() -> JacksonMapper.ofJson().convertValue(flow, Flow.class))
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
}
