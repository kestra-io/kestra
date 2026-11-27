package io.kestra.core.models.namespaces;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class NamespaceInterfaceTest {
    @ParameterizedTest
    @ValueSource(strings = { "team", "0team", "team.product", "company.team_1.my-project", "team._b", "team.-b" })
    void shouldAcceptNamespaceIdWithNonEmptySegments(String id) {
        assertThat(id).matches(NamespaceInterface.ID_PATTERN);
    }

    @ParameterizedTest
    @ValueSource(strings = { "team.", "team..product", ".team", "Team", "_team", "team product", "" })
    void shouldRejectNamespaceIdWithAnEmptySegmentOrInvalidCharacter(String id) {
        assertThat(id).doesNotMatch(NamespaceInterface.ID_PATTERN);
    }

    @Test
    void shouldExpandNamespaceIntoItsAncestorsFromRoot() {
        assertThat(NamespaceInterface.asTree("company.team.project")).containsExactly("company", "company.team", "company.team.project");
        assertThat(NamespaceInterface.asTree("company")).containsExactly("company");
    }

    @Test
    void shouldMatchSelfAndDescendantsOnly() {
        assertThat(NamespaceInterface.isDescendantOrSelf("prod", "prod")).isTrue();
        assertThat(NamespaceInterface.isDescendantOrSelf("prod", "prod.team")).isTrue();
        assertThat(NamespaceInterface.isDescendantOrSelf("prod", "prod.team.a")).isTrue();
        assertThat(NamespaceInterface.isDescendantOrSelf("prod", "prod2")).isFalse();
        assertThat(NamespaceInterface.isDescendantOrSelf("prod", "production.team")).isFalse();
        assertThat(NamespaceInterface.isDescendantOrSelf("prod.team", "prod")).isFalse();
    }

    @Test
    void shouldNotMatchASiblingWhenTheParentEndsWithADot() {
        assertThat(NamespaceInterface.isDescendantOrSelf("prod.", "prod.")).isTrue();
        assertThat(NamespaceInterface.isDescendantOrSelf("prod.", "prod.team")).isFalse();
        assertThat(NamespaceInterface.isDescendantOrSelf("prod.", "prod")).isFalse();
    }

    @Test
    void shouldNotMatchWhenANamespaceIsNull() {
        assertThat(NamespaceInterface.isDescendantOrSelf(null, "prod")).isFalse();
        assertThat(NamespaceInterface.isDescendantOrSelf("prod", null)).isFalse();
    }
}
