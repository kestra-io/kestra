package io.kestra.core.models.namespaces;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NamespaceInterfaceTest {
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
    void shouldNotMatchWhenANamespaceIsNull() {
        assertThat(NamespaceInterface.isDescendantOrSelf(null, "prod")).isFalse();
        assertThat(NamespaceInterface.isDescendantOrSelf("prod", null)).isFalse();
    }
}
