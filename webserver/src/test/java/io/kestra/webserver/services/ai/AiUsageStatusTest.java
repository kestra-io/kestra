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

        // Then the single number shown is the one that will actually stop the next turn. Showing the roomier axis
        // would have a user reading "90% left" right up to being refused.
        assertThat(status.remainingPercent()).isEqualTo(5);
        assertThat(status.isWarning()).isTrue();
        assertThat(status.isExceeded()).isFalse();
    }

    @Test
    void shouldReportAnAxisWithNoCeilingAsUnlimitedRatherThanExhausted() {
        // Given spend against an axis nobody set a ceiling for — the per-user axis of an installation that only
        // capped the whole install
        AiUsageStatus.Axis axis = AiUsageStatus.Axis.of(50_000, 0);

        // Then it is never exceeded and never warns. A zero ceiling is far more likely to be an unset property
        // than a request for no AI at all, and reading it as the latter would refuse every turn.
        assertThat(axis.exceeded()).isFalse();
        assertThat(axis.remainingPercent()).isEqualTo(100);
    }

    @Test
    void shouldReportZeroRemainingRatherThanNegativeWhenSpendOvershotTheCeiling() {
        // Given a ceiling passed mid-call — check-then-charge permits exactly this, bounded by one call
        AiUsageStatus.Axis axis = AiUsageStatus.Axis.of(12_000, 10_000);

        // Then the overshoot is not reported as a negative percentage, which a progress bar would render as
        // something between wrong and alarming
        assertThat(axis.exceeded()).isTrue();
        assertThat(axis.remainingPercent()).isZero();
    }

    @Test
    void shouldShowNothingWhenLimitsAreDisabled() {
        // Given the default: recording, no reporting
        AiUsageStatus status = AiUsageStatus.disabled("gemini-1");

        // Then there is no figure to render and nothing to enforce, but the provider is still named so a client
        // knows which provider answered
        assertThat(status.enabled()).isFalse();
        assertThat(status.providerId()).isEqualTo("gemini-1");
        assertThat(status.global()).isNull();
        assertThat(status.user()).isNull();
        assertThat(status.isExceeded()).isFalse();
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

        // Then the sentence is left off entirely. A user who is told a date will come back on it, so a guessed
        // one costs more than the silence does.
        assertThat(status.exceededMessage())
            .contains("You have reached your AI usage limit")
            .doesNotContain("It resets on");
    }
}
