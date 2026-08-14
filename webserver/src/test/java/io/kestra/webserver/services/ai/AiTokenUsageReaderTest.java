package io.kestra.webserver.services.ai;

import io.kestra.core.ai.usage.models.AiUsageTotals;

import dev.langchain4j.model.googleai.GoogleAiGeminiTokenUsage;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The counts every ceiling is computed from, where a misreading is a wrong bill rather than a wrong log line. */
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

        // Then thoughts are their own count: Gemini's outputTokenCount is candidatesTokenCount and excludes
        // them, so treating the two as overlapping would drop 128 of the 147 generated tokens
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

        // Then the prompt total stays whole with the cached share alongside it: Gemini's promptTokenCount
        // already includes the cached tokens, so subtracting again would lose 900 from the total
        assertThat(counts.promptTokens()).isEqualTo(1_000);
        assertThat(counts.cachedPromptTokens()).isEqualTo(900);
        assertThat(counts.coldPromptTokens()).isEqualTo(100);
    }

    @Test
    void shouldReadWhatTheCommonTypeOffersWhenTheProviderIsNotKnown() {
        // Given a provider reporting only the base counts
        AiUsageTotals counts = reader.read(new TokenUsage(500, 200, 700));

        // Then the prompt is read whole and weighs at the cold rate — an overestimate, the safe direction
        assertThat(counts.promptTokens()).isEqualTo(500);
        assertThat(counts.cachedPromptTokens()).isZero();
        assertThat(counts.completionTokens()).isEqualTo(200);
        assertThat(counts.thoughtTokens()).isZero();
    }

    @Test
    void shouldReadZeroWhenTheProviderReportedNothing() {
        // Given no usage at all, as a turn that failed mid-stream leaves behind
        // Then nothing is claimed, rather than throwing on a path that runs after every call
        assertThat(reader.read(null)).isEqualTo(AiUsageTotals.ZERO);
        assertThat(reader.read(new TokenUsage())).isEqualTo(AiUsageTotals.ZERO);
    }
}
