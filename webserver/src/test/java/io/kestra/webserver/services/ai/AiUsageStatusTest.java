package io.kestra.webserver.services.ai;

import io.kestra.core.serializers.JacksonMapper;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The figure a user is shown, and the flag a client warns on. */
class AiUsageStatusTest {
    @Test
    void shouldReportTheTightestAxisWhenBothHaveCeilings() {
        // Given an installation with most of its allowance left and a user with little of theirs
        AiUsageStatus status = new AiUsageStatus(
            "gemini-1", true, Instant.now(), null,
            AiUsageStatus.Axis.of(1_000, 10_000),
            AiUsageStatus.Axis.of(950, 1_000),
            10
        );

        // Then the number shown is the one that will stop the next turn, not the roomier axis
        assertThat(status.remainingPercent()).isEqualTo(5);
        assertThat(status.isWarning()).isTrue();
        assertThat(status.isExceeded()).isFalse();
    }

    @Test
    void shouldReportAnAxisWithNoCeilingAsUnlimitedRatherThanExhausted() {
        // Given spend against an axis nobody set a ceiling for
        AiUsageStatus.Axis axis = AiUsageStatus.Axis.of(50_000, 0);

        // Then it never exceeds and never warns: a zero ceiling reads as unset, not as "no AI at all"
        assertThat(axis.exceeded()).isFalse();
        assertThat(axis.remainingPercent()).isEqualTo(100);
    }

    @Test
    void shouldReportZeroRemainingRatherThanNegativeWhenSpendOvershotTheCeiling() {
        // Given a ceiling passed mid-call — check-then-charge permits exactly this, bounded by one call
        AiUsageStatus.Axis axis = AiUsageStatus.Axis.of(12_000, 10_000);

        // Then the overshoot is clamped rather than reported as a negative percentage
        assertThat(axis.exceeded()).isTrue();
        assertThat(axis.remainingPercent()).isZero();
    }

    @Test
    void shouldShowNothingWhenLimitsAreDisabled() {
        // Given the default: recording, no reporting
        AiUsageStatus status = AiUsageStatus.disabled("gemini-1");

        // Then there is nothing to render or enforce, though the provider is still named
        assertThat(status.enabled()).isFalse();
        assertThat(status.providerId()).isEqualTo("gemini-1");
        assertThat(status.global()).isNull();
        assertThat(status.user()).isNull();
        assertThat(status.isExceeded()).isFalse();
    }

    @Test
    void shouldPublishTheDerivedFlagsOnTheWire() {
        // Given a status whose interesting fields are all derived rather than record components
        AiUsageStatus status = new AiUsageStatus(
            "gemini-1", true, Instant.parse("2026-01-01T00:00:00Z"), null,
            AiUsageStatus.Axis.of(9_500, 10_000),
            null,
            10
        );

        // When it is serialised the way the endpoint answers with it
        Map<String, Object> json = JacksonMapper.toMap(status);

        // Then the derived flags are on the response: they are computed rather than record components, so
        // without them a client would have to recompute the ceiling it is refused by
        assertThat(json).containsEntry("exceeded", false);
        assertThat(json).containsEntry("warning", true);
        assertThat(json).containsEntry("remainingPercent", 5);
    }

    @Test
    void shouldNameWhenTheCeilingStartsAgainInTheRefusalMessage() {
        // Given an exhausted user axis whose period turns over at a known moment
        AiUsageStatus status = new AiUsageStatus(
            "gemini-1", true, Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-02-01T00:00:00Z"),
            AiUsageStatus.Axis.of(1_000, 10_000),
            AiUsageStatus.Axis.of(1_000, 1_000),
            10
        );

        // Then the refusal says when to come back rather than only that the caller has run out
        assertThat(status.exceededMessage())
            .contains("You have reached your AI usage limit")
            .contains("It resets on 2026-02-01T00:00:00Z");
    }

    @Test
    void shouldOmitTheMomentFromTheRefusalMessageWhenItIsUnknown() {
        // Given the same exhausted axis, with no period end to report
        AiUsageStatus status = new AiUsageStatus(
            "gemini-1", true, Instant.parse("2026-01-01T00:00:00Z"), null,
            AiUsageStatus.Axis.of(1_000, 10_000),
            AiUsageStatus.Axis.of(1_000, 1_000),
            10
        );

        // Then the sentence is left off entirely, since a guessed date costs more than silence
        assertThat(status.exceededMessage())
            .contains("You have reached your AI usage limit")
            .doesNotContain("It resets on");
    }
}
