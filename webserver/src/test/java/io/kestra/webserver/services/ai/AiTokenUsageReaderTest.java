package io.kestra.webserver.services.ai;

import io.kestra.core.ai.usage.models.AiUsageTotals;

import dev.langchain4j.model.googleai.GoogleAiGeminiTokenUsage;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The counts every ceiling is computed from, so a misreading here is a wrong bill rather than a wrong log line.
 */
class AiTokenUsageReaderTest {
    private final AiTokenUsageReader reader = new AiTokenUsageReader();

    @Test
    void shouldKeepThoughtsApartFromOutputWhenGeminiReportsBoth() {
        // Given a Gemini turn that thought far more than it said — the ordinary shape for a thinking model
        TokenUsage usage = GoogleAiGeminiTokenUsage.builder()
            .inputTokenCount(1_000)
            .outputTokenCount(19)
            .totalTokenCount(1_147)
            .cachedContentTokenCount(400)
            .thoughtsTokenCount(128)
            .build();

        // When
        AiUsageTotals counts = reader.read(usage);

        // Then thoughts are their own count, because Gemini's outputTokenCount is candidatesTokenCount and
        // excludes them. Treating the two as overlapping would drop 128 of the 147 generated tokens — and
        // generated tokens are the expensive ones, at six times the input rate.
        assertThat(counts.completionTokens()).isEqualTo(19);
        assertThat(counts.thoughtTokens()).isEqualTo(128);
        assertThat(counts.outputTokens()).isEqualTo(147);
    }

    @Test
    void shouldSplitTheCachedShareOutOfThePromptWhenGeminiReportsIt() {
        // Given a prompt of which most was served from cache
        TokenUsage usage = GoogleAiGeminiTokenUsage.builder()
            .inputTokenCount(1_000)
            .outputTokenCount(50)
            .cachedContentTokenCount(900)
            .build();

        // When
        AiUsageTotals counts = reader.read(usage);

        // Then the prompt total stays whole and the cached share is carried alongside it. Gemini's
        // promptTokenCount already includes the cached tokens, so subtracting here as well would count them
        // twice against the cheap rate and lose 900 tokens from the total.
        assertThat(counts.promptTokens()).isEqualTo(1_000);
        assertThat(counts.cachedPromptTokens()).isEqualTo(900);
        assertThat(counts.coldPromptTokens()).isEqualTo(100);
    }

    @Test
    void shouldReadWhatTheCommonTypeOffersWhenTheProviderIsNotKnown() {
        // Given a provider reporting only the base counts — every Enterprise provider today
        AiUsageTotals counts = reader.read(new TokenUsage(500, 200, 700));

        // Then the prompt is read whole with no cached share, so all of it weighs at the cold rate. That
        // overestimates cost rather than underestimating it, which is the safe direction for a ceiling.
        assertThat(counts.promptTokens()).isEqualTo(500);
        assertThat(counts.cachedPromptTokens()).isZero();
        assertThat(counts.completionTokens()).isEqualTo(200);
        assertThat(counts.thoughtTokens()).isZero();
    }

    @Test
    void shouldReadZeroWhenTheProviderReportedNothing() {
        // Given no usage at all, which is what a turn that failed mid-stream leaves behind
        // Then nothing is claimed rather than a NullPointerException on a path that runs after every call
        assertThat(reader.read(null)).isEqualTo(AiUsageTotals.ZERO);
        assertThat(reader.read(new TokenUsage())).isEqualTo(AiUsageTotals.ZERO);
    }
}
