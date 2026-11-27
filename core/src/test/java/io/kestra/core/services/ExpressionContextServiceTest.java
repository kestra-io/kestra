package io.kestra.core.services;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.kestra.core.exceptions.KestraRuntimeException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExpressionContextServiceTest {

    @Test
    void shouldFailWhenTwoContributorsProvideTheSameCategory() {
        List<ExpressionContributor> contributors = List.of(secretContributor(), secretContributor());

        assertThatThrownBy(() -> new ExpressionContextService(contributors))
            .isInstanceOf(KestraRuntimeException.class)
            .hasMessageContaining("'SECRETS'");
    }

    private static ExpressionContributor secretContributor() {
        return new ExpressionContributor() {
            @Override
            public ExpressionCategory category() {
                return ExpressionCategory.SECRETS;
            }

            @Override
            public List<String> contribute(ExpressionRequest request) {
                return List.of();
            }
        };
    }
}
