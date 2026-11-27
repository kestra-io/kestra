package io.kestra.core.utils;

import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.kestra.core.exceptions.InvalidSourceSearchQueryException;
import io.kestra.core.models.SourceMatch;
import io.kestra.core.models.flows.SourceSearchScope;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SourceSearchMatcherTest {

    private static final String SOURCE = """
        id: my-flow
        namespace: company.team
        tasks:
          - id: query
            type: io.kestra.plugin.gcp.bigquery.Query
            projectId: analytics-prod
          - id: reload
            type: io.kestra.plugin.gcp.bigquery.Query
            projectId: analytics-prod
        """;

    private static final String SCOPED_SOURCE = """
        id: my-flow
        namespace: company.team
        description: marker outside
        tasks:
          - id: t1
            type: io.kestra.plugin.core.debug.Return
            format: marker inside
        triggers:
          - id: trg
            type: io.kestra.plugin.core.trigger.Schedule
            cron: marker-trigger
        """;

    @Test
    void shouldFindEveryLiteralMatchCaseInsensitiveByDefault() {
        List<SourceMatch> matches = SourceSearchMatcher.findMatches(SOURCE, "BIGQUERY.QUERY", false, false, false);

        assertThat(matches).hasSize(2);
        assertThat(matches.get(0).line()).isEqualTo(5);
        assertThat(matches.get(0).snippet()).contains("[mark]bigquery.Query[/mark]");
        assertThat(matches.get(1).line()).isEqualTo(8);
    }

    @Test
    void shouldNotMatchWhenCaseSensitiveAndCaseDiffers() {
        List<SourceMatch> matches = SourceSearchMatcher.findMatches(SOURCE, "BIGQUERY.QUERY", true, false, false);

        assertThat(matches).isEmpty();
    }

    @Test
    void shouldRespectWholeWordOption() {
        String text = "id: query\nid: queryable\nid: my_query\n";

        List<SourceMatch> wholeWord = SourceSearchMatcher.findMatches(text, "query", false, true, false);
        List<SourceMatch> substring = SourceSearchMatcher.findMatches(text, "query", false, false, false);

        assertThat(wholeWord).hasSize(1);
        assertThat(wholeWord.getFirst().line()).isEqualTo(1);
        assertThat(substring).hasSize(3);
    }

    @Test
    void shouldMatchAcrossLinesWhenRegexEnabled() {
        String multiline = "concurrency:\n  limit: 4\n";

        List<SourceMatch> matches = SourceSearchMatcher.findMatches(multiline, "concurrency:\\s*\\n\\s*limit:", false, false, true);

        assertThat(matches).hasSize(1);
        assertThat(matches.getFirst().line()).isEqualTo(1);
    }

    @Test
    void shouldThrowInvalidSourceSearchQueryExceptionForInvalidRegex() {
        assertThatThrownBy(() -> SourceSearchMatcher.findMatches(SOURCE, "concurrency:(\\s*limit:", false, false, true))
            .isInstanceOf(InvalidSourceSearchQueryException.class)
            .hasMessageContaining("Unclosed group");
    }

    @Test
    void shouldRejectRegexProneToCatastrophicBacktracking() {
        assertThatThrownBy(() -> SourceSearchMatcher.ensureSafeQuery("(a+)+$", true))
            .isInstanceOf(InvalidSourceSearchQueryException.class)
            .hasMessageContaining("unsafe");
        assertThatThrownBy(() -> SourceSearchMatcher.ensureSafeQuery("(id|id)*", true))
            .isInstanceOf(InvalidSourceSearchQueryException.class);
        assertThatThrownBy(() -> SourceSearchMatcher.ensureSafeQuery("a".repeat(RegexUtils.MAX_USER_REGEX_LENGTH + 1), true))
            .isInstanceOf(InvalidSourceSearchQueryException.class);
    }

    @Test
    void shouldAcceptUnsafeRegexShapesWhenSearchedAsALiteral() {
        assertThatNoException().isThrownBy(() -> SourceSearchMatcher.ensureSafeQuery("(a+)+$", false));
        assertThat(SourceSearchMatcher.findMatches("description: (a+)+$", "(a+)+$", false, false, false)).hasSize(1);
    }

    @Test
    void shouldRejectReplacementReferencingAnUndeclaredGroup() {
        Pattern pattern = SourceSearchMatcher.toPattern("(a)(b)", false, false, true);

        assertThatThrownBy(() -> SourceSearchMatcher.ensureValidReplacement(pattern, "$9", true))
            .isInstanceOf(InvalidSourceSearchQueryException.class)
            .hasMessageContaining("only 2");
        assertThatThrownBy(() -> SourceSearchMatcher.ensureValidReplacement(pattern, "${missing}", true))
            .isInstanceOf(InvalidSourceSearchQueryException.class)
            .hasMessageContaining("missing");
        assertThatThrownBy(() -> SourceSearchMatcher.ensureValidReplacement(pattern, "${unclosed", true))
            .isInstanceOf(InvalidSourceSearchQueryException.class);
        assertThatThrownBy(() -> SourceSearchMatcher.ensureValidReplacement(pattern, "trailing\\", true))
            .isInstanceOf(InvalidSourceSearchQueryException.class);
        assertThatThrownBy(() -> SourceSearchMatcher.ensureValidReplacement(pattern, "dangling$", true))
            .isInstanceOf(InvalidSourceSearchQueryException.class);
        assertThatThrownBy(() -> SourceSearchMatcher.ensureValidReplacement(pattern, "$x", true))
            .isInstanceOf(InvalidSourceSearchQueryException.class);
    }

    @Test
    void shouldAcceptReplacementReferencingDeclaredGroups() {
        Pattern named = SourceSearchMatcher.toPattern("(?<prefix>a)(b)", false, false, true);

        assertThatNoException().isThrownBy(() -> SourceSearchMatcher.ensureValidReplacement(named, "${prefix}-$2", true));
        assertThatNoException().isThrownBy(() -> SourceSearchMatcher.ensureValidReplacement(named, "escaped \\$ and \\\\", true));
        assertThatNoException().isThrownBy(() -> SourceSearchMatcher.ensureValidReplacement(named, "$9 is fine as a literal", false));
    }

    @Test
    void shouldReturnEmptyListForBlankQuery() {
        assertThat(SourceSearchMatcher.findMatches(SOURCE, "", false, false, false)).isEmpty();
        assertThat(SourceSearchMatcher.findMatches(SOURCE, null, false, false, false)).isEmpty();
    }

    @Test
    void shouldExtractPlainLineText() {
        assertThat(SourceSearchMatcher.extractLine(SOURCE, 1)).isEqualTo("id: my-flow");
        assertThat(SourceSearchMatcher.extractLine(SOURCE, 999)).isEmpty();
    }

    @Test
    void shouldRestrictMatchesToTopLevelScope() {
        List<SourceMatch> all = SourceSearchMatcher.findMatches(SCOPED_SOURCE, "marker", false, false, false, SourceSearchScope.ALL);
        List<SourceMatch> tasksOnly = SourceSearchMatcher.findMatches(SCOPED_SOURCE, "marker", false, false, false, SourceSearchScope.TASKS);
        List<SourceMatch> triggersOnly = SourceSearchMatcher.findMatches(SCOPED_SOURCE, "marker", false, false, false, SourceSearchScope.TRIGGERS);
        List<SourceMatch> inputsOnly = SourceSearchMatcher.findMatches(SCOPED_SOURCE, "marker", false, false, false, SourceSearchScope.INPUTS);

        assertThat(all).hasSize(3);
        assertThat(tasksOnly).hasSize(1);
        assertThat(tasksOnly.getFirst().snippet()).contains("format: [mark]marker[/mark] inside");
        assertThat(triggersOnly).hasSize(1);
        assertThat(triggersOnly.getFirst().snippet()).contains("cron: [mark]marker[/mark]-trigger");
        assertThat(inputsOnly).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = { 499, 500, 501 })
    void shouldFindTaskMatchesWhenOutOfScopeMatchesReachTheLimit(int descriptionMatches) {
        String source = "id: scoped-search\nnamespace: company.team\ndescription: "
            + "needle ".repeat(descriptionMatches)
            + "\ntasks:\n  - id: log\n    type: io.kestra.plugin.core.log.Log\n    message: needle\n"
            + "triggers:\n  - id: needle\n";

        List<SourceMatch> matches = SourceSearchMatcher.findMatches(source, "needle", false, false, false, SourceSearchScope.TASKS);

        assertThat(matches).containsExactly(new SourceMatch(7, 13, "    message: [mark]needle[/mark]"));
    }

    @Test
    void shouldLimitScopedZeroWidthMatchesWhenOutOfScopeMatchesReachTheLimit() {
        String source = "description: " + "needle ".repeat(500)
            + "\ntasks:\n  - id: log\n    message: " + "needle ".repeat(501)
            + "\ntriggers:\n  - id: needle\n";

        List<SourceMatch> matches = SourceSearchMatcher.findMatches(source, "(?=needle)", false, false, true, SourceSearchScope.TASKS);

        assertThat(matches).hasSize(500);
        assertThat(matches.getFirst().line()).isEqualTo(4);
        assertThat(matches.getFirst().column()).isEqualTo(13);
        assertThat(matches.getLast().line()).isEqualTo(4);
        assertThat(matches.getLast().column()).isEqualTo(13 + "needle ".length() * 499);
        assertThat(SourceSearchMatcher.findMatches(source, "needle", false, false, false)).hasSize(500);
    }

    @Test
    void shouldReplaceOnlyWithinScope() {
        Pattern pattern = SourceSearchMatcher.toPattern("marker", false, false, false);

        String replaced = SourceSearchMatcher.replaceWithinScope(SCOPED_SOURCE, pattern, "changed", SourceSearchScope.TASKS);

        assertThat(replaced).contains("description: marker outside");
        assertThat(replaced).contains("format: changed inside");
        assertThat(replaced).contains("cron: marker-trigger");
    }
}
