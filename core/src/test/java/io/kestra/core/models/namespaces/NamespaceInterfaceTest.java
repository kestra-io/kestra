package io.kestra.core.models.namespaces;

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
}
